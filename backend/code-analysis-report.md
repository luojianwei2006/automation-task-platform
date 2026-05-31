# 后端代码分析报告

> 生成时间：2026-05-24  
> 分析范围：`/backend` 全部子模块

---

## 一、模块总览

| # | 模块 | 端口 | 职责 | 完成度 |
|---|------|------|------|--------|
| 0 | **task-common** | — | 公共工具类、异常定义、统一响应体、JWT工具 | ✅ 完整 |
| 1 | **task-gateway** | 8080 | API网关、JWT鉴权、路由转发、CORS | ✅ 基本完整 |
| 2 | **task-user-service** | 8081 | 用户注册/登录、实名认证、邀请返佣 | ✅ 基本完整 |
| 3 | **task-task-service** | 8083 | 任务发布/接单/审核/奖励发放 | ⚠️ 部分完成 |
| 4 | **task-pay-service** | 8083 | 充值、提现、打款、对账 | ❌ 空壳 |
| 5 | **task-admin-api** | 8084 | 管理后台RBAC、用户管理、任务审核 | ✅ 基本完整 |
| 6 | **task-job** | 8085 | 定时任务（邀请返佣结算、提现重试） | ❌ 空壳 |

**子服务数量：6个（不含公共模块 task-common）**

---

## 二、技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.1.5 | 应用框架 |
| Spring Cloud Gateway | 2022.0.4 | API网关 |
| MyBatis-Plus | 3.5.7 | ORM |
| MySQL | 8.0.33 | 数据库 |
| Druid | 1.2.20 | 连接池 |
| Redis (Spring Data) | — | 缓存 / 限流 / Session |
| JJWT | 0.12.3 | JWT Token生成与校验 |
| Spring Security | — | RBAC权限控制（admin-api/task-service） |
| RabbitMQ | — | 事件驱动（已引入依赖，未使用） |
| Quartz | — | 定时任务（job模块，未实现） |
| Hutool | 5.8.25 | 工具类 |
| Lombok | 1.18.30 | 简化Java代码 |
| FastJSON2 | 2.0.43 | JSON序列化 |

---

## 三、各模块详细分析

### 3.1 task-common（公共模块）

**包结构：**
```
com.task.platform.common
├── exception
│   ├── BusinessException.java     # 业务异常
│   └── GlobalExceptionHandler.java # 全局异常处理
├── response
│   ├── ApiResponse.java          # 统一响应体
│   └── ErrorCode.java            # 错误码枚举
└── utils
    ├── JwtUtil.java              # JWT工具类
    └── PasswordUtil.java          # 密码加密工具
```

**评价：**
- ✅ `ApiResponse<T>` 设计规范，支持泛型、时间戳、静态工厂方法
- ✅ `GlobalExceptionHandler` 统一捕获业务异常
- ⚠️ **`JwtUtil.java` 第22行：JWT密钥硬编码**，应移至配置文件
- ⚠️ `JwtUtil.main()` 方法残留测试代码，应删除

---

### 3.2 task-gateway（API网关）

**关键文件：**
- `JwtAuthGlobalFilter.java` — JWT全局鉴权过滤器

**路由配置（application.yml）：**

| 路由ID | 路径匹配 | 目标服务 | StripPrefix |
|---------|-----------|------------|-------------|
| task-user-service | `/api/user/**` | localhost:8081 | 1 |
| task-user-info-service | `/api/user-info/**` | localhost:8081 | 1 |
| task-task-service | `/api/task/**` | localhost:8083 | 1 |
| task-pay-service | `/api/pay/**` | localhost:8083 | 1 |
| task-admin-api | `/api/admin/**` | localhost:8084 | 1 |

**白名单（无需登录）：**
- `POST /api/user/auth/login`
- `POST /api/user/auth/sms-login`
- `POST /api/user/auth/register`
- `POST /api/user/auth/sms-code`
- `POST /api/admin/auth/login`
- `GET /api/task/tasks`（仅GET）

**发现问题：**
1. ⚠️ **端口冲突**：`task-service` 和 `pay-service` 都路由到 `localhost:8083`
2. ⚠️ **CORS配置 `allowedOriginPatterns: "*"`** 通配符在生产环境不安全，应指定具体域名
3. ⚠️ **未授权响应体JSON格式错误**：`JwtAuthGlobalFilter.java` 第130行 `\"message\"` 转义错误，应为 `\"message\"`

---

### 3.3 task-user-service（用户服务）

**API接口：**

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/user/auth/sms/send` | 发送短信验证码 |
| POST | `/user/auth/register` | 注册（密码+验证码） |
| POST | `/user/auth/login` | 密码登录 |
| POST | `/user/auth/login/sms` | 验证码登录 |
| POST | `/user/auth/password/reset` | 重置密码 |
| POST | `/user/auth/token/refresh` | 刷新Token |

**关键实现：**
- ✅ 短信验证码使用Redis双Key设计（验证码Key + 限频Key）
- ✅ 邀请码生成（BASE62编码，排除易混淆字符）
- ✅ 手机号脱敏处理
- ✅ 验证码登录自动注册

**发现问题：**
1. 🔴 **`UserService.java` 第186行：使用 `Math.random()` 生成验证码**，不安全，应使用 `SecureRandom`
2. ⚠️ **腾讯云SMS未集成**（`sendSmsCode` 方法仅打印日志）
3. ⚠️ **`AuthController.java` 第5行：import拼写错误** `jakarta.validation.constraints.NotBlank` 应为 `jakarta.validation.constraints.NotBlank`（少了个 `r`）
4. ⚠️ **Redis配置**：`host: localhost`，生产环境应为实际Redis服务器地址

---

### 3.4 task-task-service（任务服务）

**API接口：**

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| POST | `/task` | 发布任务 | SUPER_ADMIN/MERCHANT_ADMIN |
| GET | `/task` | 任务列表（分页） | SUPER_ADMIN/MERCHANT_ADMIN |
| GET | `/task/{taskId}` | 任务详情 | SUPER_ADMIN/MERCHANT_ADMIN |
| PUT | `/task/{taskId}/status` | 上下架任务 | MERCHANT_ADMIN |
| GET | `/user-task/tasks` | 用户端任务大厅 | 公开 |
| POST | `/user-task/tasks/{taskId}/accept` | 接受任务 | USER |
| POST | `/user-task/records/{recordId}/submit` | 提交截图 | USER |
| POST | `/user-task/records/{recordId}/review` | 审核截图 | SUPER_ADMIN |

**关键实现：**
- ✅ 任务配额管理（`totalQuota` / `usedQuota`）
- ✅ 截止时间检查（截止前1小时禁止接取）
- ✅ 提交次数限制（最多2次）
- ✅ 超时任务自动放弃（`processTimeoutTasks`）
- ✅ 商户只能操作自己的任务（权限控制）

**发现问题：**
1. ⚠️ **奖励发放未实现**：`reviewTaskRecord` 方法有 `// TODO: 调用支付服务发放奖励`
2. ⚠️ **AI审核未集成**：`submitTask` 方法有 `// TODO: 预留AI审核接口调用`
3. ⚠️ **`TaskController.java` 使用了 `JwtClaims` 作为 `@AuthenticationPrincipal`**，需要确认 `JwtClaims` 类是否正确实现了 `UserDetails` 接口
4. ⚠️ **`task-task-service/pom.xml` 中JJWT版本为 `0.11.5`**，而根POM定义的是 `0.12.3`，版本不一致

---

### 3.5 task-pay-service（支付服务）

**状态：❌ 空壳**

- 只有 `PayServiceApplication.java` 启动类
- **无任何 Controller / Service / Mapper 实现**
- `pom.xml` 中标注了需要手动添加微信支付SDK和支付宝SDK依赖（未添加）

**待实现功能（根据描述）：**
- 充值接口（微信支付 / 支付宝）
- 提现接口
- 奖励打款
- 对账功能

---

### 3.6 task-admin-api（管理后台API）

**API接口：**

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| POST | `/admin/auth/login` | 管理员登录 | 公开 |
| GET | `/admin/users` | 用户列表（分页+筛选） | SUPER_ADMIN/MERCHANT_ADMIN/FINANCE |
| GET | `/admin/users/{userId}` | 用户详情 | SUPER_ADMIN/MERCHANT_ADMIN |
| PUT | `/admin/users/{userId}/status` | 封禁/解封用户 | SUPER_ADMIN |
| GET | `/admin/users/{userId}/real-auth` | 实名认证详情 | SUPER_ADMIN/MERCHANT_ADMIN |
| POST | `/admin/users/{userId}/real-auth/review` | 实名认证审核 | SUPER_ADMIN |
| POST | `/admin/users` | 管理员新增C端用户 | SUPER_ADMIN |
| PUT | `/admin/users/{userId}` | 管理员编辑C端用户 | SUPER_ADMIN |
| GET | `/admin/tasks` | 任务列表 | SUPER_ADMIN/MERCHANT_ADMIN |
| POST | `/admin/tasks/review` | 任务审核 | SUPER_ADMIN |
| GET | `/admin/statistics/dashboard` | 数据看板 | SUPER_ADMIN/FINANCE |

**关键实现：**
- ✅ RBAC权限控制（`@PreAuthorize` 注解）
- ✅ 手机号脱敏（列表接口脱敏，详情接口不过敏）
- ✅ 身份证脱敏（保留前6位和后4位）
- ✅ BCrypt密码加密

**发现问题：**
1. ⚠️ **`AdminUserController.java` 直接使用了 `AppUserMapper`**，应改为调用 `AdminUserService` 以保持层次清晰
2. ⚠️ **统计接口（`StatisticsController`）** 需要确认是否已实现数据聚合逻辑
3. ⚠️ **文件上传路径硬编码**：`application.yml` 中 `file.upload.path` 为本地绝对路径，生产环境需调整

---

### 3.7 task-job（定时任务）

**状态：❌ 空壳**

- 只有 `JobApplication.java` 启动类
- 引入了 `spring-boot-starter-quartz` 依赖，但**无任何 Job 实现**
- **待实现功能（根据描述）：**
  - 邀请返佣结算（每日）
  - 提现重试（失败重试）
  - 数据报表生成（每日/每周）

---

## 四、配置问题分析

### 4.1 端口冲突 🔴

| 服务 | 配置端口 | 状态 |
|------|-----------|------|
| task-task-service | 8083 | 冲突 |
| task-pay-service | 8083 | 冲突 |

**建议**：将 `task-pay-service` 改为 `8086`

### 4.2 数据库配置不一致 ⚠️

| 服务 | 数据库Host | 用户名 |
|------|-------------|----------|
| user-service | `118.145.198.135:3306` | `task_user` |
| task-service | `118.145.198.135:3306` | `task_user` |
| admin-api | `118.145.198.135:3306` | `task_user` |
| pay-service | `localhost:3306` | `root` ⚠️ |
| job | `localhost:3306` | `root` ⚠️ |

**建议**：统一使用远程数据库 `118.145.198.135:3306`，或使用配置中心统一管理

### 4.3 Redis配置不一致 ⚠️

| 服务 | Redis Host | Database |
|------|-------------|----------|
| user-service | `localhost:6379` | 0 |
| task-service | `192.168.10.11:6379` | 2 |
| admin-api | `192.168.10.11:6379` | 1 |
| pay-service | `localhost:6379` | 2 |
| job | `localhost:6379` | 4 |

**建议**：统一Redis地址，或使用配置中心

### 4.4 JWT密钥管理 🔴

JWT密钥在以下位置硬编码：
1. `JwtUtil.java` 第22行
2. 所有服务的 `application.yml` 中的 `jwt.secret`

**建议**：使用配置中心（Nacos/Apollo）或环境变量管理密钥

---

## 五、代码质量问题汇总

### 🔴 严重问题

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | 端口冲突（task-service & pay-service 都使用8083） | `application.yml` | 服务启动失败 |
| 2 | `Math.random()` 生成验证码 | `UserService.java:186` | 验证码可被预测 |
| 3 | JWT密钥硬编码 | `JwtUtil.java:22` + 所有 `application.yml` | 安全风险 |
| 4 | 未授权响应JSON格式错误（转义字符问题） | `JwtAuthGlobalFilter.java:130` | 网关鉴权失败时返回非法JSON |

### ⚠️ 中等问题

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | 短信发送未集成（仅打印日志） | `UserService.java:203` | 用户无法收到验证码 |
| 2 | 奖励发放未实现（TODO标注） | `TaskService.java:391` | 用户完成任务后无奖励 |
| 3 | AI审核未集成（TODO标注） | `TaskService.java:333` | 截图审核需人工操作 |
| 4 | `task-task-service/pom.xml` JJWT版本不一致 | `pom.xml` | 可能引发兼容性问题 |
| 5 | CORS `allowedOriginPatterns: "*"` | `gateway application.yml` | 生产环境跨域安全风险 |
| 6 | 数据库配置不一致（部分使用localhost） | 各服务 `application.yml` | 部署时连接失败 |
| 7 | Redis配置不一致 | 各服务 `application.yml` | 部署时连接失败 |
| 8 | `AuthController.java` import拼写错误 | `AuthController.java:5` | 编译可能失败 |

### ℹ️ 轻微问题

| # | 问题 | 位置 |
|---|------|------|
| 1 | `JwtUtil.main()` 测试代码未删除 | `JwtUtil.java:161-179` |
| 2 | 文件上传路径硬编码为本地路径 | `admin-api application.yml` |
| 3 | 缺少API文档（Swagger/OpenAPI） | 全局 |
| 4 | 缺少单元测试 | 全局 |
| 5 | RabbitMQ依赖已引入但未使用 | `user-service`, `task-service`, `pay-service` |

---

## 六、完成度评估

| 模块 | 完成度 | 说明 |
|------|--------|------|
| task-common | 95% | 基本完整，JWT密钥应移至配置 |
| task-gateway | 85% | 路由配置完成，JSON响应格式有Bug |
| task-user-service | 80% | 核心功能完成，短信发送未集成 |
| task-task-service | 70% | 核心流程完成，奖励发放/AI审核未实现 |
| task-pay-service | 0% | 空壳，需完整实现 |
| task-admin-api | 75% | 管理接口基本完成，统计功能需确认 |
| task-job | 0% | 空壳，需完整实现 |

**整体完成度约：50-60%**

---

## 七、改进建议

### 优先级 P0（立即修复）

1. **修复端口冲突**：将 `task-pay-service` 端口改为 `8086`
2. **修复验证码生成**：将 `Math.random()` 改为 `SecureRandom`
3. **修复网关JSON响应格式**：`JwtAuthGlobalFilter.java` 第130行
4. **统一JJWT版本**：`task-task-service/pom.xml` 移除硬编码版本号
5. **修复 `AuthController.java` import拼写错误**

### 优先级 P1（近期完成）

1. **集成腾讯云SMS**：实现 `sendSmsCode` 方法中的TODO
2. **实现奖励发放**：集成支付服务（或先模拟）
3. **统一数据库/Redis配置**：使用配置中心或环境变量
4. **JWT密钥移至配置**：从环境变量或配置中心读取

### 优先级 P2（迭代优化）

1. **实现 `task-pay-service`**：微信支付/支付宝SDK集成
2. **实现 `task-job`**：Quartz定时任务
3. **集成AI审核**：截图自动审核
4. **添加API文档**：Swagger/OpenAPI 3
5. **添加单元测试**：核心Service层
6. **CORS精细化配置**：指定允许的域名

---

## 八、关于项目约定（来自工作记忆）

1. ✅ **JWT密钥已统一**：所有服务使用相同 `jwt.secret`
2. ✅ **Gateway StripPrefix=1 影响已确认**：下游服务的SecurityConfig已匹配去掉前缀后的路径
3. ✅ **Kotlin 2.0.21 + KSP**：Android端已规避kapt兼容性问题和Hilt DI问题
4. ⚠️ **AMap SDK已修正**：Lite版方法名为 `getMapAsyn`（非标准 `getMapAsync`）

---

*报告结束*

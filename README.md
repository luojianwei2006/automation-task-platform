# 自动化任务平台 (Automation Task Platform)

短视频 / 社交媒体**自动化任务接单平台**：商户下发视频发布任务，用户领取后在 App 内借助自动化引擎完成剪辑 / 发布 / 提交审核，平台审核后发放奖励；商户侧通过管理后台进行任务管理与结算。

> 仓库包含三大端：**后端微服务**（Spring Boot 多模块）、**安卓客户端**（Kotlin + Jetpack Compose）、**管理后台前端**（Vue 3）。

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot **3.1.5** / Java 17、Spring Security + JWT、Spring Cloud Gateway **2022.0.4**、Redis、MyBatis-Plus **3.5.7**、MySQL 8.0 |
| 安卓 | Kotlin **2.0.21** / Jetpack Compose、Hilt、MVVM、Retrofit + OkHttp、DataStore |
| 管理后台 | Vue 3 + TypeScript + Element Plus 2.x + Pinia |
| 统一上传 | `task-upload-service`（本地存储，预留 OSS / COS） |

---

## 模块结构

### 后端（`backend/`，Spring Boot 多模块 Maven 工程）

| 模块 | 端口 | 职责 |
|------|------|------|
| `task-gateway` | 8085 | API 网关 + JWT 鉴权（StripPrefix=1 转发下游） |
| `task-user-service` | 8081 | 用户注册/登录、实名认证、收益与提现（余额入账 `t_user_earnings`）、钱包绑定、系统配置 `sys_config` |
| `task-task-service` | 8082 | 任务 CRUD、领取、提交、截图审核（审核通过经内部接口入账奖励） |
| `task-admin-api` | 8084 | 管理后台 API（RBAC、商户、发布任务、交易结算、审核上架/下架、系统设置） |
| `task-upload-service` | 8086 | 统一文件上传（本地存储，预留 OSS/COS） |
| `task-common` | — | 公共模块（Result、枚举、工具类） |
| `task-job` | — | 定时任务 |

> ⚠️ 原 `task-pay-service`（奖励自动发放）已在「奖励入账改造」中整体删除：奖励改为审核通过时由 `task-user-service` 内部接口 `POST /internal/earnings/credit` 直接写入用户虚拟余额（`t_user_earnings`），不再有独立发奖模块。

### 安卓客户端（`android/`）

Kotlin + Jetpack Compose，Hilt 依赖注入，MVVM 架构。四大底部 Tab：**任务大厅 / 广告大厅 / 收益中心 / 个人中心**。

### 管理后台（`frontend/admin-frontend/`）

Vue 3 + TypeScript + Element Plus + Pinia。商户/平台运营后台，含系统设置（`sys_config` 在线编辑：上传域名、API 基址、App 名称等）。

---

## 核心业务模型：奖励与结算

- **奖励入账**：任务 / 发布记录审核通过 → `task-user-service` 内部接口 `POST /internal/earnings/credit` 按 `biz_id` 幂等写入用户虚拟余额表 `t_user_earnings`（收入 type 1~4）。平台不在审核时自动打款。
- **提现**：用户发起提现 → 后台审核通过 → 线下转账；提现支出同样写入 `t_user_earnings`（type=5，金额为负）。
- **流水查询**：C 端 `GET /api/user/earnings/records` 返回全部余额变动（收入 + 支出），「收益中心」明细与「我的 → 流水记录」共用此接口。**差异**：「收益中心」仅展示奖励记录（显示层过滤提现 type=5），「流水记录」为完整账单（含提现支出）。
- 详见 `数据库设计文档_v1.0.md` 的 `t_user_earnings` 表。

---

## 安卓端功能完成度（截至 2026-07-12）

### ✅ 已完成页面（UI + 接口调用齐全）

| 页面 | 状态 | 说明 |
|------|------|------|
| 登录 / 注册 / 实名认证 | ✅ | UI 与调用齐全；实名照片上传待后端接口 |
| 任务大厅 / 任务详情 / 我的任务 / 截图上传 | ✅ | 完整闭环（浏览→领取→提交截图→审核） |
| 广告大厅 | ✅ | 复用任务大厅逻辑 |
| 发布任务大厅 / 发布详情 / 提交审核 / 合并历史 | ✅ | 完整；列表状态精确映射 6 态 |
| 收益中心 | ✅ | 概览 + 奖励明细列表（类型筛选：全部/任务收益/邀请奖励/其他，已移除「提现」Tab，仅展示奖励 type≠5）+ 加载更多；「流水记录」为独立页（含提现） |
| 个人中心 / 关于页 / 钱包绑定 | ✅ | UI 完整 |

### ⚠️ 已知缺口（安卓端）

1. **自动化引擎截图上传硬编码本地地址** —— `service/XhsAutomator.kt`、`service/DouyinAutomator.kt` 写死 `http://10.0.2.2:8086/upload/image`（仅模拟器对宿主机的 localhost 映射），真机 / 远程后端上传必然失败。应改为读取 `sys_config.upload_domain`。
2. **图片 URL 模拟器专用替换** —— `ui/publish/PublishScreen.kt`、`ui/task/TaskDetailScreen.kt` 把 `localhost/127.0.0.1` 替换成 `10.0.2.2`，远程后端环境图片会显示破图。

---

## 后端已知缺口（影响端到端可用）

- 实名认证照片上传 + 审核接口待完成（安卓 `RealAuthScreen` 已写，后端上传/审核待补齐）
- 截图上传 COS 集成待做（`task-upload-service` 已建，目前仅本地存储）
- 身份证存储为明文占位 —— 后端 `t_user.id_card` 以 `[ENCRYPTED]` 前缀 + 明文 18 位存储（非真加密），存在数据安全风险，后续应接真 AES
- 自动化引擎（XhsAutomator / DouyinAutomator / WechatVideoAutomator）端到端是否真能跑通需实测确认

> 已实装：C 端收益 / 提现 API、任务 accept / submit、奖励余额入账（内部接口）、`sys_config` 在线配置、端口冲突（task 用 8082，原 8083 冲突已消除）。

---

## 快速开始

### 后端

```bash
cd backend
mvn clean package -DskipTests
# 各模块独立启动（建议配合 Redis + MySQL），或自行编排 docker-compose
```

### 安卓

```bash
cd android
./gradlew assembleDebug
# 模拟器调试：需用本机后端并允许 10.0.2.2:808X 访问
```

### 管理后台

```bash
cd frontend/admin-frontend
npm install
npm run dev
```

---

## 环境与约定

- **JWT 密钥**：所有微服务必须共用同一个 `jwt.secret`。
- **网关路径**：Gateway 去掉 `/api` 前缀后，下游 SecurityConfig 必须匹配去掉前缀后的路径（如 `/task/**` 而非 `/api/task/**`）。
- **上传 URL 格式**：统一为 `/api/upload/uploads/{type}/{uuid}.ext`。
- **系统配置 `sys_config`**：`upload_domain` / `api_base_url` / `app_name` 等全局配置由管理后台「系统设置」在线维护；安卓端截图上传 / 图片地址应读取 `upload_domain` 而非硬编码。

---

## License

内部项目，未开源。

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
| `task-user-service` | 8081 | 用户注册/登录、收益、提现、钱包绑定 |
| `task-task-service` | 8082 | 任务 CRUD、领取、提交、截图审核 |
| `task-pay-service` | 8083 | 奖励发放【当前为占位空壳】 |
| `task-admin-api` | 8084 | 管理后台 API（RBAC、商户、发布任务、交易结算） |
| `task-upload-service` | 8086 | 统一文件上传（本地存储，预留 OSS/COS） |
| `task-common` | — | 公共模块（Result、枚举、工具类） |
| `task-job` | — | 定时任务 |

### 安卓客户端（`android/`）

Kotlin + Jetpack Compose，Hilt 依赖注入，MVVM 架构。四大底部 Tab：任务大厅 / 发布任务 / 收益中心 / 个人中心。

### 管理后台（`frontend/admin-frontend/`）

Vue 3 + TypeScript + Element Plus + Pinia。商户/平台运营后台。

---

## 安卓端功能完成度（截至 2026-07-10）

### ✅ 已完成页面（UI + 接口调用齐全）

| 页面 | 状态 | 说明 |
|------|------|------|
| 登录 / 注册 / 实名认证 | ✅ | UI 与调用齐全；实名照片上传待后端接口 |
| 任务大厅 / 任务详情 / 我的任务 / 截图上传 | ✅ | 完整闭环（浏览→领取→提交截图→审核） |
| 广告大厅 | ✅ | 复用任务大厅逻辑 |
| 发布任务大厅 / 发布详情 / 提交审核 / 合并历史 | ✅ | 完整；列表状态已精确映射 6 态 |
| 收益中心 / 个人中心 | ✅ | 收益概览/明细、钱包绑定 UI 完整 |

### ⚠️ 已知缺口

**安卓端代码层本身未完成：**
1. **关于页面未实现** —— `ui/profile/SettingsScreen.kt:110` 的 `/* TODO: 关于页面 */` 仍是死链。
2. **自动化引擎截图上传硬编码本地地址** —— `service/XhsAutomator.kt:1912`、`service/DouyinAutomator.kt:1015` 写死 `http://10.0.2.2:8086/upload/image`（仅安卓模拟器对宿主机的 localhost 映射），真机 / 远程后端环境上传必然失败。
3. **图片 URL 模拟器专用替换** —— `ui/publish/PublishScreen.kt:1813`、`ui/task/TaskDetailScreen.kt:632` 把 `localhost/127.0.0.1` 替换成 `10.0.2.2`，远程后端环境图片会显示破图。

**安卓侧已写完、但后端 stub / 缺失导致实际跑不通（对接缺口）：**
4. 任务奖励发放 —— `task-pay-service` 是空壳，奖励不会真正入账。
5. C 端收益 / 提现 —— 安卓 `EarningsViewModel` / `WithdrawScreen` 已完整对接 `api/user/withdraw/*`、`api/user/earnings/*`；需确认 `task-user-service` 是否已实装这些接口。
6. 实名认证照片上传 + 审核 —— `RealAuthScreen` + `uploadIdCardImage` 已写，后端上传/审核接口待完成。
7. 任务接受 / 提交 —— 安卓 `acceptTask` / `submit` 接口齐全（`ApiClient.kt` 已定义），后端 `task-task-service` 的 accept/submit 仍是 stub。
8. 截图上传 COS 集成 —— `task-upload-service` 已建，但 COS 集成待做，截图可能只存本地未上云。

**需实测确认：**
9. 自动化引擎（XhsAutomator / DouyinAutomator / WechatVideoAutomator）端到端是否真能跑通，还是仅接了壳。

---

## 后端已知缺口（影响端到端可用）

- `task-pay-service` 空壳 —— 奖励发放未实装
- C 端收益 / 提现 API 待确认 `task-user-service` 实装
- 实名认证照片上传 + 审核接口待完成
- 任务 accept / submit 为 stub
- 截图上传 COS 集成待做
- 端口冲突：`task-task-service` 与 `task-pay-service` 都用 8083（待修复）

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

---

## License

内部项目，未开源。

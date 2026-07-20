# 自动化任务平台 (Automation Task Platform)

短视频 / 社交媒体**自动化任务接单平台**：商户下发视频发布任务，用户领取后在 App 内借助自动化引擎完成剪辑 / 发布 / 提交审核，平台审核后发放奖励；商户侧通过管理后台进行任务管理与结算。**视频剪辑能力**由后端 FFmpeg 渲染引擎（指令驱动）+ 安卓端本地编辑/预览器协同提供。

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
| `task-user-service` | 8081 | 用户注册/登录、实名认证、收益与提现（余额入账 `t_user_earnings`）、钱包绑定、系统配置 `sys_config`、协议读接口（匿名 `GET /api/user/agreements/{type}`） |
| `task-task-service` | 8082 | 任务 CRUD、领取、提交、截图审核（审核通过经内部接口入账奖励） |
| `task-admin-api` | 8084 | 管理后台 API（RBAC、商户、发布任务、交易结算、审核上架/下架、系统设置、协议管理写接口）+ 视频编辑渲染（`VideoEditController`/`VideoEditService`，FFmpeg 指令驱动） |
| `task-upload-service` | 8086 | 统一文件上传：图片 `POST /upload/image`（5MB）+ 视频 `POST /upload/video`（200MB，本地存储，预留 OSS/COS） |
| `task-common` | — | 公共模块（Result、枚举、工具类；协议实体 `Agreement` / Mapper / VO） |
| `task-job` | — | 定时任务 |

> ⚠️ 原 `task-pay-service`（奖励自动发放）已在「奖励入账改造」中整体删除：奖励改为审核通过时由 `task-user-service` 内部接口 `POST /internal/earnings/credit` 直接写入用户虚拟余额（`t_user_earnings`），不再有独立发奖模块。

### 安卓客户端（`android/`）

Kotlin + Jetpack Compose，Hilt 依赖注入，MVVM 架构。四大底部 Tab：**任务大厅 / 广告大厅 / 收益中心 / 个人中心**。另含独立**视频编辑器**（`VideoEditorScreen`，多片段拼接 + 转场 + 渐入渐出 + 字幕，基于 Media3 本地预览/生成，结果保存到相册）。

### 管理后台（`frontend/admin-frontend/`）

Vue 3 + TypeScript + Element Plus + Pinia。商户/平台运营后台，含系统设置（`sys_config` 在线编辑：上传域名、API 基址、App 名称等）与**视频编辑器**（`VideoEditor.vue`，时间轴 + 片段卡片，产出编辑指令 JSON 提交后端渲染）。

---

## 核心业务模型：奖励与结算

- **奖励入账**：任务 / 发布记录审核通过 → `task-user-service` 内部接口 `POST /internal/earnings/credit` 按 `biz_id` 幂等写入用户虚拟余额表 `t_user_earnings`（收入 type 1~4）。平台不在审核时自动打款。
- **提现**：用户发起提现 → 后台审核通过 → 线下转账；提现支出同样写入 `t_user_earnings`（type=5，金额为负）。
- **流水查询**：C 端 `GET /api/user/earnings/records` 返回全部余额变动（收入 + 支出），「收益中心」明细与「我的 → 流水记录」共用此接口。**差异**：「收益中心」仅展示奖励记录（显示层过滤提现 type=5），「流水记录」为完整账单（含提现支出）。
- 详见 `数据库设计文档_v1.0.md` 的 `t_user_earnings` 表。

---

## 协议文档功能（2026-07-13 新增）

管理后台可视化编辑、安卓端 WebView 展示三份平台文档（**关于我们 / 隐私协议 / 注册协议**），采用 HTML5 富文本（wangEditor）。

- **存储**：独立表 `t_agreement`（`type` 唯一索引：about / privacy / register，`content_html` 存完整 HTML）。
- **后端接口**：
  - 写（管理后台，RBAC 超管/商户管理员）：`POST /api/admin/agreements`、`GET /api/admin/agreements?type=`
  - 读（匿名，网关白名单放行 `GET /api/user/agreements/**`）：`GET /api/user/agreements/{type}`
- **管理后台**：新增「协议管理」页面（el-tabs 切换三份、wangEditor 编辑、图片自定义上传复用统一上传服务、预览弹窗）。
- **安卓端**：`AgreementScreen`（WebView 按 HTML5 原样渲染）由三处入口进入 —— 个人中心「关于我们」、登录/注册页「用户协议」「隐私政策」可点文字。

> ⚠️ 协议内容需由运营在管理后台填写，或执行 `backend/sql/agreement_seed_data.sql` 初始化；库为空时安卓端显示"暂无协议内容"。

---

## 视频剪辑系统（2026-07-19/20 新增）

对标抖音/剪映的「指令驱动渲染」视频剪辑能力，由**后端 FFmpeg 渲染引擎** + **安卓端本地编辑器** + **管理后台编辑器** 三端协同。

### 架构：指令驱动渲染

前端/安卓只产出"编辑时间线 JSON 指令"，后端 `VideoEditService` 解析为 FFmpeg `filter_complex` 链统一渲染，沿用"提交 → 异步轮询 → 拿结果 URL"范式（与原 `MergePreviewService` 一脉相承）。

```
[管理后台 Vue3] ─┐ 编辑指令 JSON
                 ├─→ [后端 VideoEditService] ──FFmpeg──→ 结果URL(t_video_edit_task)
[安卓端 Compose] ─┘                                  ↑轮询/预览
```

### 数据结构

- **`t_video_edit_task`**（`backend/sql/video_edit_task.sql`）：`instruction_json` 存完整 `EditInstruction`，`status` 流转 `PENDING→PROCESSING→COMPLETED/FAILED`，`result_url` 为渲染产物访问路径。
- **`EditInstruction`** 指令结构（`backend/task-admin-api/.../dto/publish/VideoEditInstruction.java`）：
  - `timeline.segments[]`：每段含 `src` / `trim` / `rotate` / `mirror` / `crop` / `speed` / `volume` / `filterPreset` / `overlays[]`
  - `timeline.transitions[]`：xfade 转场（type + duration）
  - `timeline.subtitles[]`：drawtext 字幕（text/start/end/size/color/position/align）
  - `audio`：`originalVolume` + `bgm[]`（amix 混音 + 淡入淡出）+ `voiceover[]`（adelay 配音轨）
  - `output.ratio`：画幅 `9:16` / `1:1` / `16:9`

### 后端渲染能力（FFmpeg filter_complex）

| 能力 | 实现 |
|------|------|
| 裁剪/分割 | `trim` + `setpts=PTS-STARTPTS` |
| 旋转/镜像 | `rotate` / `hflip` |
| 调速 | `atempo`（视频 `setpts` 配合） |
| 画幅 | `scale` + `pad` letterbox，9:16→1080×1920 / 1:1→1080×1080 / 16:9→1920×1080 |
| 转场 | 多段 `xfade` 链 + 音频 `acrossfade` 链；无转场则 `concat` |
| 滤镜预设 | `FilterPreset` 枚举（亮度/对比度/饱和度 eq 链） |
| 字幕 | `drawtext`（黑边 + 位置 + 启用区间） |
| 贴纸/画中画 | 图片 `-i` + `overlay`（按时段启用） |
| 音频 | 原声 `volume` + BGM `amix` 循环 + 配音 `adelay` |

### 视频上传接口

`POST /api/upload/video`（`task-upload-service`）：`MultipartFile`，校验 `video/*` + 扩展名（mp4/mov/avi/mkv/webm/3gp）+ 200MB 上限，存 `/upload/uploads/video/{uuid}.ext`。网关 `spring.codec.max-in-memory-size: 300MB`，上传服务 `max-file-size: 200MB`。

### 安卓端发布流程改造（2026-07-20）

发布任务详情页的"合并预览"整块已替换为**手动分步流程**：

```
从相册选择视频 → 点「上传视频」上传服务器得 relativePath
              → 点「发布视频」(弹说明 + 拉起对应 App)
              → 底部「提交」按钮 → 上传截图 → 提交任务
```

- `selectedMergeUrl`（视频 relativePath）由 `SubmitReviewDialog` 作为 `mergedVideoUrl` 提交审核。
- submission 状态流转：`CLAIMED`（显示"发布视频"）→ 发布后 `MERGED`（显示"提交"）→ 提交后 `SUBMITTED` → `PASSED/REJECTED`。
- 「编辑视频」入口跳转 `VideoEditorScreen` 生成/保存到相册，再回详情页选相册视频。

> ⚠️ 后端 FFmpeg 渲染依赖服务端安装 `ffmpeg` / `ffprobe`；`VideoEditService.UPLOAD_ROOT` 当前硬编码为开发机本地路径，部署时需改为配置项。

---

## 安卓端功能完成度（截至 2026-07-20）

### ✅ 已完成页面（UI + 接口调用齐全）

| 页面 | 状态 | 说明 |
|------|------|------|
| 登录 / 注册 / 实名认证 | ✅ | UI 与调用齐全；实名照片上传待后端接口 |
| 任务大厅 / 任务详情 / 我的任务 / 截图上传 | ✅ | 完整闭环（浏览→领取→提交截图→审核） |
| 广告大厅 | ✅ | 复用任务大厅逻辑 |
| 发布任务大厅 / 发布详情 / 提交审核 | ✅ | 发布详情改为「相册选视频→上传→发布→提交」分步流程；合并历史入口已移除；列表状态精确映射 6 态 |
| 视频编辑器（VideoEditorScreen） | ✅ | 多片段拼接 + 转场 + 渐入渐出 + 字幕，基于 Media3 本地预览/生成，结果保存到相册 |
| 收益中心 | ✅ | 概览 + 奖励明细列表（类型筛选：全部/任务收益/邀请奖励/其他，已移除「提现」Tab，仅展示奖励 type≠5）+ 加载更多；「流水记录」为独立页（含提现） |
| 协议文档页（关于我们 / 用户协议 / 隐私政策） | ✅ | WebView 按 HTML5 原样渲染；个人中心「关于我们」、登录/注册页「用户协议」「隐私政策」三入口接匿名读接口 |
| 个人中心 / 钱包绑定 | ✅ | UI 完整；「关于我们」入口跳协议接口 |

### ⚠️ 已知缺口（安卓端）

1. **自动化引擎截图上传硬编码本地地址** —— `service/XhsAutomator.kt`、`service/DouyinAutomator.kt` 写死 `http://10.0.2.2:8086/upload/image`（仅模拟器对宿主机的 localhost 映射），真机 / 远程后端上传必然失败。应改为读取 `sys_config.upload_domain`。
2. **图片 URL 模拟器专用替换** —— `ui/publish/PublishScreen.kt`、`ui/task/TaskDetailScreen.kt` 把 `localhost/127.0.0.1` 替换成 `10.0.2.2`，远程后端环境图片会显示破图。

---

## 后端已知缺口（影响端到端可用）

- 实名认证照片上传 + 审核接口待完成（安卓 `RealAuthScreen` 已写，后端上传/审核待补齐）
- 截图上传 COS 集成待做（`task-upload-service` 已建，目前仅本地存储）
- 身份证存储为明文占位 —— 后端 `t_user.id_card` 以 `[ENCRYPTED]` 前缀 + 明文 18 位存储（非真加密），存在数据安全风险，后续应接真 AES
- 自动化引擎（XhsAutomator / DouyinAutomator / WechatVideoAutomator）端到端是否真能跑通需实测确认

> 已实装：C 端收益 / 提现 API、任务 accept / submit、奖励余额入账（内部接口）、`sys_config` 在线配置、端口冲突（task 用 8082，原 8083 冲突已消除）、**视频上传接口 `/api/upload/video`**、**视频编辑 FFmpeg 渲染引擎**（`VideoEditController`/`VideoEditService`）。

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
- **视频上传限制**：网关 `spring.codec.max-in-memory-size: 300MB`；上传服务 `max-file-size: 200MB` / `max-request-size: 250MB`；视频接口 `POST /api/upload/video` 仅接受 `video/*`。
- **系统配置 `sys_config`**：`upload_domain` / `api_base_url` / `app_name` 等全局配置由管理后台「系统设置」在线维护；安卓端截图上传 / 图片地址应读取 `upload_domain` 而非硬编码。
- **协议文档**：管理后台「协议管理」用 wangEditor 编辑 HTML5；安卓端 WebView 原样展示。读接口 `GET /api/user/agreements/{type}` 匿名且网关白名单放行（`JwtAuthGlobalFilter` 中 `WHITE_LIST` 已含该路径）。
- **Gateway 不连数据库**：`task-gateway` 已从 `task-common` 依赖排除 `mybatis-plus-boot-starter`，避免 Spring Boot 误建 DataSource（仅保留 Redis 用于限流）。
- **FFmpeg 依赖**：视频编辑渲染（`VideoEditService`）依赖服务端安装 `ffmpeg` / `ffprobe`；`UPLOAD_ROOT` 当前硬编码开发机路径，部署需改为配置项。

---

## 品牌更新（2026-07-13）

应用品牌视觉已更新：Logo 融入抖音 / 快手 / 网赚元素，启动页（Splash）与应用图标（ic_launcher）已更换。相关资源：`android/app/src/main/res/drawable/ic_launcher_foreground.png`、`ic_logo.png`、`ic_launcher_background.xml` 及 `mipmap-*` 各密度图标。

---

## License

内部项目，未开源。

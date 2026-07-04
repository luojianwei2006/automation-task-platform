# 自动化任务平台

## 项目结构

```
automation_project/
├── backend/          # Spring Boot 3.1.5 多模块后端
│   ├── task-common/         # 公共模块
│   ├── task-gateway/        # API 网关 (8085)
│   ├── task-user-service/   # 用户服务 (8081)
│   ├── task-task-service/   # 任务服务 (8082)
│   ├── task-pay-service/    # 支付服务 (8083)
│   ├── task-admin-api/      # 管理后台 API (8084)
│   ├── task-upload-service/ # 上传服务 (8086)
│   └── task-job/            # 定时任务
├── android/          # Android 端 (Kotlin + Jetpack Compose)
├── frontend/         # 管理后台 (Vue 3 + Element Plus)
└── sql/              # 数据库脚本
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.1.5、Java 17、Spring Cloud Gateway 2022.0.4、MyBatis-Plus 3.5.7、Redis、JWT |
| Android | Kotlin 2.0.21、Jetpack Compose、Hilt DI、MVVM、Retrofit + OkHttp |
| 前端 | Vue 3、TypeScript、Element Plus 2.x、Pinia |
| 数据库 | MySQL 8.0 |

## 功能模块

### 视频发布任务（短视频代发）

完整流程：管理后台创建任务 → Android 端领取 → 视频合并剪辑 → 发布到平台 → 截图提交审核 → 审核通过发放奖励

- **管理后台**：创建/编辑发布任务、设置奖励金额、管理素材（视频/音乐/截图）、发布记录审核
- **视频合并**：后端 FFmpeg 异步合并，支持转场效果（19种）、渐入渐出（2秒）、字幕叠加
- **Android 端**：领取任务、合并历史、剪辑选项（转场/渐入渐出/字幕）、预览/保存/发布/提交审核
- **状态机**：CLAIMED → MERGED → SUBMITTED → PASSED/REJECTED

### 转场效果（19种）

| 无 | 淡入淡出 | 黑场过渡 | 白场过渡 |
| 左擦/右擦/上擦/下擦 | 左滑/右滑/上滑/下滑 |
| 圆形裁剪 | 圆形展开 | 圆形收缩 |
| 溶解 | 像素化 | 水平展开 | 垂直展开 |

### 自动化点赞/评论

| taskType | 操作 |
|----------|------|
| 1 | 点赞 |
| 2 | 点赞 + 评论 |

### 支持平台

| platform | 名称 | 自动化引擎 |
|----------|------|------------|
| 1 | 抖音 | `DouyinAutomator` |
| 2 | 小红书 | `XhsAutomator` |
| 3 | 微信视频号 | `WechatVideoAutomator`（打开微信并截图） |

## 自动化截图策略（多级降级）

```
API 34 takeScreenshot() → screencap 写文件 → GLOBAL_ACTION + MediaStore → SurfaceControl 反射
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `MYSQL_HOST` | MySQL 主机 |
| `MYSQL_PORT` | MySQL 端口 |
| `REDIS_HOST` | Redis 主机 |
| `jwt.secret` | JWT 签名密钥 |
| `upload.root` | 上传文件根目录 |

## 快速启动

### 后端
```bash
cd backend
mvn clean package -DskipTests
java -jar task-gateway/target/task-gateway-*.jar
```

### 前端管理后台
```bash
cd frontend/admin-frontend
npm install && npm run dev
```

### Android
用 Android Studio 打开 `android/` 目录，Run app。

## 数据库

```bash
# 初始化
mysql -u root -p < sql/init_database.sql

# 发布任务相关表
mysql -u root -p < sql/publish_merge_history.sql
mysql -u root -p < sql/user_publish_record.sql
mysql -u root -p < sql/publish_task_add_reward.sql
```

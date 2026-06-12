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

## 支持平台

| platform | 名称 | 自动化引擎 |
|----------|------|------------|
| 1 | 抖音 | `DouyinAutomator` |
| 2 | 小红书 | `XhsAutomator` |
| 3 | 微信视频号 | `WechatVideoAutomator`（打开微信并截图） |

## 任务类型

| taskType | 操作 |
|----------|------|
| 1 | 点赞 |
| 2 | 点赞 + 评论 |

## 自动化截图策略（多级降级）

```
API 34 takeScreenshot() → screencap 写文件 → GLOBAL_ACTION + MediaStore → SurfaceControl 反射
```

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
mysql -u root -p < sql/init_database.sql
```

## Git 历史

```
main: 2288db3  refactor: 视频号简化为打开微信+截图+关闭
      e40f469  fix: 搜索图标多候选位点击
      e0db19a  feat: 微信无障碍树不可用时全流程坐标降级
      0a5e682  fix: enterVideoChannel dump诊断 + 坐标兜底 + 滑动查找
      87a4619  feat: 自动发现微信包名 + 显示应用名称
      9d27aff  fix: 改用 queryIntentActivities 搜索微信
      b9c3cf8  debug: 微信未安装时 dump 所有已装应用包名
      056ce7a  debug: 点搜索前dump视频号页面控件
      7399bcb  fix: 无障碍服务配置增强 + 微信全窗口搜索
      2db4488  feat: 新增微信视频号自动化（platform=3）
      9d1492d  feat: 前后端添加微信视频号平台选项
      6a3e46b  fix: Android 强制竖屏
      2f57c2d  fix: 三重策略启动微信
      68f9552  feat: 小红书评论自动化 + 截图流程完善 + 稳定性修复
```

# PRD：自动化任务平台后端 Stub 补全（简单版）

> 文档作者：许清楚（Product Manager）
> 版本：v0.1（简单 PRD）
> 范围：仅产品需求分析，不含代码实现

## 0. 背景与约束（必须遵循）

本期目标是在既有 Spring Boot 3.1.5 多模块微服务骨架上，补全 5 项后端 stub 缺口 + 1 项端口配置修复，使 C 端业务闭环可在本期权跑通。

**已与用户确认的业务规则（硬约束）：**
- **奖励发放**：任务截图审核 **PASSED** 后，由系统**自动发放**到用户余额，无需人工介入。
- **提现**：**无门槛随时提**，有余额即可申请。本期无真实打款通道（微信/支付宝商户未接入），落地为「扣减余额 + 生成提现申请记录 + 标记待打款/已打款」，真实打款留待接入商户号。
- **实名审核**：**人工后台审核**，运营通过/驳回。
- **对象存储**：**本地磁盘先跑通**，复用 `LocalFileStorageService`，预留 `OssFileStorageService`（本期不接云）。

**路径一致性约束（后端补全必须保持，安卓端已按此联调）：**
- 任务接受：`POST /api/task/tasks/{id}/accept`（task-service）
- 任务提交（带截图）：submit 接口（task-service）
- 实名：身份证/人脸照片上传 `uploadIdCardImage` + 实名信息提交（user-service）
- 收益：收益/余额/明细查询（user-service）
- 提现申请：`POST /api/user/withdraw/apply`（user-service，已接）
- 截图上传：`/api/upload/uploads/...`（upload-service，已建）

## 1. 产品目标

本次补全 5 项后端 stub 缺口 + 1 项端口配置，跑通「接任务→做任务→传截图→人工审核→系统自动发奖→收益/提现」与「实名提交 + 人工审核」完整闭环，让 C 端 App 关键链路可用、运营后台可管、资金流水可查。

## 2. 用户故事

### C 端用户
| 编号 | 角色 | 场景 | 诉求 | 验收标准 |
|---|---|---|---|---|
| US-1 | C 端用户 | 在 App 接任务并去对应平台发笔记后上传截图提交 | 完成任务赚取奖励 | accept/submit 接口可用；任务状态正确流转（待提交→审核中→通过/驳回） |
| US-2 | C 端用户 | 提交截图被运营审核通过 | 奖励自动到账，无需操作 | 审核 PASSED 后余额自动增加，生成奖励发放记录 |
| US-3 | C 端用户 | 上传身份证+人脸照片并提交实名信息 | 通过认证解锁提现等能力 | 照片落库、信息提交，状态=待审核；App 可查看审核结果 |
| US-4 | C 端用户 | 查看余额与收益明细 | 了解收入构成 | 余额查询、收益明细列表接口返回正确数据 |
| US-5 | C 端用户 | 有余额即申请提现 | 无门槛随时提 | POST /api/user/withdraw/apply 扣减余额并生成提现申请记录，状态=待打款 |

### 运营 / 管理员
| 编号 | 角色 | 场景 | 诉求 | 验收标准 |
|---|---|---|---|---|
| US-6 | 运营 | 在后台查看实名申请，通过或驳回并填备注 | 完成实名审核闭环 | 通过则用户实名状态变更；驳回可填原因 |
| US-7 | 运营 | 查看用户收益与提现申请记录 | 掌握资金流水 | 列表可筛选/分页/查看详情 |
| US-8 | 运营 | 查看系统自动发放奖励记录 | 对账 | 奖励发放记录列表可查，关联任务与用户 |

## 3. 需求池（P0/P1/P2）

**P0 — 本期权必须跑通的闭环**
- P0-1 端口冲突修复：核对并修正端口配置（见 §6 待确认 / 配置修复）。
- P0-2 任务奖励自动发放：审核 PASSED → 自动发放至用户余额 + 生成发放记录（pay-service）。
- P0-3 任务 accept/submit 后端实现：真实接受/提交逻辑 + 状态机（task-service）。
- P0-4 实名提交 + 人工审核：身份证/人脸照片上传落库、信息提交、状态流转（user-service）。
- P0-5 截图本地上传：复用 `LocalFileStorageService` 本地磁盘存储跑通（upload-service）。
- P0-6 提现申请 + 记录：无门槛申请，扣减余额 + 生成提现申请记录 + 待打款状态（user-service）。

**P1 — 收益展示 / 明细**
- P1-1 收益明细查询接口（user-service）。
- P1-2 余额查询接口（user-service）。

**P2 — 后续迭代**
- P2-1 真实打款通道接入（微信/支付宝商户）。
- P2-2 云存储 OSS/COS 适配器接入（`OssFileStorageService`）。

## 4. 管理后台页面需求

> 技术栈 Vue3 管理后台；以下仅列页面用途与关键字段，不写前端代码。

**4.1 实名审核页**
- 用途：运营对 C 端实名申请进行人工审核（通过 / 驳回+备注）。
- 列表字段：用户ID、昵称、提交时间、审核状态（待审核/通过/驳回）。
- 详情字段：真实姓名、身份证号、身份证正面照URL、身份证反面照URL、人脸照URL、提交时间。
- 操作：通过 / 驳回（驳回必填备注）。

**4.2 收益 / 提现记录查看页**
- 用途：运营查看用户资金流水。
- 收益记录列表字段：记录ID、用户ID、关联任务ID、奖励金额、发放时间、发放状态。
- 提现记录列表字段：提现单号、用户ID、提现金额、申请时间、状态（待打款/已打款）、打款时间。
- 筛选：按用户 / 时间区间 / 状态。

**4.3 奖励发放记录页**
- 用途：运营对账系统自动发放奖励。
- 列表字段：发放ID、用户ID、关联任务ID、奖励金额、发放时间、发放状态。
- 关联：任务详情、用户信息。

## 5. 接口概要（本期需新增 / 补全）

> 路径严格对齐安卓端约束；经 Gateway（8085）统一入口，`StripPrefix=1` 去掉 `/api`。

| Method | 路径（网关侧） | 所属服务 | 用途 | 调用方 |
|---|---|---|---|---|
| POST | `/api/task/tasks/{id}/accept` | task-task-service | 接受任务 | 安卓 App（C端） |
| POST | `/api/task/tasks/{id}/submit` | task-task-service | 提交任务（带截图） | 安卓 App（C端） |
| POST | `/api/task/tasks/{id}/review` | task-task-service | 审核通过/驳回（PASSED 触发发奖） | 运营后台 / 内部 |
| POST | `/api/user/realname/upload` | task-user-service | 身份证/人脸照片上传（对应 `uploadIdCardImage`） | 安卓 App（RealAuthScreen） |
| POST | `/api/user/realname/submit` | task-user-service | 实名信息提交 | 安卓 App（C端） |
| GET | `/api/user/realname/status` | task-user-service | 查询实名审核状态 | 安卓 App（C端） |
| GET | `/api/user/earnings` | task-user-service | 收益明细查询 | 安卓 App（C端） |
| GET | `/api/user/balance` | task-user-service | 余额查询 | 安卓 App（C端） |
| POST | `/api/user/withdraw/apply` | task-user-service | 提现申请（无门槛） | 安卓 App（C端，已接） |
| POST | `/api/pay/grant` | task-pay-service | 奖励发放（被 task-service 审核 PASSED 触发） | task-task-service（内部调用，携带 internal api-token） |
| POST | `/api/upload/uploads/...` | task-upload-service | 截图/照片本地上传 | 安卓 App（C端，已建） |

**发奖联动说明（P0-2）**：`task-task-service` 审核置为 PASSED 后，必须通过内部调用 `task-pay-service` 的 `/api/pay/grant`（经网关或内部直连，携带 `internal.api-token`），由 pay-service 完成「余额增加 + 写入奖励发放记录」。该链路为系统自动，无人工介入。

## 6. 待确认问题

1. **提现真实打款（P0-6）**：本期无微信/支付宝商户号。落地方案建议 = 扣减余额 + 生成提现申请记录 + 待打款/已打款状态，真实打款留待接入商户号。**需用户确认此方案可接受。**
2. **奖励金额来源与配置方式**：奖励金额取自任务配置字段（如任务表 `reward_amount`）还是全局配置 `sys_config`？是否支持运营后台配置？**需确认字段来源与可配置性。**
3. **任务提交状态机细节**：建议 `待提交(accepted) → 审核中(reviewing) → 通过(passed)/驳回(rejected)`；驳回后是否允许重提、重提是否复用原任务？**需架构师确认枚举与流转。**
4. **端口实际冲突核对结果（P0-1，已核对 application.yml）**：
   - `task-task-service` 主 `application.yml` 写死 `server.port=8083`；其 `application-dev.yml` 覆盖为 **8082**（与网关路由 `8082` 一致）。
   - `task-pay-service` 主/ dev 均为 **8083**（与网关路由 `8083` 一致）。
   - 结论：激活 dev 配置下两者**不冲突**（8082 / 8083）；但 `task-task-service` 的**默认（非 dev）端口 8083 与 pay-service 的 8083 重复**，一旦以非 dev 方式启动即冲突，属潜伏隐患。
   - 建议修复（需架构师落实）：
     - 将 `task-task-service` 主 `application.yml` 默认端口 **8083 → 8082**；
     - 并按主理人建议将 `task-pay-service` 改到 **8087**，同时**同步修改网关 `task-pay-service` 路由 uri `8083 → 8087`**，彻底消除冲突。
5. **实名与提现前置关系**：实名通过是否强制作为提现前置条件？**需确认。**
6. **上传返回约定**：`upload` 接口返回 URL 还是 fileKey？需与安卓端 `uploadIdCardImage` 返回结构保持一致。**需前后端对齐。**

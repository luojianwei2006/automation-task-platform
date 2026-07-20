# PRD｜安卓端界面全面优化（Design System 视觉规范）

> 文档版本：v1.0
> 负责人：许清楚（Xu）· 产品经理
> 团队：software-ui-opt
> 技术栈：Jetpack Compose + Material3
> 项目路径：`/Users/luojianwei/Documents/Workbuddy/automation_project/android`
> 覆盖范围：28 个 Compose 页面（四大底部 Tab + 子页面）

---

## 1. 产品目标

建立统一的 Android Design System，以品牌橙 `0xFFFF8C00` 为权威主色，收敛当前 28 个 Compose 页面中散落重复的颜色、圆角、字号与顶栏范式，提供可落地的 light/dark `colorScheme`、圆角尺度阶梯、字体语义层级与一组公共组件（AppCard / AppTopBar / AppButton / StatusTag / EmptyState / LoadingIndicator）；实现全量页面的视觉一致性与可维护性，支持浅色与深色双模式，并针对中老年用户群体提升文字可读性与整体品牌精致感。

---

## 2. 用户故事

1. **作为中老年用户**，我希望界面使用统一、足够大的可读字号和一致的品牌橙色，以便我能快速看懂页面并避免误触。
2. **作为接单用户**，我希望「审核中 / 已通过 / 已拒绝 / 待领取 / 超时」等状态标签在全站使用一致的颜色语义，以便我能一眼判断任务状态。
3. **作为开发工程师**，我希望存在单一 `AppTheme` 与可复用公共组件，以便我不必在 28 个页面里反复声明 `private val Orange = ...`。
4. **作为夜间使用者**，我希望应用提供正确对比度的深色模式，以便我在弱光环境下舒适使用。
5. **作为产品经理**，我希望品牌橙在全站权威且一致，以便 App 传达精致、统一的品牌调性。

---

## 3. 需求池（P0 / P1 / P2）

### P0 — Must Have（MVP，阻塞全量接入）

- **[P0-1] 主题系统 AppTheme**：建立 `AppTheme`（封装 `MaterialTheme`），注入 `lightScheme` + `darkScheme` 两套 `colorScheme`，以及统一的 `shapes` 与 `typography`；在 `MainActivity.kt` 替换默认 `MaterialTheme { ... }`，按系统深浅模式（`isSystemInDarkTheme()`）切换。
- **[P0-2] 主色单一来源**：全量收敛散落的橙色取值——`AdHallScreen.kt:30` 的 `0xFFFF6B00`、`VideoEditorScreen.kt:74-77` 的 `0xFFFF6A00`、`SplashScreen` 的快手橙 `0xFFFF6B00` 等，统一指向 `colorScheme.primary = 0xFFFF8C00`，删除各页 `private val Orange / NavOrange / AccentOrange = Color(0xFFFF8C00)` 的重复声明。
- **[P0-3] 语义状态色统一表**：建立 `AppStatusColors` 语义 token（审核中 / 已通过 / 已拒绝 / 待领取 / 超时），收敛现状分歧（如 `PublishScreen` 用 `0xFF2196F3` 作「已领取」蓝，而 `MyTasksScreen` 用 `0xFF42A5F5`；「审核中」蓝与橙各处混用），统一为下表固定值。
- **[P0-4] 公共基础组件**：抽离 `AppCard / AppTopBar / AppButton / StatusTag / EmptyState / LoadingIndicator` 至 `ui/theme/components/`，全量页面接入替换手写实现。
- **[P0-5] 顶栏范式统一**：将并存的「Material3 `TopAppBar`」与「自定义 `Surface`/渐变头」统一为 `AppTopBar`，返回箭头 `tint = colorScheme.primary`，消除硬编码 `0xFFFF8C00` 箭头。

### P1 — Should Have（一致性收敛）

- **[P1-1] 复用型组件抽离**：`SegmentedTab`（分段切换）、`FilterChipGroup`（列表筛选）、`AppTextButton`（文字按钮）抽离复用，替换各页手写 Tab/Chip。
- **[P1-2] 字号语义化**：将散落的 `13~18.sp` 行内字号全部收敛为 `MaterialTheme.typography` 语义层级（见 §4.3），禁止页面内 `fontSize = 13.sp` 硬编码。
- **[P1-3] 圆角尺度阶梯**：将混乱的 `14/16/20dp × 1/2/4/8dp` 收敛为 `sm/md/lg/xl` 四档（8/12/16/24dp）；`AppCard` 固定 `lg=16dp`，按钮固定 `sm=8dp`，底部导航顶部圆角固定 `xl=24dp`。
- **[P1-4] 底部导航接入主题**：`MainScreen` 的 `private val NavOrange / NavOrangeBg / NavGrayUnselected / NavBarBg` 迁移到 `colorScheme`，保留选中态渐变指示器与 `xl=24dp` 顶部圆角，补充深色变体。

### P2 — Nice to Have（精细化）

- **[P2-1] 详情页 / 视频编辑器精细化**：`TaskDetailScreen`、`VideoEditorScreen` 视觉打磨（间距、层级、对比度）。
- **[P2-2] 空状态插画**：`EmptyState` 从纯图标升级为品牌风格插画。
- **[P2-3] 微交互**：按钮按压态、卡片轻量 hover、Tab 切换 `300ms` 动画（沿用 `MainScreen` 现有 `tween(300)` 节奏）。
- **[P2-4] 动态色预留**：架构上**不引入** Material You 动态取色，仅预留扩展位并写入注释，保持本次范围明确。

---

## 4. UI 设计稿（Design System 视觉规范 · 可落地取值）

### 4.1 颜色 Token 表

#### 4.1.1 品牌与中性基础色（语义 token，全模式通用）

| Token | 含义 | 取值（浅色） | 取值（深色） |
|---|---|---|---|
| `primary` | 品牌主色（权威橙） | `0xFFFF8C00` | `0xFFFF8C00`（保持权威，见 Open Q1） |
| `onPrimary` | 主色上的文字/图标 | `0xFFFFFFFF`（White） | `0xFFFFFFFF` |
| `secondary` | 次级强调色 | `0xFFB26A00`（深橙） | `0xFFFFB74D`（亮橙） |
| `onSecondary` | 次级色上的内容 | `0xFFFFFFFF` | `0xFF3D2100` |
| `background` | 页面背景 | `0xFFFAFAFA`（Gray50） | `0xFF121212` |
| `surface` | 卡片/容器表面 | `0xFFFFFFFF` | `0xFF1C1C1E` |
| `surfaceVariant` | 次级表面（输入/芯片底） | `0xFFE0E0E0`（Gray300） | `0xFF2A2A2C` |
| `outline` | 描边/分割线 | `0xFFBDBDBD`（Gray400） | `0xFF3A3A3C` |
| `error` | 错误/危险 | `0xFFE53935` | `0xFFEF5350` |
| `onError` | 错误色上的内容 | `0xFFFFFFFF` | `0xFF601410` |
| `textPrimary` | 主文字 | `0xFF212121`（Gray900） | `0xFFE0E0E0` |
| `textSecondary` | 次级文字 | `0xFF616161`（Gray700） | `0xFFBDBDBD` |
| `textDisabled` | 禁用文字 | `0xFF9E9E9E`（Gray500） | `0xFF6E6E6E` |

> 注：`textPrimary/secondary/disabled` 在实际代码中直接映射为 `colorScheme.onSurface / onSurfaceVariant / outline` 的语义等价色，或作为 `AppColors` 扩展字段暴露，避免页面再写 `Gray900`。

#### 4.1.2 语义状态色（Status Colors · 全模式语义一致）

| 状态 | 语义 | 主色值 | 浅底（Tag bg） | 深字（Tag fg） |
|---|---|---|---|---|
| `statusReviewing` | 审核中 | `0xFF42A5F5`（蓝） | `0xFFE3F2FD` | `0xFF0D47A1` |
| `statusApproved` | 已通过 | `0xFF4CAF50`（绿） | `0xFFE8F5E9` | `0xFF1B5E20` |
| `statusRejected` | 已拒绝 | `0xFFE53935`（红） | `0xFFFFEBEE` | `0xFFB71C1C` |
| `statusPending` | 待领取 | `0xFFFF8C00`（橙） | `0xFFFFF3E0` | `0xFF5C2E00` |
| `statusTimeout` | 超时 | `0xFF9E9E9E`（灰） | `0xFFF5F5F5` | `0xFF424242` |

> 收敛前分歧示例（来自代码确认）：`PublishScreen` 的「已领取蓝」=`0xFF2196F3`，`MyTasksScreen` 的蓝=`0xFF42A5F5`，`TaskDetailScreen` 无蓝态；本表统一以 `0xFF42A5F5` 为蓝系权威值，绿/红统一为 `0xFF4CAF50 / 0xFFE53935`，消除「已通过=绿 `0xFF4CAF50` vs `0xFF2E7D32`」「审核中=蓝 vs 橙」的混用。

#### 4.1.3 浅色 colorScheme 完整取值（Material3）

```
primary            = 0xFFFF8C00
onPrimary          = 0xFFFFFFFF
primaryContainer   = 0xFFFFE0B2
onPrimaryContainer = 0xFF4A2400
secondary          = 0xFFB26A00
onSecondary        = 0xFFFFFFFF
secondaryContainer = 0xFFFFE8CC
onSecondaryContainer = 0xFF3D2100
tertiary           = 0xFF2196F3
onTertiary         = 0xFFFFFFFF
error              = 0xFFE53935
onError            = 0xFFFFFFFF
errorContainer     = 0xFFFFDAD6
onErrorContainer   = 0xFF410002
background         = 0xFFFAFAFA
onBackground       = 0xFF212121
surface            = 0xFFFFFFFF
onSurface          = 0xFF212121
surfaceVariant     = 0xFFE0E0E0
onSurfaceVariant   = 0xFF616161
outline            = 0xFFBDBDBD
outlineVariant     = 0xFFE0E0E0
```

#### 4.1.4 深色 colorScheme 完整取值（Material3）

```
primary            = 0xFFFF8C00
onPrimary          = 0xFFFFFFFF
primaryContainer   = 0xFF5C2E00
onPrimaryContainer = 0xFFFFDDB0
secondary          = 0xFFFFB74D
onSecondary        = 0xFF3D2100
secondaryContainer = 0xFF4A2E00
onSecondaryContainer = 0xFFFFDDB0
tertiary           = 0xFF64B5F6
onTertiary         = 0xFF00344F
error              = 0xFFEF5350
onError            = 0xFF601410
errorContainer     = 0xFF410002
onErrorContainer   = 0xFFFFDAD6
background         = 0xFF121212
onBackground       = 0xFFE0E0E0
surface            = 0xFF1C1C1E
onSurface          = 0xFFE0E0E0
surfaceVariant     = 0xFF2A2A2C
onSurfaceVariant   = 0xFFBDBDBD
outline            = 0xFF3A3A3C
outlineVariant     = 0xFF2A2A2C
```

---

### 4.2 圆角尺度阶梯（Corner Radius Scale）

| Token | dp | 用途 |
|---|---|---|
| `radiusSm` | 8dp | 按钮、Tag、Chip、小卡片 |
| `radiusMd` | 12dp | 输入框、中段容器 |
| `radiusLg` | 16dp | `AppCard`、列表项卡片（默认） |
| `radiusXl` | 24dp | 底部导航顶部圆角、弹层、大浮层 |

> 现状 `14/16/20dp` 混乱统一收敛到上述四档；其中 `AppCard` 固定 `radiusLg = 16dp`，`AppButton` 固定 `radiusSm = 8dp`，`MainScreen` 底部导航顶部固定 `radiusXl = 24dp`。

---

### 4.3 字体层级（Typography Scale · 语义化）

| Token（MaterialTheme.typography） | 字号 | 字重 | 行高 | 用途 |
|---|---|---|---|---|
| `titleLarge` | 20sp | Bold | 26sp | 页面主标题 |
| `titleMedium` | 18sp | SemiBold | 24sp | 区块标题、卡片标题 |
| `bodyLarge` | 16sp | Regular/Medium | 22sp | 正文、主操作按钮文字 |
| `bodyMedium` | 14sp | Regular | 20sp | 次要正文、列表项 |
| `labelMedium` | 12sp | Medium | 16sp | 标签、状态、辅助说明 |
| `labelSmall` | 11sp | Regular | 14sp | 角标、极小注记 |

> 现状 `13~18.sp` 散落（含底部导航选中 `15.sp`、未选中 `13.sp`）统一收敛到上表；底部导航文字落在 `bodyMedium(14sp)/labelMedium(12sp)` 区间，标题层级用 `titleLarge/titleMedium`，禁止页面内 `fontSize = X.sp` 硬编码。

---

### 4.4 组件视觉规格（Component Spec）

#### AppCard
- 形状：`RoundedCornerShape(16.dp)`（radiusLg）
- 背景：`colorScheme.surface`
- 阴影：`shadowElevation = 1.dp`（轻量浮起，沿用现状 `elevation 1dp` 基调）
- 描边：可选 `border = BorderStroke(1.dp, colorScheme.outlineVariant)`
- 内边距：默认 `16.dp`

#### AppTopBar
- 类型：基于 Material3 `TopAppBar` 收敛，禁用自定义 `Surface`/渐变头
- 返回箭头：`Icon tint = colorScheme.primary`（消除 `0xFFFF8C00` 硬编码）
- 标题：`colorScheme.onSurface`（浅色=Gray900 / 深色=onSurface 浅色），字重 `titleLarge/titleMedium`
- 背景：`colorScheme.surface`，底部 `1.dp` `outlineVariant` 分割线，无重阴影
- 高度：标准 `56.dp`

#### AppButton（主按钮）
- 背景：`colorScheme.primary`，文字：`colorScheme.onPrimary`（白字）
- 形状：`RoundedCornerShape(8.dp)`（radiusSm）
- 宽度：默认 `fillMaxWidth()`（填充最大宽），内边距水平 `24.dp`
- 高度：`48.dp`，文字 `bodyLarge` Medium
- 状态：禁用态背景 `colorScheme.surfaceVariant`、文字 `colorScheme.onSurfaceVariant`（对应 `textDisabled`）

#### AppTextButton（文字按钮）
- 无背景，文字色 `colorScheme.primary`
- 形状：`RoundedCornerShape(8.dp)`（radiusSm）
- 高度：`40.dp`，文字 `bodyMedium` Medium
- 用途：次级操作（如「取消」「查看详情」）

#### StatusTag（状态标签）
- 形状：`RoundedCornerShape(8.dp)`（radiusSm）
- 配色：**语义色浅底 + 同色深字**（见 §4.1.2 浅底/深字列）
- 内边距：水平 `8.dp`、垂直 `4.dp`
- 文字：`labelMedium(12sp)` Medium
- 深色模式：浅底改为对应语义 `container` 低透度色（如审核中 `0xFF0D2A3D` 底 + `0xFF90CAF9` 字），保持可读对比度

#### EmptyState（空状态）
- 图标：`colorScheme.outline`（浅色=`0xFFBDBDBD` Gray400），尺寸 `64.dp`
- 文案：`colorScheme.onSurfaceVariant`（浅色=`0xFF9E9E9E` Gray500），`bodyLarge` 居中
- 布局：纵向居中（`Column` + `Arrangement.Center`）

#### LoadingIndicator（加载指示）
- 组件：Material3 `CircularProgressIndicator`，`color = colorScheme.primary`
- `strokeWidth = 4.dp`，`size = 36~48.dp`，居中
- 接入点：替换 `BaseScreen.kt` 现有裸 `CircularProgressIndicator()`，使其继承主题主色

---

### 4.5 深色模式策略

- 在 `MainActivity` 以 `isSystemInDarkTheme()` 切换 `lightScheme / darkScheme`，不做动态取色（不引入 Material You）。
- 中性表面按 Material3 深色标高模型：`background(0xFF121212) < surface(0xFF1C1C1E) < surfaceVariant(0xFF2A2A2C)`，文字与图标转浅色（`onSurface = 0xFFE0E0E0`）。
- 语义状态色语义值不变，仅 Tag 的底/字对（见 §4.1.2 与 §4.4 StatusTag）切换为深色可读组合。
- 底部导航：`NavBarBg` 在深色改为 `surface(0xFF1C1C1E)`，选中渐变指示器保留橙色系但在深色下降透明度以保证对比。

---

## 5. 待确认问题（Open Questions）

1. **主色在深色模式是否提亮**：用户决策明确「保留 `0xFFFF8C00` 为权威主色」，故本 PRD 深色 `primary` 仍取 `0xFFFF8C00`。若 QA/设计认为深色下对比不足，是否同意微调为 `0xFFFFA726`？需确认（默认：保持 `0xFFFF8C00`）。
2. **`secondary` 取值**：用户列出了 `secondary` token 但未给具体值，本 PRD 暂定浅色 `0xFFB26A00` / 深色 `0xFFFFB74D`（深橙系）。是否接受，或改为中性灰（`0xFF616161`）？
3. **底部导航深色变体细节**：`MainScreen` 选中态渐变（`NavOrangeBg → 0xFFFFECB3`）在深色下的底色与透明度方案待设计确认（本 PRD 已给方向：surface 底 + 橙色降透）。
4. **状态 Tag 深色浅底方案**：本 PRD 采用「深色 container + 浅色字」；是否接受，或坚持「同语义浅底反白字」？
5. **顶部导航栏是否保留渐变装饰**：现状部分页面用渐变橙头，统一为 `AppTopBar` 后是否完全去除渐变（建议去除，仅保留 `primary` 箭头 + `onSurface` 标题）。

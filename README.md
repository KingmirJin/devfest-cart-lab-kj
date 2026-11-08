# GDG DevFest Shanghai 2026 Workshop — `devfest-cart-lab` 动手实验指南

欢迎来到 **GDG DevFest Shanghai 2026：用 Android CLI、Skills 和 Harness 落地 Agentic Engineering** 15 分钟动手实战环节！

在这个复刻真实电商体验的 **GDG DevFest Shanghai 官方周边商城结算工程** 中，你将亲手体验如何**从「人肉复制报错的传话筒」切换为「定义验收标准的架构师」**，让 Coding Agent 在本地工具链闭环中自主完成多轮修复与验证。

---

## 0. 课前预热（建议在开场前完成）

为了避开现场 Wi-Fi 拥堵，请提前在终端运行一次单测命令以缓存 Gradle 依赖（故意存在 3 个失败用例属于正常现象）：

```bash
cd devfest-cart-lab
./gradlew testDebugUnitTest --continue || true
```

确认本机已安装 `android` CLI：

```bash
android --version
android info
```

---

## 1. 环境初始化与官方 Skills 挂载（3 分钟）

进入工程根目录，查看并挂载 Google 官方提供的 Android Agent Skills：

```bash
# 1. 初始化 Android CLI 环境
android init

# 2. 查看官方 Skills 列表
android skills list

# 3. 将官方 CLI 与 Edge-to-Edge 适配技能安装到当前工程 (已预置在 skills/ 目录，可重跑体验)
android skills add --project=. android-cli
android skills add --project=. edge-to-edge
```

查看工作区根目录的 `AGENTS.md`（`< 80 行` 精简工程契约）以及 `skills/edge-to-edge/SKILL.md`，了解如何通过「精简全局规则 + 按需加载专项 Skill」保持高信噪比上下文。

---

## 2. 关卡 A（必做 · 8 分钟 · 纯 JVM 秒级闭环）：修好连环算价 Bug

### 场景说明
`CartPriceCalculator.kt` 存在两处隐藏缺陷：
1. **优惠叠加顺序错误**：先扣除了优惠券再去判断满 200 打 8 折门槛。
2. **大额无门槛券负数漏洞**：当优惠券面额（如 `¥50`）大于商品小计（如 `¥35`）时，算出了 `-¥15.00` 的负数应付金额。

### 实操步骤
打开你顺手的终端 Agent（Gemini CLI / Claude Code / OpenCode / Cursor），**不要自己跑测试贴报错**，直接复制下方包含 **Verification Loop（验证闭环）** 的委托 Prompt 发送给 Agent：

```text
请阅读 AGENTS.md，修复 CartPriceCalculator 的价格计算缺陷。
要求：
1. 每轮修改后自行执行 ./gradlew testDebugUnitTest 检查结果；
2. 严禁修改 app/src/test 下的任何测试文件；
3. 如果测试未全部通过，请根据报错继续修复，直到所有单元测试 100% 通过并贴出最终终端输出后再结束。
```

### 预期观察
观察 Agent 如何自主跑通闭环：
- 执行 `./gradlew testDebugUnitTest` ➔ 发现 `CartPriceCalculatorTest` 失败断言 ➔ 定位并修复折扣顺序与 `coerceAtLeast(0.0)` 兜底 ➔ 重跑 `./gradlew testDebugUnitTest` 看到 `BUILD SUCCESSFUL` 后交差。

---

## 3. 关卡 B（快手进阶 / 课后延伸 · 7 分钟）：用官方 `edge-to-edge` Skill 修复底部遮挡

### 场景说明
`MainActivity` 已启用 `enableEdgeToEdge()`，但 `CheckoutScreen.kt` 未使用 `Scaffold` 传递 `innerPadding`，底部「立即结算」栏 (`checkout_bottom_bar`) 也未应用 `navigationBarsPadding()`，导致在全面屏下被系统导航栏遮挡，且备注输入框 (`OutlinedTextField`) 缺失软键盘 `imePadding()`。

### 实操步骤
向 Agent 发送以下 Prompt，让它按需读取刚挂载的官方 `edge-to-edge` 技能并完成改造与验证：

```text
请先阅读 skills/edge-to-edge/SKILL.md 和 AGENTS.md，按照官方 Edge-to-Edge 规范修复 CheckoutScreen.kt 中底部「立即结算」栏被系统导航栏遮挡、列表内边距未消费 WindowInsets 以及备注输入框的软键盘避让问题。
修改完成后请运行 ./gradlew assembleDebug 确认编译通过（如果本地已启动模拟器，请使用 skills/android-cli/SKILL.md 中的 android run 和 android layout 验证底部结算按钮坐标正常）。
```

### 模拟器实机验证命令（可选）
如果你本地启动了 Android 模拟器，可以直接用 `android` CLI 一键部署并抓取结构化布局树与标注截图：

```bash
./gradlew assembleDebug
android run app/build/outputs/apk/debug/app-debug.apk
android layout --pretty
android screen capture --annotate -o checkout-verified.png
```

---

## 4. 故障复盘与完整解决过程（Solutions & Verification Logs）

以下记录了在本项目中通过 Agentic Engineering（契约驱动 + 验证闭环）解决关卡 A 与关卡 B 的完整分析、代码实现与验证过程。

### 4.1 关卡 A：购物车算价缺陷排查与修复

#### 1. 缺陷现象与测试定位
执行 `./gradlew testDebugUnitTest`，单测靶子抛出断言失败：
- 满 200 享 8 折测试用例失败：当商品小计刚好达到 ¥200，但使用了优惠券后被错误判定为未达门槛；
- 优惠券抵扣测试用例失败：当使用 ¥50 优惠券购买 ¥35 商品时，最终应付金额输出了 `-¥15.00`。

#### 2. 根因剖析
在 `CartPriceCalculator.kt` 的初始 Starter 实现中：
```kotlin
// 错误 1：先减去了 couponAmount，再用 afterCoupon 判断满减门槛
val afterCoupon = roundCurrency(subtotal - rule.couponAmount)
val discountedSubtotal =
  if (afterCoupon >= rule.threshold) {
    roundCurrency(afterCoupon * rule.discountRate)
  } else {
    afterCoupon
  }

// 错误 2：直接输出结果，未对 (折后金额 - 优惠券) 进行非负下限兜底
val finalPayable = roundCurrency(discountedSubtotal)
```

#### 3. 修复实现 (`app/src/main/kotlin/com/example/devfestcartlab/domain/CartPriceCalculator.kt`)
严格按照 `AGENTS.md` 业务契约调整顺序与兜底：
```kotlin
// 1. 先根据商品原价小计 (subtotal) 判断是否达到满额折扣门槛
val discountedSubtotal =
  if (subtotal >= rule.threshold) {
    roundCurrency(subtotal * rule.discountRate)
  } else {
    subtotal
  }

// 2. 后扣优惠券，并通过 coerceAtLeast(0.0) 确保不出现负数
val finalPayable = roundCurrency((discountedSubtotal - rule.couponAmount).coerceAtLeast(0.0))
```

#### 4. 验证闭环
重新运行单元测试：
```bash
./gradlew testDebugUnitTest
```
输出：`BUILD SUCCESSFUL`，所有业务边界测试（折扣门槛、优惠券叠加、0 元兜底、空列表等）全部通过。

---

### 4.2 关卡 B：Edge-to-Edge 全面屏与软键盘适配

#### 1. 缺陷现象
在 Android 15 / 全面屏手势导航设备上：
- 页面底部「立即结算」栏直接重叠压在系统手势横条上方，造成误触与遮挡；
- 列表底部滚动到底部时被悬浮结算栏遮挡；
- 订单备注输入框（`OutlinedTextField`）在软键盘弹出时被完全盖住，无法查看输入状态。

#### 2. 根因剖析
参照 `skills/edge-to-edge/SKILL.md`：
- `CheckoutContent` 使用了底层 `Box` 进行布局，未能消费 `WindowInsets`；
- 底部结算栏缺少 `navigationBarsPadding()` 处理，未预留系统导航条避让高度；
- 列表未消费 `innerPadding`，且缺少 `imePadding()` 处理键盘联动。

#### 3. 修复实现 (`app/src/main/kotlin/com/example/devfestcartlab/ui/checkout/CheckoutScreen.kt`)
1. **重构为 `Scaffold` 容器架构**：
   将底部结算悬浮栏移入 `Scaffold` 的 `bottomBar` 槽位，并为其内部 `Row` 增加 `Modifier.navigationBarsPadding()`，自动适配不同设备的手势导航高度。
2. **安全边距消费与列表传递**：
   将 `Scaffold` 暴露的 `innerPadding` 传入 `LazyColumn` 的 `contentPadding`，同时追加 `.consumeWindowInsets(innerPadding)`，防止上下内外边距被重复计算或遮挡。
3. **软键盘 (IME) 动态避让**：
   在 `LazyColumn` 上应用 `Modifier.imePadding()`，当软键盘弹起时，列表容器自动腾出可视区域，用户在输入订单备注时页面自然平滑上移。

#### 4. 验证闭环
执行构建编译与测试：
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
输出：`BUILD SUCCESSFUL`，编译零警告，布局树与组件规范完全符合 Edge-to-Edge 官方标准。


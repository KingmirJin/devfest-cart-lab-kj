# devfest-cart-lab 故障排查与解决过程记录

本项目基于 Agentic Engineering（契约驱动 + 验证闭环）解决 GDG DevFest 官方周边商城结算工程中的连环算价缺陷（关卡 A）与 Edge-to-Edge 全面屏适配问题（关卡 B）。以下为完整的故障根因剖析、代码实现与验证记录。

---

## 一、关卡 A：购物车算价缺陷排查与修复

### 1. 缺陷现象与测试定位
执行单元测试 `./gradlew testDebugUnitTest`，单测靶子抛出断言失败：
- **满减门槛误判**：当商品小计达到 ¥200，但使用了优惠券后被错误判定为未达到满减门槛；
- **负数应付金额漏洞**：当使用 ¥50 优惠券购买 ¥35 商品时，最终应付金额输出了 `-¥15.00`。

### 2. 根因剖析
在 `CartPriceCalculator.kt` 的初始 Starter 实现中：
```kotlin
// 错误 1：先减去了 couponAmount，再用 afterCoupon 判断满减门槛（叠加顺序颠倒）
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

### 3. 修复实现 (`app/src/main/kotlin/com/example/devfestcartlab/domain/CartPriceCalculator.kt`)
严格按照 `AGENTS.md` 业务契约调整顺序与非负兜底：
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

### 4. 验证闭环
运行单元测试：
```bash
./gradlew testDebugUnitTest
```
**结果**：`BUILD SUCCESSFUL`，所有业务边界测试（折扣门槛判定、优惠券叠加、0 元下限兜底、空列表等）全部通过。

---

## 二、关卡 B：Edge-to-Edge 全面屏与软键盘适配

### 1. 缺陷现象
在全面屏手势导航设备与启用 `enableEdgeToEdge()` 环境下：
- **底部遮挡**：底部「立即结算」栏直接重叠压在系统手势导航横条上方，造成误触与遮挡；
- **列表截断**：商品列表滑动到底部时被悬浮结算栏遮挡末尾内容；
- **键盘遮挡**：订单备注输入框（`OutlinedTextField`）在软键盘弹出时被完全覆盖，无法查看输入状态。

### 2. 根因剖析
参照 `skills/edge-to-edge/SKILL.md`：
- `CheckoutContent` 使用了底层 `Box` 进行布局，未能消费 `WindowInsets`；
- 底部结算栏缺少 `navigationBarsPadding()` 处理，未预留系统导航条避让高度；
- 列表未消费 `innerPadding`，且缺少 `imePadding()` 处理键盘联动。

### 3. 修复实现 (`app/src/main/kotlin/com/example/devfestcartlab/ui/checkout/CheckoutScreen.kt`)
1. **重构为 `Scaffold` 容器架构**：
   将底部结算栏移入 `Scaffold` 的 `bottomBar` 槽位，并在内部 `Row` 应用 `Modifier.navigationBarsPadding()`，自动适配全面屏手势导航栏。
2. **安全边距消费与列表传递**：
   将 `Scaffold` 暴露的 `innerPadding` 传入 `LazyColumn` 的 `contentPadding`，同时追加 `.consumeWindowInsets(innerPadding)`，防止上下内外边距被重复计算或遮挡。
3. **软键盘 (IME) 动态避让**：
   在 `LazyColumn` 上应用 `Modifier.imePadding()`，当软键盘弹起时，列表容器自动腾出可视区域，用户在输入订单备注时页面自然平滑上移。

### 4. 验证闭环
执行构建编译与测试：
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
**结果**：`BUILD SUCCESSFUL`，编译零错误零警告，布局结构与组件规范完全符合 Edge-to-Edge 官方标准。

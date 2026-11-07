package com.example.devfestcartlab.domain

import kotlin.math.round

data class CartItem(
  val id: String,
  val name: String,
  val unitPrice: Double,
  val quantity: Int,
)

data class DiscountRule(
  val threshold: Double = 200.0,
  val discountRate: Double = 0.8,
  val couponAmount: Double = 0.0,
)

data class PriceBreakdown(
  val subtotal: Double,
  val discountedSubtotal: Double,
  val couponApplied: Double,
  val finalPayable: Double,
)

/**
 * 购物车结算价格计算器
 *
 * 业务规范（参见 AGENTS.md）：
 * 1. 先计算商品原价小计 `subtotal = sum(unitPrice * quantity)`。
 * 2. 【满额折扣优先】：若原价小计 `subtotal >= rule.threshold`，先对 `subtotal` 打折（`subtotal * rule.discountRate`）；
 *    否则不打折。
 * 3. 【优惠券后扣 + 0 元兜底】：在折后金额基础上减去 `rule.couponAmount`；
 *    若优惠券面额大于折后金额，最终应付金额 `finalPayable` 最低为 `0.0`（严禁出现负数金额）。
 */
class CartPriceCalculator {

  fun calculate(items: List<CartItem>, rule: DiscountRule = DiscountRule()): PriceBreakdown {
    val subtotal = roundCurrency(items.sumOf { it.unitPrice * it.quantity })

    val discountedSubtotal =
      if (subtotal >= rule.threshold) {
        roundCurrency(subtotal * rule.discountRate)
      } else {
        subtotal
      }

    val finalPayable = roundCurrency((discountedSubtotal - rule.couponAmount).coerceAtLeast(0.0))

    return PriceBreakdown(
      subtotal = subtotal,
      discountedSubtotal = discountedSubtotal,
      couponApplied = rule.couponAmount,
      finalPayable = finalPayable,
    )
  }

  fun calculateFinalPrice(items: List<CartItem>, rule: DiscountRule = DiscountRule()): Double {
    return calculate(items, rule).finalPayable
  }

  private fun roundCurrency(value: Double): Double {
    return round(value * 100.0) / 100.0
  }
}

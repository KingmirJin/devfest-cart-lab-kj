package com.example.devfestcartlab.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.devfestcartlab.ui.theme.BrandBlue
import com.example.devfestcartlab.ui.theme.CoralPrice
import com.example.devfestcartlab.ui.theme.DevFestCartLabTheme

/**
 * GDG DevFest Shanghai 2026 官方周边商城结算页（Workshop Level B 靶子）
 *
 * 当前已知 UI 缺陷（请配合 `skills/edge-to-edge/SKILL.md` 修复）：
 * 1. `MainActivity` 已启用 `enableEdgeToEdge()`，但本页面未使用 `Scaffold` 传递 `innerPadding`，
 *    也未对顶部商城横幅、滚动列表 (`LazyColumn`) 与底部「立即结算」栏应用 `WindowInsets` / `safeDrawingPadding()`。
 * 2. 底部结算栏 (`checkout_bottom_bar`) 直接贴合屏幕物理底边 (`Alignment.BottomCenter`)，在全面屏手势导航或三键导航下与系统导航栏重叠。
 * 3. 订单备注输入框 (`OutlinedTextField`) 未处理软键盘 `imePadding()` / `WindowInsetsRulers.Ime`。
 */
@Composable
fun CheckoutScreen(
  modifier: Modifier = Modifier,
  viewModel: CheckoutViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  CheckoutContent(
    uiState = uiState,
    onSelectCoupon = viewModel::selectCoupon,
    onUpdateQuantity = viewModel::updateQuantity,
    onToggleItemSelection = viewModel::toggleItemSelection,
    onToggleSelectAll = viewModel::toggleSelectAll,
    onCycleItemSpec = viewModel::cycleItemSpec,
    onAddCatalogItem = viewModel::addCatalogItem,
    onResetCart = viewModel::resetCart,
    onSelectDeliveryMode = viewModel::selectDeliveryMode,
    onUpdatePromoNote = viewModel::updatePromoNote,
    onAppendQuickNoteTag = viewModel::appendQuickNoteTag,
    onSubmitOrder = viewModel::submitOrder,
    onDismissOrderDialog = viewModel::dismissOrderDialog,
    modifier = modifier,
  )
}

@Composable
internal fun CheckoutContent(
  uiState: CheckoutUiState,
  onSelectCoupon: (String) -> Unit,
  onUpdateQuantity: (String, Int) -> Unit,
  onToggleItemSelection: (String) -> Unit = {},
  onToggleSelectAll: () -> Unit = {},
  onCycleItemSpec: (String) -> Unit = {},
  onAddCatalogItem: (String) -> Unit = {},
  onResetCart: () -> Unit = {},
  onSelectDeliveryMode: (DeliveryMode) -> Unit = {},
  onUpdatePromoNote: (String) -> Unit,
  onAppendQuickNoteTag: (String) -> Unit = {},
  onSubmitOrder: () -> Unit = {},
  onDismissOrderDialog: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  if (uiState.showOrderSubmittedDialog) {
    OrderSubmittedSummaryDialog(
      uiState = uiState,
      onDismiss = onDismissOrderDialog,
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      // 底部固定结算栏：使用 Scaffold bottomBar 槽位并在 Row 上应用 navigationBarsPadding() 确保内容避开系统导航栏
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("checkout_bottom_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
      ) {
        val totalSaved =
          (uiState.breakdown.subtotal - uiState.breakdown.finalPayable).coerceAtLeast(0.0)

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // 左侧：全选按钮
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(onClick = onToggleSelectAll),
          ) {
            MallCheckCircle(
              checked = uiState.isAllSelected,
              onClick = onToggleSelectAll,
            )
            Text(
              text = "全选",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // 右侧：合计金额 + 立即结算胶囊按钮
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(horizontalAlignment = Alignment.End) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "合计:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = formatYen(uiState.breakdown.finalPayable),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (uiState.breakdown.finalPayable < 0) MaterialTheme.colorScheme.error else CoralPrice,
                  modifier = Modifier.testTag("checkout_final_payable_text"),
                )
              }
              Text(
                text = "共省 ${formatYen(totalSaved)}",
                style = MaterialTheme.typography.labelSmall,
                color = CoralPrice,
              )
            }

            Button(
              onClick = onSubmitOrder,
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(
                containerColor = BrandBlue,
                contentColor = Color.White,
              ),
              contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
              modifier = Modifier.testTag("checkout_submit_button"),
            ) {
              Text(
                text = "立即结算(${uiState.totalSelectedCount})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .consumeWindowInsets(innerPadding)
        .imePadding()
        .testTag("checkout_item_list"),
      contentPadding = innerPadding,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. GDG DevFest Shanghai 商城沉浸式顶栏 + 满 200 打 8 折动态进度条
      item {
        MallHeroBanner(
          subtotal = uiState.breakdown.subtotal,
          threshold = 200.0,
          onResetCart = onResetCart,
        )
      }

      // 2. 上海站会场自提 / 顺丰包邮切换卡片
      item {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
          DeliveryAddressCard(
            selectedMode = uiState.deliveryMode,
            onSelectMode = onSelectDeliveryMode,
          )
        }
      }

      // 3. 购物车商品卡片列表
      items(uiState.items, key = { it.id }) { item ->
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
          MallMerchItemCard(
            item = item,
            isSelected = item.id in uiState.selectedItemIds,
            selectedSpecIndex = uiState.selectedSpecs[item.id] ?: 0,
            onToggleSelect = { onToggleItemSelection(item.id) },
            onCycleSpec = { onCycleItemSpec(item.id) },
            onDecrease = { onUpdateQuantity(item.id, -1) },
            onIncrease = { onUpdateQuantity(item.id, 1) },
          )
        }
      }

      // 4. 上海站人气周边一键凑单横滑区
      if (uiState.addonCandidates.isNotEmpty()) {
        item {
          Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            AddonMerchSection(
              candidates = uiState.addonCandidates,
              onAddItem = onAddCatalogItem,
            )
          }
        }
      }

      // 5. 商城优惠券卡券包选择区
      item {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
          CouponTicketSection(
            coupons = uiState.availableCoupons,
            selectedCouponId = uiState.selectedCouponId,
            onSelectCoupon = onSelectCoupon,
          )
        }
      }

      // 6. 订单备注 + 快捷备注标签（用于测试软键盘 IME Inset）
      item {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
          OrderNoteCard(
            promoNote = uiState.promoNote,
            onUpdatePromoNote = onUpdatePromoNote,
            onAppendQuickTag = onAppendQuickNoteTag,
          )
        }
      }

      // 7. 结算金额明细小票卡片
      item {
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
          PriceBreakdownReceiptCard(uiState = uiState)
        }
      }

      // 列表底部预留适当间隔
      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun OrderNoteCard(
  promoNote: String,
  onUpdatePromoNote: (String) -> Unit,
  onAppendQuickTag: (String) -> Unit,
) {
  val quickTags = listOf(
    "需要 GDG 上海限定手提袋",
    "附赠 Android Bot 徽章",
    "会场下午茶时段自提",
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "订单备注与定制心愿",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "点击标签快捷填入",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        quickTags.forEach { tag ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable { onAppendQuickTag(tag) },
          ) {
            Text(
              text = "+ $tag",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }

      OutlinedTextField(
        value = promoNote,
        onValueChange = onUpdatePromoNote,
        label = { Text("订单备注（选填）") },
        placeholder = { Text("例如：需要 GDG DevFest Shanghai 纪念纸袋") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("checkout_note_input"),
        singleLine = true,
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CheckoutScreenPreview() {
  DevFestCartLabTheme {
    CheckoutContent(
      uiState = CheckoutUiState(),
      onSelectCoupon = {},
      onUpdateQuantity = { _, _ -> },
      onUpdatePromoNote = {},
    )
  }
}

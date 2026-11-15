package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
import com.example.ui.components.AdBannerSlot
import com.example.ui.viewmodel.DukanMainUiState
import com.example.util.formatPkr
import com.example.util.isToday
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
  uiState: DukanMainUiState,
  isOnline: Boolean,
  strings: AppStrings,
  onSeedDemoData: () -> Unit,
  onTrackAdImpression: (String) -> Unit,
  onTrackAdClick: (String) -> Unit,
  onOpenPos: () -> Unit,
) {
  val shop = uiState.activeShop ?: return
  val canSeeProfit = uiState.currentRole != UserRole.CASHIER

  val todayCompletedSales = remember(uiState.sales) {
    uiState.sales.filter { it.status == "completed" && isToday(it.createdAt) }
  }
  val todayRevenue = remember(todayCompletedSales) { todayCompletedSales.sumOf { it.grandTotal } }
  val todayCost = remember(todayCompletedSales) {
    todayCompletedSales.sumOf { s -> s.items.sumOf { it.purchasePrice * it.qty } }
  }
  val todayExpenses = remember(uiState.expenses) {
    uiState.expenses.filter { isToday(it.createdAt) }.sumOf { it.amount }
  }
  val todayProfit = todayRevenue - todayCost - todayExpenses
  val pendingUdhaar = remember(uiState.customers) {
    uiState.customers.filter { it.balance > 0 }.sumOf { it.balance }
  }
  val lowStockCount = remember(uiState.products) {
    uiState.products.count { it.stockQty <= it.lowStockLimit }
  }
  val top10Udhaar = remember(uiState.customers) {
    uiState.customers.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(10)
  }

  val last7Days = remember(uiState.sales) {
    val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val labelFmt = SimpleDateFormat("dd/MM", Locale.ENGLISH)
    val completed = uiState.sales.filter { it.status == "completed" }
    (6 downTo 0).map { daysAgo ->
      val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
      val dayKey = keyFmt.format(cal.time)
      val dayTotal =
        completed
          .filter { s -> s.createdAt?.toDate()?.let { keyFmt.format(it) == dayKey } == true }
          .sumOf { it.grandTotal }
      labelFmt.format(cal.time) to dayTotal
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      KpiCard(
        title = strings.todaySales,
        value = formatPkr(todayRevenue, shop.currency),
        icon = Icons.Default.PointOfSale,
        modifier = Modifier.weight(1f),
      )
      if (canSeeProfit) {
        KpiCard(
          title = strings.todayProfit,
          value = formatPkr(todayProfit, shop.currency),
          icon = Icons.Default.TrendingUp,
          modifier = Modifier.weight(1f),
        )
      } else {
        KpiCard(
          title = strings.lowStock,
          value = "$lowStockCount",
          icon = Icons.Default.Inventory,
          modifier = Modifier.weight(1f),
        )
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      KpiCard(
        title = strings.pendingUdhaar,
        value = formatPkr(pendingUdhaar, shop.currency),
        icon = Icons.Default.AccountBalanceWallet,
        modifier = Modifier.weight(1f),
      )
      if (canSeeProfit) {
        KpiCard(
          title = strings.todayExpenses,
          value = formatPkr(todayExpenses, shop.currency),
          icon = Icons.Default.MoneyOff,
          modifier = Modifier.weight(1f),
        )
      } else {
        KpiCard(
          title = strings.todaySales,
          value = "${todayCompletedSales.size} bills",
          icon = Icons.Default.PointOfSale,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (canSeeProfit) {
      Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.padding(14.dp).fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("${strings.lowStock}: $lowStockCount ${if (strings.isUrdu) "اشیاء" else "items"}", style = MaterialTheme.typography.titleMedium)
          }
          Button(onClick = onOpenPos) { Text(strings.navPos) }
        }
      }
    }

    if (uiState.products.isEmpty() && canSeeProfit) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
              if (strings.isUrdu) "فوری ٹیسٹنگ ڈیٹا" else "Quick Start Pakistani Kiryana Demo",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Button(
            onClick = onSeedDemoData,
            modifier = Modifier.fillMaxWidth().testTag("seed_demo_data_button"),
          ) {
            Text(strings.seedDemoData)
          }
        }
      }
    }

    // 7-Day Sales Bar Chart
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.last7DaysSales, style = MaterialTheme.typography.titleMedium)
        val primaryColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val maxVal = (last7Days.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(100.0)
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
          val barSlot = size.width / last7Days.size
          val barW = barSlot * 0.52f
          last7Days.forEachIndexed { idx, (_, value) ->
            val left = idx * barSlot + (barSlot - barW) / 2f
            drawRoundRect(
              color = trackColor,
              topLeft = Offset(left, 0f),
              size = Size(barW, size.height),
              cornerRadius = CornerRadius(12f, 12f),
            )
            val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
            val barH = size.height * ratio
            if (barH > 0f) {
              drawRoundRect(
                color = primaryColor,
                topLeft = Offset(left, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(12f, 12f),
              )
            }
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          last7Days.forEach { (label, _) ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // Top 10 Pending Udhaar Customers
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.topUdhaar, style = MaterialTheme.typography.titleMedium)
        if (top10Udhaar.isEmpty()) {
          Text(
            if (strings.isUrdu) "کوئی واجب الادا ادھار نہیں" else "No pending Udhaar balances.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          top10Udhaar.forEach { cust ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(cust.name, style = MaterialTheme.typography.bodyLarge)
              Text(
                formatPkr(cust.balance, shop.currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }

    // Dashboard bottom Ad Slot (NEVER shown on POS, hidden on Pro or offline)
    AdBannerSlot(
      shop = shop,
      isOnline = isOnline,
      ads = uiState.activeAds,
      placement = "dashboard",
      strings = strings,
      onImpression = onTrackAdImpression,
      onClick = onTrackAdClick,
    )
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun KpiCard(
  title: String,
  value: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(6.dp),
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
  }
}

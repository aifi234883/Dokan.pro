package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.PurchaseItem
import com.example.data.model.Shop
import com.example.data.model.Supplier
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
import com.example.ui.components.AdBannerSlot
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.viewmodel.DukanMainUiState
import com.example.util.ExportAndPrintUtils
import com.example.util.daysUntilExpiry
import com.example.util.formatPkr
import com.example.util.formatQty
import com.example.util.formatTimestampDisplay
import com.example.util.isToday

private enum class MoreSubScreen {
  HUB, REPORTS, SUPPLIERS, EXPENSES, DAY_CLOSING, SETTINGS, UPGRADE
}

@Composable
fun MoreManagementScreen(
  uiState: DukanMainUiState,
  isOnline: Boolean,
  strings: AppStrings,
  onToggleLanguage: () -> Unit,
  onToggleTheme: () -> Unit,
  onSignOut: () -> Unit,
  onSaveSettings: (Shop) -> Unit,
  onInviteStaff: (String, String) -> Unit,
  onUpdateMember: (String, String, Boolean) -> Unit,
  onAddExpense: (String, Double, String) -> Unit,
  onSaveSupplier: (Supplier) -> Unit,
  onRecordPurchase: (String, String, List<PurchaseItem>, Double, Boolean) -> Unit,
  onPaySupplier: (String, Double) -> Unit,
  onSaveDayClosing: (Double, Double, Double, Double) -> Unit,
  onSubmitUpgrade: (String, String, String) -> Unit,
  onSeedDemoData: () -> Unit,
  onTrackAdImpression: (String) -> Unit,
  onTrackAdClick: (String) -> Unit,
) {
  var subScreen by remember { mutableStateOf(MoreSubScreen.HUB) }
  val isManagerOrOwner = uiState.currentRole != UserRole.CASHIER
  val isOwner = uiState.currentRole == UserRole.OWNER

  if (subScreen != MoreSubScreen.HUB) {
    BackHandler { subScreen = MoreSubScreen.HUB }
  }

  when (subScreen) {
    MoreSubScreen.HUB -> {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (isManagerOrOwner) {
          HubTile(Icons.Default.Assessment, strings.reportsTitle) { subScreen = MoreSubScreen.REPORTS }
          HubTile(Icons.Default.LocalShipping, strings.suppliersTitle) { subScreen = MoreSubScreen.SUPPLIERS }
          HubTile(Icons.Default.ReceiptLong, strings.expensesTitle) { subScreen = MoreSubScreen.EXPENSES }
          HubTile(Icons.Default.PointOfSale, strings.dayClosingTitle) { subScreen = MoreSubScreen.DAY_CLOSING }
        }
        if (isOwner) {
          HubTile(Icons.Default.Settings, strings.settingsTitle) { subScreen = MoreSubScreen.SETTINGS }
          if (uiState.activeShop?.plan != "pro") {
            HubTile(Icons.Default.WorkspacePremium, strings.upgradeProTitle) { subScreen = MoreSubScreen.UPGRADE }
          }
        }

        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (strings.isUrdu) "زبان (اردو / English)" else "Language (English / اردو)")
              }
              Switch(checked = strings.isUrdu, onCheckedChange = { onToggleLanguage() })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (strings.isUrdu) "ڈارک تھیم" else "Dark Theme")
              }
              Switch(checked = uiState.preferences.isDarkTheme, onCheckedChange = { onToggleTheme() })
            }
          }
        }

        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.Default.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(strings.signOut)
        }
      }
    }

    MoreSubScreen.REPORTS -> ReportsSubScreen(
      uiState = uiState,
      isOnline = isOnline,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onTrackAdImpression = onTrackAdImpression,
      onTrackAdClick = onTrackAdClick,
    )

    MoreSubScreen.SUPPLIERS -> SuppliersSubScreen(
      uiState = uiState,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onSaveSupplier = onSaveSupplier,
      onRecordPurchase = onRecordPurchase,
      onPaySupplier = onPaySupplier,
    )

    MoreSubScreen.EXPENSES -> ExpensesSubScreen(
      uiState = uiState,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onAddExpense = onAddExpense,
    )

    MoreSubScreen.DAY_CLOSING -> DayClosingSubScreen(
      uiState = uiState,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onSaveDayClosing = onSaveDayClosing,
    )

    MoreSubScreen.SETTINGS -> SettingsSubScreen(
      uiState = uiState,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onSaveSettings = onSaveSettings,
      onInviteStaff = onInviteStaff,
      onUpdateMember = onUpdateMember,
      onSeedDemoData = onSeedDemoData,
    )

    MoreSubScreen.UPGRADE -> UpgradeProSubScreen(
      uiState = uiState,
      strings = strings,
      onBack = { subScreen = MoreSubScreen.HUB },
      onSubmitUpgrade = onSubmitUpgrade,
    )
  }
}

@Composable
private fun HubTile(icon: ImageVector, title: String, onClick: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
  }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
    Text(title, style = MaterialTheme.typography.titleLarge)
  }
}

@Composable
private fun ReportsSubScreen(
  uiState: DukanMainUiState,
  isOnline: Boolean,
  strings: AppStrings,
  onBack: () -> Unit,
  onTrackAdImpression: (String) -> Unit,
  onTrackAdClick: (String) -> Unit,
) {
  val context = LocalContext.current
  val shop = uiState.activeShop ?: return
  val completed = uiState.sales.filter { it.status == "completed" }
  val totalSales = completed.sumOf { it.grandTotal }
  val totalCost = completed.sumOf { s -> s.items.sumOf { it.purchasePrice * it.qty } }
  val totalExp = uiState.expenses.sumOf { it.amount }
  val netProfit = totalSales - totalCost - totalExp

  val bestSellers = remember(completed) {
    completed.flatMap { it.items }.groupBy { it.name }.mapValues { (_, list) -> list.sumOf { it.qty } }
      .entries.sortedByDescending { it.value }.take(5)
  }
  val paymentBreakdown = remember(completed) {
    PaymentMethod.entries.map { m ->
      m to completed.filter { it.paymentMethod == m.key }.sumOf { it.grandTotal }
    }
  }
  val lowStockItems = uiState.products.filter { it.stockQty <= it.lowStockLimit }
  val expiringItems = uiState.products.filter { val d = daysUntilExpiry(it.expiryDate); d != null && d <= 30 }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.reportsTitle, onBack)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("P&L Summary (${completed.size} sales)", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Gross Sales:"); Text(formatPkr(totalSales, shop.currency), fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cost of Goods:"); Text(formatPkr(totalCost, shop.currency)) }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Total Expenses:"); Text(formatPkr(totalExp, shop.currency)) }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
          Text("Net Profit:", fontWeight = FontWeight.Bold)
          Text(formatPkr(netProfit, shop.currency), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Payment Breakdown", style = MaterialTheme.typography.titleMedium)
        paymentBreakdown.forEach { (m, amt) ->
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(if (strings.isUrdu) m.labelUr else m.labelEn)
            Text(formatPkr(amt, shop.currency))
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Top Selling Products", style = MaterialTheme.typography.titleMedium)
        bestSellers.forEach { (name, qty) ->
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(name)
            Text("${formatQty(qty)} sold")
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Low Stock (${lowStockItems.size}) • Expiring (${expiringItems.size})", style = MaterialTheme.typography.titleMedium)
        lowStockItems.take(8).forEach { Text("• ${it.name}: ${formatQty(it.stockQty)} ${it.unit}") }
      }
    }

    Button(
      onClick = {
        val csv = ExportAndPrintUtils.salesReportToCsv(uiState.sales)
        ExportAndPrintUtils.shareTextFile(context, csv, "dukanpro_sales_report.csv", "text/csv")
      },
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Export Sales Report (CSV)") }

    AdBannerSlot(
      shop = shop,
      isOnline = isOnline,
      ads = uiState.activeAds,
      placement = "dashboard",
      strings = strings,
      onImpression = onTrackAdImpression,
      onClick = onTrackAdClick,
    )
  }
}

@Composable
private fun SuppliersSubScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onBack: () -> Unit,
  onSaveSupplier: (Supplier) -> Unit,
  onRecordPurchase: (String, String, List<PurchaseItem>, Double, Boolean) -> Unit,
  onPaySupplier: (String, Double) -> Unit,
) {
  val shop = uiState.activeShop ?: return
  var showAddSupplier by remember { mutableStateOf(false) }
  var purchaseForSupplier by remember { mutableStateOf<Supplier?>(null) }
  var payForSupplier by remember { mutableStateOf<Supplier?>(null) }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.suppliersTitle, onBack)
    Button(onClick = { showAddSupplier = true }, modifier = Modifier.fillMaxWidth()) {
      Text(if (strings.isUrdu) "نیا سپلائر شامل کریں" else "Add Supplier")
    }
    uiState.suppliers.forEach { sup ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Column {
              Text(sup.name, style = MaterialTheme.typography.titleMedium)
              Text(sup.phone, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Payable: ${formatPkr(sup.balance, shop.currency)}", fontWeight = FontWeight.Bold)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { purchaseForSupplier = sup }) { Text("New Purchase") }
            Button(onClick = { payForSupplier = sup }) { Text("Pay") }
          }
        }
      }
    }
  }

  if (showAddSupplier) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showAddSupplier = false },
      title = { Text("Add Supplier") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name") }, singleLine = true)
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
        }
      },
      dismissButton = { TextButton(onClick = { showAddSupplier = false }) { Text(strings.cancel) } },
      confirmButton = {
        Button(onClick = { onSaveSupplier(Supplier(name = name, phone = phone)); showAddSupplier = false }, enabled = name.isNotBlank()) {
          Text(strings.save)
        }
      },
    )
  }

  purchaseForSupplier?.let { sup ->
    val firstProd = uiState.products.firstOrNull()
    var selectedProdId by remember(sup.id) { mutableStateOf(firstProd?.id ?: "") }
    var qty by remember(sup.id) { mutableStateOf("10") }
    var cost by remember(sup.id) { mutableStateOf((firstProd?.purchasePrice ?: 0.0).toString()) }
    var paid by remember(sup.id) { mutableStateOf("0") }
    var updateCost by remember(sup.id) { mutableStateOf(true) }
    var showPurchaseScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val selectedProd = uiState.products.firstOrNull { it.id == selectedProdId }

    AlertDialog(
      onDismissRequest = { purchaseForSupplier = null },
      title = { Text("Purchase from ${sup.name}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { showPurchaseScanner = true },
            modifier = Modifier.fillMaxWidth().testTag("purchase_scan_barcode_button"),
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (strings.isUrdu) "بارکوڈ اسکین کریں" else "Scan Barcode")
          }
          if (scanError != null) {
            Text(scanError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
          }
          if (selectedProd != null) {
            Text(
              if (strings.isUrdu) "منتخب: ${selectedProd.name}" else "Selected: ${selectedProd.name}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.products.take(15).forEach { p ->
              FilterChip(
                selected = selectedProdId == p.id,
                onClick = {
                  selectedProdId = p.id
                  cost = p.purchasePrice.toString()
                  scanError = null
                },
                label = { Text(p.name.take(18)) },
              )
            }
          }
          OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
          OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Unit Cost (Rs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
          OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text("Paid Now (Rs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = updateCost, onCheckedChange = { updateCost = it })
            Spacer(Modifier.width(8.dp))
            Text("Update product cost price")
          }
        }
      },
      dismissButton = { TextButton(onClick = { purchaseForSupplier = null }) { Text(strings.cancel) } },
      confirmButton = {
        Button(
          onClick = {
            val prod = uiState.products.firstOrNull { it.id == selectedProdId }
            val q = qty.toDoubleOrNull() ?: 0.0
            val c = cost.toDoubleOrNull() ?: 0.0
            if (prod != null && q > 0) {
              onRecordPurchase(
                sup.id,
                sup.name,
                listOf(PurchaseItem(prod.id, prod.name, q, c, q * c)),
                paid.toDoubleOrNull() ?: 0.0,
                updateCost,
              )
            }
            purchaseForSupplier = null
          },
          enabled = selectedProdId.isNotBlank() && (qty.toDoubleOrNull() ?: 0.0) > 0.0,
        ) { Text(strings.save) }
      },
    )

    if (showPurchaseScanner) {
      BarcodeScannerDialog(
        strings = strings,
        onBarcodeDetected = { rawCode ->
          showPurchaseScanner = false
          val clean = rawCode.trim()
          val matched = uiState.barcodeMap[clean]
          if (matched != null) {
            selectedProdId = matched.id
            cost = matched.purchasePrice.toString()
            scanError = null
          } else {
            scanError = if (strings.isUrdu) "بارکوڈ نہیں ملا: $clean" else "Barcode not found: $clean"
          }
        },
        onDismiss = { showPurchaseScanner = false },
      )
    }
  }

  payForSupplier?.let { sup ->
    var amt by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { payForSupplier = null },
      title = { Text("Pay ${sup.name}") },
      text = {
        OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (Rs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
      },
      dismissButton = { TextButton(onClick = { payForSupplier = null }) { Text(strings.cancel) } },
      confirmButton = {
        Button(onClick = { onPaySupplier(sup.id, amt.toDoubleOrNull() ?: 0.0); payForSupplier = null }, enabled = (amt.toDoubleOrNull() ?: 0.0) > 0) {
          Text(strings.save)
        }
      },
    )
  }
}

@Composable
private fun ExpensesSubScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onBack: () -> Unit,
  onAddExpense: (String, Double, String) -> Unit,
) {
  val shop = uiState.activeShop ?: return
  var category by remember { mutableStateOf(ExpenseCategory.OTHER.key) }
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.expensesTitle, onBack)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ExpenseCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat.key,
              onClick = { category = cat.key },
              label = { Text(if (strings.isUrdu) cat.labelUr else cat.labelEn) },
            )
          }
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (${shop.currency})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Button(
          onClick = {
            onAddExpense(category, amount.toDoubleOrNull() ?: 0.0, note)
            amount = ""
            note = ""
          },
          enabled = (amount.toDoubleOrNull() ?: 0.0) > 0,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Add Expense") }
      }
    }
    uiState.expenses.forEach { exp ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text(exp.category.uppercase(), style = MaterialTheme.typography.titleMedium)
            if (exp.note.isNotBlank()) Text(exp.note, style = MaterialTheme.typography.bodyMedium)
            Text(formatTimestampDisplay(exp.createdAt), style = MaterialTheme.typography.labelSmall)
          }
          Text(formatPkr(exp.amount, shop.currency), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}

@Composable
private fun DayClosingSubScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onBack: () -> Unit,
  onSaveDayClosing: (Double, Double, Double, Double) -> Unit,
) {
  val shop = uiState.activeShop ?: return
  val todayCashSales = uiState.sales.filter { it.status == "completed" && it.paymentMethod == "cash" && isToday(it.createdAt) }.sumOf { it.paidAmount }
  val todayExp = uiState.expenses.filter { isToday(it.createdAt) }.sumOf { it.amount }
  var opening by remember { mutableStateOf("0") }
  var counted by remember { mutableStateOf("") }
  val openVal = opening.toDoubleOrNull() ?: 0.0
  val expected = openVal + todayCashSales - todayExp
  val countedVal = counted.toDoubleOrNull() ?: 0.0
  val diff = countedVal - expected

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.dayClosingTitle, onBack)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Cash Drawer (Rs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Text("Today Cash Sales: ${formatPkr(todayCashSales, shop.currency)}")
        Text("Today Expenses: ${formatPkr(todayExp, shop.currency)}")
        Text("Expected Cash in Box: ${formatPkr(expected, shop.currency)}", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = counted, onValueChange = { counted = it }, label = { Text("Counted Cash in Box (Rs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if (counted.isNotBlank()) {
          Text(
            "Difference: ${formatPkr(diff, shop.currency)}",
            color = if (diff == 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
          )
        }
        Button(
          onClick = { onSaveDayClosing(openVal, todayCashSales, todayExp, countedVal) },
          enabled = counted.isNotBlank(),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Close Day Register") }
      }
    }
    uiState.dayClosings.forEach { dc ->
      Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text(dc.dateDisplay, style = MaterialTheme.typography.titleMedium)
            Text("Expected: ${formatPkr(dc.expectedCash, shop.currency)} | Counted: ${formatPkr(dc.countedCash, shop.currency)}", style = MaterialTheme.typography.bodyMedium)
          }
          Text("Diff: ${formatPkr(dc.difference, shop.currency)}", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun SettingsSubScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onBack: () -> Unit,
  onSaveSettings: (Shop) -> Unit,
  onInviteStaff: (String, String) -> Unit,
  onUpdateMember: (String, String, Boolean) -> Unit,
  onSeedDemoData: () -> Unit,
) {
  val context = LocalContext.current
  val shop = uiState.activeShop ?: return
  var name by remember(shop.id) { mutableStateOf(shop.name) }
  var address by remember(shop.id) { mutableStateOf(shop.address) }
  var phone by remember(shop.id) { mutableStateOf(shop.phone) }
  var footer by remember(shop.id) { mutableStateOf(shop.receiptFooter) }
  var tax by remember(shop.id) { mutableStateOf(shop.taxRate.toString()) }
  var showSponsor by remember(shop.id) { mutableStateOf(shop.showReceiptSponsor) }
  var allowNeg by remember(shop.id) { mutableStateOf(shop.allowNegativeStockOverride) }

  var staffEmail by remember { mutableStateOf("") }
  var staffRole by remember { mutableStateOf("cashier") }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.settingsTitle, onBack)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Shop Profile (Plan: ${shop.plan.uppercase()})", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tax, onValueChange = { tax = it }, label = { Text("Default Tax %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = footer, onValueChange = { footer = it }, label = { Text("Receipt Footer") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text("Show Sponsor on Receipt")
          Switch(checked = showSponsor, onCheckedChange = { showSponsor = it })
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text("Allow Negative Stock Override")
          Switch(checked = allowNeg, onCheckedChange = { allowNeg = it })
        }
        Button(
          onClick = {
            onSaveSettings(
              shop.copy(
                name = name,
                phone = phone,
                address = address,
                taxRate = tax.toDoubleOrNull() ?: 0.0,
                receiptFooter = footer,
                showReceiptSponsor = showSponsor,
                allowNegativeStockOverride = allowNeg,
              )
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text(strings.save) }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Staff Management", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = staffEmail, onValueChange = { staffEmail = it }, label = { Text("Staff Google Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = staffRole == "cashier", onClick = { staffRole = "cashier" }, label = { Text("Cashier") })
          FilterChip(selected = staffRole == "manager", onClick = { staffRole = "manager" }, label = { Text("Manager") })
        }
        Button(
          onClick = { onInviteStaff(staffEmail, staffRole); staffEmail = "" },
          enabled = staffEmail.contains("@"),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Invite Staff") }
        HorizontalDivider()
        uiState.members.forEach { m ->
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
              Text("${m.name} (${m.role.uppercase()})", style = MaterialTheme.typography.bodyLarge)
              Text(m.email, style = MaterialTheme.typography.labelSmall)
            }
            if (m.role != "owner") {
              Switch(checked = m.active, onCheckedChange = { onUpdateMember(m.uid, m.role, it) })
            }
          }
        }
      }
    }

    OutlinedButton(
      onClick = {
        val json = ExportAndPrintUtils.buildJsonBackup(shop, uiState.products, uiState.customers, uiState.sales)
        ExportAndPrintUtils.shareTextFile(context, json, "dukanpro_backup.json", "application/json")
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Default.Backup, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Export Full JSON Backup")
    }

    OutlinedButton(onClick = onSeedDemoData, modifier = Modifier.fillMaxWidth()) {
      Text(strings.seedDemoData)
    }
  }
}

@Composable
private fun UpgradeProSubScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onBack: () -> Unit,
  onSubmitUpgrade: (String, String, String) -> Unit,
) {
  val context = LocalContext.current
  val accountNumber = "03363135004"
  val accountName = "Saifullah"

  var method by remember { mutableStateOf("easypaisa") }
  var txRef by remember { mutableStateOf("") }
  var proofNote by remember { mutableStateOf("") }

  val screenshotPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) proofNote = "Screenshot attached: ${uri.lastPathSegment}"
    }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SubHeader(strings.upgradeProTitle, onBack)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          if (strings.isUrdu) "دکان پرو (بغیر اشتہار) - 500 روپے ماہانہ" else "DukanPro Ad-Free Plan - Rs 500 / month",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          if (strings.isUrdu) "1. نیچے دیے گئے ایزی پیسہ اکاؤنٹ پر 500 روپے بھیجیں:"
          else "1. Send Rs 500 to the EasyPaisa account below:",
        )
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("EasyPaisa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
              Text(
                if (strings.isUrdu) "اکاؤنٹ نام: $accountName" else "Account Name: $accountName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                if (strings.isUrdu) "نمبر: $accountNumber" else "Account No: $accountNumber",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            OutlinedButton(
              onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("EasyPaisa Number", accountNumber))
                Toast.makeText(
                  context,
                  if (strings.isUrdu) "نمبر کاپی ہو گیا: $accountNumber" else "Copied: $accountNumber",
                  Toast.LENGTH_SHORT,
                ).show()
              },
              modifier = Modifier.testTag("copy_easypaisa_button"),
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (strings.isUrdu) "کاپی" else "Copy")
            }
          }
        }
        Text(
          if (strings.isUrdu) "2. ٹرانزیکشن آئی ڈی (TID) درج کریں اور اسکرین شاٹ منسلک کریں۔"
          else "2. Enter Transaction ID (TID) and attach payment screenshot.",
        )
        Text(
          if (strings.isUrdu) "3. تصدیق کے بعد آپ کی دکان سے تمام اشتہارات ختم ہو جائیں گے۔"
          else "3. Verification removes all ads from your shop.",
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = method == "easypaisa", onClick = { method = "easypaisa" }, label = { Text("EasyPaisa") })
      FilterChip(selected = method == "jazzcash", onClick = { method = "jazzcash" }, label = { Text("JazzCash") })
    }
    OutlinedTextField(
      value = txRef,
      onValueChange = { txRef = it },
      label = { Text(if (strings.isUrdu) "ٹرانزیکشن آئی ڈی (TID)" else "Transaction ID (TID)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("upgrade_tid_input"),
    )
    OutlinedButton(
      onClick = { screenshotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        if (proofNote.isBlank()) (if (strings.isUrdu) "ادائیگی کا اسکرین شاٹ منسلک کریں" else "Attach Payment Screenshot")
        else proofNote
      )
    }
    Button(
      onClick = { onSubmitUpgrade(method, txRef, proofNote); txRef = ""; proofNote = "" },
      enabled = txRef.length >= 3,
      modifier = Modifier.fillMaxWidth().testTag("submit_upgrade_button"),
    ) { Text(if (strings.isUrdu) "پرو ایکٹیویشن کے لیے جمع کریں" else "Submit for Pro Activation") }

    if (uiState.upgradeRequests.isNotEmpty()) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
      Text(
        if (strings.isUrdu) "آپ کی درخواستیں" else "Your Upgrade Requests",
        style = MaterialTheme.typography.titleMedium,
      )
      uiState.upgradeRequests.forEach { req ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("${req.paymentMethod.uppercase()} • TID: ${req.transactionRef}", style = MaterialTheme.typography.titleMedium)
              Text(formatTimestampDisplay(req.createdAt), style = MaterialTheme.typography.labelSmall)
            }
            Text(
              req.status.uppercase(),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (req.status == "approved") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }
  }
}

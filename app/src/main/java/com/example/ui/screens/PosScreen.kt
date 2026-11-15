package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Customer
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.Shop
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.ReceiptModalDialog
import com.example.ui.components.pickActiveAdForPlacement
import com.example.ui.viewmodel.CartLine
import com.example.ui.viewmodel.DukanMainUiState
import com.example.ui.viewmodel.PosTotals
import com.example.ui.viewmodel.PosUiState
import com.example.util.encodeProductImageUriToDataUrl
import com.example.util.formatPkr
import com.example.util.formatQty
import com.example.util.formatTimestampDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
  uiState: DukanMainUiState,
  posState: PosUiState,
  totals: PosTotals,
  strings: AppStrings,
  onScanBarcode: (String) -> Unit,
  onClearUnmatchedBarcode: () -> Unit,
  onDismissLastScanned: () -> Unit,
  onQuickAddProduct: (Product) -> Unit,
  onAddProductToCart: (Product) -> Unit,
  onUpdateCartQty: (String, Double) -> Unit,
  onSetDiscount: (String, Boolean) -> Unit,
  onSetPaymentMethod: (String) -> Unit,
  onSelectCustomer: (String) -> Unit,
  onSetReceived: (String) -> Unit,
  onCompleteSale: (Boolean) -> Unit,
  onHoldBill: () -> Unit,
  onResumeHeldSale: (Sale) -> Unit,
  onReturnSale: (Sale) -> Unit,
  onClearCart: () -> Unit,
  onDismissReceipt: () -> Unit,
  onShowReceipt: (Sale) -> Unit,
  onDismissNegativeWarning: () -> Unit,
) {
  val shop = uiState.activeShop ?: return
  var scanInput by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("") }
  var showCameraScanner by remember { mutableStateOf(false) }
  var showMobileCartSheet by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }
  var quickAddBarcode by remember { mutableStateOf<String?>(null) }
  var qtyEditLine by remember { mutableStateOf<CartLine?>(null) }

  val scanFocusRequester = remember { FocusRequester() }

  val categories = remember(uiState.products) {
    listOf("") + uiState.products.map { it.category }.distinct().sorted()
  }

  val filteredProducts = remember(uiState.products, searchQuery, selectedCategory) {
    val q = searchQuery.trim().lowercase()
    uiState.products.filter { p ->
      val catOk = selectedCategory.isBlank() || p.category == selectedCategory
      val qOk =
        q.isBlank() ||
          p.name.lowercase().contains(q) ||
          p.nameUrdu.lowercase().contains(q) ||
          p.barcode.lowercase().contains(q)
      catOk && qOk
    }
  }

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
          when (event.key) {
            Key.F2 -> {
              scanFocusRequester.requestFocus()
              true
            }
            Key.F4 -> {
              onCompleteSale(false)
              true
            }
            Key.Escape -> {
              onClearCart()
              true
            }
            else -> false
          }
        } else false
      }
  ) {
    val isWide = maxWidth >= 780.dp

    if (isWide) {
      Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
          PosCatalogPane(
            shop = shop,
            strings = strings,
            scanInput = scanInput,
            onScanInputChange = { scanInput = it },
            onSubmitScan = {
              onScanBarcode(scanInput)
              scanInput = ""
            },
            scanFocusRequester = scanFocusRequester,
            onOpenCamera = { showCameraScanner = true },
            onOpenHistory = { showHistoryDialog = true },
            heldCount = uiState.sales.count { it.status == "held" },
            lastScannedProduct = posState.lastScannedProduct,
            lastScannedCartQty = posState.lastScannedProduct?.let { scanned ->
              posState.lines.firstOrNull { it.product.id == scanned.id }?.qty ?: 0.0
            } ?: 0.0,
            onDismissLastScanned = onDismissLastScanned,
            unmatchedBarcode = posState.unmatchedBarcode,
            onQuickAddBarcode = { code ->
              quickAddBarcode = code
              onClearUnmatchedBarcode()
            },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            products = filteredProducts,
            onTapProduct = onAddProductToCart,
            columns = 3,
          )
        }
        Surface(
          shape = RoundedCornerShape(18.dp),
          tonalElevation = 2.dp,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
          PosCartPane(
            shop = shop,
            posState = posState,
            totals = totals,
            customers = uiState.customers,
            strings = strings,
            onUpdateQty = onUpdateCartQty,
            onEditQtyKeypad = { qtyEditLine = it },
            onSetDiscount = onSetDiscount,
            onSetPaymentMethod = onSetPaymentMethod,
            onSelectCustomer = onSelectCustomer,
            onSetReceived = onSetReceived,
            onCompleteSale = { onCompleteSale(false) },
            onHoldBill = onHoldBill,
            onClearCart = onClearCart,
          )
        }
      }
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
          PosCatalogPane(
            shop = shop,
            strings = strings,
            scanInput = scanInput,
            onScanInputChange = { scanInput = it },
            onSubmitScan = {
              onScanBarcode(scanInput)
              scanInput = ""
            },
            scanFocusRequester = scanFocusRequester,
            onOpenCamera = { showCameraScanner = true },
            onOpenHistory = { showHistoryDialog = true },
            heldCount = uiState.sales.count { it.status == "held" },
            lastScannedProduct = posState.lastScannedProduct,
            lastScannedCartQty = posState.lastScannedProduct?.let { scanned ->
              posState.lines.firstOrNull { it.product.id == scanned.id }?.qty ?: 0.0
            } ?: 0.0,
            onDismissLastScanned = onDismissLastScanned,
            unmatchedBarcode = posState.unmatchedBarcode,
            onQuickAddBarcode = { code ->
              quickAddBarcode = code
              onClearUnmatchedBarcode()
            },
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            products = filteredProducts,
            onTapProduct = onAddProductToCart,
            columns = 2,
            bottomPadding = 88.dp,
          )
        }

        // Sticky Mobile Cart Dock
        val itemCount = posState.lines.sumOf { it.qty }
        Surface(
          color = MaterialTheme.colorScheme.primary,
          shape = RoundedCornerShape(16.dp),
          shadowElevation = 6.dp,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(12.dp)
              .clickable { showMobileCartSheet = true }
              .testTag("mobile_cart_dock"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.ShoppingCart,
                contentDescription = strings.cartTitle,
                tint = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(Modifier.width(10.dp))
              Column {
                Text(
                  "${formatQty(itemCount)} ${if (strings.isUrdu) "اشیاء" else "items"}",
                  color = MaterialTheme.colorScheme.onPrimary,
                  style = MaterialTheme.typography.titleMedium,
                )
                if (posState.lines.isNotEmpty()) {
                  Text(
                    if (strings.isUrdu) "بل مکمل کرنے کے لیے ٹیپ کریں" else "Tap to review & pay",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }
            Text(
              formatPkr(totals.grandTotal, shop.currency),
              color = MaterialTheme.colorScheme.onPrimary,
              style = MaterialTheme.typography.headlineMedium,
            )
          }
        }
      }
    }
  }

  if (showMobileCartSheet) {
    ModalBottomSheet(onDismissRequest = { showMobileCartSheet = false }) {
      Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
        PosCartPane(
          shop = shop,
          posState = posState,
          totals = totals,
          customers = uiState.customers,
          strings = strings,
          onUpdateQty = onUpdateCartQty,
          onEditQtyKeypad = { qtyEditLine = it },
          onSetDiscount = onSetDiscount,
          onSetPaymentMethod = onSetPaymentMethod,
          onSelectCustomer = onSelectCustomer,
          onSetReceived = onSetReceived,
          onCompleteSale = {
            onCompleteSale(false)
            showMobileCartSheet = false
          },
          onHoldBill = {
            onHoldBill()
            showMobileCartSheet = false
          },
          onClearCart = onClearCart,
        )
      }
    }
  }

  if (showCameraScanner) {
    BarcodeScannerDialog(
      strings = strings,
      onBarcodeDetected = { code ->
        showCameraScanner = false
        val clean = code.trim()
        if (uiState.barcodeMap.containsKey(clean)) {
          onScanBarcode(clean)
        } else if (clean.isNotBlank()) {
          quickAddBarcode = clean
        }
      },
      onDismiss = { showCameraScanner = false },
    )
  }

  qtyEditLine?.let { line ->
    NumericQtyDialog(
      line = line,
      strings = strings,
      onConfirm = { newQty ->
        onUpdateCartQty(line.product.id, newQty)
        qtyEditLine = null
      },
      onDismiss = { qtyEditLine = null },
    )
  }

  quickAddBarcode?.let { code ->
    QuickAddProductDialog(
      barcode = code,
      strings = strings,
      canEditCost = uiState.currentRole != UserRole.CASHIER,
      onSave = { product ->
        onQuickAddProduct(product)
        quickAddBarcode = null
      },
      onDismiss = { quickAddBarcode = null },
    )
  }

  if (showHistoryDialog) {
    HeldAndHistoryDialog(
      shop = shop,
      sales = uiState.sales,
      strings = strings,
      onResumeHeld = {
        onResumeHeldSale(it)
        showHistoryDialog = false
      },
      onReturnSale = onReturnSale,
      onViewReceipt = {
        showHistoryDialog = false
        onShowReceipt(it)
      },
      onDismiss = { showHistoryDialog = false },
    )
  }

  if (posState.negativeStockItemNames.isNotEmpty()) {
    AlertDialog(
      onDismissRequest = onDismissNegativeWarning,
      title = { Text(if (strings.isUrdu) "اسٹاک کم ہے" else "Low / Insufficient Stock") },
      text = {
        Text(
          "Insufficient stock for:\n" +
            posState.negativeStockItemNames.joinToString("\n") +
            "\n\nProceed anyway?"
        )
      },
      dismissButton = {
        TextButton(onClick = onDismissNegativeWarning) { Text(strings.cancel) }
      },
      confirmButton = {
        if (uiState.currentRole == UserRole.OWNER || shop.allowNegativeStockOverride) {
          Button(onClick = { onCompleteSale(true) }) {
            Text(if (strings.isUrdu) "پھر بھی فروخت کریں" else "Override & Sell")
          }
        }
      },
    )
  }

  posState.lastCompletedSale?.let { completed ->
    val receiptAd = remember(uiState.activeAds) { pickActiveAdForPlacement(uiState.activeAds, "receipt") }
    ReceiptModalDialog(
      shop = shop,
      sale = completed,
      receiptAd = receiptAd,
      strings = strings,
      onDismiss = onDismissReceipt,
    )
  }
}

@Composable
private fun PosCatalogPane(
  shop: Shop,
  strings: AppStrings,
  scanInput: String,
  onScanInputChange: (String) -> Unit,
  onSubmitScan: () -> Unit,
  scanFocusRequester: FocusRequester,
  onOpenCamera: () -> Unit,
  onOpenHistory: () -> Unit,
  heldCount: Int,
  lastScannedProduct: Product?,
  lastScannedCartQty: Double,
  onDismissLastScanned: () -> Unit,
  unmatchedBarcode: String?,
  onQuickAddBarcode: (String) -> Unit,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  categories: List<String>,
  selectedCategory: String,
  onSelectCategory: (String) -> Unit,
  products: List<Product>,
  onTapProduct: (Product) -> Unit,
  columns: Int,
  bottomPadding: androidx.compose.ui.unit.Dp = 8.dp,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = scanInput,
      onValueChange = { value ->
        if (value.endsWith("\n")) {
          onScanInputChange(value.trim())
          onSubmitScan()
        } else {
          onScanInputChange(value)
        }
      },
      placeholder = { Text(strings.scanBarcodeHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onSubmitScan() }),
      leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
      trailingIcon = {
        if (scanInput.isNotEmpty()) {
          IconButton(onClick = onSubmitScan) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
      },
      modifier =
        Modifier.weight(1f)
          .focusRequester(scanFocusRequester)
          .testTag("pos_scan_input"),
    )
    FilledTonalButton(
      onClick = onOpenCamera,
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
      modifier = Modifier.height(54.dp).testTag("pos_camera_scan_button"),
    ) {
      Icon(Icons.Default.QrCodeScanner, contentDescription = strings.scanCamera)
    }
    OutlinedButton(
      onClick = onOpenHistory,
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
      modifier = Modifier.height(54.dp).testTag("pos_history_button"),
    ) {
      Icon(Icons.Default.History, contentDescription = strings.returnBill)
      if (heldCount > 0) {
        Spacer(Modifier.width(4.dp))
        Badge { Text("$heldCount") }
      }
    }
  }

  if (lastScannedProduct != null) {
    Surface(
      color = MaterialTheme.colorScheme.primaryContainer,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("pos_last_scanned_banner"),
    ) {
      Row(
        modifier = Modifier.padding(10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ProductThumbnail(
          imageUrl = lastScannedProduct.imageUrl,
          name = lastScannedProduct.name,
          modifier = Modifier.size(48.dp),
          cornerRadius = 10.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
              Icons.Default.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              if (strings.isUrdu) "بل میں شامل ہو گیا (x${formatQty(lastScannedCartQty)})"
              else "Auto-added to bill (x${formatQty(lastScannedCartQty)})",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            if (strings.isUrdu && lastScannedProduct.nameUrdu.isNotBlank()) lastScannedProduct.nameUrdu else lastScannedProduct.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            formatPkr(lastScannedProduct.salePrice, shop.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onDismissLastScanned) {
          Icon(Icons.Default.Close, contentDescription = strings.cancel)
        }
      }
    }
  }

  if (unmatchedBarcode != null) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "${strings.barcodeNotFound}: $unmatchedBarcode",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        Button(
          onClick = { onQuickAddBarcode(unmatchedBarcode) },
          modifier = Modifier.testTag("pos_quick_add_button"),
        ) {
          Text(strings.quickAddProduct)
        }
      }
    }
  }

  Spacer(Modifier.height(6.dp))
  OutlinedTextField(
    value = searchQuery,
    onValueChange = onSearchChange,
    placeholder = { Text(strings.searchProductHint) },
    singleLine = true,
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
    trailingIcon = {
      if (searchQuery.isNotEmpty()) {
        IconButton(onClick = { onSearchChange("") }) {
          Icon(Icons.Default.Clear, contentDescription = "Clear")
        }
      }
    },
    modifier = Modifier.fillMaxWidth().testTag("pos_search_input"),
  )

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    categories.forEach { cat ->
      FilterChip(
        selected = selectedCategory == cat,
        onClick = { onSelectCategory(cat) },
        label = { Text(cat.ifBlank { strings.allCategories }) },
      )
    }
  }

  if (products.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          Icons.Default.AddShoppingCart,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          if (strings.isUrdu) "کوئی آئٹم نہیں ملا" else "No matching products yet",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  } else {
    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      contentPadding = PaddingValues(bottom = bottomPadding),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      items(products, key = { it.id }) { product ->
        val isLow = product.stockQty <= product.lowStockLimit
        Card(
          onClick = { onTapProduct(product) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border =
            if (isLow) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.testTag("pos_product_tile_${product.id}"),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            ProductThumbnail(
              imageUrl = product.imageUrl,
              name = product.name,
              modifier = Modifier.fillMaxWidth().height(84.dp),
              cornerRadius = 10.dp,
            )
            Text(
              if (strings.isUrdu && product.nameUrdu.isNotBlank()) product.nameUrdu else product.name,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (strings.isUrdu && product.nameUrdu.isNotBlank()) {
              Text(
                product.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                formatPkr(product.salePrice, shop.currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                color =
                  if (isLow) MaterialTheme.colorScheme.errorContainer
                  else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  "${formatQty(product.stockQty)} ${product.unit}",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isLow) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PosCartPane(
  shop: Shop,
  posState: PosUiState,
  totals: PosTotals,
  customers: List<Customer>,
  strings: AppStrings,
  onUpdateQty: (String, Double) -> Unit,
  onEditQtyKeypad: (CartLine) -> Unit,
  onSetDiscount: (String, Boolean) -> Unit,
  onSetPaymentMethod: (String) -> Unit,
  onSelectCustomer: (String) -> Unit,
  onSetReceived: (String) -> Unit,
  onCompleteSale: () -> Unit,
  onHoldBill: () -> Unit,
  onClearCart: () -> Unit,
) {
  var showCustomerMenu by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(strings.cartTitle, style = MaterialTheme.typography.titleLarge)
      Row {
        if (posState.lines.isNotEmpty()) {
          TextButton(onClick = onHoldBill, modifier = Modifier.testTag("hold_bill_button")) {
            Icon(Icons.Default.PauseCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.holdBill)
          }
          IconButton(onClick = onClearCart) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear cart")
          }
        }
      }
    }

    if (posState.lines.isEmpty()) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(strings.emptyCart, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(posState.lines, key = { it.product.id }) { line ->
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(10.dp).fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ProductThumbnail(
                imageUrl = line.product.imageUrl,
                name = line.product.name,
                modifier = Modifier.size(44.dp),
                cornerRadius = 8.dp,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(line.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${formatPkr(line.product.salePrice, shop.currency)} / ${line.product.unit}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { onUpdateQty(line.product.id, line.qty - 1.0) },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.clickable { onEditQtyKeypad(line) },
                ) {
                  Text(
                    formatQty(line.qty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
                IconButton(
                  onClick = { onUpdateQty(line.product.id, line.qty + 1.0) },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(Icons.Default.Add, contentDescription = "Increase")
                }
              }
              Spacer(Modifier.width(8.dp))
              Text(
                formatPkr(line.lineTotal, shop.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

    // Discount & Received inputs
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = posState.discountInput,
        onValueChange = { onSetDiscount(it, posState.discountIsPercent) },
        label = { Text(if (posState.discountIsPercent) "${strings.discount} (%)" else "${strings.discount} (${shop.currency})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        trailingIcon = {
          TextButton(onClick = { onSetDiscount(posState.discountInput, !posState.discountIsPercent) }) {
            Text(if (posState.discountIsPercent) "%" else shop.currency)
          }
        },
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = posState.receivedInput,
        onValueChange = onSetReceived,
        label = { Text(strings.received) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
    }

    // Payment method chips
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      PaymentMethod.entries.forEach { method ->
        FilterChip(
          selected = posState.paymentMethod == method.key,
          onClick = { onSetPaymentMethod(method.key) },
          label = { Text(if (strings.isUrdu) method.labelUr else method.labelEn) },
          modifier = Modifier.testTag("pay_method_${method.key}"),
        )
      }
    }

    // Customer selector (required for Udhaar, optional for others)
    Box(modifier = Modifier.fillMaxWidth()) {
      val selectedCust = customers.firstOrNull { it.id == posState.selectedCustomerId }
      OutlinedButton(
        onClick = { showCustomerMenu = true },
        modifier = Modifier.fillMaxWidth().testTag("pos_select_customer_button"),
      ) {
        Text(
          selectedCust?.let { "${it.name} (${formatPkr(it.balance, shop.currency)})" }
            ?: strings.selectCustomer
        )
      }
      DropdownMenu(expanded = showCustomerMenu, onDismissRequest = { showCustomerMenu = false }) {
        DropdownMenuItem(
          text = { Text(if (strings.isUrdu) "بغیر گاہک (واک ان)" else "Walk-in Customer") },
          onClick = {
            onSelectCustomer("")
            showCustomerMenu = false
          },
        )
        customers.forEach { c ->
          DropdownMenuItem(
            text = { Text("${c.name} • ${c.phone} (${formatPkr(c.balance, shop.currency)})") },
            onClick = {
              onSelectCustomer(c.id)
              showCustomerMenu = false
            },
          )
        }
      }
    }

    Spacer(Modifier.height(6.dp))

    // Totals summary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("${strings.subtotal}: ${formatPkr(totals.subtotal, shop.currency)}", style = MaterialTheme.typography.bodyMedium)
      if (totals.tax > 0) {
        Text("${strings.tax}: ${formatPkr(totals.tax, shop.currency)}", style = MaterialTheme.typography.bodyMedium)
      }
      if (totals.changeReturn > 0) {
        Text(
          "${strings.changeReturn}: ${formatPkr(totals.changeReturn, shop.currency)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Button(
      onClick = onCompleteSale,
      enabled = posState.lines.isNotEmpty() && !posState.isSubmitting,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("complete_sale_button"),
    ) {
      Text(
        "${strings.completeSale} • ${formatPkr(totals.grandTotal, shop.currency)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun NumericQtyDialog(
  line: CartLine,
  strings: AppStrings,
  onConfirm: (Double) -> Unit,
  onDismiss: () -> Unit,
) {
  var value by remember { mutableStateOf(formatQty(line.qty)) }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(line.product.name, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text(if (strings.isUrdu) "مقدار (${line.product.unit})" else "Quantity (${line.product.unit})") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "DEL"))
        keys.forEach { row ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { key ->
              FilledTonalButton(
                onClick = {
                  value =
                    when (key) {
                      "DEL" -> if (value.isNotEmpty()) value.dropLast(1) else ""
                      "." -> if (value.contains(".")) value else "$value."
                      else -> if (value == "0") key else value + key
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                if (key == "DEL") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                else Text(key, style = MaterialTheme.typography.titleMedium)
              }
            }
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text(strings.cancel) }
          Spacer(Modifier.width(8.dp))
          Button(onClick = { onConfirm(value.toDoubleOrNull() ?: 1.0) }) { Text(strings.save) }
        }
      }
    }
  }
}

@Composable
private fun QuickAddProductDialog(
  barcode: String,
  strings: AppStrings,
  canEditCost: Boolean,
  onSave: (Product) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var name by remember { mutableStateOf("") }
  var salePrice by remember { mutableStateOf("") }
  var costPrice by remember { mutableStateOf("") }
  var stock by remember { mutableStateOf("10") }
  var imageUrl by remember { mutableStateOf("") }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        imageUrl = encodeProductImageUriToDataUrl(context, uri) ?: uri.toString()
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.quickAddProduct) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("${strings.barcode}: $barcode", style = MaterialTheme.typography.labelSmall)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ProductThumbnail(
            imageUrl = imageUrl,
            name = name.ifBlank { "Item" },
            modifier = Modifier.size(64.dp),
            cornerRadius = 10.dp,
          )
          OutlinedButton(
            onClick = {
              photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.weight(1f),
          ) {
            Text(if (strings.isUrdu) "تصویر شامل کریں" else "Add Photo")
          }
        }
        if (!imageUrl.startsWith("data:image", ignoreCase = true)) {
          OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text(if (strings.isUrdu) "تصویر لنک (اختیاری)" else "Image URL (Optional)") },
            singleLine = true,
          )
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.productName) }, singleLine = true)
        OutlinedTextField(
          value = salePrice,
          onValueChange = { salePrice = it },
          label = { Text(strings.salePrice) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
        )
        if (canEditCost) {
          OutlinedTextField(
            value = costPrice,
            onValueChange = { costPrice = it },
            label = { Text(strings.purchasePrice) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
          )
        }
        OutlinedTextField(
          value = stock,
          onValueChange = { stock = it },
          label = { Text(strings.stockQty) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            Product(
              name = name,
              barcode = barcode,
              salePrice = salePrice.toDoubleOrNull() ?: 0.0,
              purchasePrice = if (canEditCost) (costPrice.toDoubleOrNull() ?: 0.0) else 0.0,
              stockQty = stock.toDoubleOrNull() ?: 0.0,
              lowStockLimit = 5.0,
              unit = "pcs",
              imageUrl = imageUrl,
            )
          )
        },
        enabled = name.isNotBlank() && (salePrice.toDoubleOrNull() ?: 0.0) > 0.0,
      ) { Text(if (strings.isUrdu) "محفوظ اور بل میں شامل کریں" else "Save & Add to Bill") }
    },
  )
}

@Composable
private fun HeldAndHistoryDialog(
  shop: Shop,
  sales: List<Sale>,
  strings: AppStrings,
  onResumeHeld: (Sale) -> Unit,
  onReturnSale: (Sale) -> Unit,
  onViewReceipt: (Sale) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.padding(16.dp).height(460.dp)) {
        Text(strings.returnBill, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(sales.take(30), key = { it.id }) { sale ->
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(sale.invoiceNo, style = MaterialTheme.typography.titleMedium)
                  Text(
                    sale.status.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      when (sale.status) {
                        "held" -> MaterialTheme.colorScheme.secondary
                        "returned" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                      },
                  )
                }
                Text(
                  "${formatTimestampDisplay(sale.createdAt)} • ${formatPkr(sale.grandTotal, shop.currency)}",
                  style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (sale.status == "held") {
                    Button(onClick = { onResumeHeld(sale) }) {
                      Text(if (strings.isUrdu) "دوبارہ کھولیں" else "Resume")
                    }
                  } else if (sale.status == "completed") {
                    OutlinedButton(onClick = { onViewReceipt(sale) }) { Text(strings.receiptTitle) }
                    TextButton(onClick = { onReturnSale(sale) }) {
                      Text(if (strings.isUrdu) "واپسی" else "Return", color = MaterialTheme.colorScheme.error)
                    }
                  }
                }
              }
            }
          }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(strings.cancel) }
      }
    }
  }
}

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AdjustmentReason
import com.example.data.model.Product
import com.example.data.model.ProductUnit
import com.example.data.model.Shop
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.BarcodeStickerDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.viewmodel.DukanMainUiState
import com.example.util.ExportAndPrintUtils
import com.example.util.daysUntilExpiry
import com.example.util.encodeProductImageUriToDataUrl
import com.example.util.formatPkr
import com.example.util.formatQty

@Composable
fun InventoryScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onSaveProduct: (Product) -> Unit,
  onUpdateProductPhoto: (productId: String, imageUrl: String) -> Unit,
  onDeleteProduct: (String) -> Unit,
  onAdjustStock: (productId: String, deltaQty: Double, reason: String, note: String) -> Unit,
  onImportCsv: (List<Product>) -> Unit,
) {
  val context = LocalContext.current
  val shop = uiState.activeShop ?: return
  val canEdit = uiState.currentRole != UserRole.CASHIER

  var search by remember { mutableStateOf("") }
  var filterCategory by remember { mutableStateOf("") }
  var showLowOnly by remember { mutableStateOf(false) }
  var showExpiringOnly by remember { mutableStateOf(false) }

  var editingProduct by remember { mutableStateOf<Product?>(null) }
  var showScanToAdd by remember { mutableStateOf(false) }
  var adjustingProduct by remember { mutableStateOf<Product?>(null) }
  var barcodeStickerProduct by remember { mutableStateOf<Product?>(null) }
  var confirmDeleteProduct by remember { mutableStateOf<Product?>(null) }
  var photoTargetProductId by remember { mutableStateOf<String?>(null) }

  val productPhotoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
      val targetId = photoTargetProductId
      photoTargetProductId = null
      if (uri != null && !targetId.isNullOrBlank()) {
        val encoded = encodeProductImageUriToDataUrl(context, uri)
        if (encoded != null) {
          onUpdateProductPhoto(targetId, encoded)
        }
      }
    }

  val csvImportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        onImportCsv(ExportAndPrintUtils.parseProductsCsv(text))
      }
    }

  val categories = remember(uiState.products) {
    listOf("") + uiState.products.map { it.category }.distinct().sorted()
  }

  val filtered = remember(uiState.products, search, filterCategory, showLowOnly, showExpiringOnly) {
    val q = search.trim().lowercase()
    uiState.products.filter { p ->
      val qOk = q.isBlank() || p.name.lowercase().contains(q) || p.nameUrdu.lowercase().contains(q) || p.barcode.contains(q)
      val cOk = filterCategory.isBlank() || p.category == filterCategory
      val lowOk = !showLowOnly || p.stockQty <= p.lowStockLimit
      val expDays = daysUntilExpiry(p.expiryDate)
      val expOk = !showExpiringOnly || (expDays != null && expDays <= 30)
      qOk && cOk && lowOk && expOk
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = { Text(strings.searchProductHint) },
          singleLine = true,
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          modifier = Modifier.weight(1f).testTag("inventory_search_input"),
        )
        if (canEdit) {
          FilledTonalButton(
            onClick = { showScanToAdd = true },
            modifier = Modifier.height(54.dp).testTag("scan_to_add_button"),
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (strings.isUrdu) "اسکین" else "Scan")
          }
        }
      }

      if (canEdit) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { csvImportLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.importCsv)
          }
          OutlinedButton(
            onClick = {
              val csv = ExportAndPrintUtils.productsToCsv(uiState.products)
              ExportAndPrintUtils.shareTextFile(context, csv, "dukanpro_products.csv", "text/csv")
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.exportCsv)
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilterChip(
          selected = showLowOnly,
          onClick = { showLowOnly = !showLowOnly },
          label = { Text(strings.lowStock) },
        )
        FilterChip(
          selected = showExpiringOnly,
          onClick = { showExpiringOnly = !showExpiringOnly },
          label = { Text(strings.expiringSoon) },
        )
        categories.forEach { cat ->
          FilterChip(
            selected = filterCategory == cat,
            onClick = { filterCategory = cat },
            label = { Text(cat.ifBlank { strings.allCategories }) },
          )
        }
      }

      if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(if (strings.isUrdu) "کوئی پروڈکٹ موجود نہیں" else "No products found", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(bottom = 84.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filtered, key = { it.id }) { prod ->
            val isLow = prod.stockQty <= prod.lowStockLimit
            val expDays = daysUntilExpiry(prod.expiryDate)
            val isExpiring = expDays != null && expDays <= 30

            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("inventory_item_${prod.id}"),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  ProductThumbnail(
                    imageUrl = prod.imageUrl,
                    name = prod.name,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 12.dp,
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(prod.name, style = MaterialTheme.typography.titleMedium)
                    if (prod.nameUrdu.isNotBlank()) {
                      Text(prod.nameUrdu, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                      "${prod.category} • ${prod.barcode.ifBlank { "No barcode" }}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      formatPkr(prod.salePrice, shop.currency),
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                    if (canEdit) {
                      Text(
                        "Cost: ${formatPkr(prod.purchasePrice, shop.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                      color = if (isLow) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                      shape = RoundedCornerShape(8.dp),
                    ) {
                      Text(
                        "${formatQty(prod.stockQty)} ${prod.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                      )
                    }
                    if (isExpiring) {
                      Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                          "Exp: ${prod.expiryDate}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                      }
                    }
                  }
                  Row {
                    IconButton(
                      onClick = {
                        photoTargetProductId = prod.id
                        productPhotoPicker.launch(
                          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                      },
                      modifier = Modifier.testTag("upload_photo_btn_${prod.id}"),
                    ) {
                      Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = if (strings.isUrdu) "تصویر اپلوڈ کریں" else "Upload Photo",
                      )
                    }
                    IconButton(onClick = { barcodeStickerProduct = prod }) {
                      Icon(Icons.Default.QrCode, contentDescription = strings.printBarcode)
                    }
                    if (canEdit) {
                      IconButton(onClick = { adjustingProduct = prod }) {
                        Icon(Icons.Default.Tune, contentDescription = strings.adjustStock)
                      }
                      IconButton(onClick = { editingProduct = prod }) {
                        Icon(Icons.Default.Edit, contentDescription = strings.editProduct)
                      }
                      IconButton(onClick = { confirmDeleteProduct = prod }) {
                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (canEdit) {
      FloatingActionButton(
        onClick = { editingProduct = Product() },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_product_fab"),
      ) {
        Icon(Icons.Default.Add, contentDescription = strings.addProduct)
      }
    }
  }

  if (showScanToAdd) {
    BarcodeScannerDialog(
      strings = strings,
      onBarcodeDetected = { code ->
        showScanToAdd = false
        val existing = uiState.barcodeMap[code]
        editingProduct = existing ?: Product(barcode = code)
      },
      onDismiss = { showScanToAdd = false },
    )
  }

  editingProduct?.let { prod ->
    ProductEditorDialog(
      initial = prod,
      strings = strings,
      onSave = {
        onSaveProduct(it)
        editingProduct = null
      },
      onDismiss = { editingProduct = null },
    )
  }

  adjustingProduct?.let { prod ->
    StockAdjustmentDialog(
      product = prod,
      strings = strings,
      onAdjust = { delta, reason, note ->
        onAdjustStock(prod.id, delta, reason, note)
        adjustingProduct = null
      },
      onDismiss = { adjustingProduct = null },
    )
  }

  barcodeStickerProduct?.let { prod ->
    BarcodeStickerDialog(
      product = prod,
      shop = shop,
      strings = strings,
      onDismiss = { barcodeStickerProduct = null },
    )
  }

  confirmDeleteProduct?.let { prod ->
    AlertDialog(
      onDismissRequest = { confirmDeleteProduct = null },
      title = { Text(strings.delete) },
      text = { Text("Delete ${prod.name}?") },
      dismissButton = { TextButton(onClick = { confirmDeleteProduct = null }) { Text(strings.cancel) } },
      confirmButton = {
        Button(
          onClick = {
            onDeleteProduct(prod.id)
            confirmDeleteProduct = null
          }
        ) { Text(strings.delete) }
      },
    )
  }
}

@Composable
private fun ProductEditorDialog(
  initial: Product,
  strings: AppStrings,
  onSave: (Product) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initial.name) }
  var nameUrdu by remember { mutableStateOf(initial.nameUrdu) }
  var barcode by remember { mutableStateOf(initial.barcode) }
  var category by remember { mutableStateOf(initial.category.ifBlank { "General" }) }
  var cost by remember { mutableStateOf(if (initial.purchasePrice > 0) initial.purchasePrice.toString() else "") }
  var sale by remember { mutableStateOf(if (initial.salePrice > 0) initial.salePrice.toString() else "") }
  var stock by remember { mutableStateOf(if (initial.id.isNotBlank()) formatQty(initial.stockQty) else "10") }
  var lowLimit by remember { mutableStateOf(formatQty(initial.lowStockLimit)) }
  var unit by remember { mutableStateOf(initial.unit.ifBlank { "pcs" }) }
  var expiryDate by remember { mutableStateOf(initial.expiryDate) }
  var imageUrl by remember { mutableStateOf(initial.imageUrl) }
  val context = LocalContext.current

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        imageUrl = encodeProductImageUriToDataUrl(context, uri) ?: uri.toString()
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.id.isBlank()) strings.addProduct else strings.editProduct) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ProductThumbnail(
            imageUrl = imageUrl,
            name = name.ifBlank { "Product" },
            modifier = Modifier.size(72.dp),
            cornerRadius = 12.dp,
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
              onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(if (strings.isUrdu) "تصویر منتخب کریں" else "Select Photo")
            }
            if (imageUrl.isNotBlank()) {
              TextButton(onClick = { imageUrl = "" }) {
                Text(if (strings.isUrdu) "تصویر ہٹائیں" else "Remove Photo")
              }
            }
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
        OutlinedTextField(value = nameUrdu, onValueChange = { nameUrdu = it }, label = { Text(strings.productNameUrdu) }, singleLine = true)
        OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text(strings.barcode) }, singleLine = true)
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(strings.category) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = cost,
            onValueChange = { cost = it },
            label = { Text(strings.purchasePrice) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = sale,
            onValueChange = { sale = it },
            label = { Text(strings.salePrice) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = stock,
            onValueChange = { stock = it },
            label = { Text(strings.stockQty) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = lowLimit,
            onValueChange = { lowLimit = it },
            label = { Text(strings.lowStockLimit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ProductUnit.entries.forEach { u ->
            FilterChip(
              selected = unit == u.key,
              onClick = { unit = u.key },
              label = { Text(if (strings.isUrdu) u.labelUr else u.labelEn) },
            )
          }
        }
        OutlinedTextField(
          value = expiryDate,
          onValueChange = { expiryDate = it },
          label = { Text(strings.expiryDate) },
          placeholder = { Text("DD/MM/YYYY") },
          singleLine = true,
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            initial.copy(
              name = name,
              nameUrdu = nameUrdu,
              barcode = barcode,
              category = category,
              purchasePrice = cost.toDoubleOrNull() ?: 0.0,
              salePrice = sale.toDoubleOrNull() ?: 0.0,
              stockQty = stock.toDoubleOrNull() ?: 0.0,
              lowStockLimit = lowLimit.toDoubleOrNull() ?: 5.0,
              unit = unit,
              expiryDate = expiryDate,
              imageUrl = imageUrl,
            )
          )
        },
        enabled = name.isNotBlank(),
      ) { Text(strings.save) }
    },
  )
}

@Composable
private fun StockAdjustmentDialog(
  product: Product,
  strings: AppStrings,
  onAdjust: (Double, String, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var deltaText by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf(AdjustmentReason.CORRECTION.key) }
  var note by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${strings.adjustStock}: ${product.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Current: ${formatQty(product.stockQty)} ${product.unit}")
        OutlinedTextField(
          value = deltaText,
          onValueChange = { deltaText = it },
          label = { Text(if (strings.isUrdu) "تبدیلی (+ یا -)" else "Qty Change (e.g. -2 or +5)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          AdjustmentReason.entries.forEach { r ->
            FilterChip(
              selected = reason == r.key,
              onClick = { reason = r.key },
              label = { Text(if (strings.isUrdu) r.labelUr else r.labelEn) },
            )
          }
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { onAdjust(deltaText.toDoubleOrNull() ?: 0.0, reason, note) },
        enabled = (deltaText.toDoubleOrNull() ?: 0.0) != 0.0,
      ) { Text(strings.save) }
    },
  )
}

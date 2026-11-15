package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AdBanner
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.Shop
import com.example.i18n.AppStrings
import com.example.util.ExportAndPrintUtils
import com.example.util.decodeDataUrlBitmap
import com.example.util.formatPkr
import com.example.util.formatQty
import com.example.util.formatTimestampDisplay
import com.example.util.openExternalUrl
import com.example.util.openWhatsAppMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductThumbnail(
  imageUrl: String,
  name: String,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 10.dp,
) {
  val trimmed = imageUrl.trim()
  val decodedBitmap = remember(trimmed) {
    if (trimmed.startsWith("data:image", ignoreCase = true)) decodeDataUrlBitmap(trimmed) else null
  }
  val shape = RoundedCornerShape(cornerRadius)
  Box(
    modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    when {
      decodedBitmap != null -> {
        Image(
          bitmap = decodedBitmap.asImageBitmap(),
          contentDescription = name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
        )
      }
      trimmed.isNotBlank() && !trimmed.startsWith("data:image", ignoreCase = true) -> {
        AsyncImage(
          model = trimmed,
          contentDescription = name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
        )
      }
      else -> {
        val initials =
          name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }
        if (initials.isNotEmpty()) {
          Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Icon(
            Icons.Default.Inventory2,
            contentDescription = name,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun SyncStatusPill(
  isOnline: Boolean,
  hasPendingWrites: Boolean,
  strings: AppStrings,
) {
  val (label, icon, bg, fg) =
    when {
      !isOnline ->
        Quad(
          strings.offlineBadge,
          Icons.Default.CloudOff,
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.error,
        )
      hasPendingWrites ->
        Quad(
          strings.syncingBadge,
          Icons.Default.Sync,
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.secondary,
        )
      else ->
        Quad(
          strings.syncedBadge,
          Icons.Default.CloudDone,
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.primary,
        )
    }

  Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(4.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun pickActiveAdForPlacement(ads: List<AdBanner>, placement: String): AdBanner? {
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
  return ads.firstOrNull { ad ->
    ad.active &&
      ad.placement == placement &&
      (ad.startDate.isBlank() || today >= ad.startDate) &&
      (ad.endDate.isBlank() || today <= ad.endDate)
  }
}

@Composable
fun AdBannerSlot(
  shop: Shop?,
  isOnline: Boolean,
  ads: List<AdBanner>,
  placement: String,
  strings: AppStrings,
  onImpression: (String) -> Unit,
  onClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (shop == null || shop.plan == "pro" || !isOnline) return
  val activeAd = remember(ads, placement) { pickActiveAdForPlacement(ads, placement) }

  if (activeAd != null) {
    val context = LocalContext.current
    LaunchedEffect(activeAd.id) { onImpression(activeAd.id) }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(14.dp),
      modifier =
        modifier
          .fillMaxWidth()
          .clickable {
            onClick(activeAd.id)
            openExternalUrl(context, activeAd.linkUrl)
          }
          .testTag("sponsored_ad_banner"),
    ) {
      Row(
        modifier = Modifier.padding(12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (activeAd.imageUrl.isNotBlank()) {
          AsyncImage(
            model = activeAd.imageUrl,
            contentDescription = activeAd.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "${strings.sponsored} • ${activeAd.sponsor}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          Text(activeAd.title, style = MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
    }
  } else {
    // Fallback AdSense-style partner slot when no direct sponsor ad is active on Free plan
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
      shape = RoundedCornerShape(14.dp),
      modifier = modifier.fillMaxWidth().testTag("fallback_ad_slot"),
    ) {
      Row(
        modifier = Modifier.padding(12.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "${strings.sponsored} • DukanPro Partner Network",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            if (strings.isUrdu) "بغیر اشتہار استعمال کے لیے پرو پلان اپ گریڈ کریں"
            else "Upgrade to Pro to remove ads & unlock priority support",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

@Composable
fun ReceiptModalDialog(
  shop: Shop,
  sale: Sale,
  receiptAd: AdBanner?,
  strings: AppStrings,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.receiptTitle, style = MaterialTheme.typography.titleLarge)
          IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = strings.cancel) }
        }

        // 58mm/80mm thermal paper preview
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(androidx.compose.ui.graphics.Color(0xFFFFFDF8), RoundedCornerShape(10.dp))
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
              .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            shop.name,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          if (shop.address.isNotBlank()) {
            Text(
              shop.address,
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodyMedium,
              color = androidx.compose.ui.graphics.Color.DarkGray,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (shop.phone.isNotBlank()) {
            Text(
              "Tel: ${shop.phone}",
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodyMedium,
              color = androidx.compose.ui.graphics.Color.DarkGray,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text("Inv: ${sale.invoiceNo}", fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
          Text("Date: ${formatTimestampDisplay(sale.createdAt)}", fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.DarkGray)
          if (sale.customerName.isNotBlank()) {
            Text("Customer: ${sale.customerName}", fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          sale.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                "${item.name} x${formatQty(item.qty)}",
                fontFamily = FontFamily.Monospace,
                color = androidx.compose.ui.graphics.Color.Black,
                modifier = Modifier.weight(1f),
              )
              Text(
                formatPkr(item.total, shop.currency),
                fontFamily = FontFamily.Monospace,
                color = androidx.compose.ui.graphics.Color.Black,
              )
            }
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.subtotal, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
            Text(formatPkr(sale.subtotal, shop.currency), fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
          }
          if (sale.discount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(strings.discount, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
              Text("-${formatPkr(sale.discount, shop.currency)}", fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
            }
          }
          if (sale.tax > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(strings.tax, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
              Text(formatPkr(sale.tax, shop.currency), fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black)
            }
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TOTAL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
            Text(formatPkr(sale.grandTotal, shop.currency), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
          }
          Text(
            "Paid (${sale.paymentMethod.uppercase()}): ${formatPkr(sale.paidAmount, shop.currency)}",
            fontFamily = FontFamily.Monospace,
            color = androidx.compose.ui.graphics.Color.DarkGray,
          )
          if (sale.dueAmount > 0) {
            Text(
              "Udhaar Due: ${formatPkr(sale.dueAmount, shop.currency)}",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = androidx.compose.ui.graphics.Color.Red,
            )
          }
          if (shop.receiptFooter.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
              shop.receiptFooter,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center,
              color = androidx.compose.ui.graphics.Color.DarkGray,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          if (shop.plan == "free" && shop.showReceiptSponsor && receiptAd != null) {
            Text(
              "${strings.sponsored}: ${receiptAd.sponsor} - ${receiptAd.title}",
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              color = androidx.compose.ui.graphics.Color.Gray,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val file = ExportAndPrintUtils.createThermalReceiptPdf(context, shop, sale, receiptAd)
              ExportAndPrintUtils.printPdfFile(context, file, "Receipt_${sale.invoiceNo}")
            },
            modifier = Modifier.weight(1f).testTag("print_receipt_pdf_button"),
          ) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.printPdf)
          }
          Button(
            onClick = {
              val text = ExportAndPrintUtils.buildWhatsAppReceiptText(shop, sale)
              openWhatsAppMessage(context, "", text)
            },
            modifier = Modifier.weight(1f).testTag("share_receipt_whatsapp_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.shareWhatsApp)
          }
        }
      }
    }
  }
}

@Composable
fun BarcodeStickerDialog(
  product: Product,
  shop: Shop,
  strings: AppStrings,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val code = product.barcode.ifBlank { product.id.take(10).uppercase() }
  val bitmap = remember(product.id, code) {
    ExportAndPrintUtils.generateBarcodeStickerBitmap(
      code = code,
      productName = product.name,
      priceLabel = formatPkr(product.salePrice, shop.currency),
      shopName = shop.name,
    )
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.printBarcode, style = MaterialTheme.typography.titleLarge)
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = "Barcode sticker for ${product.name}",
          modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(strings.cancel)
          }
          Button(
            onClick = {
              ExportAndPrintUtils.saveAndShareBitmap(context, bitmap, "barcode_${code}.png")
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (strings.isUrdu) "شیئر / پرنٹ" else "Share / Print")
          }
        }
      }
    }
  }
}

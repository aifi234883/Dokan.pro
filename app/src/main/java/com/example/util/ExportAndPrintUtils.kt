package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.model.AdBanner
import com.example.data.model.Customer
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.Shop
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ExportAndPrintUtils {

  fun buildWhatsAppReceiptText(shop: Shop, sale: Sale): String {
    val sb = StringBuilder()
    sb.appendLine("*${shop.name}*")
    if (shop.phone.isNotBlank()) sb.appendLine("Tel: ${shop.phone}")
    sb.appendLine("Invoice: ${sale.invoiceNo}")
    sb.appendLine("Date: ${formatTimestampDisplay(sale.createdAt)}")
    if (sale.customerName.isNotBlank()) sb.appendLine("Customer: ${sale.customerName}")
    sb.appendLine("--------------------")
    sale.items.forEach { item ->
      sb.appendLine("${item.name} x${formatQty(item.qty)} = ${formatPkr(item.total, shop.currency)}")
    }
    sb.appendLine("--------------------")
    sb.appendLine("Subtotal: ${formatPkr(sale.subtotal, shop.currency)}")
    if (sale.discount > 0) sb.appendLine("Discount: -${formatPkr(sale.discount, shop.currency)}")
    if (sale.tax > 0) sb.appendLine("Tax: ${formatPkr(sale.tax, shop.currency)}")
    sb.appendLine("*Total: ${formatPkr(sale.grandTotal, shop.currency)}*")
    sb.appendLine("Paid (${sale.paymentMethod.uppercase()}): ${formatPkr(sale.paidAmount, shop.currency)}")
    if (sale.dueAmount > 0) sb.appendLine("Udhaar Due: ${formatPkr(sale.dueAmount, shop.currency)}")
    if (shop.receiptFooter.isNotBlank()) {
      sb.appendLine("--------------------")
      sb.appendLine(shop.receiptFooter)
    }
    return sb.toString()
  }

  fun createThermalReceiptPdf(
    context: Context,
    shop: Shop,
    sale: Sale,
    receiptAd: AdBanner?,
  ): File {
    val pageWidth = 226 // ~80mm thermal receipt scaled to 72dpi points
    val lineCount = 14 + sale.items.size * 2 + (if (receiptAd != null) 3 else 0)
    val pageHeight = (lineCount * 16 + 80).coerceAtLeast(320)

    val pdf = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdf.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply {
      color = Color.BLACK
      textSize = 13f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
      textAlign = Paint.Align.CENTER
    }
    val bodyPaint = Paint().apply {
      color = Color.BLACK
      textSize = 8.5f
      typeface = Typeface.MONOSPACE
    }
    val boldPaint = Paint(bodyPaint).apply {
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val rightPaint = Paint(bodyPaint).apply {
      textAlign = Paint.Align.RIGHT
    }

    var y = 24f
    canvas.drawText(shop.name, pageWidth / 2f, y, titlePaint)
    y += 13f
    if (shop.address.isNotBlank()) {
      canvas.drawText(shop.address.take(36), pageWidth / 2f, y, Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER })
      y += 11f
    }
    if (shop.phone.isNotBlank()) {
      canvas.drawText("Tel: ${shop.phone}", pageWidth / 2f, y, Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER })
      y += 11f
    }
    y += 4f
    canvas.drawText("--------------------------------", 8f, y, bodyPaint)
    y += 12f
    canvas.drawText("Inv: ${sale.invoiceNo}", 8f, y, boldPaint)
    y += 11f
    canvas.drawText("Date: ${formatTimestampDisplay(sale.createdAt)}", 8f, y, bodyPaint)
    y += 11f
    if (sale.customerName.isNotBlank()) {
      canvas.drawText("Cust: ${sale.customerName.take(26)}", 8f, y, bodyPaint)
      y += 11f
    }
    canvas.drawText("--------------------------------", 8f, y, bodyPaint)
    y += 12f

    sale.items.forEach { item ->
      canvas.drawText(item.name.take(28), 8f, y, boldPaint)
      y += 11f
      canvas.drawText("${formatQty(item.qty)} x ${formatPkr(item.price, shop.currency)}", 10f, y, bodyPaint)
      canvas.drawText(formatPkr(item.total, shop.currency), pageWidth - 8f, y, rightPaint)
      y += 13f
    }

    canvas.drawText("--------------------------------", 8f, y, bodyPaint)
    y += 12f
    canvas.drawText("Subtotal:", 8f, y, bodyPaint)
    canvas.drawText(formatPkr(sale.subtotal, shop.currency), pageWidth - 8f, y, rightPaint)
    y += 11f
    if (sale.discount > 0) {
      canvas.drawText("Discount:", 8f, y, bodyPaint)
      canvas.drawText("-${formatPkr(sale.discount, shop.currency)}", pageWidth - 8f, y, rightPaint)
      y += 11f
    }
    if (sale.tax > 0) {
      canvas.drawText("Tax:", 8f, y, bodyPaint)
      canvas.drawText(formatPkr(sale.tax, shop.currency), pageWidth - 8f, y, rightPaint)
      y += 11f
    }
    canvas.drawText("TOTAL:", 8f, y, boldPaint)
    canvas.drawText(formatPkr(sale.grandTotal, shop.currency), pageWidth - 8f, y, Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT })
    y += 13f
    canvas.drawText("Paid (${sale.paymentMethod}):", 8f, y, bodyPaint)
    canvas.drawText(formatPkr(sale.paidAmount, shop.currency), pageWidth - 8f, y, rightPaint)
    y += 11f
    if (sale.dueAmount > 0) {
      canvas.drawText("Udhaar Due:", 8f, y, boldPaint)
      canvas.drawText(formatPkr(sale.dueAmount, shop.currency), pageWidth - 8f, y, rightPaint)
      y += 12f
    }
    canvas.drawText("--------------------------------", 8f, y, bodyPaint)
    y += 13f
    if (shop.receiptFooter.isNotBlank()) {
      canvas.drawText(shop.receiptFooter.take(36), pageWidth / 2f, y, Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER })
      y += 13f
    }
    if (shop.plan == "free" && shop.showReceiptSponsor && receiptAd != null) {
      canvas.drawText("Sponsored: ${receiptAd.sponsor}", pageWidth / 2f, y, Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER; textSize = 7.5f })
      y += 10f
      canvas.drawText(receiptAd.title.take(36), pageWidth / 2f, y, Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER; textSize = 7.5f })
    }

    pdf.finishPage(page)
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outFile = File(exportDir, "receipt_${sale.invoiceNo.replace("/", "-")}.pdf")
    FileOutputStream(outFile).use { pdf.writeTo(it) }
    pdf.close()
    return outFile
  }

  fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback,
          extras: android.os.Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder(file.name)
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
              .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
              .build()
          callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>?,
          destination: ParcelFileDescriptor,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback,
        ) {
          try {
            FileInputStream(file).use { input ->
              FileOutputStream(destination.fileDescriptor).use { output ->
                input.copyTo(output)
              }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.message)
          }
        }
      }
    printManager.print(jobName, adapter, null)
  }

  fun generateBarcodeStickerBitmap(
    code: String,
    productName: String,
    priceLabel: String,
    shopName: String,
  ): Bitmap {
    val width = 560
    val height = 280
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = 24f
      typeface = Typeface.DEFAULT_BOLD
      textAlign = Paint.Align.CENTER
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.DKGRAY
      textSize = 18f
      textAlign = Paint.Align.CENTER
    }
    val barPaint = Paint().apply { color = Color.BLACK }

    canvas.drawText(shopName.take(28), width / 2f, 34f, subPaint)
    canvas.drawText(productName.take(30), width / 2f, 66f, textPaint)

    // Deterministic barcode stripe pattern derived from code characters
    val cleanCode = code.ifBlank { "DUKANPRO" }
    val startX = 60f
    val endX = width - 60f
    val barTop = 84f
    val barBottom = 196f
    val bits = buildString {
      append("10110")
      cleanCode.forEach { ch ->
        val b = ch.code.toString(2).padStart(7, '0')
        append(b).append('0')
      }
      append("11011")
    }
    val barWidth = (endX - startX) / bits.length
    bits.forEachIndexed { idx, c ->
      if (c == '1') {
        val left = startX + idx * barWidth
        canvas.drawRect(left, barTop, left + barWidth, barBottom, barPaint)
      }
    }

    canvas.drawText(cleanCode, width / 2f, 224f, subPaint.apply { typeface = Typeface.MONOSPACE })
    canvas.drawText(priceLabel, width / 2f, 260f, textPaint.apply { textSize = 28f })
    return bmp
  }

  fun saveAndShareBitmap(context: Context, bitmap: Bitmap, fileName: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    shareFile(context, file, "image/png", "Share Barcode Sticker")
  }

  fun shareTextFile(context: Context, content: String, fileName: String, mimeType: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content)
    shareFile(context, file, mimeType, "Export $fileName")
  }

  fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  fun productsToCsv(products: List<Product>): String {
    val sb = StringBuilder()
    sb.appendLine("name,nameUrdu,barcode,category,purchasePrice,salePrice,stockQty,lowStockLimit,unit,expiryDate")
    products.forEach { p ->
      sb.appendLine(
        listOf(
          csvEsc(p.name),
          csvEsc(p.nameUrdu),
          csvEsc(p.barcode),
          csvEsc(p.category),
          p.purchasePrice.toString(),
          p.salePrice.toString(),
          p.stockQty.toString(),
          p.lowStockLimit.toString(),
          csvEsc(p.unit),
          csvEsc(p.expiryDate),
        ).joinToString(",")
      )
    }
    return sb.toString()
  }

  fun parseProductsCsv(csvText: String): List<Product> {
    val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size <= 1) return emptyList()
    return lines.drop(1).mapNotNull { line ->
      val cols = line.split(",").map { it.trim().trim('"') }
      if (cols.isEmpty() || cols[0].isBlank()) return@mapNotNull null
      Product(
        name = cols.getOrElse(0) { "" },
        nameUrdu = cols.getOrElse(1) { "" },
        barcode = cols.getOrElse(2) { "" },
        category = cols.getOrElse(3) { "General" }.ifBlank { "General" },
        purchasePrice = cols.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
        salePrice = cols.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
        stockQty = cols.getOrNull(6)?.toDoubleOrNull() ?: 0.0,
        lowStockLimit = cols.getOrNull(7)?.toDoubleOrNull() ?: 5.0,
        unit = cols.getOrElse(8) { "pcs" }.let { if (it in listOf("pcs", "kg", "ltr", "dozen")) it else "pcs" },
        expiryDate = cols.getOrElse(9) { "" },
      )
    }
  }

  fun salesReportToCsv(sales: List<Sale>): String {
    val sb = StringBuilder()
    sb.appendLine("invoiceNo,date,customer,paymentMethod,subtotal,discount,tax,grandTotal,paid,due,status")
    sales.forEach { s ->
      sb.appendLine(
        listOf(
          csvEsc(s.invoiceNo),
          csvEsc(formatTimestampDisplay(s.createdAt)),
          csvEsc(s.customerName),
          csvEsc(s.paymentMethod),
          s.subtotal.toString(),
          s.discount.toString(),
          s.tax.toString(),
          s.grandTotal.toString(),
          s.paidAmount.toString(),
          s.dueAmount.toString(),
          csvEsc(s.status),
        ).joinToString(",")
      )
    }
    return sb.toString()
  }

  @Serializable
  data class BackupProduct(val name: String, val nameUrdu: String, val barcode: String, val category: String, val purchasePrice: Double, val salePrice: Double, val stockQty: Double, val unit: String)

  @Serializable
  data class BackupCustomer(val name: String, val phone: String, val address: String, val balance: Double)

  @Serializable
  data class ShopBackup(val shopName: String, val exportedAt: String, val products: List<BackupProduct>, val customers: List<BackupCustomer>, val salesCount: Int)

  fun buildJsonBackup(shop: Shop, products: List<Product>, customers: List<Customer>, sales: List<Sale>): String {
    val backup =
      ShopBackup(
        shopName = shop.name,
        exportedAt = formatTimestampDisplay(null),
        products = products.map { BackupProduct(it.name, it.nameUrdu, it.barcode, it.category, it.purchasePrice, it.salePrice, it.stockQty, it.unit) },
        customers = customers.map { BackupCustomer(it.name, it.phone, it.address, it.balance) },
        salesCount = sales.size,
      )
    return Json { prettyPrint = true }.encodeToString(backup)
  }

  private fun csvEsc(v: String): String =
    if (v.contains(",") || v.contains("\"")) "\"${v.replace("\"", "\"\"")}\"" else v
}

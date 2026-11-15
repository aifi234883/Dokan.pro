package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.google.firebase.Timestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

fun encodeProductImageUriToDataUrl(context: Context, uri: Uri): String? {
  return try {
    val original =
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
      } ?: return null
    val maxDim = 220
    val scale = max(original.width, original.height).toFloat() / maxDim.toFloat()
    val target =
      if (scale > 1f) {
        val w = (original.width / scale).toInt().coerceAtLeast(1)
        val h = (original.height / scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(original, w, h, true).also {
          if (it !== original) original.recycle()
        }
      } else {
        original
      }
    val out = ByteArrayOutputStream()
    target.compress(Bitmap.CompressFormat.JPEG, 65, out)
    target.recycle()
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    "data:image/jpeg;base64,$b64"
  } catch (_: Exception) {
    null
  }
}

fun decodeDataUrlBitmap(dataUrl: String): Bitmap? {
  if (!dataUrl.startsWith("data:image", ignoreCase = true)) return null
  val commaIdx = dataUrl.indexOf(',')
  if (commaIdx < 0 || commaIdx >= dataUrl.length - 1) return null
  return try {
    val bytes = Base64.decode(dataUrl.substring(commaIdx + 1), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  } catch (_: Exception) {
    null
  }
}

private val pkrWholeFormat = DecimalFormat("#,##0")
private val pkrDecFormat = DecimalFormat("#,##0.##")
private val dateDisplayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH)
private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

fun formatPkr(amount: Double, currency: String = "Rs"): String {
  val formatted =
    if (amount % 1.0 == 0.0) pkrWholeFormat.format(amount) else pkrDecFormat.format(amount)
  return "$currency $formatted"
}

fun formatQty(qty: Double): String =
  if (qty % 1.0 == 0.0) qty.toLong().toString() else pkrDecFormat.format(qty)

fun formatDateDisplay(date: Date = Date()): String = dateDisplayFormat.format(date)

fun formatTimestampDisplay(ts: Timestamp?): String =
  dateTimeFormat.format(ts?.toDate() ?: Date())

fun formatTimestampDateOnly(ts: Timestamp?): String =
  dateDisplayFormat.format(ts?.toDate() ?: Date())

fun todayKey(): String = dateKeyFormat.format(Date())

fun isToday(ts: Timestamp?): Boolean {
  val d = ts?.toDate() ?: return true
  return dateKeyFormat.format(d) == todayKey()
}

fun daysUntilExpiry(expiryDdMmYyyy: String): Long? {
  if (expiryDdMmYyyy.isBlank()) return null
  return try {
    val parsed = dateDisplayFormat.parse(expiryDdMmYyyy.trim()) ?: return null
    val todayCal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val diffMs = parsed.time - todayCal.timeInMillis
    diffMs / (1000L * 60L * 60L * 24L)
  } catch (_: Exception) {
    null
  }
}

fun openWhatsAppMessage(context: Context, rawPhone: String, message: String) {
  val digits = rawPhone.filter { it.isDigit() }
  val normalizedPhone =
    when {
      digits.startsWith("03") && digits.length == 11 -> "92" + digits.drop(1)
      digits.startsWith("92") -> digits
      else -> digits
    }
  val encoded = URLEncoder.encode(message, "UTF-8")
  val url =
    if (normalizedPhone.isNotBlank()) "https://wa.me/$normalizedPhone?text=$encoded"
    else "https://wa.me/?text=$encoded"
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  } catch (_: Exception) {
    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
  }
}

fun openExternalUrl(context: Context, url: String) {
  if (url.isBlank()) return
  try {
    val safeUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  } catch (_: Exception) {
    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
  }
}

fun observeNetworkOnline(context: Context): Flow<Boolean> = callbackFlow {
  val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  fun checkNow(): Boolean {
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
  trySend(checkNow())

  val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(true)
      }

      override fun onLost(network: Network) {
        trySend(checkNow())
      }
    }
  val request =
    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
  try {
    cm.registerNetworkCallback(request, callback)
  } catch (_: Exception) {
    trySend(true)
  }
  awaitClose {
    try {
      cm.unregisterNetworkCallback(callback)
    } catch (_: Exception) {
    }
  }
}

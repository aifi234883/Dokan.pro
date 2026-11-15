package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.i18n.AppStrings
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun BarcodeScannerDialog(
  strings: AppStrings,
  onBarcodeDetected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var manualCode by remember { mutableStateOf("") }
  var detectedOnce by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
    }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(strings.scanCamera, style = MaterialTheme.typography.titleLarge)
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = strings.cancel)
          }
        }

        if (hasPermission) {
          val scanner = remember {
            BarcodeScanning.getClient(
              BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
            )
          }
          val controller = remember {
            LifecycleCameraController(context).apply {
              val executor = ContextCompat.getMainExecutor(context)
              setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(
                  listOf(scanner),
                  COORDINATE_SYSTEM_VIEW_REFERENCED,
                  executor,
                ) { result ->
                  val codes = result?.getValue(scanner).orEmpty()
                  val raw = codes.firstOrNull()?.rawValue
                  if (!raw.isNullOrBlank() && !detectedOnce) {
                    detectedOnce = true
                    onBarcodeDetected(raw)
                  }
                },
              )
            }
          }
          DisposableEffect(lifecycleOwner) {
            controller.bindToLifecycle(lifecycleOwner)
            onDispose {
              controller.unbind()
              scanner.close()
            }
          }
          Box(
            modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp))
          ) {
            AndroidView(
              factory = { ctx ->
                PreviewView(ctx).apply { this.controller = controller }
              },
              modifier = Modifier.matchParentSize(),
            )
          }
        } else {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                if (strings.isUrdu) "بارکوڈ اسکین کرنے کے لیے کیمرہ کی اجازت دیں"
                else "Allow camera permission to scan product barcodes",
                style = MaterialTheme.typography.bodyMedium,
              )
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.testTag("grant_camera_button"),
              ) {
                Text(if (strings.isUrdu) "کیمرہ کھولیں" else "Enable Camera")
              }
            }
          }
        }

        OutlinedTextField(
          value = manualCode,
          onValueChange = { manualCode = it },
          label = { Text(if (strings.isUrdu) "یا بارکوڈ نمبر لکھیں" else "Or enter barcode manually") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("manual_barcode_dialog_input"),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text(strings.cancel) }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              if (manualCode.isNotBlank()) onBarcodeDetected(manualCode.trim())
            },
            enabled = manualCode.isNotBlank(),
            modifier = Modifier.testTag("manual_barcode_submit_button"),
          ) {
            Text(if (strings.isUrdu) "استعمال کریں" else "Use Barcode")
          }
        }
      }
    }
  }
}

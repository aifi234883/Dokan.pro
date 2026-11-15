package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.Sale
import com.example.data.model.Shop
import com.example.data.model.UserRole
import com.example.i18n.AppStrings
import com.example.ui.viewmodel.DukanMainUiState
import com.example.util.formatPkr
import com.example.util.formatTimestampDisplay
import com.example.util.openWhatsAppMessage

@Composable
fun KhataScreen(
  uiState: DukanMainUiState,
  strings: AppStrings,
  onSaveCustomer: (Customer) -> Unit,
  onDeleteCustomer: (String) -> Unit,
  onReceivePayment: (customerId: String, amount: Double, note: String) -> Unit,
) {
  val shop = uiState.activeShop ?: return
  var search by remember { mutableStateOf("") }
  var selectedCustomerId by remember { mutableStateOf<String?>(null) }
  var editingCustomer by remember { mutableStateOf<Customer?>(null) }
  var payingCustomer by remember { mutableStateOf<Customer?>(null) }

  val filteredCustomers = remember(uiState.customers, search) {
    val q = search.trim().lowercase()
    if (q.isBlank()) uiState.customers
    else uiState.customers.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
  }
  val totalPending = remember(uiState.customers) {
    uiState.customers.filter { it.balance > 0 }.sumOf { it.balance }
  }
  val selectedCustomer = uiState.customers.firstOrNull { it.id == selectedCustomerId }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 780.dp

    if (!isWide && selectedCustomer != null) {
      BackHandler { selectedCustomerId = null }
      CustomerDetailPane(
        shop = shop,
        customer = selectedCustomer,
        ledger = uiState.ledger.filter { it.customerId == selectedCustomer.id },
        sales = uiState.sales.filter { it.customerId == selectedCustomer.id },
        strings = strings,
        canDelete = uiState.currentRole != UserRole.CASHIER,
        showBack = true,
        onBack = { selectedCustomerId = null },
        onReceivePayment = { payingCustomer = selectedCustomer },
        onDelete = {
          onDeleteCustomer(selectedCustomer.id)
          selectedCustomerId = null
        },
      )
    } else {
      Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(strings.pendingUdhaar, style = MaterialTheme.typography.bodyMedium)
                  Text(
                    formatPkr(totalPending, shop.currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text(
                  "${uiState.customers.size} ${if (strings.isUrdu) "گاہک" else "Customers"}",
                  style = MaterialTheme.typography.labelLarge,
                )
              }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = search,
              onValueChange = { search = it },
              placeholder = { Text(if (strings.isUrdu) "نام یا فون سے تلاش کریں" else "Search customer name or phone") },
              singleLine = true,
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              modifier = Modifier.fillMaxWidth().testTag("khata_search_input"),
            )
            Spacer(Modifier.height(8.dp))

            if (filteredCustomers.isEmpty()) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(Modifier.height(8.dp))
                  Text(if (strings.isUrdu) "کوئی گاہک نہیں" else "No customers yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            } else {
              LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(filteredCustomers, key = { it.id }) { cust ->
                  Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier =
                      Modifier.fillMaxWidth()
                        .clickable { selectedCustomerId = cust.id }
                        .testTag("khata_customer_${cust.id}"),
                  ) {
                    Row(
                      modifier = Modifier.padding(14.dp).fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(cust.name, style = MaterialTheme.typography.titleMedium)
                        if (cust.phone.isNotBlank()) {
                          Text(cust.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                      }
                      Column(horizontalAlignment = Alignment.End) {
                        Text(
                          formatPkr(cust.balance, shop.currency),
                          style = MaterialTheme.typography.titleMedium,
                          color = if (cust.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          if (cust.balance > 0) (if (strings.isUrdu) "واجب الادا" else "Udhaar Due")
                          else (if (strings.isUrdu) "کلیئر" else "Settled"),
                          style = MaterialTheme.typography.labelSmall,
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          FloatingActionButton(
            onClick = { editingCustomer = Customer() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_customer_fab"),
          ) {
            Icon(Icons.Default.Add, contentDescription = strings.addCustomer)
          }
        }

        if (isWide) {
          Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.weight(1.1f).fillMaxHeight(),
          ) {
            if (selectedCustomer != null) {
              CustomerDetailPane(
                shop = shop,
                customer = selectedCustomer,
                ledger = uiState.ledger.filter { it.customerId == selectedCustomer.id },
                sales = uiState.sales.filter { it.customerId == selectedCustomer.id },
                strings = strings,
                canDelete = uiState.currentRole != UserRole.CASHIER,
                showBack = false,
                onBack = {},
                onReceivePayment = { payingCustomer = selectedCustomer },
                onDelete = {
                  onDeleteCustomer(selectedCustomer.id)
                  selectedCustomerId = null
                },
              )
            } else {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                  if (strings.isUrdu) "تفصیل دیکھنے کے لیے گاہک منتخب کریں" else "Select a customer to view Udhaar Khata",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  editingCustomer?.let { cust ->
    CustomerEditorDialog(
      initial = cust,
      strings = strings,
      onSave = {
        onSaveCustomer(it)
        editingCustomer = null
      },
      onDismiss = { editingCustomer = null },
    )
  }

  payingCustomer?.let { cust ->
    ReceivePaymentDialog(
      shop = shop,
      customer = cust,
      strings = strings,
      onConfirm = { amt, note ->
        onReceivePayment(cust.id, amt, note)
        payingCustomer = null
      },
      onDismiss = { payingCustomer = null },
    )
  }
}

@Composable
private fun CustomerDetailPane(
  shop: Shop,
  customer: Customer,
  ledger: List<LedgerEntry>,
  sales: List<Sale>,
  strings: AppStrings,
  canDelete: Boolean,
  showBack: Boolean,
  onBack: () -> Unit,
  onReceivePayment: () -> Unit,
  onDelete: () -> Unit,
) {
  val context = LocalContext.current
  Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
        Column {
          Text(customer.name, style = MaterialTheme.typography.titleLarge)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(customer.phone.ifBlank { "No phone" }, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      if (canDelete) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
        }
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(14.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(strings.balance, style = MaterialTheme.typography.bodyMedium)
          Text(
            formatPkr(customer.balance, shop.currency),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              val msg =
                if (strings.isUrdu) {
                  "السلام علیکم ${customer.name} صاحب، ${shop.name} کی طرف سے آپ کا ادھار بیلنس ${formatPkr(customer.balance, shop.currency)} واجب الادا ہے۔ براہ مہربانی ادائیگی فرمائیں۔ شکریہ!"
                } else {
                  "Assalam-o-Alaikum ${customer.name}, gentle reminder from ${shop.name}: your pending Udhaar balance is ${formatPkr(customer.balance, shop.currency)}. JazakAllah!"
                }
              openWhatsAppMessage(context, customer.phone, msg)
            },
            modifier = Modifier.testTag("khata_whatsapp_reminder_button"),
          ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (strings.isUrdu) "یاددہانی" else "Remind")
          }
          Button(
            onClick = onReceivePayment,
            modifier = Modifier.testTag("khata_receive_payment_button"),
          ) {
            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (strings.isUrdu) "وصولی" else "Receive")
          }
        }
      }
    }

    Text(
      if (strings.isUrdu) "کھاتہ ریکارڈ (${ledger.size}) • بل (${sales.size})"
      else "Khata Ledger (${ledger.size}) • Purchases (${sales.size})",
      style = MaterialTheme.typography.titleMedium,
    )
    HorizontalDivider()

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(ledger, key = { it.id }) { entry ->
        val isCredit = entry.type == "credit"
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                if (isCredit) (if (strings.isUrdu) "ادھار دیا" else "Udhaar Given")
                else (if (strings.isUrdu) "رقم موصول" else "Payment Received"),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              )
              if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
              Text(formatTimestampDisplay(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
              (if (isCredit) "+" else "-") + formatPkr(entry.amount, shop.currency),
              style = MaterialTheme.typography.titleMedium,
              color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CustomerEditorDialog(
  initial: Customer,
  strings: AppStrings,
  onSave: (Customer) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf(initial.name) }
  var phone by remember { mutableStateOf(initial.phone) }
  var address by remember { mutableStateOf(initial.address) }
  var openingBalance by remember { mutableStateOf(if (initial.balance != 0.0) initial.balance.toString() else "0") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.addCustomer) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (strings.isUrdu) "نام" else "Customer Name") }, singleLine = true)
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(if (strings.isUrdu) "فون / واٹس ایپ" else "Phone / WhatsApp (03xx...)") }, singleLine = true)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(if (strings.isUrdu) "پتہ" else "Address") }, singleLine = true)
        OutlinedTextField(
          value = openingBalance,
          onValueChange = { openingBalance = it },
          label = { Text(strings.balance) },
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
            initial.copy(
              name = name,
              phone = phone,
              address = address,
              balance = openingBalance.toDoubleOrNull() ?: 0.0,
            )
          )
        },
        enabled = name.isNotBlank(),
      ) { Text(strings.save) }
    },
  )
}

@Composable
private fun ReceivePaymentDialog(
  shop: Shop,
  customer: Customer,
  strings: AppStrings,
  onConfirm: (Double, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${strings.receivePayment} - ${customer.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Current Udhaar: ${formatPkr(customer.balance, shop.currency)}")
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it },
          label = { Text(if (strings.isUrdu) "رقم (${shop.currency})" else "Amount (${shop.currency})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
        )
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    confirmButton = {
      Button(
        onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, note) },
        enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0,
      ) { Text(strings.save) }
    },
  )
}

package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.local.PreferencesManager
import com.example.data.model.AdBanner
import com.example.data.model.Customer
import com.example.data.model.DayClosing
import com.example.data.model.Expense
import com.example.data.model.LedgerEntry
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Shop
import com.example.data.model.ShopInvite
import com.example.data.model.ShopMember
import com.example.data.model.Supplier
import com.example.data.model.UpgradeRequest
import com.example.data.model.UserRole
import com.example.data.remote.unwrapFirestoreMessage
import com.example.data.repository.DukanProRepository
import com.example.util.formatDateDisplay
import com.example.util.todayKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CartLine(
  val product: Product,
  val qty: Double,
) {
  val lineTotal: Double get() = qty * product.salePrice
}

data class PosUiState(
  val lines: List<CartLine> = emptyList(),
  val discountInput: String = "",
  val discountIsPercent: Boolean = false,
  val paymentMethod: String = "cash",
  val selectedCustomerId: String = "",
  val receivedInput: String = "",
  val resumingHeldSaleId: String = "",
  val isSubmitting: Boolean = false,
  val lastCompletedSale: Sale? = null,
  val lastScannedProduct: Product? = null,
  val unmatchedBarcode: String? = null,
  val negativeStockItemNames: List<String> = emptyList(),
)

data class DukanMainUiState(
  val isLoadingShops: Boolean = true,
  val shops: List<Shop> = emptyList(),
  val activeShop: Shop? = null,
  val currentRole: UserRole = UserRole.CASHIER,
  val pendingInvites: List<ShopInvite> = emptyList(),
  val members: List<ShopMember> = emptyList(),
  val products: List<Product> = emptyList(),
  val barcodeMap: Map<String, Product> = emptyMap(),
  val customers: List<Customer> = emptyList(),
  val ledger: List<LedgerEntry> = emptyList(),
  val sales: List<Sale> = emptyList(),
  val suppliers: List<Supplier> = emptyList(),
  val purchases: List<Purchase> = emptyList(),
  val expenses: List<Expense> = emptyList(),
  val dayClosings: List<DayClosing> = emptyList(),
  val activeAds: List<AdBanner> = emptyList(),
  val upgradeRequests: List<UpgradeRequest> = emptyList(),
  val preferences: AppPreferences = AppPreferences(),
  val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DukanProViewModel(
  private val repo: DukanProRepository,
  private val prefs: PreferencesManager,
  val currentUserId: String,
  val currentUserName: String,
  val currentUserEmail: String,
) : ViewModel() {

  private val _posState = MutableStateFlow(PosUiState())
  val posState: StateFlow<PosUiState> = _posState.asStateFlow()

  private val _toastEvent = MutableStateFlow<String?>(null)
  val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

  private val shopsFlow: StateFlow<Pair<Boolean, List<Shop>>> =
    repo
      .observeUserShops(currentUserId)
      .map { false to it }
      .catch { e ->
        _toastEvent.value = unwrapFirestoreMessage(e, "Failed to load shops")
        emit(false to emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true to emptyList())

  private val invitesFlow =
    if (currentUserEmail.isNotBlank()) {
      repo.observePendingInvites(currentUserEmail, currentUserId).catch { emit(emptyList()) }
    } else flowOf(emptyList())

  private val activeShopFlow: StateFlow<Shop?> =
    combine(shopsFlow, prefs.preferencesFlow) { (_, shops), appPrefs ->
      shops.firstOrNull { it.id == appPrefs.selectedShopId } ?: shops.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  private val membersFlow =
    activeShopFlow.flatMapLatest { shop ->
      if (shop == null) flowOf(emptyList())
      else repo.observeMembers(shop.id, currentUserId).catch { emit(emptyList()) }
    }

  private val roleFlow: StateFlow<UserRole> =
    combine(activeShopFlow, membersFlow) { shop, members ->
      if (shop == null) UserRole.CASHIER
      else if (shop.ownerId == currentUserId) UserRole.OWNER
      else UserRole.fromKey(members.firstOrNull { it.uid == currentUserId }?.role)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserRole.CASHIER)

  private val productsFlow =
    activeShopFlow.flatMapLatest { shop ->
      if (shop == null) flowOf(emptyList())
      else repo.observeProducts(shop.id, currentUserId).catch { emit(emptyList()) }
    }

  private val customersFlow =
    activeShopFlow.flatMapLatest { shop ->
      if (shop == null) flowOf(emptyList())
      else repo.observeCustomers(shop.id, currentUserId).catch { emit(emptyList()) }
    }

  private val ledgerFlow =
    activeShopFlow.flatMapLatest { shop ->
      if (shop == null) flowOf(emptyList())
      else repo.observeLedger(shop.id, currentUserId).catch { emit(emptyList()) }
    }

  private val salesFlow =
    activeShopFlow.flatMapLatest { shop ->
      if (shop == null) flowOf(emptyList())
      else repo.observeSales(shop.id, currentUserId).catch { emit(emptyList()) }
    }

  private val suppliersFlow =
    combine(activeShopFlow, roleFlow) { shop, role -> shop to role }
      .flatMapLatest { (shop, role) ->
        if (shop == null || role == UserRole.CASHIER) flowOf(emptyList())
        else repo.observeSuppliers(shop.id, currentUserId).catch { emit(emptyList()) }
      }

  private val purchasesFlow =
    combine(activeShopFlow, roleFlow) { shop, role -> shop to role }
      .flatMapLatest { (shop, role) ->
        if (shop == null || role == UserRole.CASHIER) flowOf(emptyList())
        else repo.observePurchases(shop.id, currentUserId).catch { emit(emptyList()) }
      }

  private val expensesFlow =
    combine(activeShopFlow, roleFlow) { shop, role -> shop to role }
      .flatMapLatest { (shop, role) ->
        if (shop == null || role == UserRole.CASHIER) flowOf(emptyList())
        else repo.observeExpenses(shop.id, currentUserId).catch { emit(emptyList()) }
      }

  private val dayClosingsFlow =
    combine(activeShopFlow, roleFlow) { shop, role -> shop to role }
      .flatMapLatest { (shop, role) ->
        if (shop == null || role == UserRole.CASHIER) flowOf(emptyList())
        else repo.observeDayClosings(shop.id, currentUserId).catch { emit(emptyList()) }
      }

  private val activeAdsFlow =
    repo.observeActiveAds(currentUserId).catch { emit(emptyList()) }

  private val upgradeRequestsFlow =
    repo.observeUpgradeRequests(currentUserId).catch { emit(emptyList()) }

  private val coreDataFlow =
    combine(
      shopsFlow,
      activeShopFlow,
      roleFlow,
      invitesFlow,
      membersFlow,
    ) { (loading, shops), activeShop, role, invites, members ->
      CoreBundle(loading, shops, activeShop, role, invites, members)
    }

  private val shopCatalogFlow =
    combine(
      productsFlow,
      customersFlow,
      ledgerFlow,
      salesFlow,
    ) { products, customers, ledger, sales ->
      CatalogBundle(products, customers, ledger, sales)
    }

  private val managementFlow =
    combine(
      suppliersFlow,
      purchasesFlow,
      expensesFlow,
      dayClosingsFlow,
    ) { suppliers, purchases, expenses, closings ->
      ManagementBundle(suppliers, purchases, expenses, closings)
    }

  private val platformFlow =
    combine(
      activeAdsFlow,
      upgradeRequestsFlow,
      prefs.preferencesFlow,
    ) { activeAds, upgrades, appPrefs ->
      PlatformBundle(activeAds, upgrades, appPrefs)
    }

  val uiState: StateFlow<DukanMainUiState> =
    combine(coreDataFlow, shopCatalogFlow, managementFlow, platformFlow) { core, cat, mgmt, plat ->
      val barcodeLookup =
        cat.products
          .filter { it.barcode.isNotBlank() }
          .associateBy { it.barcode.trim() }
      DukanMainUiState(
        isLoadingShops = core.loading,
        shops = core.shops,
        activeShop = core.activeShop,
        currentRole = core.role,
        pendingInvites = core.invites,
        members = core.members,
        products = cat.products,
        barcodeMap = barcodeLookup,
        customers = cat.customers,
        ledger = cat.ledger,
        sales = cat.sales,
        suppliers = mgmt.suppliers,
        purchases = mgmt.purchases,
        expenses = mgmt.expenses,
        dayClosings = mgmt.dayClosings,
        activeAds = plat.activeAds,
        upgradeRequests = plat.upgradeRequests,
        preferences = plat.prefs,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DukanMainUiState())

  fun consumeToast() {
    _toastEvent.value = null
  }

  fun showToast(msg: String) {
    _toastEvent.value = msg
  }

  fun toggleLanguage() {
    viewModelScope.launch { prefs.setUrdu(!uiState.value.preferences.isUrdu) }
  }

  fun toggleTheme() {
    viewModelScope.launch { prefs.setDarkTheme(!uiState.value.preferences.isDarkTheme) }
  }

  fun selectShop(shopId: String) {
    viewModelScope.launch {
      prefs.setSelectedShopId(shopId)
      clearCart()
    }
  }

  // --- SHOP & STAFF ACTIONS ---

  fun createShop(name: String, address: String, phone: String) {
    viewModelScope.launch {
      repo
        .createDefaultShop(currentUserName, currentUserEmail, name, address, phone)
        .onSuccess { id ->
          prefs.setSelectedShopId(id)
          _toastEvent.value = "Shop created!"
        }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not create shop") }
    }
  }

  fun acceptInvite(invite: ShopInvite) {
    viewModelScope.launch {
      repo
        .acceptInvite(invite, currentUserName, currentUserEmail)
        .onSuccess {
          prefs.setSelectedShopId(invite.shopId)
          _toastEvent.value = "Joined ${invite.shopName}"
        }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not accept invite") }
    }
  }

  fun updateShopSettings(shop: Shop) {
    viewModelScope.launch {
      repo
        .updateShopSettings(shop)
        .onSuccess { _toastEvent.value = "Settings saved" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to update settings") }
    }
  }

  fun inviteStaff(email: String, role: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .inviteStaff(shop, email, role)
        .onSuccess { _toastEvent.value = "Invitation sent to $email" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to invite staff") }
    }
  }

  fun updateMemberRole(memberUid: String, role: String, active: Boolean) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .updateMember(shop.id, memberUid, role, active)
        .onSuccess { _toastEvent.value = "Staff updated" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to update staff") }
    }
  }

  // --- POS & CART ---

  fun handleBarcodeScannedInPos(rawCode: String) {
    val code = rawCode.trim()
    if (code.isBlank()) return
    val product = uiState.value.barcodeMap[code]
    if (product != null) {
      addProductToCart(product)
      _posState.update { it.copy(unmatchedBarcode = null, lastScannedProduct = product) }
    } else {
      _posState.update { it.copy(unmatchedBarcode = code, lastScannedProduct = null) }
      _toastEvent.value = "Barcode not found: $code"
    }
  }

  fun clearUnmatchedBarcode() {
    _posState.update { it.copy(unmatchedBarcode = null) }
  }

  fun dismissLastScannedProduct() {
    _posState.update { it.copy(lastScannedProduct = null) }
  }

  fun quickAddAndCartProduct(product: Product) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .saveProduct(shop, product)
        .onSuccess { newId ->
          val savedProduct = product.copy(id = newId, shopId = shop.id)
          addProductToCart(savedProduct)
          _posState.update {
            it.copy(unmatchedBarcode = null, lastScannedProduct = savedProduct)
          }
          _toastEvent.value = "Added ${savedProduct.name} to bill"
        }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not add product") }
    }
  }

  fun addProductToCart(product: Product) {
    _posState.update { state ->
      val existing = state.lines.firstOrNull { it.product.id == product.id }
      val updatedLines =
        if (existing != null) {
          state.lines.map {
            if (it.product.id == product.id) it.copy(qty = it.qty + 1.0) else it
          }
        } else {
          state.lines + CartLine(product = product, qty = 1.0)
        }
      state.copy(lines = updatedLines)
    }
  }

  fun updateCartQty(productId: String, newQty: Double) {
    _posState.update { state ->
      val updated =
        if (newQty <= 0.0) state.lines.filterNot { it.product.id == productId }
        else state.lines.map { if (it.product.id == productId) it.copy(qty = newQty) else it }
      state.copy(lines = updated)
    }
  }

  fun setDiscount(input: String, isPercent: Boolean) {
    _posState.update { it.copy(discountInput = input, discountIsPercent = isPercent) }
  }

  fun setPaymentMethod(method: String) {
    _posState.update { it.copy(paymentMethod = method) }
  }

  fun selectPosCustomer(customerId: String) {
    _posState.update { it.copy(selectedCustomerId = customerId) }
  }

  fun setReceivedInput(value: String) {
    _posState.update { it.copy(receivedInput = value) }
  }

  fun clearCart() {
    _posState.value = PosUiState(lastCompletedSale = _posState.value.lastCompletedSale)
  }

  fun dismissReceipt() {
    _posState.update { it.copy(lastCompletedSale = null) }
  }

  fun showReceipt(sale: Sale) {
    _posState.update { it.copy(lastCompletedSale = sale) }
  }

  fun dismissNegativeStockWarning() {
    _posState.update { it.copy(negativeStockItemNames = emptyList()) }
  }

  fun computeTotals(pos: PosUiState, shop: Shop?): PosTotals {
    val subtotal = pos.lines.sumOf { it.lineTotal }
    val rawDisc = pos.discountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val discount =
      if (pos.discountIsPercent) ((subtotal * rawDisc / 100.0) * 100.0).roundToInt() / 100.0
      else rawDisc.coerceAtMost(subtotal)
    val taxable = (subtotal - discount).coerceAtLeast(0.0)
    val taxRate = shop?.taxRate ?: 0.0
    val tax = ((taxable * taxRate / 100.0) * 100.0).roundToInt() / 100.0
    val grandTotal = taxable + tax
    val receivedVal = pos.receivedInput.toDoubleOrNull()
    val paidAmount =
      when {
        pos.paymentMethod == "udhaar" -> (receivedVal ?: 0.0).coerceIn(0.0, grandTotal)
        receivedVal != null -> receivedVal.coerceAtLeast(0.0)
        else -> grandTotal
      }
    val dueAmount =
      if (pos.paymentMethod == "udhaar") (grandTotal - paidAmount).coerceAtLeast(0.0)
      else (grandTotal - paidAmount).coerceAtLeast(0.0)
    val changeReturn = (paidAmount - grandTotal).coerceAtLeast(0.0)
    return PosTotals(subtotal, discount, tax, grandTotal, paidAmount.coerceAtMost(grandTotal), dueAmount, changeReturn)
  }

  fun completeSale(forceOverrideNegativeStock: Boolean = false) {
    val state = uiState.value
    val shop = state.activeShop ?: return
    val pos = _posState.value
    if (pos.lines.isEmpty()) {
      _toastEvent.value = "Cart is empty"
      return
    }
    val totals = computeTotals(pos, shop)
    if (pos.paymentMethod == "udhaar" && pos.selectedCustomerId.isBlank()) {
      _toastEvent.value = "Please select a customer for Udhaar"
      return
    }
    if (totals.dueAmount > 0.0 && pos.selectedCustomerId.isBlank()) {
      _toastEvent.value = "Paid amount is less than total. Select customer for Udhaar or pay full."
      return
    }

    val allowOverride = forceOverrideNegativeStock || shop.allowNegativeStockOverride
    if (!allowOverride) {
      val insufficient =
        pos.lines.filter { it.qty > it.product.stockQty }.map { "${it.product.name} (${it.product.stockQty} left)" }
      if (insufficient.isNotEmpty()) {
        _posState.update { it.copy(negativeStockItemNames = insufficient) }
        return
      }
    }

    val customer = state.customers.firstOrNull { it.id == pos.selectedCustomerId }
    val invoiceNo = "DP-${System.currentTimeMillis() % 1000000}"
    val sale =
      Sale(
        id = pos.resumingHeldSaleId,
        shopId = shop.id,
        invoiceNo = invoiceNo,
        items =
          pos.lines.map { line ->
            SaleItem(
              productId = line.product.id,
              name = line.product.name,
              nameUrdu = line.product.nameUrdu,
              qty = line.qty,
              price = line.product.salePrice,
              purchasePrice = line.product.purchasePrice,
              total = line.lineTotal,
              imageUrl = line.product.imageUrl,
            )
          },
        subtotal = totals.subtotal,
        discount = totals.discount,
        tax = totals.tax,
        grandTotal = totals.grandTotal,
        paidAmount = totals.paidAmount,
        dueAmount = totals.dueAmount,
        paymentMethod = pos.paymentMethod,
        customerId = customer?.id ?: "",
        customerName = customer?.name ?: "",
        cashierId = currentUserId,
        cashierName = currentUserName,
        status = "completed",
      )

    viewModelScope.launch {
      _posState.update { it.copy(isSubmitting = true, negativeStockItemNames = emptyList()) }
      repo
        .completeSaleTransaction(shop, sale, allowOverride)
        .onSuccess { createdId ->
          _posState.value = PosUiState(lastCompletedSale = sale.copy(id = createdId))
        }
        .onFailure { e ->
          _posState.update { it.copy(isSubmitting = false) }
          _toastEvent.value = unwrapFirestoreMessage(e, "Failed to complete sale")
        }
    }
  }

  fun holdCurrentBill() {
    val state = uiState.value
    val shop = state.activeShop ?: return
    val pos = _posState.value
    if (pos.lines.isEmpty()) return
    val totals = computeTotals(pos, shop)
    val customer = state.customers.firstOrNull { it.id == pos.selectedCustomerId }
    val sale =
      Sale(
        shopId = shop.id,
        invoiceNo = "HOLD-${System.currentTimeMillis() % 100000}",
        items =
          pos.lines.map {
            SaleItem(
              productId = it.product.id,
              name = it.product.name,
              nameUrdu = it.product.nameUrdu,
              qty = it.qty,
              price = it.product.salePrice,
              purchasePrice = it.product.purchasePrice,
              total = it.lineTotal,
              imageUrl = it.product.imageUrl,
            )
          },
        subtotal = totals.subtotal,
        discount = totals.discount,
        tax = totals.tax,
        grandTotal = totals.grandTotal,
        paymentMethod = pos.paymentMethod,
        customerId = customer?.id ?: "",
        customerName = customer?.name ?: "",
        cashierId = currentUserId,
        cashierName = currentUserName,
        status = "held",
      )
    viewModelScope.launch {
      repo
        .holdSale(shop, sale)
        .onSuccess {
          clearCart()
          _toastEvent.value = "Bill held"
        }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not hold bill") }
    }
  }

  fun resumeHeldSale(sale: Sale) {
    val productsById = uiState.value.products.associateBy { it.id }
    val lines =
      sale.items.mapNotNull { item ->
        val prod =
          productsById[item.productId]
            ?: Product(
              id = item.productId,
              name = item.name,
              nameUrdu = item.nameUrdu,
              salePrice = item.price,
              purchasePrice = item.purchasePrice,
              stockQty = 999.0,
              imageUrl = item.imageUrl,
            )
        CartLine(product = prod, qty = item.qty)
      }
    _posState.value =
      PosUiState(
        lines = lines,
        discountInput = if (sale.discount > 0) sale.discount.toString() else "",
        paymentMethod = sale.paymentMethod,
        selectedCustomerId = sale.customerId,
        resumingHeldSaleId = sale.id,
      )
    _toastEvent.value = "Resumed ${sale.invoiceNo}"
  }

  fun returnSale(sale: Sale) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .returnSaleTransaction(shop, sale)
        .onSuccess { _toastEvent.value = "Returned ${sale.invoiceNo} & restored stock" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Return failed") }
    }
  }

  // --- INVENTORY ---

  fun saveProduct(product: Product) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .saveProduct(shop, product)
        .onSuccess {
          clearUnmatchedBarcode()
          _toastEvent.value = "Product saved"
        }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not save product") }
    }
  }

  fun deleteProduct(productId: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .deleteProduct(shop.id, productId)
        .onSuccess { _toastEvent.value = "Product deleted" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not delete product") }
    }
  }

  fun updateProductPhoto(productId: String, imageUrl: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .updateProductPhoto(shop.id, productId, imageUrl)
        .onSuccess { _toastEvent.value = "Product photo updated" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Could not update photo") }
    }
  }

  fun adjustStock(productId: String, deltaQty: Double, reason: String, note: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .adjustStockTransaction(shop, productId, deltaQty, reason, note)
        .onSuccess { _toastEvent.value = "Stock adjusted" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to adjust stock") }
    }
  }

  fun importCsvProducts(items: List<Product>) {
    val shop = uiState.value.activeShop ?: return
    if (items.isEmpty()) {
      _toastEvent.value = "No valid rows found in CSV"
      return
    }
    viewModelScope.launch {
      var saved = 0
      items.forEach { p ->
        if (repo.saveProduct(shop, p).isSuccess) saved++
      }
      _toastEvent.value = "Imported $saved products"
    }
  }

  // --- CUSTOMERS / UDHAAR ---

  fun saveCustomer(customer: Customer) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .saveCustomer(shop, customer)
        .onSuccess { _toastEvent.value = "Customer saved" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to save customer") }
    }
  }

  fun deleteCustomer(customerId: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .deleteCustomer(shop.id, customerId)
        .onSuccess { _toastEvent.value = "Customer deleted" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to delete customer") }
    }
  }

  fun receiveUdhaarPayment(customerId: String, amount: Double, note: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .receiveUdhaarPaymentTransaction(shop, customerId, amount, note)
        .onSuccess { _toastEvent.value = "Payment recorded" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to record payment") }
    }
  }

  // --- SUPPLIERS, EXPENSES, DAY CLOSING ---

  fun saveSupplier(supplier: Supplier) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .saveSupplier(shop, supplier)
        .onSuccess { _toastEvent.value = "Supplier saved" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to save supplier") }
    }
  }

  fun recordPurchase(
    supplierId: String,
    supplierName: String,
    items: List<PurchaseItem>,
    paidAmount: Double,
    updateCostPrice: Boolean,
  ) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .recordPurchaseTransaction(shop, supplierId, supplierName, items, paidAmount, updateCostPrice)
        .onSuccess { _toastEvent.value = "Purchase recorded & stock updated" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Purchase failed") }
    }
  }

  fun paySupplier(supplierId: String, amount: Double) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .paySupplier(shop.id, supplierId, amount)
        .onSuccess { _toastEvent.value = "Supplier payment recorded" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Payment failed") }
    }
  }

  fun addExpense(category: String, amount: Double, note: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .addExpense(shop, category, amount, note)
        .onSuccess { _toastEvent.value = "Expense added" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to add expense") }
    }
  }

  fun deleteExpense(expenseId: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .deleteExpense(shop.id, expenseId)
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Failed to delete expense") }
    }
  }

  fun saveDayClosing(openingCash: Double, totalSales: Double, totalExpenses: Double, countedCash: Double) {
    val shop = uiState.value.activeShop ?: return
    val expected = openingCash + totalSales - totalExpenses
    val diff = countedCash - expected
    val closing =
      DayClosing(
        id = todayKey(),
        shopId = shop.id,
        dateDisplay = formatDateDisplay(),
        openingCash = openingCash,
        totalSales = totalSales,
        totalExpenses = totalExpenses,
        expectedCash = expected,
        countedCash = countedCash,
        difference = diff,
        closedByUid = currentUserId,
      )
    viewModelScope.launch {
      repo
        .saveDayClosing(shop, todayKey(), closing)
        .onSuccess { _toastEvent.value = "Day closing saved" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Day closing failed") }
    }
  }

  // --- ADS, PRO UPGRADE & SEED ---

  fun trackAdImpression(adId: String) {
    viewModelScope.launch { repo.incrementAdImpression(adId) }
  }

  fun trackAdClick(adId: String) {
    viewModelScope.launch { repo.incrementAdClick(adId) }
  }

  fun submitUpgradeRequest(paymentMethod: String, txRef: String, proofNote: String) {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .submitUpgradeRequest(shop, currentUserEmail, paymentMethod, txRef, proofNote)
        .onSuccess { _toastEvent.value = "Upgrade request submitted for review!" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Submission failed") }
    }
  }

  fun seedDemoData() {
    val shop = uiState.value.activeShop ?: return
    viewModelScope.launch {
      repo
        .seedDemoData(shop)
        .onSuccess { _toastEvent.value = "Added 20 Pakistani products & 5 customers!" }
        .onFailure { _toastEvent.value = unwrapFirestoreMessage(it, "Seed failed") }
    }
  }

  private data class CoreBundle(
    val loading: Boolean,
    val shops: List<Shop>,
    val activeShop: Shop?,
    val role: UserRole,
    val invites: List<ShopInvite>,
    val members: List<ShopMember>,
  )

  private data class CatalogBundle(
    val products: List<Product>,
    val customers: List<Customer>,
    val ledger: List<LedgerEntry>,
    val sales: List<Sale>,
  )

  private data class ManagementBundle(
    val suppliers: List<Supplier>,
    val purchases: List<Purchase>,
    val expenses: List<Expense>,
    val dayClosings: List<DayClosing>,
  )

  private data class PlatformBundle(
    val activeAds: List<AdBanner>,
    val upgradeRequests: List<UpgradeRequest>,
    val prefs: AppPreferences,
  )
}

data class PosTotals(
  val subtotal: Double,
  val discount: Double,
  val tax: Double,
  val grandTotal: Double,
  val paidAmount: Double,
  val dueAmount: Double,
  val changeReturn: Double,
)

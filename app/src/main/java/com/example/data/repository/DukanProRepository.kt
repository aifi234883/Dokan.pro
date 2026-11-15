package com.example.data.repository

import android.content.Context
import com.example.R
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
import com.example.data.model.StockAdjustment
import com.example.data.model.Supplier
import com.example.data.model.UpgradeRequest
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DukanProRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(createConfiguredFirestore(context))

  companion object {
    fun createConfiguredFirestore(context: Context): FirebaseFirestore {
      val dbId = context.applicationContext.getString(R.string.firestore_database_id)
      val instance = FirebaseFirestore.getInstance(dbId)
      try {
        instance.firestoreSettings =
          FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
      } catch (_: IllegalStateException) {
        // Settings already applied prior to first Firestore operation.
      }
      return instance
    }
  }

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before accessing Firestore.")

  private fun <T> Flow<T>.handleStreamError(op: OperationType, path: String, uid: String): Flow<T> =
    catch { error ->
      val firestoreError =
        generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val toLog = firestoreError ?: (error as? Exception)
      if (toLog != null && auth.currentUser?.uid == uid) {
        handleFirestoreError(toLog, op, path)
      }
      throw error
    }

  private fun shopsCol() = db.collection("shops")
  private fun shopDoc(shopId: String) = shopsCol().document(shopId)
  private fun membersCol(shopId: String) = shopDoc(shopId).collection("members")
  private fun productsCol(shopId: String) = shopDoc(shopId).collection("products")
  private fun customersCol(shopId: String) = shopDoc(shopId).collection("customers")
  private fun salesCol(shopId: String) = shopDoc(shopId).collection("sales")
  private fun ledgerCol(shopId: String) = shopDoc(shopId).collection("ledger")
  private fun suppliersCol(shopId: String) = shopDoc(shopId).collection("suppliers")
  private fun purchasesCol(shopId: String) = shopDoc(shopId).collection("purchases")
  private fun expensesCol(shopId: String) = shopDoc(shopId).collection("expenses")
  private fun dayClosingsCol(shopId: String) = shopDoc(shopId).collection("dayClosings")
  private fun stockAdjustmentsCol(shopId: String) = shopDoc(shopId).collection("stockAdjustments")
  private fun invitesCol() = db.collection("shopInvites")
  private fun adsCol() = db.collection("ads")
  private fun upgradesCol() = db.collection("upgradeRequests")

  // --- SHOPS & MEMBERS ---

  fun observeUserShops(uid: String): Flow<List<Shop>> =
    shopsCol()
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleStreamError(OperationType.LIST, "shops", uid)

  fun observeShop(shopId: String): Flow<Shop?> {
    val uid = auth.currentUser?.uid ?: ""
    return shopDoc(shopId)
      .snapshots()
      .map { snap ->
        snap.toObject(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleStreamError(OperationType.GET, "shops/$shopId", uid)
  }

  suspend fun createDefaultShop(
    ownerName: String,
    ownerEmail: String,
    shopName: String,
    address: String = "",
    phone: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val newShopRef = shopsCol().document()
    val shopId = newShopRef.id
    val cleanName = shopName.trim().ifBlank { "My Dukan" }
    val shopPayload =
      mapOf(
        "name" to cleanName,
        "ownerId" to uid,
        "memberUids" to listOf(uid),
        "managerUids" to listOf(uid),
        "address" to address.trim(),
        "phone" to phone.trim(),
        "logoUrl" to "",
        "plan" to "free",
        "currency" to "Rs",
        "taxRate" to 0.0,
        "receiptFooter" to "Shukriya! Dobara tashreef laayein.",
        "showReceiptSponsor" to true,
        "allowNegativeStockOverride" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      newShopRef.set(shopPayload).await()
      val memberPayload =
        mapOf(
          "shopId" to shopId,
          "uid" to uid,
          "name" to ownerName.ifBlank { "Owner" },
          "email" to ownerEmail.lowercase(),
          "role" to "owner",
          "active" to true,
          "memberUids" to listOf(uid),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      membersCol(shopId).document(uid).set(memberPayload).await()
      shopId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, newShopRef.path)
      throw e
    }
  }

  suspend fun updateShopSettings(shop: Shop): Result<Unit> = runCatching {
    requireUserId()
    val docRef = shopDoc(shop.id)
    val updates =
      mapOf(
        "name" to shop.name.trim(),
        "address" to shop.address.trim(),
        "phone" to shop.phone.trim(),
        "logoUrl" to shop.logoUrl.trim(),
        "currency" to shop.currency.ifBlank { "Rs" },
        "taxRate" to shop.taxRate.coerceIn(0.0, 100.0),
        "receiptFooter" to shop.receiptFooter.trim(),
        "showReceiptSponsor" to shop.showReceiptSponsor,
        "allowNegativeStockOverride" to shop.allowNegativeStockOverride,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeMembers(shopId: String, uid: String): Flow<List<ShopMember>> =
    membersCol(shopId)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(ShopMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/members", uid)

  fun observePendingInvites(email: String, uid: String): Flow<List<ShopInvite>> =
    invitesCol()
      .whereEqualTo("email", email.lowercase().trim())
      .snapshots()
      .map { snap ->
        snap
          .toObjects(ShopInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .filter { it.status == "pending" }
      }
      .handleStreamError(OperationType.LIST, "shopInvites", uid)

  suspend fun inviteStaff(shop: Shop, email: String, role: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanEmail = email.lowercase().trim()
    require(cleanEmail.contains("@")) { "Enter a valid staff email address." }
    require(role in listOf("manager", "cashier")) { "Invalid role" }
    val docRef = invitesCol().document()
    val payload =
      mapOf(
        "shopId" to shop.id,
        "shopName" to shop.name,
        "email" to cleanEmail,
        "role" to role,
        "invitedByUid" to uid,
        "status" to "pending",
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun acceptInvite(invite: ShopInvite, name: String, email: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val shopRef = shopDoc(invite.shopId)
      try {
        val shopUpdates = mutableMapOf<String, Any>(
          "memberUids" to FieldValue.arrayUnion(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (invite.role == "manager") {
          shopUpdates["managerUids"] = FieldValue.arrayUnion(uid)
        }
        shopRef.update(shopUpdates).await()
        val updatedShop = shopRef.get().await().toObject(Shop::class.java)
        val memberUids = updatedShop?.memberUids ?: listOf(uid)
        val memberPayload =
          mapOf(
            "shopId" to invite.shopId,
            "uid" to uid,
            "name" to name.ifBlank { email.substringBefore("@") },
            "email" to email.lowercase(),
            "role" to invite.role,
            "active" to true,
            "memberUids" to memberUids,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        membersCol(invite.shopId).document(uid).set(memberPayload).await()
        invitesCol().document(invite.id).update("status", "accepted").await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, shopRef.path)
        throw e
      }
    }

  suspend fun updateMember(
    shopId: String,
    memberUid: String,
    role: String,
    active: Boolean,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = membersCol(shopId).document(memberUid)
    try {
      docRef
        .update(
          mapOf(
            "role" to role,
            "active" to active,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // --- PRODUCTS ---

  fun observeProducts(shopId: String, uid: String): Flow<List<Product>> =
    productsCol(shopId)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.name.lowercase() }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/products", uid)

  suspend fun saveProduct(shop: Shop, product: Product): Result<String> = runCatching {
    requireUserId()
    val isNew = product.id.isBlank()
    val docRef =
      if (isNew) productsCol(shop.id).document() else productsCol(shop.id).document(product.id)
    val payload =
      mutableMapOf<String, Any>(
        "shopId" to shop.id,
        "memberUids" to shop.memberUids.ifEmpty { listOf(requireUserId()) },
        "name" to product.name.trim(),
        "nameUrdu" to product.nameUrdu.trim(),
        "barcode" to product.barcode.trim(),
        "category" to product.category.trim().ifBlank { "General" },
        "purchasePrice" to product.purchasePrice.coerceAtLeast(0.0),
        "salePrice" to product.salePrice.coerceAtLeast(0.0),
        "stockQty" to product.stockQty,
        "lowStockLimit" to product.lowStockLimit.coerceAtLeast(0.0),
        "unit" to product.unit,
        "expiryDate" to product.expiryDate.trim(),
        "imageUrl" to product.imageUrl.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      if (isNew) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        docRef.update(payload).await()
      }
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteProduct(shopId: String, productId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = productsCol(shopId).document(productId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun updateProductPhoto(shopId: String, productId: String, imageUrl: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = productsCol(shopId).document(productId)
    try {
      docRef
        .update(
          mapOf(
            "imageUrl" to imageUrl.trim(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun adjustStockTransaction(
    shop: Shop,
    productId: String,
    deltaQty: Double,
    reason: String,
    note: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val prodRef = productsCol(shop.id).document(productId)
    val adjRef = stockAdjustmentsCol(shop.id).document()
    try {
      db.runTransaction { tx ->
        val prodSnap = tx.get(prodRef)
        val prodName = prodSnap.getString("name") ?: "Product"
        val currentQty = (prodSnap.get("stockQty") as? Number)?.toDouble() ?: 0.0
        val newQty = (currentQty + deltaQty).coerceAtLeast(0.0)
        tx.update(
          prodRef,
          mapOf("stockQty" to newQty, "updatedAt" to FieldValue.serverTimestamp()),
        )
        tx.set(
          adjRef,
          mapOf(
            "shopId" to shop.id,
            "managerUids" to shop.managerUids.ifEmpty { listOf(uid) },
            "productId" to productId,
            "productName" to prodName,
            "deltaQty" to deltaQty,
            "reason" to reason,
            "note" to note.trim(),
            "adjustedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, prodRef.path)
      throw e
    }
  }

  // --- CUSTOMERS & UDHAAR LEDGER ---

  fun observeCustomers(shopId: String, uid: String): Flow<List<Customer>> =
    customersCol(shopId)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Customer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.balance }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/customers", uid)

  suspend fun saveCustomer(shop: Shop, customer: Customer): Result<String> = runCatching {
    requireUserId()
    val isNew = customer.id.isBlank()
    val docRef =
      if (isNew) customersCol(shop.id).document() else customersCol(shop.id).document(customer.id)
    val payload =
      mutableMapOf<String, Any>(
        "shopId" to shop.id,
        "memberUids" to shop.memberUids.ifEmpty { listOf(requireUserId()) },
        "name" to customer.name.trim(),
        "phone" to customer.phone.trim(),
        "address" to customer.address.trim(),
        "balance" to customer.balance,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      if (isNew) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        docRef.update(payload).await()
      }
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteCustomer(shopId: String, customerId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = customersCol(shopId).document(customerId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeLedger(shopId: String, uid: String): Flow<List<LedgerEntry>> =
    ledgerCol(shopId)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(LedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/ledger", uid)

  suspend fun receiveUdhaarPaymentTransaction(
    shop: Shop,
    customerId: String,
    amount: Double,
    note: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    require(amount > 0.0) { "Payment amount must be greater than 0" }
    val custRef = customersCol(shop.id).document(customerId)
    val ledgerRef = ledgerCol(shop.id).document()
    try {
      db.runTransaction { tx ->
        val custSnap = tx.get(custRef)
        val currentBalance = (custSnap.get("balance") as? Number)?.toDouble() ?: 0.0
        tx.update(
          custRef,
          mapOf(
            "balance" to (currentBalance - amount),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        tx.set(
          ledgerRef,
          mapOf(
            "shopId" to shop.id,
            "memberUids" to shop.memberUids.ifEmpty { listOf(requireUserId()) },
            "customerId" to customerId,
            "type" to "payment",
            "amount" to amount,
            "note" to note.ifBlank { "Udhaar payment received" },
            "saleId" to "",
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, custRef.path)
      throw e
    }
  }

  // --- SALES / POS ---

  fun observeSales(shopId: String, uid: String): Flow<List<Sale>> =
    salesCol(shopId)
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Sale::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/sales", uid)

  suspend fun completeSaleTransaction(
    shop: Shop,
    sale: Sale,
    allowNegativeOverride: Boolean,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    require(sale.items.isNotEmpty()) { "Cart cannot be empty" }
    val isResumingHeld = sale.id.isNotBlank()
    val saleRef = if (isResumingHeld) salesCol(shop.id).document(sale.id) else salesCol(shop.id).document()
    val custRef =
      if (sale.customerId.isNotBlank()) customersCol(shop.id).document(sale.customerId) else null
    val ledgerRef =
      if (sale.dueAmount > 0.0 && custRef != null) ledgerCol(shop.id).document() else null

    try {
      db.runTransaction { tx ->
        // 1. Read all product snapshots first (Firestore requirement: all reads before writes)
        val prodReads =
          sale.items.map { item ->
            val ref = productsCol(shop.id).document(item.productId)
            item to tx.get(ref)
          }
        val custSnap = custRef?.let { tx.get(it) }

        // 2. Validate and decrement stock
        for ((item, snap) in prodReads) {
          if (snap.exists()) {
            val currentQty = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
            val newQty = currentQty - item.qty
            if (newQty < 0 && !allowNegativeOverride) {
              throw IllegalStateException("Insufficient stock for ${item.name} (available: $currentQty)")
            }
            tx.update(
              snap.reference,
              mapOf("stockQty" to newQty, "updatedAt" to FieldValue.serverTimestamp()),
            )
          }
        }

        // 3. Write sale
        val itemsPayload =
          sale.items.map {
            mapOf(
              "productId" to it.productId,
              "name" to it.name,
              "nameUrdu" to it.nameUrdu,
              "qty" to it.qty,
              "price" to it.price,
              "purchasePrice" to it.purchasePrice,
              "total" to it.total,
              "imageUrl" to it.imageUrl,
            )
          }
        val memberUids = shop.memberUids.ifEmpty { listOf(uid) }
        if (isResumingHeld) {
          tx.update(
            saleRef,
            mapOf(
              "items" to itemsPayload,
              "subtotal" to sale.subtotal,
              "discount" to sale.discount,
              "tax" to sale.tax,
              "grandTotal" to sale.grandTotal,
              "paidAmount" to sale.paidAmount,
              "dueAmount" to sale.dueAmount,
              "paymentMethod" to sale.paymentMethod,
              "status" to "completed",
            ),
          )
        } else {
          tx.set(
            saleRef,
            mapOf(
              "shopId" to shop.id,
              "memberUids" to memberUids,
              "invoiceNo" to sale.invoiceNo,
              "items" to itemsPayload,
              "subtotal" to sale.subtotal,
              "discount" to sale.discount,
              "tax" to sale.tax,
              "grandTotal" to sale.grandTotal,
              "paidAmount" to sale.paidAmount,
              "dueAmount" to sale.dueAmount,
              "paymentMethod" to sale.paymentMethod,
              "customerId" to sale.customerId,
              "customerName" to sale.customerName,
              "cashierId" to uid,
              "cashierName" to sale.cashierName,
              "status" to "completed",
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        // 4. Update customer Udhaar balance + ledger
        if (sale.dueAmount > 0.0 && custRef != null && custSnap != null && ledgerRef != null) {
          val curBal = (custSnap.get("balance") as? Number)?.toDouble() ?: 0.0
          tx.update(
            custRef,
            mapOf(
              "balance" to (curBal + sale.dueAmount),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          tx.set(
            ledgerRef,
            mapOf(
              "shopId" to shop.id,
              "memberUids" to memberUids,
              "customerId" to sale.customerId,
              "type" to "credit",
              "amount" to sale.dueAmount,
              "note" to "Invoice ${sale.invoiceNo}",
              "saleId" to saleRef.id,
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        saleRef.id
      }.await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, saleRef.path)
      throw e
    }
  }

  suspend fun holdSale(shop: Shop, sale: Sale): Result<String> = runCatching {
    val uid = requireUserId()
    val saleRef = salesCol(shop.id).document()
    val memberUids = shop.memberUids.ifEmpty { listOf(uid) }
    val itemsPayload =
      sale.items.map {
        mapOf(
          "productId" to it.productId,
          "name" to it.name,
          "nameUrdu" to it.nameUrdu,
          "qty" to it.qty,
          "price" to it.price,
          "purchasePrice" to it.purchasePrice,
          "total" to it.total,
          "imageUrl" to it.imageUrl,
        )
      }
    try {
      saleRef
        .set(
          mapOf(
            "shopId" to shop.id,
            "memberUids" to memberUids,
            "invoiceNo" to sale.invoiceNo,
            "items" to itemsPayload,
            "subtotal" to sale.subtotal,
            "discount" to sale.discount,
            "tax" to sale.tax,
            "grandTotal" to sale.grandTotal,
            "paidAmount" to 0.0,
            "dueAmount" to 0.0,
            "paymentMethod" to sale.paymentMethod,
            "customerId" to sale.customerId,
            "customerName" to sale.customerName,
            "cashierId" to uid,
            "cashierName" to sale.cashierName,
            "status" to "held",
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      saleRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, saleRef.path)
      throw e
    }
  }

  suspend fun returnSaleTransaction(shop: Shop, sale: Sale): Result<Unit> = runCatching {
    val uid = requireUserId()
    val saleRef = salesCol(shop.id).document(sale.id)
    val custRef =
      if (sale.customerId.isNotBlank() && sale.dueAmount > 0.0) {
        customersCol(shop.id).document(sale.customerId)
      } else null
    val ledgerRef = if (custRef != null) ledgerCol(shop.id).document() else null

    try {
      db.runTransaction { tx ->
        val prodReads =
          sale.items.map { item -> item to tx.get(productsCol(shop.id).document(item.productId)) }
        val custSnap = custRef?.let { tx.get(it) }

        for ((item, snap) in prodReads) {
          if (snap.exists()) {
            val currentQty = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
            tx.update(
              snap.reference,
              mapOf("stockQty" to (currentQty + item.qty), "updatedAt" to FieldValue.serverTimestamp()),
            )
          }
        }
        tx.update(saleRef, mapOf("status" to "returned"))

        if (custRef != null && custSnap != null && ledgerRef != null) {
          val curBal = (custSnap.get("balance") as? Number)?.toDouble() ?: 0.0
          tx.update(
            custRef,
            mapOf(
              "balance" to (curBal - sale.dueAmount).coerceAtLeast(0.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          tx.set(
            ledgerRef,
            mapOf(
              "shopId" to shop.id,
              "memberUids" to shop.memberUids.ifEmpty { listOf(uid) },
              "customerId" to sale.customerId,
              "type" to "payment",
              "amount" to sale.dueAmount,
              "note" to "Return ${sale.invoiceNo}",
              "saleId" to sale.id,
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }.await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, saleRef.path)
      throw e
    }
  }

  // --- SUPPLIERS & PURCHASES ---

  fun observeSuppliers(shopId: String, uid: String): Flow<List<Supplier>> =
    suppliersCol(shopId)
      .whereArrayContains("managerUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(Supplier::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/suppliers", uid)

  fun observePurchases(shopId: String, uid: String): Flow<List<Purchase>> =
    purchasesCol(shopId)
      .whereArrayContains("managerUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Purchase::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/purchases", uid)

  suspend fun saveSupplier(shop: Shop, supplier: Supplier): Result<String> = runCatching {
    val uid = requireUserId()
    val isNew = supplier.id.isBlank()
    val docRef =
      if (isNew) suppliersCol(shop.id).document() else suppliersCol(shop.id).document(supplier.id)
    val payload =
      mutableMapOf<String, Any>(
        "shopId" to shop.id,
        "managerUids" to shop.managerUids.ifEmpty { listOf(uid) },
        "name" to supplier.name.trim(),
        "phone" to supplier.phone.trim(),
        "balance" to supplier.balance,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      if (isNew) {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      } else {
        docRef.update(payload).await()
      }
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun recordPurchaseTransaction(
    shop: Shop,
    supplierId: String,
    supplierName: String,
    items: List<PurchaseItem>,
    paidAmount: Double,
    updateCostPrice: Boolean,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(items.isNotEmpty()) { "Purchase items cannot be empty" }
    val total = items.sumOf { it.total }
    val due = (total - paidAmount).coerceAtLeast(0.0)
    val purchaseRef = purchasesCol(shop.id).document()
    val supRef = suppliersCol(shop.id).document(supplierId)
    val managerUids = shop.managerUids.ifEmpty { listOf(uid) }

    try {
      db.runTransaction { tx ->
        val prodReads =
          items.map { it to tx.get(productsCol(shop.id).document(it.productId)) }
        val supSnap = tx.get(supRef)

        for ((item, snap) in prodReads) {
          if (snap.exists()) {
            val curQty = (snap.get("stockQty") as? Number)?.toDouble() ?: 0.0
            val updates =
              mutableMapOf<String, Any>(
                "stockQty" to (curQty + item.qty),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            if (updateCostPrice && item.costPrice >= 0.0) {
              updates["purchasePrice"] = item.costPrice
            }
            tx.update(snap.reference, updates)
          }
        }
        val curSupBal = (supSnap.get("balance") as? Number)?.toDouble() ?: 0.0
        tx.update(
          supRef,
          mapOf("balance" to (curSupBal + due), "updatedAt" to FieldValue.serverTimestamp()),
        )
        tx.set(
          purchaseRef,
          mapOf(
            "shopId" to shop.id,
            "managerUids" to managerUids,
            "supplierId" to supplierId,
            "supplierName" to supplierName,
            "items" to
              items.map {
                mapOf(
                  "productId" to it.productId,
                  "name" to it.name,
                  "qty" to it.qty,
                  "costPrice" to it.costPrice,
                  "total" to it.total,
                )
              },
            "total" to total,
            "paid" to paidAmount,
            "due" to due,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }.await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, purchaseRef.path)
      throw e
    }
  }

  suspend fun paySupplier(shopId: String, supplierId: String, amount: Double): Result<Unit> =
    runCatching {
      requireUserId()
      val supRef = suppliersCol(shopId).document(supplierId)
      try {
        db.runTransaction { tx ->
          val snap = tx.get(supRef)
          val curBal = (snap.get("balance") as? Number)?.toDouble() ?: 0.0
          tx.update(
            supRef,
            mapOf(
              "balance" to (curBal - amount).coerceAtLeast(0.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }.await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, supRef.path)
        throw e
      }
    }

  // --- EXPENSES & DAY CLOSINGS ---

  fun observeExpenses(shopId: String, uid: String): Flow<List<Expense>> =
    expensesCol(shopId)
      .whereArrayContains("managerUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/expenses", uid)

  suspend fun addExpense(shop: Shop, category: String, amount: Double, note: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docRef = expensesCol(shop.id).document()
      try {
        docRef
          .set(
            mapOf(
              "shopId" to shop.id,
              "managerUids" to shop.managerUids.ifEmpty { listOf(uid) },
              "category" to category,
              "amount" to amount.coerceAtLeast(0.0),
              "note" to note.trim(),
              "createdAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path)
        throw e
      }
    }

  suspend fun deleteExpense(shopId: String, expenseId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = expensesCol(shopId).document(expenseId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  fun observeDayClosings(shopId: String, uid: String): Flow<List<DayClosing>> =
    dayClosingsCol(shopId)
      .whereArrayContains("managerUids", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(DayClosing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.id }
      }
      .handleStreamError(OperationType.LIST, "shops/$shopId/dayClosings", uid)

  suspend fun saveDayClosing(shop: Shop, dateKey: String, closing: DayClosing): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docRef = dayClosingsCol(shop.id).document(dateKey)
      try {
        docRef
          .set(
            mapOf(
              "shopId" to shop.id,
              "managerUids" to shop.managerUids.ifEmpty { listOf(uid) },
              "dateDisplay" to closing.dateDisplay,
              "openingCash" to closing.openingCash.coerceAtLeast(0.0),
              "totalSales" to closing.totalSales.coerceAtLeast(0.0),
              "totalExpenses" to closing.totalExpenses.coerceAtLeast(0.0),
              "expectedCash" to closing.expectedCash,
              "countedCash" to closing.countedCash.coerceAtLeast(0.0),
              "difference" to closing.difference,
              "closedByUid" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path)
        throw e
      }
    }

  // --- ADS & PRO UPGRADES ---

  fun observeActiveAds(uid: String): Flow<List<AdBanner>> =
    adsCol()
      .whereEqualTo("active", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(AdBanner::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .handleStreamError(OperationType.LIST, "ads", uid)

  suspend fun incrementAdImpression(adId: String) {
    if (auth.currentUser == null || adId.isBlank()) return
    try {
      adsCol().document(adId).update("impressions", FieldValue.increment(1L)).await()
    } catch (_: Exception) {
      // Ads fail silently when offline
    }
  }

  suspend fun incrementAdClick(adId: String) {
    if (auth.currentUser == null || adId.isBlank()) return
    try {
      adsCol().document(adId).update("clicks", FieldValue.increment(1L)).await()
    } catch (_: Exception) {
      // Ads fail silently when offline
    }
  }

  suspend fun submitUpgradeRequest(
    shop: Shop,
    ownerEmail: String,
    paymentMethod: String,
    transactionRef: String,
    proofNote: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = upgradesCol().document()
    try {
      docRef
        .set(
          mapOf(
            "shopId" to shop.id,
            "shopName" to shop.name,
            "ownerUid" to uid,
            "ownerEmail" to ownerEmail,
            "paymentMethod" to paymentMethod,
            "transactionRef" to transactionRef.trim(),
            "proofNote" to proofNote.trim(),
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  fun observeUpgradeRequests(uid: String): Flow<List<UpgradeRequest>> =
    upgradesCol()
      .whereEqualTo("ownerUid", uid)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(UpgradeRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .handleStreamError(OperationType.LIST, "upgradeRequests", uid)

  // --- SEED DEMO DATA (20 Pakistani Products + 5 Customers) ---

  suspend fun seedDemoData(shop: Shop): Result<Unit> = runCatching {
    val uid = requireUserId()
    val memberUids = shop.memberUids.ifEmpty { listOf(uid) }
    val batch = db.batch()

    val demoProducts =
      listOf(
        Product(name = "Tapal Danedar Tea 900g", nameUrdu = "ٹپال دانے دار چائے", barcode = "8961003010101", category = "Tea & Beverages", purchasePrice = 1280.0, salePrice = 1420.0, stockQty = 28.0, lowStockLimit = 6.0, unit = "pcs"),
        Product(name = "Olper's Milk 1 Ltr", nameUrdu = "اولپرز دودھ ١ لیٹر", barcode = "8964000111222", category = "Dairy", purchasePrice = 290.0, salePrice = 325.0, stockQty = 48.0, lowStockLimit = 12.0, unit = "ltr"),
        Product(name = "Dalda Cooking Oil 5 Ltr", nameUrdu = "ڈالڈا کوکنگ آئل ٥ لیٹر", barcode = "8961014005001", category = "Oil & Ghee", purchasePrice = 2580.0, salePrice = 2780.0, stockQty = 14.0, lowStockLimit = 4.0, unit = "pcs"),
        Product(name = "Shan Biryani Masala 60g", nameUrdu = "شان بریانی مصالحہ", barcode = "8961001100105", category = "Spices", purchasePrice = 110.0, salePrice = 135.0, stockQty = 60.0, lowStockLimit = 15.0, unit = "pcs"),
        Product(name = "Guard Basmati Rice", nameUrdu = "گارڈ باسمتی چاول", barcode = "8964002300018", category = "Grains & Flour", purchasePrice = 310.0, salePrice = 360.0, stockQty = 75.0, lowStockLimit = 15.0, unit = "kg"),
        Product(name = "Surf Excel Washing Powder 1kg", nameUrdu = "سرف ایکسل ١ کلو", barcode = "8961011120102", category = "Home Care", purchasePrice = 540.0, salePrice = 610.0, stockQty = 22.0, lowStockLimit = 5.0, unit = "pcs"),
        Product(name = "National Iodized Salt 800g", nameUrdu = "نیشنل نمک", barcode = "8961002000103", category = "Spices", purchasePrice = 52.0, salePrice = 65.0, stockQty = 45.0, lowStockLimit = 10.0, unit = "pcs"),
        Product(name = "Lipton Yellow Label 475g", nameUrdu = "لپٹن ییلو لیبل", barcode = "8961011200475", category = "Tea & Beverages", purchasePrice = 740.0, salePrice = 830.0, stockQty = 18.0, lowStockLimit = 5.0, unit = "pcs"),
        Product(name = "Lifebuoy Total Soap 115g", nameUrdu = "لائف بوائے صابن", barcode = "8961011300115", category = "Personal Care", purchasePrice = 105.0, salePrice = 125.0, stockQty = 55.0, lowStockLimit = 12.0, unit = "pcs"),
        Product(name = "Colgate Dental Cream 150g", nameUrdu = "کولگیٹ ٹوتھ پیسٹ", barcode = "8961006001501", category = "Personal Care", purchasePrice = 240.0, salePrice = 285.0, stockQty = 24.0, lowStockLimit = 6.0, unit = "pcs"),
        Product(name = "Habib Banaspati Ghee 1kg", nameUrdu = "حبیب بناسپتی گھی", barcode = "8961005101001", category = "Oil & Ghee", purchasePrice = 510.0, salePrice = 555.0, stockQty = 30.0, lowStockLimit = 8.0, unit = "kg"),
        Product(name = "Dawn Milky Bread Large", nameUrdu = "ڈان بریڈ بڑی", barcode = "8964003100011", category = "Bakery", purchasePrice = 145.0, salePrice = 170.0, stockQty = 4.0, lowStockLimit = 6.0, unit = "pcs", expiryDate = "18/11/2026"),
        Product(name = "Pepsi 1.5 Ltr Bottle", nameUrdu = "پیپسی ١.٥ لیٹر", barcode = "8961008150001", category = "Tea & Beverages", purchasePrice = 165.0, salePrice = 200.0, stockQty = 36.0, lowStockLimit = 10.0, unit = "pcs"),
        Product(name = "Nestle Everyday Tea Whitener 850g", nameUrdu = "نیسلے ایوری ڈے", barcode = "8961009850001", category = "Dairy", purchasePrice = 1120.0, salePrice = 1260.0, stockQty = 16.0, lowStockLimit = 4.0, unit = "pcs"),
        Product(name = "Sunridge Chakki Atta 5kg", nameUrdu = "سن رج چکی آٹا ٥ کلو", barcode = "8964005500005", category = "Grains & Flour", purchasePrice = 690.0, salePrice = 770.0, stockQty = 20.0, lowStockLimit = 5.0, unit = "pcs"),
        Product(name = "Panadol Extra Strip (10s)", nameUrdu = "پیناڈول ایکسٹرا پتا", barcode = "8961007010010", category = "Medical", purchasePrice = 34.0, salePrice = 42.0, stockQty = 80.0, lowStockLimit = 20.0, unit = "pcs", expiryDate = "30/12/2027"),
        Product(name = "Dettol Antiseptic Liquid 250ml", nameUrdu = "ڈیٹول لیکوڈ", barcode = "8961012250001", category = "Medical", purchasePrice = 315.0, salePrice = 365.0, stockQty = 12.0, lowStockLimit = 4.0, unit = "pcs"),
        Product(name = "Farm Fresh Eggs", nameUrdu = "تازہ انڈے", barcode = "8964009000012", category = "Dairy", purchasePrice = 295.0, salePrice = 330.0, stockQty = 18.0, lowStockLimit = 5.0, unit = "dozen"),
        Product(name = "White Sugar (Cheeni)", nameUrdu = "سفید چینی", barcode = "8964001000001", category = "Grains & Flour", purchasePrice = 148.0, salePrice = 165.0, stockQty = 95.0, lowStockLimit = 20.0, unit = "kg"),
        Product(name = "Lux Soft Touch Soap", nameUrdu = "لکس صابن", barcode = "8961011300201", category = "Personal Care", purchasePrice = 118.0, salePrice = 140.0, stockQty = 3.0, lowStockLimit = 8.0, unit = "pcs"),
      )

    for (p in demoProducts) {
      val ref = productsCol(shop.id).document()
      batch.set(
        ref,
        mapOf(
          "shopId" to shop.id,
          "memberUids" to memberUids,
          "name" to p.name,
          "nameUrdu" to p.nameUrdu,
          "barcode" to p.barcode,
          "category" to p.category,
          "purchasePrice" to p.purchasePrice,
          "salePrice" to p.salePrice,
          "stockQty" to p.stockQty,
          "lowStockLimit" to p.lowStockLimit,
          "unit" to p.unit,
          "expiryDate" to p.expiryDate,
          "imageUrl" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }

    val demoCustomers =
      listOf(
        Customer(name = "Haji Muhammad Akram", phone = "923001234567", address = "Gulberg Block C", balance = 4250.0),
        Customer(name = "Chaudhary Tariq Mehmood", phone = "923214567890", address = "Model Town", balance = 1800.0),
        Customer(name = "Saima Bibi", phone = "923339876543", address = "Johar Town", balance = 950.0),
        Customer(name = "Bilal Ahmed Khan", phone = "923451122334", address = "Faisal Town", balance = 0.0),
        Customer(name = "Usman Raza", phone = "923127788990", address = "Ichhra Bazaar", balance = 6400.0),
      )

    for (c in demoCustomers) {
      val ref = customersCol(shop.id).document()
      batch.set(
        ref,
        mapOf(
          "shopId" to shop.id,
          "memberUids" to memberUids,
          "name" to c.name,
          "phone" to c.phone,
          "address" to c.address,
          "balance" to c.balance,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }

    try {
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "shops/${shop.id}/seed")
      throw e
    }
  }
}

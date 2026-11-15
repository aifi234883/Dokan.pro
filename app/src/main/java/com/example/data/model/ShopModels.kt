package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val key: String) {
  OWNER("owner"),
  MANAGER("manager"),
  CASHIER("cashier");

  companion object {
    fun fromKey(key: String?): UserRole =
      entries.firstOrNull { it.key == key } ?: CASHIER
  }
}

enum class PaymentMethod(val key: String, val labelEn: String, val labelUr: String) {
  CASH("cash", "Cash", "نقد"),
  JAZZCASH("jazzcash", "JazzCash", "جاز کیش"),
  EASYPAISA("easypaisa", "EasyPaisa", "ایزی پیسہ"),
  CARD("card", "Card", "کارڈ"),
  UDHAAR("udhaar", "Udhaar (Credit)", "ادھار");

  companion object {
    fun fromKey(key: String?): PaymentMethod =
      entries.firstOrNull { it.key == key } ?: CASH
  }
}

enum class ProductUnit(val key: String, val labelEn: String, val labelUr: String) {
  PCS("pcs", "pcs", "عدد"),
  KG("kg", "kg", "کلو"),
  LTR("ltr", "ltr", "لیٹر"),
  DOZEN("dozen", "dozen", "درجن");

  companion object {
    fun fromKey(key: String?): ProductUnit =
      entries.firstOrNull { it.key == key } ?: PCS
  }
}

enum class ExpenseCategory(val key: String, val labelEn: String, val labelUr: String) {
  RENT("rent", "Rent", "کرایہ"),
  ELECTRICITY("electricity", "Electricity", "بجلی بل"),
  SALARY("salary", "Salary", "تنخواہ"),
  TRANSPORT("transport", "Transport", "کرایہ آمدورفت"),
  OTHER("other", "Other", "دیگر");

  companion object {
    fun fromKey(key: String?): ExpenseCategory =
      entries.firstOrNull { it.key == key } ?: OTHER
  }
}

enum class AdjustmentReason(val key: String, val labelEn: String, val labelUr: String) {
  DAMAGE("damage", "Damage", "ٹوٹ پھوٹ"),
  CORRECTION("correction", "Correction", "تصحیح"),
  EXPIRED("expired", "Expired", "میعاد ختم");

  companion object {
    fun fromKey(key: String?): AdjustmentReason =
      entries.firstOrNull { it.key == key } ?: CORRECTION
  }
}

@IgnoreExtraProperties
data class Shop(
  @DocumentId val id: String = "",
  val name: String = "",
  val ownerId: String = "",
  val memberUids: List<String> = emptyList(),
  val managerUids: List<String> = emptyList(),
  val address: String = "",
  val phone: String = "",
  val logoUrl: String = "",
  val plan: String = "free",
  val currency: String = "Rs",
  val taxRate: Double = 0.0,
  val receiptFooter: String = "Shukriya! Dobara tashreef laayein.",
  val showReceiptSponsor: Boolean = true,
  val allowNegativeStockOverride: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopMember(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val uid: String = "",
  val name: String = "",
  val email: String = "",
  val role: String = "cashier",
  val active: Boolean = true,
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ShopInvite(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val shopName: String = "",
  val email: String = "",
  val role: String = "cashier",
  val invitedByUid: String = "",
  val status: String = "pending",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Product(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val memberUids: List<String> = emptyList(),
  val name: String = "",
  val nameUrdu: String = "",
  val barcode: String = "",
  val category: String = "General",
  val purchasePrice: Double = 0.0,
  val salePrice: Double = 0.0,
  val stockQty: Double = 0.0,
  val lowStockLimit: Double = 5.0,
  val unit: String = "pcs",
  val expiryDate: String = "",
  val imageUrl: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Customer(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val memberUids: List<String> = emptyList(),
  val name: String = "",
  val phone: String = "",
  val address: String = "",
  val balance: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SaleItem(
  val productId: String = "",
  val name: String = "",
  val nameUrdu: String = "",
  val qty: Double = 1.0,
  val price: Double = 0.0,
  val purchasePrice: Double = 0.0,
  val total: Double = 0.0,
  val imageUrl: String = "",
)

@IgnoreExtraProperties
data class Sale(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val memberUids: List<String> = emptyList(),
  val invoiceNo: String = "",
  val items: List<SaleItem> = emptyList(),
  val subtotal: Double = 0.0,
  val discount: Double = 0.0,
  val tax: Double = 0.0,
  val grandTotal: Double = 0.0,
  val paidAmount: Double = 0.0,
  val dueAmount: Double = 0.0,
  val paymentMethod: String = "cash",
  val customerId: String = "",
  val customerName: String = "",
  val cashierId: String = "",
  val cashierName: String = "",
  val status: String = "completed",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LedgerEntry(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val memberUids: List<String> = emptyList(),
  val customerId: String = "",
  val type: String = "credit",
  val amount: Double = 0.0,
  val note: String = "",
  val saleId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Supplier(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val managerUids: List<String> = emptyList(),
  val name: String = "",
  val phone: String = "",
  val balance: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PurchaseItem(
  val productId: String = "",
  val name: String = "",
  val qty: Double = 1.0,
  val costPrice: Double = 0.0,
  val total: Double = 0.0,
)

@IgnoreExtraProperties
data class Purchase(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val managerUids: List<String> = emptyList(),
  val supplierId: String = "",
  val supplierName: String = "",
  val items: List<PurchaseItem> = emptyList(),
  val total: Double = 0.0,
  val paid: Double = 0.0,
  val due: Double = 0.0,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Expense(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val managerUids: List<String> = emptyList(),
  val category: String = "other",
  val amount: Double = 0.0,
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DayClosing(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val managerUids: List<String> = emptyList(),
  val dateDisplay: String = "",
  val openingCash: Double = 0.0,
  val totalSales: Double = 0.0,
  val totalExpenses: Double = 0.0,
  val expectedCash: Double = 0.0,
  val countedCash: Double = 0.0,
  val difference: Double = 0.0,
  val closedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StockAdjustment(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val managerUids: List<String> = emptyList(),
  val productId: String = "",
  val productName: String = "",
  val deltaQty: Double = 0.0,
  val reason: String = "correction",
  val note: String = "",
  val adjustedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdBanner(
  @DocumentId val id: String = "",
  val title: String = "",
  val imageUrl: String = "",
  val linkUrl: String = "",
  val placement: String = "dashboard",
  val active: Boolean = true,
  val startDate: String = "",
  val endDate: String = "",
  val sponsor: String = "",
  val impressions: Long = 0L,
  val clicks: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UpgradeRequest(
  @DocumentId val id: String = "",
  val shopId: String = "",
  val shopName: String = "",
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val paymentMethod: String = "jazzcash",
  val transactionRef: String = "",
  val proofNote: String = "",
  val status: String = "pending",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Customer
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID

class DukanProRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createShopAndCompleteUdhaarSale_updatesStockAndCustomerLedger() = runBlocking {
    val ownerUid = signInTestUser("owner_${UUID.randomUUID()}@test.com")
    val repo = DukanProRepository(firestore, auth)

    val shopId =
      withTimeout(TIMEOUT_MS) {
        repo
          .createDefaultShop(
            ownerName = "Bilal",
            ownerEmail = "owner@test.com",
            shopName = "Bismillah Kiryana",
          )
          .getOrThrow()
      }

    val shop =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserShops(ownerUid).first { list -> list.any { it.id == shopId } }
      }
        .first { it.id == shopId }

    val prodId =
      withTimeout(TIMEOUT_MS) {
        repo
          .saveProduct(
            shop,
            Product(
              name = "Tapal Tea",
              nameUrdu = "ٹپال چائے",
              barcode = "8961001",
              category = "Tea",
              purchasePrice = 1000.0,
              salePrice = 1200.0,
              stockQty = 10.0,
              lowStockLimit = 2.0,
              unit = "pcs",
              imageUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
            ),
          )
          .getOrThrow()
      }

    val custId =
      withTimeout(TIMEOUT_MS) {
        repo
          .saveCustomer(
            shop,
            Customer(name = "Akram", phone = "923001112233", balance = 0.0),
          )
          .getOrThrow()
      }

    val sale =
      Sale(
        invoiceNo = "INV-1001",
        items =
          listOf(
            SaleItem(
              productId = prodId,
              name = "Tapal Tea",
              qty = 2.0,
              price = 1200.0,
              purchasePrice = 1000.0,
              total = 2400.0,
            )
          ),
        subtotal = 2400.0,
        discount = 0.0,
        tax = 0.0,
        grandTotal = 2400.0,
        paidAmount = 400.0,
        dueAmount = 2000.0,
        paymentMethod = "udhaar",
        customerId = custId,
        customerName = "Akram",
        cashierId = ownerUid,
        cashierName = "Bilal",
        status = "completed",
      )

    val saleId =
      withTimeout(TIMEOUT_MS) {
        repo.completeSaleTransaction(shop, sale, allowNegativeOverride = false).getOrThrow()
      }
    assertThat(saleId).isNotEmpty()

    val updatedProducts =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProducts(shopId, ownerUid).first { list ->
          list.any { it.id == prodId && it.stockQty == 8.0 }
        }
      }
    assertThat(updatedProducts.first { it.id == prodId }.stockQty).isEqualTo(8.0)

    val updatedCustomers =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCustomers(shopId, ownerUid).first { list ->
          list.any { it.id == custId && it.balance == 2000.0 }
        }
      }
    assertThat(updatedCustomers.first { it.id == custId }.balance).isEqualTo(2000.0)

    withTimeout(TIMEOUT_MS) {
      repo.receiveUdhaarPaymentTransaction(shop, custId, 500.0, "Partial cash").getOrThrow()
    }
    val afterPayment =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCustomers(shopId, ownerUid).first { list ->
          list.any { it.id == custId && it.balance == 1500.0 }
        }
      }
    assertThat(afterPayment.first { it.id == custId }.balance).isEqualTo(1500.0)

    val newImg = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
    withTimeout(TIMEOUT_MS) {
      repo.updateProductPhoto(shopId, prodId, newImg).getOrThrow()
    }
    val afterPhoto =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeProducts(shopId, ownerUid).first { list ->
          list.any { it.id == prodId && it.imageUrl == newImg }
        }
      }
    assertThat(afterPhoto.first { it.id == prodId }.imageUrl).isEqualTo(newImg)
  }

  @Test
  fun crossUserRead_isRejectedWithPermissionDenied() = runBlocking {
    signInTestUser("owner_${UUID.randomUUID()}@test.com")
    val ownerRepo = DukanProRepository(firestore, auth)
    val shopId =
      withTimeout(TIMEOUT_MS) {
        ownerRepo.createDefaultShop("Owner", "owner@test.com", "Shop A").getOrThrow()
      }

    signInTestUser("outsider_${UUID.randomUUID()}@test.com")
    var thrown: Exception? = null
    try {
      withTimeout(TIMEOUT_MS) {
        firestore.collection("shops").document(shopId).get().await()
      }
    } catch (e: Exception) {
      thrown = e
    }
    val firestoreEx = thrown?.unwrapFirestoreException()
    assertThat(firestoreEx).isNotNull()
    assertThat(firestoreEx?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
  }

  @Test
  fun unauthenticatedObserveShops_isRejectedWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = DukanProRepository(firestore, auth)
    var thrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserShops("any_uid").first()
      }
    } catch (e: Exception) {
      thrown = e
    }
    val firestoreEx = thrown?.unwrapFirestoreException()
    assertThat(firestoreEx).isNotNull()
    assertThat(firestoreEx?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}

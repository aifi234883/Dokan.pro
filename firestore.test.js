const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_123";
const CASHIER_UID = "cashier_456";
const OUTSIDER_UID = "outsider_789";
const SHOP_ID = "shop_abc";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedShopWithOwnerAndCashier() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = new Date();
    await db.collection("shops").doc(SHOP_ID).set({
      name: "Al-Madina Kiryana",
      ownerId: OWNER_UID,
      memberUids: [OWNER_UID, CASHIER_UID],
      managerUids: [OWNER_UID],
      address: "Lahore",
      phone: "03001234567",
      plan: "free",
      currency: "Rs",
      taxRate: 0,
      receiptFooter: "Shukriya!",
      showReceiptSponsor: true,
      allowNegativeStockOverride: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("shops").doc(SHOP_ID).collection("members").doc(OWNER_UID).set({
      shopId: SHOP_ID,
      uid: OWNER_UID,
      name: "Owner",
      email: "owner@test.com",
      role: "owner",
      active: true,
      memberUids: [OWNER_UID, CASHIER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("shops").doc(SHOP_ID).collection("members").doc(CASHIER_UID).set({
      shopId: SHOP_ID,
      uid: CASHIER_UID,
      name: "Cashier",
      email: "cashier@test.com",
      role: "cashier",
      active: true,
      memberUids: [OWNER_UID, CASHIER_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("shops").doc(SHOP_ID).collection("products").doc("prod_1").set({
      shopId: SHOP_ID,
      memberUids: [OWNER_UID, CASHIER_UID],
      name: "Tapal Danedar 900g",
      nameUrdu: "ٹپال دانے دار",
      barcode: "8961003010101",
      category: "Tea & Beverages",
      purchasePrice: 1200,
      salePrice: 1380,
      stockQty: 25,
      lowStockLimit: 5,
      unit: "pcs",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("shops").doc(SHOP_ID).collection("expenses").doc("exp_1").set({
      shopId: SHOP_ID,
      managerUids: [OWNER_UID],
      category: "electricity",
      amount: 4500,
      note: "WAPDA bill",
      createdAt: now,
    });
  });
}

test("Unauthenticated user cannot read shops or products", async () => {
  await seedShopWithOwnerAndCashier();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("shops").doc(SHOP_ID).get());
  await assertFails(unauthDb.collection("shops").doc(SHOP_ID).collection("products").get());
});

test("Outsider cannot read shop, products, or query unfiltered shops", async () => {
  await seedShopWithOwnerAndCashier();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(outsiderDb.collection("shops").doc(SHOP_ID).get());
  await assertFails(outsiderDb.collection("shops").get());
  await assertFails(
    outsiderDb.collection("shops").doc(SHOP_ID).collection("products")
      .where("memberUids", "array-contains", OWNER_UID).get()
  );
});

test("Owner can list their shop, read expenses, and create a product", async () => {
  await seedShopWithOwnerAndCashier();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(
    ownerDb.collection("shops").where("memberUids", "array-contains", OWNER_UID).get()
  );
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_ID).collection("expenses")
      .where("managerUids", "array-contains", OWNER_UID).get()
  );
  const now = new Date();
  await assertSucceeds(
    ownerDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_2").set({
      shopId: SHOP_ID,
      memberUids: [OWNER_UID, CASHIER_UID],
      name: "Olpers Milk 1L",
      nameUrdu: "اولپرز دودھ",
      barcode: "8964000111222",
      category: "Dairy",
      purchasePrice: 280,
      salePrice: 320,
      stockQty: 40,
      lowStockLimit: 10,
      unit: "ltr",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Negative price is rejected by product schema validation", async () => {
  await seedShopWithOwnerAndCashier();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const now = new Date();
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_bad").set({
      shopId: SHOP_ID,
      memberUids: [OWNER_UID, CASHIER_UID],
      name: "Bad Item",
      category: "General",
      purchasePrice: -10,
      salePrice: 50,
      stockQty: 5,
      lowStockLimit: 1,
      unit: "pcs",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Cashier can list products and create sales, but CANNOT read/write expenses or update product prices", async () => {
  await seedShopWithOwnerAndCashier();
  const cashierDb = testEnv.authenticatedContext(CASHIER_UID).firestore();
  await assertSucceeds(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products")
      .where("memberUids", "array-contains", CASHIER_UID).get()
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("expenses").doc("exp_1").get()
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("expenses").get()
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("expenses")
      .where("managerUids", "array-contains", OWNER_UID).get()
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("expenses").doc("exp_cashier").set({
      shopId: SHOP_ID,
      managerUids: [CASHIER_UID],
      category: "other",
      amount: 100,
      note: "Attempt",
      createdAt: new Date(),
    })
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_1").update({
      salePrice: 10,
      updatedAt: new Date(),
    })
  );
  // Cashier CAN decrement stockQty on sale
  await assertSucceeds(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_1").update({
      stockQty: 24,
      updatedAt: new Date(),
    })
  );
  // Cashier CAN upload/update product picture on an existing product
  await assertSucceeds(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_1").update({
      imageUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      updatedAt: new Date(),
    })
  );
  // Cashier CAN quick-add a scanned product with purchasePrice == 0 and a picture, but CANNOT set purchasePrice > 0
  await assertSucceeds(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_cashier_ok").set({
      shopId: SHOP_ID,
      memberUids: [OWNER_UID, CASHIER_UID],
      name: "Cashier Scanned Item",
      nameUrdu: "",
      barcode: "896111222333",
      category: "General",
      purchasePrice: 0,
      salePrice: 150,
      stockQty: 10,
      lowStockLimit: 5,
      unit: "pcs",
      imageUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    cashierDb.collection("shops").doc(SHOP_ID).collection("products").doc("prod_cashier_bad").set({
      shopId: SHOP_ID,
      memberUids: [OWNER_UID, CASHIER_UID],
      name: "Cashier Cost Attempt",
      category: "General",
      purchasePrice: 90,
      salePrice: 150,
      stockQty: 10,
      lowStockLimit: 5,
      unit: "pcs",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Owner cannot self-upgrade shop plan to pro", async () => {
  await seedShopWithOwnerAndCashier();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    ownerDb.collection("shops").doc(SHOP_ID).update({
      plan: "pro",
      updatedAt: new Date(),
    })
  );
});

test("Ads: signed-in users can read active ads and increment clicks, but cannot create or deactivate ads", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("ads").doc("ad_1").set({
      title: "Olpers Trade Promo",
      imageUrl: "",
      linkUrl: "https://frieslandcampina.com.pk",
      placement: "dashboard",
      active: true,
      startDate: "2026-01-01",
      endDate: "2026-12-31",
      sponsor: "Olpers",
      impressions: 0,
      clicks: 0,
      createdAt: now,
    });
  });

  const userDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertFails(
    userDb.collection("ads").doc("ad_2").set({
      title: "Client Ad Attempt",
      linkUrl: "https://example.com",
      placement: "dashboard",
      active: true,
      startDate: "2026-01-01",
      endDate: "2026-12-31",
      sponsor: "Fake",
      impressions: 0,
      clicks: 0,
      createdAt: now,
    })
  );

  await assertSucceeds(userDb.collection("ads").where("active", "==", true).get());
  await assertSucceeds(userDb.collection("ads").doc("ad_1").update({ clicks: 1 }));
  await assertFails(userDb.collection("ads").doc("ad_1").update({ active: false }));
});

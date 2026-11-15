# DukanPro — Shop Management & POS (Android)

**DukanPro** is a mobile-first Shop Management & Point-of-Sale (POS) Android application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **CameraX + ML Kit Barcode Scanning**, and **Cloud Firestore** with offline persistence for small and medium businesses in Pakistan (kiryana stores, mobile shops, medical stores, garments, and general stores).

---

## Core Features

- **Google Sign-In & Multi-Shop Role-Based Access**:
  - Sign in using Jetpack Credential Manager (`Google Sign-In`).
  - First sign-in creates a shop with the user as `owner`.
  - Owners can invite staff by email and assign `manager` or `cashier` roles:
    - **Cashier**: Fast POS billing, view products & customers, hold/resume bills, print/share receipts. Purchase prices, profits, expenses, suppliers, and financial reports are hidden and protected by Firestore rules.
    - **Manager**: Inventory, stock adjustments, suppliers, purchases, expenses, day closing, and reports.
    - **Owner**: Full access including shop settings, staff management, subscription/Pro upgrade, and CSV/JSON backup export.
- **Fast POS / Billing**:
  - Instant barcode input supporting both **USB/Bluetooth HID barcode scanners** (auto-focused Enter-to-add field) and **device camera scanning** (CameraX + ML Kit) that automatically adds the scanned product's name, price, and picture to the bill (or opens Quick Add to save & auto-add a new barcode).
  - Live English & Urdu product search, category chips, numeric quantity keypad, live discount (`Rs` or `%`), tax, grand total, and change calculation.
  - Payment methods: **Cash**, **JazzCash**, **EasyPaisa**, **Card**, and **Udhaar** (customer credit).
  - Atomic Firestore transactions for stock deduction, sale recording, and Udhaar khata balance updates.
  - **Hold Bill / Resume**, **Return / Exchange**, **58mm / 80mm Thermal PDF Receipt** (`PdfDocument` + Android `PrintManager`), and **WhatsApp Receipt Share**.
- **Inventory Management**:
  - Search, category filter, low-stock & expiring-soon badges, **Scan-to-Add / Scan-to-Edit**, product pictures via Android Photo Picker, Stock Adjustment reasons (`damage`, `correction`, `expired`), Barcode Sticker generator/share, and **CSV Import / Export**.
- **Customers & Udhaar Khata**:
  - Customer balances, full credit/payment ledger, atomic Receive Payment transactions, and Urdu/English WhatsApp reminders.
- **Suppliers, Purchases, Expenses, Day Closing & P&L Dashboard**:
  - 7-day sales chart, top 10 pending Udhaar customers, Daily Cash Register Closing, Supplier purchases, Daily expenses, and **Seed Demo Data** (20 Pakistani kiryana/general items + 5 sample customers).
- **Bilingual (English / Urdu RTL) & Theme Support**:
  - Live English / Urdu language switcher with full RTL layout direction (`LayoutDirection.Rtl`), Light/Dark theme toggle, `PKR (Rs)` currency, and `DD/MM/YYYY` date formatting.
- **Promotions & Pro Upgrade**:
  - Global `ads` collection supporting `dashboard` and `receipt` placements for `free` tier shops when online (never shown on the POS screen), with atomic impression/click tracking.
  - Owners can submit JazzCash/EasyPaisa Pro Upgrade requests directly from the app.

---

## Firebase Setup & Console Configuration

This Android project is configured for Firebase project **`winzone-49c5e`** using the provisioned `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` (named Firestore database ID: `ai-studio-android-dukanpro-eb1a64de-994b-4c1d-8765-417de6ec6ad0`).

1. **Firebase Authentication**:
   - In the [Firebase Console](https://console.firebase.google.com/project/winzone-49c5e/authentication/providers), ensure **Google** sign-in provider is enabled (automatically provisioned for this Android package `com.aistudio.dukanpro.pkxrmt`).
   - *(Optional)* If you add Email/Password or Firebase Storage later in a custom build, enable **Email/Password** under **Authentication → Sign-in method** and **Firebase Storage** under **Build → Storage** in the Firebase Console.
2. **Firestore Security Rules & Composite Indexes**:
   - Rules are defined in `firestore.rules` and verified against the local Firebase Emulator via `firestore.test.js` and `DukanProRepositoryRuleTest.kt`.
   - Composite indexes for multi-shop and role-scoped queries are declared in `firestore.indexes.json`. To deploy indexes manually with Firebase CLI:
     ```bash
     firebase deploy --only firestore:indexes --project winzone-49c5e
     ```
3. **Offline Persistence**:
   - Cloud Firestore uses `PersistentCacheSettings` so POS billing, inventory lookups, and Udhaar entries continue working offline and sync automatically when connectivity returns.

---

## Running Security Rules Tests Locally

```bash
# 1. Start Firestore & Auth emulators
firebase emulators:start --only firestore,auth --project demo-no-project

# 2. Tier 1 JS rules tests
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js

# 3. Tier 2 Android/Robolectric repository rules tests
gradle :app:testDebugUnitTest --tests '*Rule*'
```

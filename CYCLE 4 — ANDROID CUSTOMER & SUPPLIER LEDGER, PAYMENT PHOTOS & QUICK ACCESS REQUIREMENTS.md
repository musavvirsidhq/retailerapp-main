# CYCLE 4 — ANDROID CUSTOMER & SUPPLIER LEDGER, PAYMENT PHOTOS & QUICK ACCESS REQUIREMENTS

## 1. Cycle Overview

Cycle 4 is a **mobile-only (Android) cycle**. It makes the Android app faster to use every day on the shop floor. It covers:

- Renaming **Shop → Customer** and **Factory → Supplier** in the Android app
- A dashboard drill-down that lists customers with **pending money owed to you**, most recent first
- A **Customer Ledger** showing each sale, how much was collected, and the balance
- The same for suppliers: **Supplier Payables** and a **Supplier Ledger**
- **Photo attachments** when money is collected or given, and on sales and purchase bills
- A **photo viewer**, so bill and payment photos can be checked when a customer or supplier disputes an amount
- **Quick-action buttons** on the dashboard for Sale, Purchase, Collect Money and Pay Supplier
- **Most-used items** for quick access when making a sale or purchase

### 1.1 Scope

| In scope | Out of scope |
|---|---|
| Android app (`android/`) | Web frontend (`frontend/`) UI changes |
| New backend APIs and migrations the Android features need | Renaming DB tables, API paths or JSON fields |
| Company data isolation for all new data | Offline mode / sync |
| | iOS |

The implementation must extend the existing architecture (Go + chi + sqlc backend, Kotlin + Jetpack Compose + Retrofit Android app). It must not redesign existing services.

---

# 2. Rename Shop → Customer, Factory → Supplier

## 2.1 Requirement

Everywhere in the Android app, users must see **Customer** instead of Shop and **Supplier** instead of Factory.

## 2.2 Label Mapping

| Old label | New label |
|---|---|
| Shop / Shops | Customer / Customers |
| Factory / Factories | Supplier / Suppliers |
| Shop Dues | Customer Dues (To Receive) |
| Factory Payables | Supplier Dues (To Pay) |
| Add Shop | Add Customer |
| Add Factory | Add Supplier |
| Owner Name (shop) | Contact Name |
| Contact Person (factory) | Contact Name |

## 2.3 Where it applies

1. Navigation drawer items and their icons (`Screen.kt`): use a person/group icon for Customers and a local-shipping icon for Suppliers
2. Screen titles, form labels, buttons, empty states, error and snackbar messages
3. Dashboard cards
4. Sales screen ("Select Customer") and Purchases screen ("Select Supplier")
5. Payments screen party-type selector ("Customer" / "Supplier")
6. Bill detail screen and shared bill text (WhatsApp)
7. The PDF bill, **only** if it is generated for the Android share flow. The web PDF may keep its current wording.

## 2.4 Rules

1. This is a **display-only rename**. Backend table names (`shops`, `factories`), API paths (`/api/shops`, `/api/factories`), and `party_type` values (`shop`, `factory`) **must not change**. The web frontend and existing data depend on them.
2. The Customer/Supplier wording lives in one place (`ui/common/Terms.kt` and `ui/ledger/PartyKind.kt`), so a future wording change touches one file.
3. Kotlin class names (`Shop`, `Factory`, `ShopApi` …) may be renamed during the cycle, but this is optional. It must not change the JSON field mapping.

---

# 3. Dashboard — Customer Dues (Money To Receive)

## 3.1 Requirement

The dashboard shows a **Customers / To Receive** card with:

- Total pending amount across all customers
- Number of customers with a pending balance

Tapping the card opens the **Customer Dues** list.

## 3.2 Customer Dues List

Each row shows:

| Field | Example |
|---|---|
| Customer name | Sri Ganesh Traders |
| Area / phone | Tirur · 98xxxxxx21 |
| Pending amount | ₹12,450 |
| Last activity | 2 days ago (Sale ₹3,200) |
| Photo indicator | 📎 if the latest transaction has photos |

## 3.3 Sorting — "based on recent"

1. **Default sort: most recent activity first.** Activity is the latest of: last sale date, last payment collected date.
2. Other sort options, via a sort menu:
   - Highest pending amount
   - Oldest pending (longest since last payment)
   - Name A–Z
3. The chosen sort is remembered on the device.

## 3.4 Filters

1. Default: show only customers with **pending amount > 0**.
2. Toggle: "Show all customers" (includes zero and advance balances).
3. Search by customer name or phone.

## 3.5 Balance Rule

```
Pending = Opening Balance
        + Σ Sale total (non-cancelled)
        − Σ Amount paid at time of sale (non-cancelled)
        − Σ Payments collected
```

This must match the existing `/api/shops/{id}/balance` calculation. A negative result means an **advance** and is shown as "Advance ₹X" in green.

---

# 4. Customer Ledger (Customer Detail)

## 4.1 Requirement

Tapping a customer (from Customer Dues, or from the Customers screen) opens the **Customer Ledger**.

## 4.2 Header Summary

| Field | Meaning |
|---|---|
| Total Billed | Sum of all non-cancelled sale totals |
| Total Collected | Paid at sale + payments collected |
| Pending | Balance as per 3.5 |
| Last payment | Date and amount of the most recent collection |

Header actions:

- **Call** (opens the dialer with the primary phone)
- **WhatsApp** (opens a chat with the pending-amount message)
- **Collect Money** (opens the Collect Money form with this customer preselected)
- **New Sale** (opens the Sales form with this customer preselected)

## 4.3 Transaction List

A chronological list, **newest first**, of every event affecting the balance:

| Entry type | Shows |
|---|---|
| Opening balance | Amount |
| Sale | Bill no., date, bill total, paid at sale, balance added, item count |
| Payment collected | Date, amount, mode (Cash / UPI / Bank / Cheque), notes |
| Cancelled sale | Shown struck-through with a "Cancelled" chip; does not affect totals |

Every row shows:

- **Running balance after that entry**
- 📎 photo count, if photos are attached

## 4.4 Row Actions

1. Tap a **Sale** row → opens the existing Bill Detail screen, which now also shows the bill's photos (see section 7).
2. Tap a **Payment** row → opens Payment Detail with its photos.

## 4.5 Filters

- Date range: This month / Last 3 months / This year / All (a Custom range picker is not built yet)
- Type: All / Sales only / Payments only
- Header totals **always reflect all-time** figures. A secondary line shows totals for the selected range.

## 4.6 Example

Customer: **Sri Ganesh Traders** — Pending ₹8,000

| Date | Entry | Amount | Paid / Collected | Balance |
|---|---|---|---|---|
| 28 Sep | Payment · UPI 📎1 | | ₹2,000 | ₹8,000 |
| 20 Sep | Sale S-0142 📎1 | ₹6,000 | ₹1,000 | ₹10,000 |
| 05 Sep | Sale S-0120 | ₹5,000 | ₹0 | ₹5,000 |
| 01 Sep | Opening balance | | | ₹0 |

---

# 5. Dashboard — Supplier Dues (Money To Pay)

## 5.1 Requirement

Same as sections 3 and 4, mirrored for suppliers:

- Dashboard card: **Suppliers / To Pay**, with the total payable and the supplier count
- Tapping it opens **Supplier Dues**, sorted by most recent activity (last purchase or last payment given)
- Tapping a supplier opens the **Supplier Ledger**

## 5.2 Supplier Ledger Differences

| Customer Ledger | Supplier Ledger |
|---|---|
| Sale | Purchase (bill no., supplier invoice no. if any) |
| Payment collected | Payment given |
| Total Billed | Total Purchased |
| Total Collected | Total Paid |
| Collect Money | Pay Supplier |
| New Sale | New Purchase |

## 5.3 Balance Rule

```
Payable = Σ Purchase total (non-cancelled)
        − Σ Amount paid at time of purchase (non-cancelled)
        − Σ Payments given
```

This must match the existing `/api/factories/{id}/balance`.

---

# 6. Photos on Money Collected / Given

## 6.1 Requirement

Every time money is **collected from a customer** or **given to a supplier**, the user can attach photos as proof, for example:

- A handwritten receipt or signed slip
- A UPI / bank transfer screenshot
- A cheque photo
- A photo of the cash handover note

## 6.2 Capture

1. The Collect Money and Pay Supplier forms have an **"Add Photo"** button with two choices:
   - **Camera** (take a photo now)
   - **Gallery** (pick existing images, including screenshots)
2. Up to **5 photos** per payment.
3. Selected photos show as thumbnails in the form. Each thumbnail can be removed before saving.
4. Photos are **optional** by default. A company setting ("Require photo for payments") may make at least one photo mandatory. This setting is optional for this cycle; see Phase 4.7.

## 6.3 Upload Behaviour

1. Before upload, the app compresses images to a maximum of **1600 px on the long edge, JPEG quality 80**. The target is under 500 KB each.
2. The payment is saved first. Photos then upload one by one with a progress indicator.
3. If a photo upload fails, the payment **stays saved**. The payment then shows a "⚠ 1 photo not uploaded — Retry" banner on its detail screen.
4. The user can **add photos later** to an existing payment from Payment Detail.

## 6.4 Rules

1. Photos are stored per company and must never be visible to other companies.
2. Staff can add photos. **Only a Company Admin can delete a photo.** Deletion is a soft delete, is recorded in the audit log with a reason, and follows the Cycle 2 rule "do not physically delete financial history".
3. Each photo records who uploaded it and when. The viewer shows this.
4. Accepted types: JPEG, PNG, WEBP. Server limit: 5 MB per file, the same as product images.

---

# 7. Photos on Sales and Purchase Bills

## 7.1 Requirement

The paper bill or delivery note is often what gets disputed. Users can attach photos to a **sale** or **purchase** bill in the same way:

1. When creating a sale or purchase, via the same "Add Photo" button
2. Later, from the Bill Detail screen

## 7.2 Rules

The rules in 6.2 – 6.4 apply unchanged: up to 5 photos, compression, admin-only soft delete, and audit.

A cancelled bill keeps its photos, and they stay viewable.

---

# 8. Checking Photos When a Customer or Supplier Disputes

## 8.1 Requirement

When a customer or supplier asks about an amount, the user must find the proof in **two taps or fewer** from the ledger.

## 8.2 Photo Viewer

1. Tap a 📎 row in a ledger, or a thumbnail on Bill/Payment Detail, to open a **full-screen viewer**.
2. The viewer supports pinch-to-zoom, double-tap zoom, and swiping between photos of the same transaction.
3. An overlay shows: transaction type, bill no. or payment date, amount, "Uploaded by X on date".
4. **Share** button: shares the photo through the Android share sheet (for example, WhatsApp it back to the customer).

## 8.3 "Show only entries with photos"

The ledger filter (4.5) gets an extra option, **"With photos only"**. It quickly narrows the list to entries that have proof.

---

# 9. Quick Actions on Dashboard

## 9.1 Requirement

The most frequent actions must be reachable with **one tap** from the dashboard.

## 9.2 Quick Action Buttons

A row of four large buttons at the top of the dashboard, above the summary cards:

| Button | Opens |
|---|---|
| **+ Sale** | New Sale form |
| **+ Purchase** | New Purchase form |
| **Collect** | Collect Money form (party type Customer) |
| **Pay** | Pay Supplier form (party type Supplier) |

## 9.3 Rules

1. Buttons respect Cycle 2 staff permissions:
   - Hide **+ Sale** and **Collect** for staff without sales access
   - Hide **+ Purchase** and **Pay** for staff without purchase access
2. When the subscription is expired, the buttons are disabled and show the existing expiry message.
3. A **floating action button (FAB)** on the Sales screen and the Purchases screen opens the new-entry form directly.
4. Optional (nice to have): an Android **app shortcut** (long-press the launcher icon) for "New Sale" and "New Purchase".

---

# 10. Most-Used Items (Quick Access)

## 10.1 Requirement

When creating a sale or purchase, the items used most often must be selectable without searching.

## 10.2 Frequent Items Strip

1. At the top of the item picker in the Sale and Purchase forms, show a **horizontal chip strip of the top 10 items**.
2. Tapping a chip adds the item with quantity 1. If the item is already in the bill, it increments the quantity.
3. Each chip shows the item name and current stock. Low-stock items get an amber dot.

## 10.3 Ranking

1. Ranking is **separate for sales and purchases**.
2. Score = number of bill lines containing the item in the **last 90 days**, among non-cancelled bills.
3. Ties are broken by the most recent use.
4. The ranking is company-wide; it is not per user.

## 10.4 Pinned Items

1. A user can **pin** an item (star icon in the product list or picker). Pinned items always appear first in the strip, before ranked items.
2. Pins are stored per company, so everyone sees the same pinned items. Only a Company Admin can pin or unpin.
3. Maximum 10 pinned items.

## 10.5 Customer-Specific Suggestion (nice to have)

When a customer is selected in a sale, show a second strip: **"Usually buys"**. It lists that customer's top 5 items from their last 10 sales.

---

# 11. New / Changed APIs

All new endpoints are inside the existing authenticated, company-scoped, subscription-gated router group.

## 11.1 Dues Lists

| Method | Path | Notes |
|---|---|---|
| GET | `/api/reports/customer-dues?sort=recent\|amount\|oldest\|name&include_zero=false&q=` | Rows: `id, name, phone, area, balance, last_sale_at, last_payment_at, last_activity_at` |
| GET | `/api/reports/supplier-dues?sort=…&include_zero=false&q=` | Same shape, for suppliers |

The existing `/api/reports/dashboard` response adds: `customer_due_total`, `customer_due_count`, `supplier_due_total`, `supplier_due_count`.

## 11.2 Ledgers

| Method | Path |
|---|---|
| GET | `/api/shops/{id}/ledger?from=&to=&type=all\|sale\|payment&with_photos=false` |
| GET | `/api/factories/{id}/ledger?from=&to=&type=all\|purchase\|payment&with_photos=false` |

Response:

```json
{
  "summary": { "total_billed": "11000.00", "total_collected": "3000.00", "pending": "8000.00",
               "last_payment_at": "2026-09-28", "last_payment_amount": "2000.00" },
  "range_summary": { "billed": "...", "collected": "..." },
  "entries": [
    { "type": "payment", "id": 91, "date": "2026-09-28", "ref": null,
      "amount": null, "paid": "2000.00", "mode": "UPI", "notes": "",
      "balance_after": "8000.00", "cancelled": false, "photo_count": 1 },
    { "type": "sale", "id": 142, "date": "2026-09-20", "ref": "S-0142",
      "amount": "6000.00", "paid": "1000.00", "balance_after": "10000.00",
      "cancelled": false, "photo_count": 1, "item_count": 4 }
  ]
}
```

The server computes the running balance, so the ledger does not load every transaction onto the phone.

## 11.3 Attachments (photos)

One generic endpoint set, where `{entity}` is `payments`, `sales` or `purchases`:

| Method | Path | Access |
|---|---|---|
| POST | `/api/{entity}/{id}/attachments` (multipart `file`) | Anyone who can view the entity; sales/purchase access rules apply |
| GET | `/api/{entity}/{id}/attachments` | Same |
| DELETE | `/api/{entity}/{id}/attachments/{attachmentId}` body `{ "reason": "…" }` | Company Admin only; soft delete + audit |

Files are stored under `ATTACHMENTS_DIR` (default `attachments/`), **outside** the public `/uploads` directory, and are served only by `GET /api/attachments/{id}/file`. That route requires a logged-in user from the same company with the matching sales or purchase permission. This is unlike product images, which are public.

## 11.4 Payments

| Method | Path | Change |
|---|---|---|
| GET | `/api/payments/{id}` | New — payment detail + attachments |
| POST | `/api/payments/` | Unchanged request; returns the new `ID` so photos can be uploaded next |

## 11.5 Frequent & Pinned Items

| Method | Path |
|---|---|
| GET | `/api/products/frequent?type=sale\|purchase&limit=10` → pinned first, then ranked |
| GET | `/api/products/frequent?type=sale&shop_id={id}&limit=5` → "Usually buys" (nice to have) |
| PUT | `/api/products/{id}/pin` / DELETE `/api/products/{id}/pin` (Company Admin) |

---

# 12. Database Changes

Migration `000004_ledger_attachments.up.sql`:

```sql
CREATE TABLE transaction_attachments (
    id SERIAL PRIMARY KEY,
    company_id INT NOT NULL REFERENCES companies(id),
    entity_type VARCHAR(10) NOT NULL CHECK (entity_type IN ('payment', 'sale', 'purchase')),
    entity_id INT NOT NULL,
    file_path TEXT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes INT NOT NULL,
    uploaded_by INT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    deleted_by INT REFERENCES users(id),
    delete_reason TEXT
);
CREATE INDEX idx_attachments_entity
    ON transaction_attachments(company_id, entity_type, entity_id)
    WHERE deleted_at IS NULL;

ALTER TABLE products ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;

-- Ledger and "recent" sorting performance
CREATE INDEX IF NOT EXISTS idx_sales_company_shop_date ON sales(company_id, shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_purchases_company_factory_date ON purchases(company_id, factory_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_company_party ON payments(company_id, party_type, party_id, payment_date DESC);
```

Provide the matching `.down.sql`.

---

# 13. Android Implementation Notes

| Area | Guidance |
|---|---|
| New screens | `ui/customers/CustomerDuesScreen`, `CustomerLedgerScreen`; `ui/suppliers/SupplierDuesScreen`, `SupplierLedgerScreen`; `ui/payments/PaymentDetailScreen`; `ui/common/PhotoViewerScreen`, `PhotoPickerRow` |
| Navigation | New routes `customer_ledger/{id}`, `supplier_ledger/{id}`, `payment/{id}`, `photos/{entity}/{id}?start=`; Sales/Purchases/Payments routes accept an optional preselected party id |
| Photo capture | `ActivityResultContracts.TakePicture` (FileProvider URI) + `PickMultipleVisualMedia(5)`. No storage permission needed on Android 13+ |
| Compression | Decode with sampling, scale to 1600 px, JPEG 80, on `Dispatchers.IO` |
| Upload | Retrofit `@Multipart`, one request per photo |
| Image loading | Coil, with the session cookie (reuse the existing OkHttp client from `NetworkModule`, so authenticated attachment URLs load) |
| Zoom | Compose `transformable` modifier, or a small zoomable-image library |
| Wording | Customer/Supplier labels live in `ui/common/Terms.kt` and `ui/ledger/PartyKind.kt` |

---

# 14. Permissions Summary

| Action | Company Admin | Staff (Sales) | Staff (Purchase) |
|---|---|---|---|
| View Customer Dues / Ledger | ✅ | ✅ | ❌ |
| View Supplier Dues / Ledger | ✅ | ❌ | ✅ |
| Collect money + add photos | ✅ | ✅ | ❌ |
| Pay supplier + add photos | ✅ | ❌ | ✅ |
| Add photos to sale bill | ✅ | ✅ | ❌ |
| Add photos to purchase bill | ✅ | ❌ | ✅ |
| Delete any photo | ✅ | ❌ | ❌ |
| Pin / unpin items | ✅ | ❌ | ❌ |

A staff member with both permissions gets both columns. The backend must enforce all of these, not only the UI.

---

# 15. Recommended Cycle 4 Development Phases

## Phase 4.1 — Rename
- `strings.xml` extraction, and Customer/Supplier labels and icons

## Phase 4.2 — Dues & Ledger backend
- `customer-dues`, `supplier-dues` and ledger endpoints; dashboard totals; indexes

## Phase 4.3 — Dues & Ledger Android
- Dues lists (sort, filter, search), ledger screens, row navigation, Call/WhatsApp actions

## Phase 4.4 — Attachments backend
- Migration, upload/list/soft-delete handlers, authenticated file serving, audit

## Phase 4.5 — Attachments Android
- Photo picker row, compression, upload with retry, Payment Detail, photos on Bill Detail, full-screen viewer, share

## Phase 4.6 — Quick access
- Dashboard quick-action buttons, FABs, frequent/pinned items API and chip strip

## Phase 4.7 — Nice to have
- "Usually buys" strip, launcher app shortcuts, "Require photo for payments" setting

## Phase 4.8 — Testing
- Ledger running balance equals the `/balance` endpoint for customers and suppliers, including cancelled bills and opening balance
- Company A cannot list, view or download Company B attachments (extend `multitenancy_test.go`)
- Staff without sales access gets 403 on customer ledger and sale attachments
- Photo delete by staff → 403; by admin → soft deleted, audit row written, still hidden from the list
- Payment saved when a photo upload fails, and retry works
- Frequent items exclude cancelled bills and respect the 90-day window
- Manual device test: camera capture, gallery multi-pick, rotation, a slow network

---

# 16. Acceptance Criteria (Summary)

1. The words "Shop" and "Factory" no longer appear anywhere in the Android UI.
2. From the dashboard, a user reaches a customer's pending amount and full transaction history in **2 taps**. The same applies to suppliers.
3. The customer list is ordered by most recent activity by default.
4. Every ledger row shows the running balance. The final balance equals the existing balance endpoint.
5. Users can attach up to 5 photos when collecting or paying money, and on sale/purchase bills. They can add more later.
6. From a disputed ledger entry, the proof photo opens full-screen in **1 tap** and can be shared.
7. New Sale / New Purchase / Collect / Pay are each **1 tap** from the dashboard and respect staff permissions.
8. The top 10 most-used items (plus pinned) appear as one-tap chips in the Sale and Purchase forms.
9. All new data and files are isolated per company and enforced by the backend.

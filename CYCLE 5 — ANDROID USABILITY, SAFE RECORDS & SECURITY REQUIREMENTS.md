# CYCLE 5 — ANDROID USABILITY, SAFE RECORDS & SECURITY REQUIREMENTS

## 1. Cycle Overview

Cycle 5 is an **Android-first cycle**. It comes out of a BA review and test pass of the Cycle 4 app. Cycle 4 added the features; Cycle 5 makes them **safe, quick and pleasant to use every day**. It covers:

- **HTTPS** for all app traffic (today passwords and data travel in plain text)
- **Archive instead of delete** for customers, suppliers and products, restricted to the Company Admin
- **Edit** customer, supplier and product details from the app
- A **bottom navigation bar** so the main jobs are always one tap away
- **Pull-to-refresh** and **date filters** on the Sales, Purchases and Payments lists
- **Share the bill on WhatsApp** straight after saving a sale
- **Payment reminders** to overdue customers from the Customer Dues screen
- **Barcode / SKU scanning** when adding items to a bill
- The items left over from Cycle 4: **launcher shortcuts**, **custom date range** in the ledger, **"Photo required for payments"** setting
- An **adaptive launcher icon** and **library upgrades**

### 1.1 Scope

| In scope | Out of scope |
|---|---|
| Android app (`android/`) | Web frontend (`frontend/`) UI changes, except where noted in 3.6 |
| Backend changes the Android features need (archive, list filters, setting) | Renaming DB tables, API paths or JSON fields |
| Server HTTPS setup (nginx + certificate) | Offline mode / sync |
| | iOS |
| | Automatic (unattended) WhatsApp sending |

The implementation must extend the existing architecture (Go + chi + sqlc backend, Kotlin + Jetpack Compose + Retrofit Android app). It must not redesign existing services.

### 1.2 Already fixed before Cycle 5 (BA review, 1 Oct 2026)

These were found in the review and fixed in the Android code. They are listed here so testing in Phase 5.9 covers them; no further build work is needed.

| Area | Fix |
|---|---|
| Bill Detail | Crash when opening a bill offline; Share PDF failing (read on the main thread); share/cancel errors now shown; money shown as `₹1,234.56` instead of raw decimals |
| Session | An expired login now returns the user to the login screen instead of leaving every screen on "unauthorized" |
| Sale / Purchase forms | No default customer/supplier (must be chosen); Cash sale shows "Paid in full"; overpayment blocked; per-line "Only X in stock" warning; reason shown when Save is disabled; "Discard changes?" on back |
| Collect / Pay form | "Full" button fills the pending amount; warning when the amount exceeds what is pending (becomes an advance) |
| Pickers | Customer, supplier and product chosen from a searchable dialog |
| Lists | Search on Customers, Suppliers and Products |
| Errors | Delete/disable failures now shown; database foreign-key errors shown as plain words |
| Small items | Ledger hides Call/WhatsApp when there is no phone; login show-password toggle and keyboard "Done" logs in; drawer shows "Company Admin" instead of `COMPANY_ADMIN` |

---

# 2. HTTPS for All App Traffic

## 2.1 Requirement

The app talks to `http://13.215.157.19/` in plain text. Usernames, passwords, the session cookie, prices, dues and proof photos can be read by anyone on the same Wi-Fi or network path. **All traffic must move to HTTPS before more shops are onboarded.**

## 2.2 Server

1. Point a domain (for example `api.<company-domain>`) at the server.
2. Issue a certificate with Let's Encrypt (`certbot --nginx`) on the existing nginx. Set up auto-renewal.
3. Redirect port 80 → 443.
4. Mark the session cookie `Secure` once HTTPS is live.
5. Keep the Cycle 4 `client_max_body_size 6m;` in the new `server { listen 443 … }` block.

## 2.3 Android

1. Change `NetworkModule.BASE_URL` to the `https://` domain.
2. Delete the cleartext `domain-config` block in `res/xml/network_security_config.xml`.
3. Set the `HttpLoggingInterceptor` to `NONE` in release builds (use `BuildConfig.DEBUG`).

## 2.4 Rules

1. The release APK must not be able to talk plain HTTP to the backend.
2. Existing users are logged out once by the switch (the old cookie belongs to the old host). They just log in again.

---

# 3. Archive Instead of Delete

## 3.1 Problem

Customers, suppliers and products are removed with a hard `DELETE`, and **any staff member** can do it:

- A customer with bills cannot be deleted (database refuses). Users see an error and don't know what to do.
- A customer with **only payments or an opening balance** *can* be deleted. Their dues disappear from the dashboard and the payments are left pointing at nothing. This breaks the Cycle 2 rule "do not physically delete financial history".

## 3.2 Requirement

Replace delete with **Archive**:

1. An archived customer/supplier/product is hidden from lists, pickers, dues and quick-item chips.
2. It **stays** in old bills, ledgers, payments and reports, with its name shown as normal plus an "Archived" tag.
3. It can be **restored**.
4. **Only a Company Admin** can archive or restore.

## 3.3 Rules

1. A customer or supplier with a **non-zero balance** cannot be archived. Message: "Settle the ₹X balance before archiving." (Avoids hiding money that is still owed.)
2. A product with **stock > 0** can be archived, with a warning in the confirm dialog: "X units in stock will no longer be sellable."
3. New sales, purchases and payments must **reject** an archived party or product (backend check, 400).
4. SKUs are unique per company (`UNIQUE (company_id, sku)`). An archived product keeps its SKU, so creating a new product with that SKU fails. Message: "SKU already used by archived product <name> — restore it instead."
5. Archive and restore are written to the audit log.

## 3.4 Android

1. Remove the red trash icon from every list row (easy to hit by mistake).
2. Add **Archive** to the overflow menu (⋮) of the Ledger screen (customer/supplier) and the product edit screen. Admin only.
3. Each list (Customers, Suppliers, Products) gets a "Show archived" option in its overflow menu. Archived rows show greyed, with a **Restore** action.

## 3.5 APIs

| Method | Path | Change |
|---|---|---|
| DELETE | `/api/shops/{id}`, `/api/factories/{id}`, `/api/products/{id}` | Now archives (soft delete). Company Admin only. Rules 3.3.1–3.3.2 |
| POST | `/api/shops/{id}/restore`, `/api/factories/{id}/restore`, `/api/products/{id}/restore` | New. Company Admin only |
| GET | `/api/shops`, `/api/factories`, `/api/products` | Excludes archived by default; `?include_archived=true` returns all with `archived_at` |

Keeping the same `DELETE` path means the web frontend keeps working with no change. The web gets archive behaviour automatically.

## 3.6 Web note

The web delete button keeps working (it now archives). Showing/restoring archived records on the web is out of scope for this cycle.

---

# 4. Edit Customer, Supplier and Product

## 4.1 Requirement

The app can add but not edit. Today, fixing a typo in a phone number means deleting and re-creating the record, which loses its history. The backend `PUT` endpoints already exist; this is **Android-only** work.

## 4.2 Where

| Record | Opens from | Editable fields |
|---|---|---|
| Customer | Ledger screen ⋮ → Edit | Name, Contact name, Primary phone, Secondary phone, Area |
| Supplier | Ledger screen ⋮ → Edit | Name, Contact name, Primary phone, Secondary phone, Address |
| Product | Tap a product in Products | Name, SKU, Unit, Category, Subcategory, Selling price |

## 4.3 Rules

1. **Opening balance is not editable** (the `PUT` endpoints do not accept it; it is financial history). Corrections go through a payment or a note.
2. **Stock is not editable** here. It only changes through purchases, sales and cancellations.
3. Changing a product's selling price affects **new** bills only. Show this under the field.
4. The edit form reuses the add dialog layout, pre-filled, as a full screen (dialogs are cramped with six fields).
5. Permission: see **Open question Q1**.

---

# 5. Bottom Navigation Bar

## 5.1 Requirement

Every screen today is behind the drawer (☰), which needs two taps and is hard to reach one-handed. Replace the drawer as the main navigation with a **bottom bar** of the most-used destinations.

## 5.2 Tabs

| Tab | Opens | Shown to |
|---|---|---|
| **Home** | Dashboard | Everyone |
| **Sales** | Sales list (FAB = new sale) | Sales access |
| **Dues** | Customer Dues; a toggle at the top switches to Supplier Dues | Sales or purchase access |
| **Purchases** | Purchases list (FAB = new purchase) | Purchase access |
| **More** | A menu: Customers, Suppliers, Products, Categories, Payments, Staff (admin), Logout | Everyone |

## 5.3 Rules

1. Tabs follow the same permission rules as the Cycle 4 quick actions. A staff member with only sales access sees Home, Sales, Dues, More.
2. Each tab keeps its own back stack and scroll position (standard Compose Navigation `saveState`/`restoreState`).
3. The bar is hidden on full-screen forms (New Sale, Collect, etc.) and the photo viewer.
4. The Super Admin keeps the current single-screen layout (no bottom bar).

---

# 6. Lists: Pull-to-Refresh, Date Filters, Paging

## 6.1 Requirement

The Sales, Purchases and Payments lists load **every record ever**, and there is no way to see only today's or this week's entries. As data grows the screens get slow and the list becomes unusable.

## 6.2 Android

1. **Pull-to-refresh** on every list screen (Dashboard, Sales, Purchases, Payments, Customers, Suppliers, Products, Dues). Use M3 `PullToRefreshBox`.
2. Date filter chips on Sales, Purchases and Payments: **Today / This week / This month / All**. Default: **This month**.
3. A summary line under the chips: "23 bills · ₹1,42,300" for the selected range.
4. Search on Sales/Purchases by bill number or customer/supplier name.
5. Load 50 at a time and fetch more on scroll.

## 6.3 APIs

| Method | Path | Change |
|---|---|---|
| GET | `/api/sales?from=&to=&q=&limit=&offset=` | New optional params |
| GET | `/api/purchases?from=&to=&q=&limit=&offset=` | Same |
| GET | `/api/payments?from=&to=&party_type=&limit=&offset=` | Same |

With no parameters the response is **unchanged**, so the web frontend keeps working. With `limit` set, the response adds a header `X-Total-Count` and `X-Total-Amount` for the summary line.

---

# 7. Share Bill on WhatsApp After a Sale

## 7.1 Requirement

After saving a sale the user lands back on the list. Sending the bill to the customer means finding the sale, opening it and tapping Share. Make it part of the save flow.

## 7.2 Flow

1. After **Create sale** succeeds, show a bottom sheet: **"Sale S-0142 saved · ₹6,000"** with buttons:
   - **WhatsApp bill** — opens WhatsApp to the customer's number with a text summary, and the PDF attached via the share sheet
   - **Share PDF** — the existing share sheet
   - **Done**
2. Text summary: "Hello <name>, your bill S-0142 for ₹6,000 dated 28 Sep. Paid ₹1,000. Total pending with us: ₹10,000. Thank you, <company>."
3. The same sheet appears after **Collect money**, with a receipt message: "Received ₹2,000 by UPI on 28 Sep. Pending: ₹8,000."
4. If the customer has no phone, hide the WhatsApp button.

## 7.3 Rules

1. Uses the existing PDF endpoint; no backend change.
2. Wording lives in one place (`ui/common/Messages.kt`) so it can be translated later.

---

# 8. Payment Reminders from Customer Dues

## 8.1 Requirement

Following up on dues is a daily job. Today the user must open each customer's ledger and tap WhatsApp one by one.

## 8.2 Flow

1. Customer Dues gets a **"Send reminders"** button.
2. It opens a checklist of customers with pending > 0, sorted by **oldest pending** first, all ticked. The user can untick.
3. Tapping **Start** opens WhatsApp for the first customer with the reminder text pre-filled. When the user returns to the app, it moves to the next customer automatically, showing "3 of 12".
4. Each customer reminded today shows "Reminded today" on the Dues row.

## 8.3 Rules

1. WhatsApp does not allow apps to send without the user tapping Send. The flow opens each chat; it does **not** send automatically.
2. "Reminded today" is stored **on the device** (SharedPreferences, keyed by company + customer + date). See **Open question Q3** for a server-side log.
3. Customers without a phone number are listed but skipped, with "No phone" shown.

---

# 9. Barcode / SKU Scanner

## 9.1 Requirement

Adding items by typing is slow at the counter. Add a **scan** button to the Sale and Purchase forms.

## 9.2 Flow

1. A scan icon next to **+ Add item**.
2. Scanning a code that matches a product **SKU** exactly adds it like a quick-item chip (quantity 1, or +1 if already on the bill).
3. No match: "No product with code 8901234567890" and buttons **Search** (opens the product picker with the code typed in) and, for admins, **Add product** (opens the add form with the SKU pre-filled).
4. The scanner stays open for continuous scanning until the user closes it. Each hit gives a short vibration.

## 9.3 Implementation note

Use the **Google code scanner** (`com.google.android.gms:play-services-code-scanner`). It runs in Google Play services and **needs no camera permission** in the app. No backend change; matching uses the product list already loaded.

---

# 10. Carried Over from Cycle 4

## 10.1 Launcher Shortcuts

Long-press the app icon → **New sale**, **New purchase**, **Collect money**.

1. Static shortcuts in `res/xml/shortcuts.xml`, each launching `MainActivity` with an extra (`shortcut=new_sale` …).
2. `MainActivity` passes it to `MainScreen`, which navigates to `newSaleRoute()` etc. after login.
3. Shortcuts the user has no permission for open the Dashboard with a message instead.
4. If the user is logged out, open login first, then the form.

## 10.2 Custom Date Range in the Ledger

Add **Custom…** to the ledger's date chips. It opens the M3 `DateRangePicker`. The chip then shows the range, e.g. "1 Sep – 15 Sep". The backend already accepts `from`/`to`.

## 10.3 "Photo Required for Payments" Setting

1. New company setting **Require photo for payments**, default off, toggled by the Company Admin (Staff screen → Settings section).
2. When on, the Collect / Pay forms disable **Save** until at least one photo is added: "Add a receipt photo to save (required by your admin)."
3. Enforced **in the app**. The backend cannot enforce it, because photos upload after the payment is created (Cycle 4 6.3). The ledger already shows which payments have no 📎.

| Method | Path | Change |
|---|---|---|
| GET | `/api/company/settings` | New: `{ "require_payment_photo": false }` |
| PUT | `/api/company/settings` | New. Company Admin only |

---

# 11. Look and Feel

## 11.1 Adaptive Launcher Icon

The launcher icon is a single square PNG also used as the round icon. Many phones crop it into a circle and cut off the edges (Android Lint: `IconLauncherShape`).

1. Add `mipmap-anydpi-v26/ic_launcher.xml` with a separate **foreground** (logo, inside the 66 dp safe zone) and **background** (solid brand colour) layer.
2. Add a monochrome layer for Android 13 themed icons.
3. Needs a design asset: the logo as an SVG or a 432 × 432 px PNG on transparency.

## 11.2 Consistency Pass

1. One money format everywhere (`money()`), one date format (`displayDate()`).
2. Every destructive action (cancel bill, archive, delete photo) uses the same red confirm dialog with a reason field.
3. Empty states tell the user what to do next ("No sales this month. Tap + to make one.").

---

# 12. Library Upgrades

Lint reports these as outdated. Upgrade, then re-test the whole app (Phase 5.9).

| Library | Now | Target |
|---|---|---|
| Compose BOM | 2024.12.01 | 2026.09.00 |
| navigation-compose | 2.8.5 | 2.10.x |
| lifecycle-* | 2.8.7 | 2.11.x |
| activity-compose | 1.9.3 | 1.13.x |
| core-ktx | 1.15.0 | 1.19.x |
| exifinterface | 1.3.7 | 1.4.x |

Also upgrade the Gradle wrapper (8.9) so the project builds with the JDK bundled in current Android Studio (JDK 25). Today it needs a separate JDK 21.

---

# 13. Database Changes

Migration `000005_archive_and_settings.up.sql`:

```sql
ALTER TABLE shops     ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);
ALTER TABLE factories ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);
ALTER TABLE products  ADD COLUMN archived_at TIMESTAMPTZ, ADD COLUMN archived_by INT REFERENCES users(id);

ALTER TABLE companies ADD COLUMN require_payment_photo BOOLEAN NOT NULL DEFAULT false;

-- List filters and paging (section 6)
CREATE INDEX IF NOT EXISTS idx_sales_company_date     ON sales(company_id, sale_date DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_purchases_company_date ON purchases(company_id, purchase_date DESC, id DESC);
```

Provide the matching `.down.sql`.

---

# 14. Android Implementation Notes

| Area | Guidance |
|---|---|
| New screens | `ui/shops/EditShopScreen`, `ui/factories/EditFactoryScreen`, `ui/products/EditProductScreen`, `ui/ledger/RemindersScreen`, `ui/common/SavedSheet` (post-save share sheet) |
| Navigation | Replace the drawer in `MainScreen.kt` with `NavigationBar`; drawer items move into the **More** tab. Routes `edit_shop/{id}`, `edit_factory/{id}`, `edit_product/{id}`, `reminders` |
| Archive | `Shop`, `Factory`, `Product` models gain nullable `archived_at`; pickers and quick items filter it out |
| Lists | Paging with a simple `offset` in each ViewModel; `PullToRefreshBox` around each list |
| Scanner | `GmsBarcodeScanning.getClient(context).startScan()` |
| Shortcuts | `res/xml/shortcuts.xml` + `<meta-data android:name="android.app.shortcuts">` in the manifest |
| Wording | Customer/Supplier labels stay in `Terms.kt` / `PartyKind.kt`; WhatsApp texts in `Messages.kt` |

---

# 15. Permissions Summary

| Action | Company Admin | Staff (Sales) | Staff (Purchase) |
|---|---|---|---|
| Archive / restore customer, supplier, product | ✅ | ❌ | ❌ |
| Edit customer | ✅ | see Q1 | ❌ |
| Edit supplier | ✅ | ❌ | see Q1 |
| Edit product (incl. selling price) | ✅ | see Q1 | see Q1 |
| Send reminders | ✅ | ✅ | ❌ |
| Share bill / receipt on WhatsApp | ✅ | ✅ | ✅ (purchases) |
| Change "Require photo" setting | ✅ | ❌ | ❌ |

The backend must enforce archive/restore and settings, not only the UI.

---

# 16. Open Questions (for the product owner)

| # | Question | Recommendation |
|---|---|---|
| Q1 | Who may **edit** records? The backend lets any staff edit today, including a product's selling price. | Staff may edit contact details of the parties they can access; **only the admin edits products** (price changes affect margins and the below-cost check). |
| Q2 | Default date filter on lists: "This month" or "Today"? | This month. Today is often empty in the morning and looks broken. |
| Q3 | Should reminders be logged on the server (who reminded whom, when) so all staff see it? | Not this cycle. Device-only is enough to start; revisit if several staff chase the same customers. |
| Q4 | Domain name for HTTPS? | Needed before Phase 5.1 can start. |

---

# 17. Recommended Cycle 5 Development Phases

## Phase 5.1 — HTTPS (blocks release)
- Domain, certificate, redirect, `Secure` cookie; Android base URL + remove cleartext; release logging off

## Phase 5.2 — Archive backend
- Migration, archive/restore handlers, admin-only, balance rule, reject archived in new bills/payments, audit

## Phase 5.3 — Archive + Edit Android
- Remove row trash icons, ⋮ menus, Show archived / Restore, edit screens

## Phase 5.4 — Bottom navigation
- `NavigationBar`, More tab, per-tab back stacks, permission-aware tabs

## Phase 5.5 — Lists backend + Android
- `from/to/q/limit/offset` params, totals headers, indexes; filter chips, summary line, paging, pull-to-refresh

## Phase 5.6 — WhatsApp flows
- Post-save sheet (sale + payment), reminders checklist and sequential flow

## Phase 5.7 — Quick input
- Barcode scanner, launcher shortcuts

## Phase 5.8 — Carry-overs and polish
- Ledger custom range, Require-photo setting, adaptive icon, consistency pass, library upgrades

## Phase 5.9 — Testing
- Release APK refuses plain HTTP; login works over HTTPS; old session logs out cleanly
- Archive: staff → 403; customer with balance → refused; archived party/product rejected on new bill; old ledgers and bills still show the archived name; restore works; audit rows written
- Web delete still works and now archives
- Edit: changed phone shows in ledger header and WhatsApp; price change affects only new bills; opening balance and stock not editable
- Lists without params return the same as before (web unaffected); paging, totals and date filters match the sum of bills
- Bottom bar matches permissions for admin / sales-only / purchase-only staff
- Reminders skip customers without phone; "Reminded today" resets the next day
- Scanner: match adds item, repeat scan increments, no match offers search/add
- Shortcuts work from a cold start and when logged out
- **Re-test the pre-Cycle 5 fixes (1.2)**: offline bill open, Share PDF, cash vs credit sale, stock warning, discard prompt, delete messages
- Manual device test: small screen (5"), large font setting, dark mode, slow network

---

# 18. Acceptance Criteria (Summary)

1. All app traffic uses HTTPS; the release APK cannot use plain HTTP.
2. Customers, suppliers and products are archived, never physically deleted; only a Company Admin can archive or restore; a record with a pending balance cannot be archived.
3. Customer, supplier and product details can be edited from the app; opening balance and stock cannot.
4. Home, Sales, Dues and Purchases are each **one tap** away from anywhere, following staff permissions.
5. Sales, Purchases and Payments lists open on "This month", load quickly with 1,000+ records, and refresh by pulling down.
6. After a sale or a collection, the bill/receipt can be sent on WhatsApp in **one tap**.
7. A user can work through reminders to all overdue customers without leaving the Dues flow.
8. Scanning a product's barcode adds it to the bill.
9. Long-pressing the app icon offers New sale, New purchase and Collect money.
10. The launcher icon displays uncropped on round-icon phones.

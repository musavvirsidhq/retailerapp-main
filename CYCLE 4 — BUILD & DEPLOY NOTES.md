# CYCLE 4 — BUILD & DEPLOY NOTES

Reference notes for the Cycle 4 Android work: what was built, how it was deployed to the
server on 29 Sep 2026, the problems we hit, and what is still left to do.

Requirements: `CYCLE 4 — ANDROID CUSTOMER & SUPPLIER LEDGER, PAYMENT PHOTOS & QUICK ACCESS REQUIREMENTS.md`
Original server setup: `Retailapp Deploy Runbook.pdf`

---

## 1. What was built

| Feature | Where to find it in the app |
|---|---|
| Shop → **Customer**, Factory → **Supplier** (app labels only; database, API and web keep the old names) | Everywhere |
| Quick buttons: **+ Sale, + Purchase, Collect, Pay** | Top of the dashboard |
| **Customers – To receive** / **Suppliers – To pay** cards | Dashboard |
| Dues list: most recent activity first, other sorts, search, "Show all" switch | Tap a dashboard card |
| **Ledger**: totals, every bill and payment with the balance after it, Call / WhatsApp / Collect / New sale, filters | Tap a customer or supplier |
| **Photos** (up to 5) on payments, sales and purchases; camera or gallery; retry if an upload fails | Payment and bill forms, bill detail, payment detail |
| Full-screen photo viewer: zoom, swipe, share | 📎 chip on a ledger row, or tap a thumbnail |
| **Most-used items** chips, **pinned** items (admin ★ in Products), "usually buys" per customer | New Sale / New Purchase forms |

### Behaviour changes
- Staff need **sales access** to record money collected from customers, and **purchase access** to pay suppliers (web app too).
- Payments to a customer or supplier from another company, and payments of zero, are rejected.
- A new sale or purchase starts with one empty item row.
- Deleting a photo: **Company Admin only**, a reason is required, the photo is hidden (not erased), and the deletion is written to the audit log.

### Not built yet
- Custom date-range picker in the ledger
- Launcher (long-press icon) shortcuts
- "Photo required for payments" setting

---

## 2. Code changes (summary)

**Backend (Go)**
- `migrations/000004_ledger_attachments.up.sql` / `.down.sql`: new `transaction_attachments` table, `products.pinned` column, 3 indexes
- `internal/db/queries/ledger.sql`, `attachments.sql` (+ generated `internal/db/*.sql.go` via `sqlc generate`)
- `internal/handlers/ledger.go`: dues lists and ledgers (money summed in paise so it matches `/balance` exactly)
- `internal/handlers/attachments.go`: photo upload, list, delete, and download (with login + permission checks)
- `internal/handlers/quick_items.go`: most-used items and pin/unpin
- `internal/handlers/payments.go`: permission and validation checks, `GET /api/payments/{id}`
- `internal/handlers/reports.go`: dashboard due totals
- `internal/middleware/auth.go`: `HasSalesAccess`, `HasPurchaseAccess`, `IsCompanyAdmin`
- `cmd/server/main.go`: new routes, `ATTACHMENTS_DIR` setting
- Tests: `internal/handlers/ledger_test.go`, `cycle4_test.go`

**New API routes**

| Route | Access |
|---|---|
| `GET /api/reports/customer-dues`, `GET /api/reports/supplier-dues` | sales / purchase access |
| `GET /api/shops/{id}/ledger`, `GET /api/factories/{id}/ledger` | sales / purchase access |
| `GET/POST /api/{sales,purchases,payments}/{id}/attachments` | matching permission |
| `DELETE /api/{…}/{id}/attachments/{attachmentId}` (body `{"reason": "…"}`) | Company Admin |
| `GET /api/attachments/{id}/file` | logged in, same company, matching permission |
| `GET /api/payments/{id}` | matching permission |
| `GET /api/products/frequent?type=sale\|purchase[&shop_id=]` | logged in |
| `PUT/DELETE /api/products/{id}/pin` | Company Admin |

**Android**
- New: `ui/ledger/` (dues and ledger screens), `ui/payments/PaymentDetailScreen.kt`, `ui/common/Photos.kt`, `PhotoViewerScreen.kt`, `QuickItems.kt`, `BillLines.kt`, `Format.kt`, `Terms.kt`, `data/model/Ledger.kt`, `data/remote/LedgerApi.kt`
- Changed: dashboard, sales, purchases, payments, products, customers/suppliers screens, bill detail, navigation, `NetworkModule.kt`
- New libraries: Coil (image loading), ExifInterface (keeps camera photos upright)
- Customer/Supplier wording lives in `ui/common/Terms.kt` and `ui/ledger/PartyKind.kt`

---

## 3. How it was tested (on the PC)

- 13 Go tests pass against a real Postgres database, run on a temporary test database that was deleted afterwards.
- 31-check smoke test against the real server: routes, photo privacy (not reachable without login or via `/uploads`), ledger totals matching `/balance`, pins, soft delete, and the audit record.
- Android: compiles and builds an APK. **Not yet tried on a phone or emulator.**

---

## 4. Server deploy — what we did (29 Sep 2026)

Server: `13.215.157.19` (EC2, Ubuntu). Repo at `/root/retailerapp-main`, run as root (`sudo -i`).

1. **Back up the database**
   ```bash
   sudo docker exec $(sudo docker compose ps -q db) pg_dump -U retailapp retailapp > /root/backup-$(date +%F).sql
   ```
2. **Pull the code:** `git pull`
3. **Check which migrations are applied** (`\dt`). On the server, 000002 and 000003 were **already applied**; only 000004 was needed.

   | Table present | Means |
   |---|---|
   | `companies` | 000002 applied (**never re-run 000002: it drops tables**) |
   | `product_images` | 000003 applied |
   | `transaction_attachments` | 000004 applied |
4. **Stop, migrate, build**
   ```bash
   systemctl stop retailapp
   sudo docker exec -i $(sudo docker compose ps -q db) psql -v ON_ERROR_STOP=1 -U retailapp -d retailapp < migrations/000004_ledger_attachments.up.sql
   go build -o retailapp ./cmd/server
   ```
5. **Photo folder** (outside the repo, so a re-clone can't delete it). Create it with `mkdir -p /var/lib/retailapp/attachments`, then add this line under `[Service]` in `/etc/systemd/system/retailapp.service`, right after `SESSION_KEY`:
   ```
   Environment=ATTACHMENTS_DIR=/var/lib/retailapp/attachments
   ```
6. **Start**
   ```bash
   systemctl daemon-reload
   systemctl start retailapp
   ```
7. **Nginx upload limit**: add `client_max_body_size 6m;` inside `server { … }` in `/etc/nginx/sites-available/retailapp`, then run `nginx -t && systemctl reload nginx`. The default is 1 MB, which would reject some photos with a 413 error.
8. **Verify**
   ```bash
   curl http://localhost:8080/health                                              # ok
   curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/products/frequent  # 401 = new code (404 = old)
   systemctl show retailapp -p Environment | grep -o 'ATTACHMENTS_DIR=[^ ]*'
   ```

Result: the backend is running the new code, connected to the database, with the photo folder set correctly.

### Problems we hit

| Symptom | Cause | Fix |
|---|---|---|
| `ERROR: column "description" of relation "products" already exists` | Ran 000003, which was already applied | Harmless (`ON_ERROR_STOP` stopped at the first line, nothing changed). Only run migrations whose table is missing. |
| `curl … 8080 … Could not connect` | The service was stopped in step 4 and never started again | `systemctl daemon-reload && systemctl start retailapp` |
| `Unknown key 'Environment' in section [Install]` / `Missing '=', ignoring line` | The `ATTACHMENTS_DIR` line was pasted at the bottom under `[Install]`, along with some guide text | Removed with `sed`, re-added under `[Service]` (backup kept at `/root/retailapp.service.bak`) |
| `status` looked fine at "29ms ago" | Too early to tell | Always re-check with `curl …/health` and `journalctl -u retailapp -n 20` |

---

## 5. Next time: short redeploy checklist

```bash
sudo -i && cd /root/retailerapp-main
sudo docker exec $(sudo docker compose ps -q db) pg_dump -U retailapp retailapp > /root/backup-$(date +%F).sql
git pull
ls migrations/                        # any new *.up.sql? check with \dt whether it's already applied
systemctl stop retailapp
# run only the NEW migration(s) here
go build -o retailapp ./cmd/server
systemctl start retailapp
curl http://localhost:8080/health && journalctl -u retailapp -n 20 --no-pager
```
If `frontend/` changed, also run `cd frontend && npm ci && npm run build`.

### Roll back Cycle 4 (if ever needed)
```bash
systemctl stop retailapp
sudo docker exec -i $(sudo docker compose ps -q db) psql -U retailapp -d retailapp < migrations/000004_ledger_attachments.down.sql
git checkout <previous-commit> && go build -o retailapp ./cmd/server
systemctl start retailapp
```
This removes the photo records; the files stay in `/var/lib/retailapp/attachments`.

---

## 6. Android build

In PowerShell on the PC. **Use JDK 21**: the JDK bundled with Android Studio is 25, which Gradle 8.9 can't run on.
```powershell
cd C:\Users\musav\Downloads\retailerapp-main\retailerapp-main\android
$env:JAVA_HOME = "C:\Users\musav\.jdks\jbr-21.0.11"
.\gradlew.bat assembleDebug
```
The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. The app talks to `http://13.215.157.19/` (set in `NetworkModule.kt`).
There is no release signing key yet. For a Play Store build, follow `android/RELEASE_SIGNING.md`.

---

## 7. To-do

- [ ] Confirm `client_max_body_size 6m;` is in the Nginx config
- [ ] Install the APK on a phone and run through: dashboard → customer ledger; Collect with a photo → 📎 opens; + Sale → "Most used" chips
- [ ] Change the `SESSION_KEY` (and, if you want to be thorough, the database password). Both were pasted into chat during the deploy.
- [ ] Back up `/var/lib/retailapp/attachments` together with the database backups
- [ ] Later: custom date range, launcher shortcuts, "photo required" setting

# CYCLE 5 — BUILD & DEPLOY NOTES

Same server routine as Cycle 4 (see `CYCLE 4 — BUILD & DEPLOY NOTES.md`, section 5). What is new in Cycle 5:

| Area | Change |
|---|---|
| Database | `migrations/000005_archive_and_settings.up.sql` / `.down.sql`: `archived_at` / `archived_by` on `shops`, `factories`, `products`; `companies.require_payment_photo`; 3 date indexes |
| Archive | `DELETE /api/shops|factories|products/{id}` now **archives** (Company Admin only; a customer/supplier must have a zero balance). New `POST …/{id}/restore`. Lists hide archived rows unless `?include_archived=true`. New sales, purchases and payments refuse archived parties/products. Archive/restore are written to `audit_log` (`SHOP_ARCHIVE`, `PRODUCT_RESTORE`, …). |
| Lists | `GET /api/sales`, `/api/purchases`: optional `from`, `to`, `q`, `limit`, `offset`. `GET /api/payments`: `from`, `to`, `party_type`, `limit`, `offset`. With `limit` the response has `X-Total-Count` and `X-Total-Amount`. **No params = same response as before** (web unaffected). |
| Settings | `GET /api/company/settings` (any company user), `PUT` (Company Admin): `{ "require_payment_photo": false }` |
| Session cookie | New env var `COOKIE_SECURE=true` marks the cookie `Secure`. Set it **only after HTTPS is live**, or logins over http:// stop working. |

## 1. Deploy the backend

1. **Back up** (as before):
   ```bash
   sudo docker exec $(sudo docker compose ps -q db) pg_dump -U retailapp retailapp > /root/backup-$(date +%F).sql
   ```
2. `git pull`
3. **Check 000005 isn't applied yet**: `\d shops` in psql — no `archived_at` column means it's needed.
4. **Stop, migrate, build, start**
   ```bash
   systemctl stop retailapp
   sudo docker exec -i $(sudo docker compose ps -q db) psql -v ON_ERROR_STOP=1 -U retailapp -d retailapp < migrations/000005_archive_and_settings.up.sql
   go build -o retailapp ./cmd/server
   systemctl start retailapp
   ```
5. **Verify**
   ```bash
   curl http://localhost:8080/health                                                   # ok
   curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/company/settings          # 401 = new code (404 = old)
   ```

Deploy this **before** shipping the Cycle 5 Android build: until the backend archives, the app's
"Archive" would still hard-delete.

## 2. HTTPS (section 2.2) — needs the domain (open question Q4)

1. Point the domain (e.g. `api.<company-domain>`) at the server's IP (DNS A record).
2. In `/etc/nginx/sites-available/retailapp` set `server_name api.<company-domain>;`, then:
   ```bash
   apt install -y certbot python3-certbot-nginx
   certbot --nginx -d api.<company-domain> --redirect     # issues the cert, adds listen 443 + the 80→443 redirect
   systemctl list-timers | grep certbot                     # auto-renewal timer is installed by the package
   certbot renew --dry-run
   ```
3. Check the new `server { listen 443 ssl; … }` block still has `client_max_body_size 6m;` (add it if certbot didn't copy it), then `nginx -t && systemctl reload nginx`.
4. Mark the cookie Secure: add under `[Service]` in `/etc/systemd/system/retailapp.service`
   ```
   Environment=COOKIE_SECURE=true
   ```
   then `systemctl daemon-reload && systemctl restart retailapp`.
5. Android: set `retailapp.releaseBaseUrl=https://api.<company-domain>/` in `android/gradle.properties` and build the release (see `android/RELEASE_SIGNING.md`). Existing users log in once again (the old cookie belongs to the old host).

## 3. Roll back Cycle 5

```bash
systemctl stop retailapp
sudo docker exec -i $(sudo docker compose ps -q db) psql -U retailapp -d retailapp < migrations/000005_archive_and_settings.down.sql
git checkout <previous-commit> && go build -o retailapp ./cmd/server
systemctl start retailapp
```
Rolling back **un-archives everything** (the columns are dropped).

## 4. Tests

`go test ./...` needs a Postgres with all 5 migrations (`DATABASE_URL`, default `localhost:55432`).
New: `internal/handlers/cycle5_test.go` (archive rules, staff 403, audit, SKU clash, list filters and totals, settings).

## 5. Android build

Builds with Android Studio's bundled JDK (Gradle 9.8, AGP 9.3.0) — no separate JDK 21 any more.

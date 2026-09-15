# Duggu Store — Production Launch Guide

Everything that stood between the app and a real launch, and exactly how to
switch each piece on. Code for all five items is already in this repo.

## 0. Run the tests (2 min)

```bash
./gradlew :app:testDebugUnitTest
```

25+ unit tests cover the money paths: cart totals, delivery-fee slabs,
minimum order, coupon maths, wallet ledger, ₹5/day ad quotes, order-status
parsing and notification mapping. They run on plain JVM — no device needed.

## 1. Complete backend schema (10 min)

In the Supabase Dashboard → SQL Editor, run these **in order** (all are
idempotent — safe to re-run):

1. `supabase_schema_fixed.sql` — base tables (if not already run)
2. `supabase_admin_stats.sql` — admin RPCs (if not already run)
3. **`supabase_production_addon.sql`** — everything else (NEW):
   - 15 missing tables: `sellers`, `seller_documents`, `delivery_partners`,
     `delivery_partner_documents`, `coupons`, `coupon_usages`, `campaigns`,
     `sponsored_slots`, `home_sections`, `home_section_categories`,
     `wallet_transactions`, `reviews`, `order_issues`, `notifications`,
     `device_tokens`
   - Missing columns on base tables (`referral_code`, `payment_method`,
     `image_urls`, `is_veg`, …) + referral-code backfill
   - 15 RPCs the app calls (`wallet_debit`, `use_coupon`, `apply_referral`,
     `home_layout`, `review_seller`, `resolve_order_issue`, …)
   - `trg_order_status_notify` — writes inbox rows on every order change
   - Storage buckets (`seller-documents`, `delivery-documents`,
     `product-images`) with locked-down policies

Verify: `SELECT * FROM stocked_categories();` and `SELECT home_layout();`
must run without errors.

## 2. Push notifications (20 min)

The app already receives pushes; this wires up the **sending** side.

```bash
# From the repo root (needs the Supabase CLI logged in):
supabase functions deploy send-push
supabase secrets set PUSH_WEBHOOK_SECRET='<long-random-string>'
# Paste the whole Firebase service-account JSON on ONE line:
supabase secrets set FCM_SERVICE_ACCOUNT='<json>'
```

(`FCM_SERVICE_ACCOUNT` comes from Firebase Console → Project settings →
Service accounts → Generate new private key.)

Then activate the DB trigger — in `supabase_production_addon.sql` find
`push_notification_to_device()` and replace the two placeholders:

- `https://YOUR_PROJECT_REF.supabase.co` → your real project URL
- `CHANGE_ME_PUSH_SECRET` → the same secret as above

…then re-run just that function block (section 15) in the SQL Editor.
`pg_net` must be enabled (Database → Extensions).

Test: place an order and move its status — a push should arrive even with
the app closed. Every push also lands in the in-app Notifications screen.

## 3. Razorpay online payments (30 min + account approval)

Without this step checkout stays exactly as before (COD + wallet) — nothing
breaks. To enable UPI/cards:

1. Create a [Razorpay](https://razorpay.com) account, finish KYC, and copy
   the **test-mode** Key Id (`rzp_test_…`) + Key Secret.
2. Deploy the two payment functions and set their secrets:
   ```bash
   supabase functions deploy create-razorpay-order verify-razorpay-payment
   supabase secrets set RAZORPAY_KEY_ID='rzp_test_...' RAZORPAY_KEY_SECRET='...'
   ```
3. Enable the app side — add to `local.properties` (and to the
   `RAZORPAY_KEY_ID` repo secret for CI builds):
   ```properties
   RAZORPAY_KEY_ID=rzp_test_...
   ```
4. Rebuild. Checkout now shows **Pay online** next to COD.
5. Test with Razorpay's test cards/UPI, then swap both secrets + the app key
   to **live** (`rzp_live_…`) for real money.

Security notes: the key secret lives only in the Edge Functions. The app
mints a server-side order, opens the Razorpay sheet, and places the store
order **only after** `verify-razorpay-payment` confirms the HMAC signature —
a faked client-side "success" can never create a paid order.

## 4. Crashlytics (5 min + one crash)

- Firebase Console → your project → **Crashlytics** → Enable.
- Rebuild and run the app once (the SDK registers on first launch).
- Crash reports and fatal stack traces now appear in the console, tagged
  with the signed-in user's id. The on-device `crash_logs` file backup is
  kept as well.

## 5. Pre-launch checklist

- [ ] Addon SQL run on the **production** project (not just staging)
- [ ] `send-push` deployed + secrets set + trigger placeholders replaced
- [ ] Razorpay live keys in place (functions + app + CI secret)
- [ ] Crashlytics enabled and seen receiving (force one test crash)
- [ ] `google-services.json` stays in this **private** repo only
- [ ] Release signing secrets set (`RELEASE_*`) so the Play build is signed
- [ ] `versionCode` bumped for every Play upload

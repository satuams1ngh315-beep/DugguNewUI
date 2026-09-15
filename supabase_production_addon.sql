-- ============================================
-- DUGGU STORE — Production Schema Addon (v2)
-- ============================================
-- WHAT:  Every table / column / RPC / trigger the app code references that is
--        NOT in supabase_schema_fixed.sql. Derived from the app source
--        (Models.kt + repositories) so a fresh database matches the app.
--
-- RUN ORDER (Supabase Dashboard → SQL Editor, in this order):
--   1. supabase_schema_fixed.sql        (base: 9 tables)
--   2. supabase_admin_stats.sql         (admin_table_stats, admin_db_health)
--   3. THIS FILE                       (everything below)
--
-- Idempotent: safe to run more than once (IF NOT EXISTS / OR REPLACE /
-- DROP ... IF EXISTS throughout).
-- ============================================

-- ---------------------------------------------------------------------------
-- 0. Columns missing on the BASE tables (the app reads/writes these)
-- ---------------------------------------------------------------------------
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_online BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS referral_code TEXT NOT NULL DEFAULT '';
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS referred_by UUID REFERENCES profiles(id) ON DELETE SET NULL;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'cod';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS wallet_used NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_id TEXT;

ALTER TABLE products ADD COLUMN IF NOT EXISTS image_urls TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_veg BOOLEAN;
-- Also in supabase_cutout_images.sql — repeated here so this file alone is sufficient.
ALTER TABLE products ADD COLUMN IF NOT EXISTS cutout_image_url TEXT;

-- Backfill referral codes for users created before this column existed.
DO $$
DECLARE r RECORD; code TEXT;
BEGIN
  FOR r IN SELECT id FROM profiles WHERE referral_code = '' LOOP
    LOOP
      code := 'DG' || upper(substr(md5(r.id::text || clock_timestamp()::text || random()::text), 1, 6));
      EXIT WHEN NOT EXISTS (SELECT 1 FROM profiles WHERE referral_code = code);
    END LOOP;
    UPDATE profiles SET referral_code = code WHERE id = r.id;
  END LOOP;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_referral_code ON profiles(referral_code);
CREATE INDEX IF NOT EXISTS idx_orders_payment_method ON orders(payment_method);

-- Signup trigger now also mints a referral code (base version didn't).
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    meta_role TEXT;
    code TEXT;
BEGIN
    meta_role := COALESCE(NEW.raw_user_meta_data->>'role', 'customer');
    IF meta_role NOT IN ('customer', 'seller', 'delivery', 'admin') THEN
        meta_role := 'customer';
    END IF;

    LOOP
        code := 'DG' || upper(substr(md5(NEW.id::text || clock_timestamp()::text || random()::text), 1, 6));
        EXIT WHEN NOT EXISTS (SELECT 1 FROM public.profiles WHERE referral_code = code);
    END LOOP;

    INSERT INTO public.profiles (id, full_name, phone, role, referral_code)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone', ''),
        meta_role,
        code
    )
    ON CONFLICT (id) DO NOTHING;

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Never let profile creation abort the auth signup itself.
    RETURN NEW;
END;
$$;

-- ============================================
-- 1. SELLERS + SELLER_DOCUMENTS (KYC)
--    sellers.id IS the user's id (profiles.id).
-- ============================================
CREATE TABLE IF NOT EXISTS sellers (
    id UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    business_name TEXT NOT NULL DEFAULT '',
    owner_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    phone TEXT,
    pan_number TEXT,
    gst_number TEXT,
    fssai_number TEXT,
    bank_account_number TEXT,
    bank_ifsc TEXT,
    upi_id TEXT,
    business_address TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION','UNDER_REVIEW','APPROVED','REJECTED','SUSPENDED')),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS seller_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
    doc_type TEXT NOT NULL
        CHECK (doc_type IN ('PAN','GST_CERTIFICATE','FSSAI_LICENSE','BANK_PROOF','ADDRESS_PROOF')),
    file_url TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (seller_id, doc_type)
);

-- Only the review RPCs may move status/rejection_reason: they set a
-- transaction-local bypass flag (see below); any direct client write trips
-- this trigger. This is the guard SellerOnboardingRepository's comment
-- refers to ("the table's own trigger would just revert them").
CREATE OR REPLACE FUNCTION guard_review_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.status IS DISTINCT FROM OLD.status
     OR NEW.rejection_reason IS DISTINCT FROM OLD.rejection_reason THEN
    IF coalesce(current_setting('app.review_bypass', true), '') <> '1' THEN
      RAISE EXCEPTION 'Only the review RPCs may change status/rejection_reason'
        USING ERRCODE = '42501';
    END IF;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_seller_status ON sellers;
CREATE TRIGGER trg_guard_seller_status
  BEFORE UPDATE ON sellers
  FOR EACH ROW EXECUTE FUNCTION guard_review_status();

-- ============================================
-- 2. DELIVERY_PARTNERS + DELIVERY_PARTNER_DOCUMENTS (rider KYC)
-- ============================================
CREATE TABLE IF NOT EXISTS delivery_partners (
    id UUID PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    phone TEXT,
    date_of_birth TEXT,
    licence_number TEXT,
    aadhaar_number TEXT,
    pan_number TEXT,
    vehicle_type TEXT CHECK (vehicle_type IN ('BIKE','SCOOTER','BICYCLE','EV_SCOOTER')),
    vehicle_number TEXT,
    bank_account_number TEXT,
    bank_ifsc TEXT,
    upi_id TEXT,
    city TEXT,
    address TEXT,
    emergency_contact_name TEXT,
    emergency_contact_phone TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION','UNDER_REVIEW','APPROVED','REJECTED','SUSPENDED')),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS delivery_partner_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES delivery_partners(id) ON DELETE CASCADE,
    doc_type TEXT NOT NULL
        CHECK (doc_type IN ('DRIVING_LICENCE','AADHAAR','PAN','VEHICLE_RC','VEHICLE_INSURANCE','BANK_PROOF')),
    file_url TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (partner_id, doc_type)
);

DROP TRIGGER IF EXISTS trg_guard_partner_status ON delivery_partners;
CREATE TRIGGER trg_guard_partner_status
  BEFORE UPDATE ON delivery_partners
  FOR EACH ROW EXECUTE FUNCTION guard_review_status();

-- ============================================
-- 3. COUPONS + COUPON_USAGES
-- ============================================
CREATE TABLE IF NOT EXISTS coupons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    discount_percent INTEGER NOT NULL DEFAULT 0 CHECK (discount_percent >= 0),
    max_discount INTEGER NOT NULL DEFAULT 0 CHECK (max_discount >= 0),
    min_order_value INTEGER NOT NULL DEFAULT 0 CHECK (min_order_value >= 0),
    expiry_label TEXT NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- One redemption per (coupon, user). Written ONLY by use_coupon() — there is
-- deliberately no INSERT policy for clients below.
CREATE TABLE IF NOT EXISTS coupon_usages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    coupon_id UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    used_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (coupon_id, user_id)
);

-- ============================================
-- 4. CAMPAIGNS (admin seasonal pushes, e.g. "Diwali Dhamaka")
-- ============================================
CREATE TABLE IF NOT EXISTS campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    label TEXT NOT NULL DEFAULT '',
    tint_hex TEXT NOT NULL DEFAULT '#F5A623',
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    cta_label TEXT NOT NULL DEFAULT 'SHOP NOW',
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- 5. SPONSORED_SLOTS (seller-paid home placements, ₹5/day)
--    fee_amount is GENERATED — clients can neither send nor forge it.
-- ============================================
CREATE TABLE IF NOT EXISTS sponsored_slots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    headline TEXT NOT NULL DEFAULT '',
    duration_days INTEGER NOT NULL DEFAULT 7 CHECK (duration_days > 0),
    fee_amount INTEGER GENERATED ALWAYS AS (duration_days * 5) STORED,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sponsored_live
  ON sponsored_slots(status, starts_at, ends_at) WHERE status = 'APPROVED';

-- ============================================
-- 6. HOME_SECTIONS + HOME_SECTION_CATEGORIES (admin-curated home layout)
-- ============================================
CREATE TABLE IF NOT EXISTS home_sections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title TEXT NOT NULL DEFAULT '',
    layout TEXT NOT NULL DEFAULT 'tile' CHECK (layout IN ('collage','tile')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS home_section_categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    section_id UUID NOT NULL REFERENCES home_sections(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (section_id, category_id)
);

-- ============================================
-- 7. WALLET_TRANSACTIONS (ledger — balance is derived, never stored)
--    Written ONLY by SECURITY DEFINER RPCs — no client INSERT policy.
-- ============================================
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL DEFAULT '',
    amount INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0),
    type TEXT NOT NULL DEFAULT 'CREDIT' CHECK (type IN ('CREDIT','DEBIT')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    refilled BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_wallet_user ON wallet_transactions(user_id, created_at DESC);

-- ============================================
-- 8. REVIEWS (one per user × order × product)
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL DEFAULT 0 CHECK (rating >= 1 AND rating <= 5),
    comment TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, order_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_product ON reviews(product_id);

-- ============================================
-- 9. ORDER_ISSUES (damage/missing/wrong-item reports)
--    Status moves ONLY via resolve_order_issue() — no client UPDATE policy.
-- ============================================
CREATE TABLE IF NOT EXISTS order_issues (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','approved','rejected')),
    refund_amount INTEGER NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_issues_order ON order_issues(order_id);
CREATE INDEX IF NOT EXISTS idx_issues_open ON order_issues(status) WHERE status = 'open';

-- ============================================
-- 10. NOTIFICATIONS (written server-side by the order trigger below)
-- ============================================
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type TEXT NOT NULL DEFAULT 'ORDER',
    title TEXT NOT NULL DEFAULT '',
    message TEXT NOT NULL DEFAULT '',
    is_read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, created_at DESC);

-- ============================================
-- 11. DEVICE_TOKENS (FCM tokens pushes are sent to)
-- ============================================
CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, token)
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens(user_id);

-- ============================================
-- 12. RLS
-- ============================================
ALTER TABLE sellers ENABLE ROW LEVEL SECURITY;
ALTER TABLE seller_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_partners ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_partner_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_usages ENABLE ROW LEVEL SECURITY;
ALTER TABLE campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE sponsored_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE home_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE home_section_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_issues ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_tokens ENABLE ROW LEVEL SECURITY;

-- --- sellers: owners manage their own application, admins everything ---
DROP POLICY IF EXISTS "Owners manage own seller application" ON sellers;
CREATE POLICY "Owners manage own seller application" ON sellers FOR ALL
  USING (id = auth.uid() OR public.is_admin())
  WITH CHECK (id = auth.uid() OR public.is_admin());

DROP POLICY IF EXISTS "Owners manage own seller docs" ON seller_documents;
CREATE POLICY "Owners manage own seller docs" ON seller_documents FOR ALL
  USING (seller_id = auth.uid() OR public.is_admin())
  WITH CHECK (seller_id = auth.uid() OR public.is_admin());

-- --- delivery partners: same shape ---
DROP POLICY IF EXISTS "Owners manage own partner application" ON delivery_partners;
CREATE POLICY "Owners manage own partner application" ON delivery_partners FOR ALL
  USING (id = auth.uid() OR public.is_admin())
  WITH CHECK (id = auth.uid() OR public.is_admin());

DROP POLICY IF EXISTS "Owners manage own partner docs" ON delivery_partner_documents;
CREATE POLICY "Owners manage own partner docs" ON delivery_partner_documents FOR ALL
  USING (partner_id = auth.uid() OR public.is_admin())
  WITH CHECK (partner_id = auth.uid() OR public.is_admin());

-- --- coupons: everyone reads active ones, admin writes, usages are RPC-only ---
DROP POLICY IF EXISTS "Anyone can view active coupons" ON coupons;
CREATE POLICY "Anyone can view active coupons" ON coupons FOR SELECT USING (is_active = true);
DROP POLICY IF EXISTS "Admin can manage coupons" ON coupons;
CREATE POLICY "Admin can manage coupons" ON coupons FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Users can view own coupon usages" ON coupon_usages;
CREATE POLICY "Users can view own coupon usages" ON coupon_usages FOR SELECT
  USING (user_id = auth.uid() OR public.is_admin());
-- NOTE: intentionally no INSERT/UPDATE/DELETE policy — use_coupon() writes.

-- --- campaigns: live-window trick — a plain select only returns running ones ---
DROP POLICY IF EXISTS "Anyone can view live campaigns" ON campaigns;
CREATE POLICY "Anyone can view live campaigns" ON campaigns FOR SELECT USING (
  is_active = true
  AND (starts_at IS NULL OR starts_at <= now())
  AND (ends_at IS NULL OR ends_at >= now())
);
DROP POLICY IF EXISTS "Admin can manage campaigns" ON campaigns;
CREATE POLICY "Admin can manage campaigns" ON campaigns FOR ALL USING (public.is_admin());

-- --- sponsored slots: public sees live ones, sellers their own, admin all ---
DROP POLICY IF EXISTS "Anyone can view live slots" ON sponsored_slots;
CREATE POLICY "Anyone can view live slots" ON sponsored_slots FOR SELECT USING (
  status = 'APPROVED' AND starts_at <= now() AND ends_at >= now()
);
DROP POLICY IF EXISTS "Sellers can view own slots" ON sponsored_slots;
CREATE POLICY "Sellers can view own slots" ON sponsored_slots FOR SELECT
  USING (seller_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS "Sellers can request slots for own products" ON sponsored_slots;
CREATE POLICY "Sellers can request slots for own products" ON sponsored_slots FOR INSERT WITH CHECK (
  seller_id = auth.uid()
  AND EXISTS (SELECT 1 FROM products WHERE id = product_id AND seller_id = auth.uid())
);
DROP POLICY IF EXISTS "Admin can manage slots" ON sponsored_slots;
CREATE POLICY "Admin can manage slots" ON sponsored_slots FOR ALL USING (public.is_admin());
-- NOTE: no client UPDATE — review_sponsored_slot() stamps the live window.

-- --- home layout: public reads the active page, admin curates ---
DROP POLICY IF EXISTS "Anyone can view active sections" ON home_sections;
CREATE POLICY "Anyone can view active sections" ON home_sections FOR SELECT
  USING (is_active = true OR public.is_admin());
DROP POLICY IF EXISTS "Admin can manage sections" ON home_sections;
CREATE POLICY "Admin can manage sections" ON home_sections FOR ALL USING (public.is_admin());

DROP POLICY IF EXISTS "Anyone can view section categories" ON home_section_categories;
CREATE POLICY "Anyone can view section categories" ON home_section_categories FOR SELECT USING (true);
DROP POLICY IF EXISTS "Admin can manage section categories" ON home_section_categories;
CREATE POLICY "Admin can manage section categories" ON home_section_categories FOR ALL USING (public.is_admin());

-- --- wallet: read-only for owners, writes are RPC-only ---
DROP POLICY IF EXISTS "Users can view own wallet" ON wallet_transactions;
CREATE POLICY "Users can view own wallet" ON wallet_transactions FOR SELECT
  USING (user_id = auth.uid() OR public.is_admin());
-- NOTE: intentionally no write policy — wallet_debit()/apply_referral()/resolve_order_issue() write.

-- --- reviews: public reads, owners write their own ---
DROP POLICY IF EXISTS "Anyone can view reviews" ON reviews;
CREATE POLICY "Anyone can view reviews" ON reviews FOR SELECT USING (true);
DROP POLICY IF EXISTS "Users can write own reviews" ON reviews;
CREATE POLICY "Users can write own reviews" ON reviews FOR INSERT
  WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS "Users can edit own reviews" ON reviews;
CREATE POLICY "Users can edit own reviews" ON reviews FOR UPDATE
  USING (user_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS "Admin can delete reviews" ON reviews;
CREATE POLICY "Admin can delete reviews" ON reviews FOR DELETE USING (public.is_admin());

-- --- order issues: customers file, sellers see their orders' issues, RPC resolves ---
DROP POLICY IF EXISTS "Users manage own issues" ON order_issues;
CREATE POLICY "Users manage own issues" ON order_issues FOR ALL
  USING (user_id = auth.uid() OR public.is_admin())
  WITH CHECK (user_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS "Sellers can view issues on own orders" ON order_issues;
CREATE POLICY "Sellers can view issues on own orders" ON order_issues FOR SELECT USING (
  EXISTS (SELECT 1 FROM orders WHERE orders.id = order_id AND orders.seller_id = auth.uid())
);

-- --- notifications: owners read + mark-read, server inserts ---
DROP POLICY IF EXISTS "Users can view own notifications" ON notifications;
CREATE POLICY "Users can view own notifications" ON notifications FOR SELECT
  USING (user_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS "Users can mark own notifications read" ON notifications;
CREATE POLICY "Users can mark own notifications read" ON notifications FOR UPDATE
  USING (user_id = auth.uid() OR public.is_admin())
  WITH CHECK (user_id = auth.uid() OR public.is_admin());
DROP POLICY IF EXISTS "Admin can manage notifications" ON notifications;
CREATE POLICY "Admin can manage notifications" ON notifications FOR ALL USING (public.is_admin());
-- NOTE: no client INSERT — the order trigger below writes.

-- --- device tokens: owners manage their own ---
DROP POLICY IF EXISTS "Users manage own device tokens" ON device_tokens;
CREATE POLICY "Users manage own device tokens" ON device_tokens FOR ALL
  USING (user_id = auth.uid() OR public.is_admin())
  WITH CHECK (user_id = auth.uid() OR public.is_admin());

-- PostgREST roles need table privileges; RLS above still scopes every row.
GRANT ALL ON sellers, seller_documents, delivery_partners, delivery_partner_documents,
  coupons, coupon_usages, campaigns, sponsored_slots, home_sections,
  home_section_categories, wallet_transactions, reviews, order_issues,
  notifications, device_tokens TO authenticated;
GRANT SELECT ON coupons, campaigns, sponsored_slots, home_sections,
  home_section_categories, reviews TO anon;

-- ============================================
-- 13. RPCs (match the app's call sites exactly)
-- ============================================
-- NOTE: every function below is DROPped before re-creation. CREATE OR REPLACE
-- cannot change a return type (Postgres error 42P13), and live databases may
-- already carry older variants of these same names — drop-first keeps this
-- file re-runnable everywhere. ( Grants are re-applied at the end of this
-- section, since DROP also drops them. )

-- --- wallet_debit(p_user_id, p_amount, p_title): spend wallet on an order ---
DROP FUNCTION IF EXISTS wallet_debit(UUID, INTEGER, TEXT);
CREATE OR REPLACE FUNCTION wallet_debit(p_user_id UUID, p_amount INTEGER, p_title TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE bal INTEGER;
BEGIN
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'INVALID_AMOUNT' USING ERRCODE = 'P0001';
  END IF;
  IF auth.uid() IS DISTINCT FROM p_user_id AND NOT public.is_admin() THEN
    RAISE EXCEPTION 'Not your wallet' USING ERRCODE = '42501';
  END IF;

  SELECT coalesce(sum(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END), 0)
    INTO bal FROM wallet_transactions WHERE user_id = p_user_id;

  IF bal < p_amount THEN
    RAISE EXCEPTION 'INSUFFICIENT_BALANCE' USING ERRCODE = 'P0001';
  END IF;

  INSERT INTO wallet_transactions (user_id, title, amount, type)
  VALUES (p_user_id, coalesce(p_title, 'Wallet spend'), p_amount, 'DEBIT');
END;
$$;

-- --- apply_referral(p_new_user_id, p_referral_code): ₹50 both sides, else no-op ---
DROP FUNCTION IF EXISTS apply_referral(UUID, TEXT);
CREATE OR REPLACE FUNCTION apply_referral(p_new_user_id UUID, p_referral_code TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE referrer UUID;
DECLARE already UUID;
BEGIN
  SELECT id INTO referrer FROM profiles
  WHERE upper(referral_code) = upper(trim(coalesce(p_referral_code, '')));

  -- Unknown code or self-referral: no-op by design.
  IF referrer IS NULL OR referrer = p_new_user_id THEN RETURN; END IF;

  SELECT referred_by INTO already FROM profiles WHERE id = p_new_user_id;
  IF already IS NOT NULL THEN RETURN; END IF; -- already redeemed one

  UPDATE profiles SET referred_by = referrer WHERE id = p_new_user_id;

  INSERT INTO wallet_transactions (user_id, title, amount, type) VALUES
    (referrer, 'Referral bonus', 50, 'CREDIT'),
    (p_new_user_id, 'Welcome referral bonus', 50, 'CREDIT');
END;
$$;

-- --- use_coupon(p_coupon_code, p_user_id, p_subtotal): validate + lock redemption ---
-- Returns [{coupon_id, discount_percent, max_discount}]; raises INVALID_COUPON /
-- MIN_ORDER:<value> / ALREADY_USED (parsed client-side into friendly strings).
DROP FUNCTION IF EXISTS use_coupon(TEXT, UUID, NUMERIC);
CREATE OR REPLACE FUNCTION use_coupon(p_coupon_code TEXT, p_user_id UUID, p_subtotal NUMERIC)
RETURNS TABLE (coupon_id UUID, discount_percent INTEGER, max_discount INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE c RECORD;
BEGIN
  SELECT * INTO c FROM coupons
  WHERE upper(code) = upper(trim(coalesce(p_coupon_code, ''))) AND is_active = true;

  IF c.id IS NULL THEN
    RAISE EXCEPTION 'INVALID_COUPON' USING ERRCODE = 'P0001';
  END IF;
  IF coalesce(p_subtotal, 0) < c.min_order_value THEN
    RAISE EXCEPTION 'MIN_ORDER:%', c.min_order_value USING ERRCODE = 'P0001';
  END IF;
  IF EXISTS (SELECT 1 FROM coupon_usages WHERE coupon_id = c.id AND user_id = p_user_id) THEN
    RAISE EXCEPTION 'ALREADY_USED' USING ERRCODE = 'P0001';
  END IF;

  INSERT INTO coupon_usages (coupon_id, user_id) VALUES (c.id, p_user_id)
  ON CONFLICT (coupon_id, user_id) DO NOTHING;

  coupon_id := c.id; discount_percent := c.discount_percent; max_discount := c.max_discount;
  RETURN NEXT;
END;
$$;

-- --- decrement_stock(items): atomic per-line stock reduction, floored at 0 ---
DROP FUNCTION IF EXISTS decrement_stock(JSONB);
CREATE OR REPLACE FUNCTION decrement_stock(items JSONB)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE elem JSONB; pid UUID; qty INTEGER;
BEGIN
  FOR elem IN SELECT * FROM jsonb_array_elements(coalesce(items, '[]'::jsonb)) LOOP
    BEGIN pid := (elem->>'product_id')::uuid; EXCEPTION WHEN OTHERS THEN CONTINUE; END;
    qty := coalesce((elem->>'quantity')::int, 0);
    IF qty < 1 OR qty > 100 THEN CONTINUE; END IF;
    UPDATE products SET stock = greatest(0, coalesce(stock, 0) - qty) WHERE id = pid;
  END LOOP;
END;
$$;

-- --- stocked_categories(): only categories that actually have something to sell ---
DROP FUNCTION IF EXISTS stocked_categories();
CREATE OR REPLACE FUNCTION stocked_categories()
RETURNS SETOF categories
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT * FROM categories c
  WHERE c.is_active = true
    AND EXISTS (
      SELECT 1 FROM products p
      WHERE p.category_id = c.id AND p.is_active = true AND coalesce(p.stock, 0) > 0
    )
  ORDER BY c.sort_order, c.name;
$$;

-- --- home_layout(): the whole customer home page in one round trip ---
DROP FUNCTION IF EXISTS home_layout();
CREATE OR REPLACE FUNCTION home_layout()
RETURNS json
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE result json;
BEGIN
  SELECT coalesce(json_agg(
    json_build_object(
      'id', hs.id,
      'title', hs.title,
      'layout', hs.layout,
      'sort_order', hs.sort_order,
      'is_active', hs.is_active,
      'categories', (
        SELECT coalesce(json_agg(
          json_build_object(
            'id', c.id,
            'name', c.name,
            'icon_url', c.icon_url,
            'color_hex', c.color_hex,
            'product_count', (
              SELECT count(*) FROM products p
              WHERE p.category_id = c.id AND p.is_active = true
            ),
            'preview_images', (
              SELECT coalesce(json_agg(img.image_url ORDER BY img.created_at DESC), '[]'::json)
              FROM (
                SELECT p.image_url, p.created_at FROM products p
                WHERE p.category_id = c.id AND p.is_active = true
                  AND p.image_url IS NOT NULL AND p.image_url <> ''
                ORDER BY p.created_at DESC LIMIT 4
              ) img
            )
          )
          ORDER BY hsc.sort_order
        ), '[]'::json)
        FROM home_section_categories hsc
        JOIN categories c ON c.id = hsc.category_id
        WHERE hsc.section_id = hs.id AND c.is_active = true
      )
    )
    ORDER BY hs.sort_order
  ), '[]'::json)
  INTO result
  FROM home_sections hs
  WHERE hs.is_active = true;

  RETURN result;
END;
$$;

-- --- Seller / partner review flow ---
DROP FUNCTION IF EXISTS submit_seller_for_review(UUID);
CREATE OR REPLACE FUNCTION submit_seller_for_review(p_seller_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS DISTINCT FROM p_seller_id AND NOT public.is_admin() THEN
    RAISE EXCEPTION 'Not your application' USING ERRCODE = '42501';
  END IF;
  PERFORM set_config('app.review_bypass', '1', true);
  UPDATE sellers SET status = 'UNDER_REVIEW'
  WHERE id = p_seller_id AND status IN ('PENDING_VERIFICATION', 'REJECTED');
END;
$$;

DROP FUNCTION IF EXISTS review_seller(UUID, BOOLEAN, TEXT);
CREATE OR REPLACE FUNCTION review_seller(p_seller_id UUID, p_approve BOOLEAN, p_rejection_reason TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501';
  END IF;
  PERFORM set_config('app.review_bypass', '1', true);
  IF coalesce(p_approve, false) THEN
    UPDATE sellers SET status = 'APPROVED', rejection_reason = NULL WHERE id = p_seller_id;
    UPDATE profiles SET role = 'seller' WHERE id = p_seller_id;
  ELSE
    UPDATE sellers SET status = 'REJECTED', rejection_reason = nullif(p_rejection_reason, '')
    WHERE id = p_seller_id;
  END IF;
END;
$$;

DROP FUNCTION IF EXISTS submit_delivery_partner_for_review(UUID);
CREATE OR REPLACE FUNCTION submit_delivery_partner_for_review(p_partner_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS DISTINCT FROM p_partner_id AND NOT public.is_admin() THEN
    RAISE EXCEPTION 'Not your application' USING ERRCODE = '42501';
  END IF;
  PERFORM set_config('app.review_bypass', '1', true);
  UPDATE delivery_partners SET status = 'UNDER_REVIEW'
  WHERE id = p_partner_id AND status IN ('PENDING_VERIFICATION', 'REJECTED');
END;
$$;

DROP FUNCTION IF EXISTS review_delivery_partner(UUID, BOOLEAN, TEXT);
CREATE OR REPLACE FUNCTION review_delivery_partner(p_partner_id UUID, p_approve BOOLEAN, p_rejection_reason TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501';
  END IF;
  PERFORM set_config('app.review_bypass', '1', true);
  IF coalesce(p_approve, false) THEN
    UPDATE delivery_partners SET status = 'APPROVED', rejection_reason = NULL WHERE id = p_partner_id;
    UPDATE profiles SET role = 'delivery' WHERE id = p_partner_id;
  ELSE
    UPDATE delivery_partners SET status = 'REJECTED', rejection_reason = nullif(p_rejection_reason, '')
    WHERE id = p_partner_id;
  END IF;
END;
$$;

-- --- review_sponsored_slot: approval stamps the live window (review delay never eats into paid days) ---
DROP FUNCTION IF EXISTS review_sponsored_slot(UUID, BOOLEAN, TEXT);
CREATE OR REPLACE FUNCTION review_sponsored_slot(p_slot_id UUID, p_approve BOOLEAN, p_rejection_reason TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE days INTEGER;
BEGIN
  IF NOT public.is_admin() THEN
    RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501';
  END IF;
  SELECT duration_days INTO days FROM sponsored_slots WHERE id = p_slot_id;
  IF days IS NULL THEN RAISE EXCEPTION 'Unknown slot' USING ERRCODE = 'P0001'; END IF;

  IF coalesce(p_approve, false) THEN
    UPDATE sponsored_slots
    SET status = 'APPROVED', rejection_reason = NULL,
        starts_at = now(), ends_at = now() + (days || ' days')::interval
    WHERE id = p_slot_id;
  ELSE
    UPDATE sponsored_slots
    SET status = 'REJECTED', rejection_reason = nullif(p_rejection_reason, '')
    WHERE id = p_slot_id;
  END IF;
END;
$$;

-- --- resolve_order_issue: seller/admin verdict; approved refunds hit the wallet atomically ---
DROP FUNCTION IF EXISTS resolve_order_issue(UUID, BOOLEAN, INTEGER, TEXT);
CREATE OR REPLACE FUNCTION resolve_order_issue(p_issue_id UUID, p_approve BOOLEAN, p_refund_amount INTEGER, p_note TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE iss RECORD; ord RECORD;
BEGIN
  SELECT * INTO iss FROM order_issues WHERE id = p_issue_id;
  IF iss.id IS NULL THEN RAISE EXCEPTION 'Unknown issue' USING ERRCODE = 'P0001'; END IF;
  SELECT * INTO ord FROM orders WHERE id = iss.order_id;

  IF auth.uid() IS DISTINCT FROM ord.seller_id AND NOT public.is_admin() THEN
    RAISE EXCEPTION 'Only the seller or an admin can resolve this' USING ERRCODE = '42501';
  END IF;

  IF coalesce(p_approve, false) THEN
    UPDATE order_issues
    SET status = 'approved',
        refund_amount = greatest(0, coalesce(p_refund_amount, 0)),
        description = CASE WHEN coalesce(p_note, '') = '' THEN description
                           ELSE description || ' | Note: ' || p_note END,
        resolved_at = now()
    WHERE id = p_issue_id;

    IF coalesce(p_refund_amount, 0) > 0 THEN
      INSERT INTO wallet_transactions (user_id, title, amount, type)
      VALUES (ord.customer_id,
              'Refund — order #' || upper(substr(ord.id::text, 1, 8)),
              p_refund_amount, 'CREDIT');
    END IF;
  ELSE
    UPDATE order_issues
    SET status = 'rejected',
        description = CASE WHEN coalesce(p_note, '') = '' THEN description
                           ELSE description || ' | Note: ' || p_note END,
        resolved_at = now()
    WHERE id = p_issue_id;
  END IF;
END;
$$;

-- --- Admin escape hatches ---
DROP FUNCTION IF EXISTS admin_promote_to_seller(UUID);
CREATE OR REPLACE FUNCTION admin_promote_to_seller(p_user_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501'; END IF;
  -- Role flip only — pair with a sellers row or the admin panel's orphan check will flag it.
  UPDATE profiles SET role = 'seller' WHERE id = p_user_id;
END;
$$;

DROP FUNCTION IF EXISTS admin_purge_seller(UUID);
CREATE OR REPLACE FUNCTION admin_purge_seller(p_seller_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501'; END IF;
  -- Application + documents only. Products are NEVER touched — purging a KYC
  -- application must not wipe a live catalogue.
  DELETE FROM seller_documents WHERE seller_id = p_seller_id;
  DELETE FROM sellers WHERE id = p_seller_id;
  UPDATE profiles SET role = 'customer' WHERE id = p_seller_id AND role = 'seller';
END;
$$;

DROP FUNCTION IF EXISTS admin_set_seller_status(UUID, TEXT);
CREATE OR REPLACE FUNCTION admin_set_seller_status(p_seller_id UUID, p_status TEXT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_admin() THEN RAISE EXCEPTION 'Admins only' USING ERRCODE = '42501'; END IF;
  IF p_status NOT IN ('PENDING_VERIFICATION','UNDER_REVIEW','APPROVED','REJECTED','SUSPENDED') THEN
    RAISE EXCEPTION 'Bad status: %', p_status USING ERRCODE = 'P0001';
  END IF;
  PERFORM set_config('app.review_bypass', '1', true);
  UPDATE sellers SET status = p_status WHERE id = p_seller_id;
END;
$$;

-- RPC access: signed-in callers only; each function enforces its own role check.
REVOKE ALL ON FUNCTION wallet_debit(UUID, INTEGER, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION apply_referral(UUID, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION use_coupon(TEXT, UUID, NUMERIC) FROM public, anon;
REVOKE ALL ON FUNCTION decrement_stock(JSONB) FROM public, anon;
REVOKE ALL ON FUNCTION stocked_categories() FROM public, anon;
REVOKE ALL ON FUNCTION home_layout() FROM public, anon;
REVOKE ALL ON FUNCTION submit_seller_for_review(UUID) FROM public, anon;
REVOKE ALL ON FUNCTION review_seller(UUID, BOOLEAN, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION submit_delivery_partner_for_review(UUID) FROM public, anon;
REVOKE ALL ON FUNCTION review_delivery_partner(UUID, BOOLEAN, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION review_sponsored_slot(UUID, BOOLEAN, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION resolve_order_issue(UUID, BOOLEAN, INTEGER, TEXT) FROM public, anon;
REVOKE ALL ON FUNCTION admin_promote_to_seller(UUID) FROM public, anon;
REVOKE ALL ON FUNCTION admin_purge_seller(UUID) FROM public, anon;
REVOKE ALL ON FUNCTION admin_set_seller_status(UUID, TEXT) FROM public, anon;

GRANT EXECUTE ON FUNCTION wallet_debit(UUID, INTEGER, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION apply_referral(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION use_coupon(TEXT, UUID, NUMERIC) TO authenticated;
GRANT EXECUTE ON FUNCTION decrement_stock(JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION stocked_categories() TO authenticated;
GRANT EXECUTE ON FUNCTION home_layout() TO authenticated;
GRANT EXECUTE ON FUNCTION submit_seller_for_review(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION review_seller(UUID, BOOLEAN, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION submit_delivery_partner_for_review(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION review_delivery_partner(UUID, BOOLEAN, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION review_sponsored_slot(UUID, BOOLEAN, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION resolve_order_issue(UUID, BOOLEAN, INTEGER, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION admin_promote_to_seller(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION admin_purge_seller(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION admin_set_seller_status(UUID, TEXT) TO authenticated;

-- ============================================
-- 14. ORDER → NOTIFICATION trigger (server-side inbox rows)
--    Titles are load-bearing: NotificationsRepository maps them to icons by
--    keyword (confirmed / preparing / pickup / delivery / delivered /
--    cancelled), so keep those words in the titles below.
-- ============================================
CREATE OR REPLACE FUNCTION notify_order_status()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  title TEXT;
  msg TEXT;
  seller_name TEXT;
BEGIN
  SELECT coalesce(nullif(full_name, ''), 'the store') INTO seller_name
  FROM profiles WHERE id = NEW.seller_id;

  IF TG_OP = 'INSERT' THEN
    title := 'Order placed';
    msg := 'Your order of ₹' || NEW.total_amount::text || ' is placed with ' || seller_name || '.';
  ELSE
    IF OLD.status IS NOT DISTINCT FROM NEW.status THEN RETURN NEW; END IF;
    CASE NEW.status
      WHEN 'confirmed' THEN
        title := 'Order confirmed';
        msg := seller_name || ' confirmed your order of ₹' || NEW.total_amount::text || '.';
      WHEN 'preparing' THEN
        title := 'Preparing your order';
        msg := seller_name || ' is packing your items.';
      WHEN 'ready_for_pickup' THEN
        title := 'Ready for pickup';
        msg := 'Your order is packed and waiting for the rider.';
      WHEN 'out_for_delivery' THEN
        title := 'Out for delivery';
        msg := 'Your rider picked up the order and is on the way.';
      WHEN 'delivered' THEN
        title := 'Delivered';
        msg := 'Your order was delivered. Enjoy!';
      WHEN 'cancelled' THEN
        title := 'Order cancelled';
        msg := 'Your order was cancelled. Any wallet money used will be refunded.';
      ELSE
        RETURN NEW;
    END CASE;
  END IF;

  INSERT INTO notifications (user_id, type, title, message, order_id)
  VALUES (NEW.customer_id, 'ORDER', title, msg, NEW.id);

  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- A notification must never break the order write itself.
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_order_status_notify ON orders;
CREATE TRIGGER trg_order_status_notify
  AFTER INSERT OR UPDATE OF status ON orders
  FOR EACH ROW EXECUTE FUNCTION notify_order_status();

-- ============================================
-- 15. NOTIFICATION → PUSH trigger (fires the send-push Edge Function)
--    Needs pg_net (Supabase cloud has it; enable under Database → Extensions).
--    !!! Replace YOUR_PROJECT_REF + CHANGE_ME_PUSH_SECRET below, and set the
--    same PUSH_WEBHOOK_SECRET on the send-push Edge Function (see
--    PRODUCTION_LAUNCH_GUIDE.md). Until then the trigger stays dormant.
-- ============================================
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

CREATE OR REPLACE FUNCTION push_notification_to_device()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  project_url TEXT := 'https://YOUR_PROJECT_REF.supabase.co';
  webhook_secret TEXT := 'CHANGE_ME_PUSH_SECRET';
BEGIN
  IF project_url LIKE '%YOUR_PROJECT_REF%' THEN RETURN NEW; END IF; -- not configured yet

  PERFORM extensions.net.http_post(
    url := project_url || '/functions/v1/send-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', webhook_secret),
    body := jsonb_build_object(
      'user_id', NEW.user_id,
      'title', NEW.title,
      'body', NEW.message,
      'order_id', NEW.order_id
    )
  );
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- Push delivery must never break the notification write itself.
  RETURN NEW;
END;
$$;

-- The trigger only makes sense where pg_net exists; everywhere else the
-- notifications table still works and pushes just don't fire yet.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_net') THEN
    DROP TRIGGER IF EXISTS trg_notifications_push ON notifications;
    EXECUTE 'CREATE TRIGGER trg_notifications_push
      AFTER INSERT ON notifications
      FOR EACH ROW EXECUTE FUNCTION push_notification_to_device()';
  END IF;
END;
$$;

-- ============================================
-- 16. STORAGE buckets + policies
-- ============================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('seller-documents', 'seller-documents', false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO storage.buckets (id, name, public)
VALUES ('delivery-documents', 'delivery-documents', false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO storage.buckets (id, name, public)
VALUES ('product-images', 'product-images', true)
ON CONFLICT (id) DO NOTHING;

-- KYC docs: each applicant touches only their own "<user-id>/..." folder.
DROP POLICY IF EXISTS "Owners manage own seller docs files" ON storage.objects;
CREATE POLICY "Owners manage own seller docs files" ON storage.objects FOR ALL
  USING (bucket_id = 'seller-documents'
         AND ((storage.foldername(name))[1] = auth.uid()::text OR public.is_admin()))
  WITH CHECK (bucket_id = 'seller-documents'
         AND ((storage.foldername(name))[1] = auth.uid()::text OR public.is_admin()));

DROP POLICY IF EXISTS "Owners manage own partner docs files" ON storage.objects;
CREATE POLICY "Owners manage own partner docs files" ON storage.objects FOR ALL
  USING (bucket_id = 'delivery-documents'
         AND ((storage.foldername(name))[1] = auth.uid()::text OR public.is_admin()))
  WITH CHECK (bucket_id = 'delivery-documents'
         AND ((storage.foldername(name))[1] = auth.uid()::text OR public.is_admin()));

-- Product photos: public reads, sellers/admin write.
DROP POLICY IF EXISTS "Anyone can view product images" ON storage.objects;
CREATE POLICY "Anyone can view product images" ON storage.objects FOR SELECT
  USING (bucket_id = 'product-images');

DROP POLICY IF EXISTS "Sellers manage product images" ON storage.objects;
CREATE POLICY "Sellers manage product images" ON storage.objects FOR ALL
  USING (bucket_id = 'product-images'
         AND EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('seller','admin')))
  WITH CHECK (bucket_id = 'product-images'
         AND EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('seller','admin')));

-- ============================================
-- DONE. Verify with:
--   SELECT count(*) FROM sellers;              -- 0, but must not error
--   SELECT * FROM stocked_categories();       -- [] until catalogue exists
--   SELECT home_layout();                      -- [] until sections exist
-- ============================================

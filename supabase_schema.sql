-- ============================================
-- DUGGU STORE - Complete Supabase Schema
-- ============================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. PROFILES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL DEFAULT '',
    phone TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL DEFAULT 'customer' CHECK (role IN ('customer', 'seller', 'delivery', 'admin')),
    avatar_url TEXT,
    -- Only meaningful for role = 'seller' — where a rider picks up their orders.
    store_address TEXT,
    store_latitude DOUBLE PRECISION,
    store_longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- Admin check as a SECURITY DEFINER function. Inlining this as
-- "EXISTS (SELECT 1 FROM profiles ...)" inside a policy ON profiles makes
-- Postgres recurse and fail every authenticated read with error 42P17.
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin'
    );
$$;

GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, anon;

-- Profiles policies
CREATE POLICY "Users can view own profile" ON profiles
    FOR SELECT USING (auth.uid() = id OR public.is_admin());

CREATE POLICY "Users can update own profile" ON profiles
    FOR UPDATE USING (auth.uid() = id OR public.is_admin());

-- Lets the app create its own profile row if the signup trigger is missing.
CREATE POLICY "Users can insert own profile" ON profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

-- Auto-create profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    meta_role TEXT;
BEGIN
    meta_role := COALESCE(NEW.raw_user_meta_data->>'role', 'customer');
    IF meta_role NOT IN ('customer', 'seller', 'delivery', 'admin') THEN
        meta_role := 'customer';
    END IF;

    INSERT INTO public.profiles (id, full_name, phone, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone', ''),
        meta_role
    )
    ON CONFLICT (id) DO NOTHING;

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Never let profile creation abort the auth signup itself.
    RAISE WARNING 'handle_new_user failed for %: %', NEW.id, SQLERRM;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ============================================
-- 2. CATEGORIES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    icon_url TEXT,
    color_hex TEXT NOT NULL DEFAULT '#7C3AED',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can view active categories" ON categories
    FOR SELECT USING (is_active = true);

CREATE POLICY "Admin can manage categories" ON categories
    FOR ALL USING (
        public.is_admin()
    );

-- ============================================
-- 3. PRODUCTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    discount_price NUMERIC(10,2) CHECK (discount_price >= 0 AND discount_price < price),
    image_url TEXT,
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    unit TEXT NOT NULL DEFAULT 'pcs',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE products ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can view active products" ON products
    FOR SELECT USING (is_active = true);

CREATE POLICY "Sellers can view own products" ON products
    FOR SELECT USING (
        seller_id = auth.uid() OR
        EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('admin', 'seller'))
    );

CREATE POLICY "Sellers can insert products" ON products
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('seller', 'admin'))
    );

CREATE POLICY "Sellers can update own products" ON products
    FOR UPDATE USING (
        seller_id = auth.uid() OR
        public.is_admin()
    );

CREATE POLICY "Sellers can delete own products" ON products
    FOR DELETE USING (
        seller_id = auth.uid() OR
        public.is_admin()
    );

-- ============================================
-- 4. CART ITEMS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS cart_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(customer_id, product_id)
);

ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own cart" ON cart_items
    FOR SELECT USING (customer_id = auth.uid());

CREATE POLICY "Users can insert own cart items" ON cart_items
    FOR INSERT WITH CHECK (customer_id = auth.uid());

CREATE POLICY "Users can update own cart items" ON cart_items
    FOR UPDATE USING (customer_id = auth.uid());

CREATE POLICY "Users can delete own cart items" ON cart_items
    FOR DELETE USING (customer_id = auth.uid());

-- ============================================
-- 5. ADDRESSES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    label TEXT NOT NULL DEFAULT 'Home',
    full_address TEXT NOT NULL,
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE addresses ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own addresses" ON addresses
    FOR SELECT USING (user_id = auth.uid());

CREATE POLICY "Users can insert own addresses" ON addresses
    FOR INSERT WITH CHECK (user_id = auth.uid());

CREATE POLICY "Users can update own addresses" ON addresses
    FOR UPDATE USING (user_id = auth.uid());

CREATE POLICY "Users can delete own addresses" ON addresses
    FOR DELETE USING (user_id = auth.uid());

-- ============================================
-- 6. ORDERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    delivery_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'preparing', 'ready_for_pickup', 'out_for_delivery', 'delivered', 'cancelled')),
    total_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    delivery_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    delivery_address TEXT NOT NULL DEFAULT '',
    -- Captured from the customer's selected address at checkout, when that
    -- address itself carries a fix; null on hand-typed addresses.
    delivery_latitude DOUBLE PRECISION,
    delivery_longitude DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Customers can view own orders" ON orders
    FOR SELECT USING (customer_id = auth.uid());

CREATE POLICY "Customers can insert orders" ON orders
    FOR INSERT WITH CHECK (customer_id = auth.uid());

CREATE POLICY "Customers can update own orders" ON orders
    FOR UPDATE USING (
        customer_id = auth.uid() AND status IN ('pending', 'confirmed')
    );

CREATE POLICY "Sellers can view and manage orders" ON orders
    FOR ALL USING (
        seller_id = auth.uid() OR
        public.is_admin()
    );

CREATE POLICY "Delivery can view assigned orders" ON orders
    FOR SELECT USING (delivery_id = auth.uid());

CREATE POLICY "Delivery can update assigned orders" ON orders
    FOR UPDATE USING (delivery_id = auth.uid());

-- Lets a delivery-role user see orders a seller has packed but nobody has
-- claimed yet, so any rider can pick one up rather than waiting for a
-- manual assignment the app never actually made.
CREATE OR REPLACE FUNCTION public.is_delivery_partner()
RETURNS BOOLEAN AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'delivery'
    );
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public;

GRANT EXECUTE ON FUNCTION public.is_delivery_partner() TO authenticated;

CREATE POLICY "Delivery can view unclaimed ready orders" ON orders
    FOR SELECT USING (
        delivery_id IS NULL AND status = 'ready_for_pickup' AND public.is_delivery_partner()
    );

-- The claim itself: a conditional UPDATE gated on delivery_id still being
-- null, so two riders racing for the same order can't both win it — only
-- the first UPDATE to reach Postgres matches the row. The WITH CHECK then
-- pins what the row is allowed to become, so a claim can only ever set
-- delivery_id to the claimer's own id together with out_for_delivery.
CREATE POLICY "Delivery can claim ready orders" ON orders
    FOR UPDATE USING (
        delivery_id IS NULL AND status = 'ready_for_pickup' AND public.is_delivery_partner()
    ) WITH CHECK (
        delivery_id = auth.uid() AND status = 'out_for_delivery'
    );

CREATE POLICY "Admin can manage all orders" ON orders
    FOR ALL USING (
        public.is_admin()
    );

-- The rider's order queries embed profiles!seller_id to read the store's
-- pickup point. Scoped by role rather than by which orders the rider has:
-- correlating this to a specific order (via an EXISTS against orders)
-- round-trips back into profiles RLS through is_delivery_partner() and
-- hits Postgres's infinite-recursion guard (42P17).
CREATE POLICY "Delivery can view seller store info" ON profiles
    FOR SELECT USING (
        role = 'seller' AND public.is_delivery_partner()
    );

-- ============================================
-- 7. ORDER ITEMS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    price_at_purchase NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Customers can view own order items" ON order_items
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.customer_id = auth.uid())
    );

CREATE POLICY "Customers can insert order items" ON order_items
    FOR INSERT WITH CHECK (
        EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.customer_id = auth.uid())
    );

CREATE POLICY "Sellers can view order items for their orders" ON order_items
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.seller_id = auth.uid()) OR
        public.is_admin()
    );

CREATE POLICY "Admin can manage all order items" ON order_items
    FOR ALL USING (
        public.is_admin()
    );

-- ============================================
-- 8. FAVORITES TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS favorites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(customer_id, product_id)
);

ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own favorites" ON favorites
    FOR SELECT USING (customer_id = auth.uid());

CREATE POLICY "Users can insert own favorites" ON favorites
    FOR INSERT WITH CHECK (customer_id = auth.uid());

CREATE POLICY "Users can delete own favorites" ON favorites
    FOR DELETE USING (customer_id = auth.uid());

-- ============================================
-- 9. DELIVERY TRACKING TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS delivery_tracking (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT '',
    latitude NUMERIC(10,7) DEFAULT 0,
    longitude NUMERIC(10,7) DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    -- One order has one rider at a time, so its live position is a single row
    -- the rider overwrites. Without this the app would append a row per update
    -- and the customer would sort a history to find the current fix.
    CONSTRAINT delivery_tracking_order_id_key UNIQUE (order_id)
);

ALTER TABLE delivery_tracking ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Delivery can manage own tracking" ON delivery_tracking
    FOR ALL USING (delivery_id = auth.uid());

CREATE POLICY "Customers can view tracking for their orders" ON delivery_tracking
    FOR SELECT USING (
        EXISTS (SELECT 1 FROM orders WHERE delivery_tracking.order_id = orders.id AND orders.customer_id = auth.uid())
    );

CREATE POLICY "Admin can view all tracking" ON delivery_tracking
    FOR SELECT USING (
        public.is_admin()
    );

-- ============================================
-- 10. INDEXES FOR PERFORMANCE
-- ============================================
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_seller ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(is_active);
CREATE INDEX IF NOT EXISTS idx_cart_items_customer ON cart_items(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_seller ON orders(seller_id);
CREATE INDEX IF NOT EXISTS idx_orders_delivery ON orders(delivery_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_favorites_customer ON favorites(customer_id);
CREATE INDEX IF NOT EXISTS idx_delivery_tracking_order ON delivery_tracking(order_id);

-- ============================================
-- 11. SEED DATA (Sample Categories)
-- ============================================
INSERT INTO categories (name, color_hex, sort_order) VALUES
    ('Grocery', '#7C3AED', 1),
    ('Veggies', '#16A34A', 2),
    ('Fruits', '#F97316', 3),
    ('Snacks', '#EAB308', 4),
    ('Chocolate', '#92400E', 5),
    ('Bread', '#D97706', 6),
    ('Shampoo', '#EC4899', 7),
    ('Cleaning', '#06B6D4', 8),
    ('Baby Care', '#F472B6', 9),
    ('Cold Drinks', '#2563EB', 10),
    ('Meats', '#DC2626', 11),
    ('Dairy', '#8B5CF6', 12),
    ('Frozen', '#0EA5E9', 13)
ON CONFLICT DO NOTHING;

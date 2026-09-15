-- ============================================
-- DUGGU STORE - Fixed Schema (drop + recreate)
-- ============================================

-- Drop everything first (safe to run multiple times)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();

DROP TABLE IF EXISTS delivery_tracking CASCADE;
DROP TABLE IF EXISTS favorites CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS cart_items CASCADE;
DROP TABLE IF EXISTS addresses CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS profiles CASCADE;

-- Enable UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. PROFILES
-- ============================================
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL DEFAULT '',
    phone TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL DEFAULT 'customer' CHECK (role IN ('customer','seller','delivery','admin')),
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own profile" ON profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = id);
CREATE POLICY "Admin can view all profiles" ON profiles FOR SELECT USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));
CREATE POLICY "Admin can update all profiles" ON profiles FOR UPDATE USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, phone, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone', ''),
        COALESCE(NEW.raw_user_meta_data->>'role', 'customer')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ============================================
-- 2. CATEGORIES
-- ============================================
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    icon_url TEXT,
    color_hex TEXT NOT NULL DEFAULT '#7C3AED',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can view active categories" ON categories FOR SELECT USING (is_active = true);
CREATE POLICY "Admin can manage categories" ON categories FOR ALL USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ============================================
-- 3. PRODUCTS
-- ============================================
CREATE TABLE products (
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
CREATE POLICY "Anyone can view active products" ON products FOR SELECT USING (is_active = true);
CREATE POLICY "Sellers can insert products" ON products FOR INSERT WITH CHECK (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role IN ('seller','admin')));
CREATE POLICY "Sellers can update products" ON products FOR UPDATE USING (seller_id = auth.uid() OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));
CREATE POLICY "Sellers can delete products" ON products FOR DELETE USING (seller_id = auth.uid() OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ============================================
-- 4. CART ITEMS
-- ============================================
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(customer_id, product_id)
);
ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own cart" ON cart_items FOR SELECT USING (customer_id = auth.uid());
CREATE POLICY "Users can insert own cart items" ON cart_items FOR INSERT WITH CHECK (customer_id = auth.uid());
CREATE POLICY "Users can update own cart items" ON cart_items FOR UPDATE USING (customer_id = auth.uid());
CREATE POLICY "Users can delete own cart items" ON cart_items FOR DELETE USING (customer_id = auth.uid());

-- ============================================
-- 5. ADDRESSES
-- ============================================
CREATE TABLE addresses (
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
CREATE POLICY "Users can view own addresses" ON addresses FOR SELECT USING (user_id = auth.uid());
CREATE POLICY "Users can insert own addresses" ON addresses FOR INSERT WITH CHECK (user_id = auth.uid());
CREATE POLICY "Users can update own addresses" ON addresses FOR UPDATE USING (user_id = auth.uid());
CREATE POLICY "Users can delete own addresses" ON addresses FOR DELETE USING (user_id = auth.uid());

-- ============================================
-- 6. ORDERS
-- ============================================
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    delivery_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','confirmed','preparing','out_for_delivery','delivered','cancelled')),
    total_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    delivery_fee NUMERIC(10,2) NOT NULL DEFAULT 0,
    delivery_address TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Customers can view own orders" ON orders FOR SELECT USING (customer_id = auth.uid());
CREATE POLICY "Customers can insert orders" ON orders FOR INSERT WITH CHECK (customer_id = auth.uid());
CREATE POLICY "Customers can cancel own orders" ON orders FOR UPDATE USING (customer_id = auth.uid() AND status IN ('pending','confirmed'));
CREATE POLICY "Sellers can manage orders" ON orders FOR ALL USING (seller_id = auth.uid() OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));
CREATE POLICY "Delivery can view assigned orders" ON orders FOR SELECT USING (delivery_id = auth.uid());
CREATE POLICY "Delivery can update assigned orders" ON orders FOR UPDATE USING (delivery_id = auth.uid());

-- ============================================
-- 7. ORDER ITEMS
-- ============================================
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    price_at_purchase NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Customers can view own order items" ON order_items FOR SELECT USING (EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.customer_id = auth.uid()));
CREATE POLICY "Customers can insert order items" ON order_items FOR INSERT WITH CHECK (EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.customer_id = auth.uid()));
CREATE POLICY "Sellers can view order items" ON order_items FOR SELECT USING (EXISTS (SELECT 1 FROM orders WHERE order_items.order_id = orders.id AND orders.seller_id = auth.uid()) OR EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ============================================
-- 8. FAVORITES
-- ============================================
CREATE TABLE favorites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(customer_id, product_id)
);
ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own favorites" ON favorites FOR SELECT USING (customer_id = auth.uid());
CREATE POLICY "Users can insert own favorites" ON favorites FOR INSERT WITH CHECK (customer_id = auth.uid());
CREATE POLICY "Users can delete own favorites" ON favorites FOR DELETE USING (customer_id = auth.uid());

-- ============================================
-- 9. DELIVERY TRACKING
-- ============================================
CREATE TABLE delivery_tracking (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    delivery_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT '',
    latitude NUMERIC(10,7) DEFAULT 0,
    longitude NUMERIC(10,7) DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE delivery_tracking ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Delivery can manage own tracking" ON delivery_tracking FOR ALL USING (delivery_id = auth.uid());
CREATE POLICY "Customers can view tracking" ON delivery_tracking FOR SELECT USING (EXISTS (SELECT 1 FROM orders WHERE delivery_tracking.order_id = orders.id AND orders.customer_id = auth.uid()));
CREATE POLICY "Admin can view all tracking" ON delivery_tracking FOR SELECT USING (EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND role = 'admin'));

-- ============================================
-- 10. INDEXES
-- ============================================
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_seller ON products(seller_id);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_cart_items_customer ON cart_items(customer_id);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_seller ON orders(seller_id);
CREATE INDEX idx_orders_delivery ON orders(delivery_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_favorites_customer ON favorites(customer_id);
CREATE INDEX idx_delivery_tracking_order ON delivery_tracking(order_id);

-- ============================================
-- 11. SEED DATA
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

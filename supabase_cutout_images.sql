-- ============================================
-- ADD CUTOUT IMAGE SUPPORT TO PRODUCTS TABLE
-- ============================================

-- Add cutout_image_url column to products table
ALTER TABLE products 
ADD COLUMN IF NOT EXISTS cutout_image_url TEXT;

-- Add index for better performance
CREATE INDEX IF NOT EXISTS idx_products_cutout_image ON products(cutout_image_url) 
WHERE cutout_image_url IS NOT NULL;

-- Add comment to document the new column
COMMENT ON COLUMN products.cutout_image_url IS 'URL of the product image with background removed (cutout), processed server-side';
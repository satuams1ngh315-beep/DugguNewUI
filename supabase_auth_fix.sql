-- ============================================
-- DUGGU STORE - Auth / login / signup fix
-- ============================================
-- Safe to run on an existing project, and safe to run more than once.
-- Fixes three things that make login and signup fail:
--   1. The admin policies on `profiles` selected from `profiles`, which makes
--      Postgres abort every authenticated read of that table with
--      "infinite recursion detected in policy for relation profiles" (42P17).
--   2. `profiles` had no INSERT policy, so a client could never create the row
--      when the signup trigger was missing.
--   3. `handle_new_user` aborted the whole signup (500 "Database error saving
--      new user") if the profile row already existed or the role in the signup
--      metadata was not one of the allowed values.

-- --------------------------------------------
-- 1. Admin check that does not re-enter profiles RLS
-- --------------------------------------------
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

-- --------------------------------------------
-- 2. Rebuild the profiles policies
-- --------------------------------------------
DROP POLICY IF EXISTS "Users can view own profile" ON profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON profiles;
DROP POLICY IF EXISTS "Admin can view all profiles" ON profiles;
DROP POLICY IF EXISTS "Admin can update all profiles" ON profiles;

CREATE POLICY "Users can view own profile" ON profiles
    FOR SELECT USING (auth.uid() = id OR public.is_admin());

CREATE POLICY "Users can update own profile" ON profiles
    FOR UPDATE USING (auth.uid() = id OR public.is_admin());

-- Lets the app self-heal its own profile row; the id is pinned to the caller
-- so nobody can create a row for another user.
CREATE POLICY "Users can insert own profile" ON profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

-- --------------------------------------------
-- 3. Signup trigger that cannot break signup
-- --------------------------------------------
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
    -- Never let profile creation abort the auth signup itself; the client
    -- recreates the row on first login if this ever fails.
    RAISE WARNING 'handle_new_user failed for %: %', NEW.id, SQLERRM;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- --------------------------------------------
-- 4. Backfill profiles for users that never got one
-- --------------------------------------------
INSERT INTO public.profiles (id, full_name, phone, role)
SELECT
    u.id,
    COALESCE(u.raw_user_meta_data->>'full_name', ''),
    COALESCE(u.raw_user_meta_data->>'phone', ''),
    CASE
        WHEN COALESCE(u.raw_user_meta_data->>'role', 'customer')
             IN ('customer', 'seller', 'delivery', 'admin')
        THEN COALESCE(u.raw_user_meta_data->>'role', 'customer')
        ELSE 'customer'
    END
FROM auth.users u
LEFT JOIN public.profiles p ON p.id = u.id
WHERE p.id IS NULL;

-- ============================================
-- 5. EMAIL A CODE INSTEAD OF A LINK  (dashboard step — not SQL)
-- ============================================
-- The app now asks for a 6-digit code on the verify screen and exchanges it via
-- POST /auth/v1/verify. Supabase decides what the mail contains, so the template
-- has to be changed by hand — there is no SQL or API for it:
--
--   Dashboard -> Authentication -> Emails -> "Confirm signup"
--   Replace the {{ .ConfirmationURL }} link with {{ .Token }}
--
-- For example:
--
--   <h2>Confirm your signup</h2>
--   <p>Your Duggu Store verification code is:</p>
--   <p style="font-size:28px;font-weight:bold;letter-spacing:6px">{{ .Token }}</p>
--   <p>This code expires in 1 hour.</p>
--
-- Notes:
--   * {{ .Token }} is the 6-digit code. {{ .TokenHash }} is a different value and
--     will not work if the user types it in.
--   * Keep "Confirm email" enabled under Authentication -> Providers -> Email.
--     With it off, signup returns a session immediately and no mail is sent at all.
--   * Code lifetime is Authentication -> Providers -> Email -> "Email OTP Expiration"
--     (default 3600s). The app surfaces an expiry as "That code has expired".
--   * Leaving the link in the template as well is fine — both keep working, and the
--     app only needs the code.

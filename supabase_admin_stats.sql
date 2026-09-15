-- Database health / stats for the in-app admin panel.
--
-- Run this once in the Supabase SQL Editor (Dashboard → SQL Editor → New query
-- → paste → Run). It creates two read-only RPCs the app calls:
--
--   admin_table_stats()  — row count + last-write time for every known table
--   admin_db_health()    — headline numbers (signups, orders, revenue, errors)
--
-- Both are SECURITY DEFINER so they can read across RLS, and both hard-check
-- that the caller is an admin. Nothing here writes, so the worst a bug can do
-- is show a wrong number.

-- ---------------------------------------------------------------------------
-- Guard: every function below refuses to run for a non-admin.
-- ---------------------------------------------------------------------------
create or replace function admin_assert()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (
    select 1 from profiles
    where id = auth.uid() and role = 'admin'
  ) then
    raise exception 'Admins only'
      using errcode = '42501';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Per-table stats.
--
-- Counts are exact (count(*)), which is fine at this store's size and avoids
-- the confusing drift of pg_class.reltuples on a table that has not been
-- analysed recently. The created_at probe is wrapped per table so a table
-- without that column still reports its count instead of failing the lot.
-- ---------------------------------------------------------------------------
create or replace function admin_table_stats()
returns table (
  table_name text,
  row_count bigint,
  last_created timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  t text;
  n bigint;
  ts timestamptz;
  has_created boolean;
begin
  perform admin_assert();

  for t in
    select c.relname
    from pg_class c
    join pg_namespace ns on ns.oid = c.relnamespace
    where ns.nspname = 'public'
      and c.relkind = 'r'
    order by c.relname
  loop
    execute format('select count(*) from public.%I', t) into n;

    select exists (
      select 1 from information_schema.columns
      where table_schema = 'public'
        and table_name = t
        and column_name = 'created_at'
    ) into has_created;

    if has_created then
      execute format('select max(created_at) from public.%I', t) into ts;
    else
      ts := null;
    end if;

    table_name := t;
    row_count := n;
    last_created := ts;
    return next;
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Headline numbers for the top of the screen.
--
-- Returned as a single json object so the app makes one call and can add
-- fields later without another migration.
-- ---------------------------------------------------------------------------
create or replace function admin_db_health()
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  result json;
begin
  perform admin_assert();

  select json_build_object(
    'signups_today', (
      select count(*) from profiles
      where created_at >= date_trunc('day', now())
    ),
    'signups_week', (
      select count(*) from profiles
      where created_at >= now() - interval '7 days'
    ),
    'orders_today', (
      select count(*) from orders
      where created_at >= date_trunc('day', now())
    ),
    'orders_week', (
      select count(*) from orders
      where created_at >= now() - interval '7 days'
    ),
    'revenue_today', coalesce((
      select sum(total_amount) from orders
      where created_at >= date_trunc('day', now())
        and status <> 'cancelled'
    ), 0),
    'revenue_week', coalesce((
      select sum(total_amount) from orders
      where created_at >= now() - interval '7 days'
        and status <> 'cancelled'
    ), 0),
    -- Things that need a human: these are the numbers worth acting on.
    'pending_sellers', (
      select count(*) from sellers
      where status in ('PENDING_VERIFICATION', 'UNDER_REVIEW')
    ),
    'pending_partners', (
      select count(*) from delivery_partners
      where status in ('PENDING_VERIFICATION', 'UNDER_REVIEW')
    ),
    'open_issues', (
      select count(*) from order_issues where status = 'open'
    ),
    'stuck_orders', (
      -- Placed over a day ago and still not delivered or cancelled.
      select count(*) from orders
      where created_at < now() - interval '1 day'
        and status not in ('delivered', 'cancelled')
    ),
    'out_of_stock', (
      select count(*) from products
      where is_active = true and coalesce(stock, 0) <= 0
    ),
    -- A profile carrying the seller role with no sellers row can't be managed
    -- from the admin panel, so surface the mismatch rather than let it sit.
    'orphan_sellers', (
      select count(*) from profiles p
      where p.role = 'seller'
        and not exists (select 1 from sellers s where s.id = p.id)
    )
  ) into result;

  return result;
end;
$$;

-- ---------------------------------------------------------------------------
-- Only signed-in users may call these; the admin check inside does the rest.
-- ---------------------------------------------------------------------------
revoke all on function admin_assert() from public, anon;
revoke all on function admin_table_stats() from public, anon;
revoke all on function admin_db_health() from public, anon;

grant execute on function admin_table_stats() to authenticated;
grant execute on function admin_db_health() to authenticated;

-- NEXORA — Production schema for Supabase (PostgreSQL)
-- Local dev mirror: nexora.db (SQLite) ships in server/. Same shape.
-- Enable RLS everywhere; admin-only writes via authenticated admin role.

create table admin_users (
  id uuid primary key default gen_random_uuid(),
  email text unique not null,
  password_hash text,                -- NULL if auth.users-managed
  role text not null default 'super_admin' check (role in ('super_admin','content_manager','server_manager','notification_manager','viewer')),
  mfa_enabled boolean default false,
  created_at timestamptz default now()
);

create table admin_roles (
  id uuid primary key default gen_random_uuid(),
  name text unique not null,
  permissions jsonb not null default '[]'
);

create table admin_permissions (
  id uuid primary key default gen_random_uuid(),
  role_id uuid references admin_roles(id) on delete cascade,
  permission text not null
);

create table categories (
  id bigserial primary key,
  name text unique not null,
  sort int default 0
);

create table app_cards (
  id bigserial primary key,
  name text not null,
  description text default '',
  logo_url text default '',
  category_id bigint references categories(id) on delete set null,
  featured boolean default false,
  label text default '' check (label in ('','NEW','HOT','TRENDING','VIP')),
  enabled boolean default true,
  sort int default 999,
  browser_mode text default 'internal' check (browser_mode in ('internal','external')),
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);
create index app_cards_category_idx on app_cards(category_id);
create index app_cards_sort_idx on app_cards(sort);

-- ONE CARD -> MANY INDEPENDENT SERVERS (not mirrors; each is a distinct website)
create table app_servers (
  id bigserial primary key,
  card_id bigint references app_cards(id) on delete cascade,
  url text not null,
  priority int default 10,
  is_default boolean default false,
  enabled boolean default true,
  maintenance boolean default false,
  note text default ''
);
create index app_servers_card_idx on app_servers(card_id);

create table home_sections (
  id bigserial primary key,
  key text unique not null,          -- featured | popular | recent | trending
  enabled boolean default true,
  sort int default 0
);

create table navigation_items (
  id bigserial primary key,
  title text not null,
  target text not null,
  sort int default 0,
  enabled boolean default true
);

create table app_favourites (
  device_id text not null,
  card_id bigint references app_cards(id) on delete cascade,
  created_at timestamptz default now(),
  primary key (device_id, card_id)
);

create table recent_activity (
  id bigserial primary key,
  device_id text,
  card_id bigint references app_cards(id) on delete cascade,
  ts timestamptz default now()
);

create table bookmarks (
  id bigserial primary key,
  device_id text not null,
  url text not null,
  title text default '',
  folder text default 'General',
  created_at timestamptz default now()
);

create table downloads (
  id bigserial primary key,
  device_id text not null,
  content_id bigint,
  url text,
  status text default 'queued' check (status in ('queued','running','paused','done','failed')),
  progress int default 0,
  created_at timestamptz default now()
);

create table content_items (
  id bigserial primary key,
  kind text not null check (kind in ('book','note','pyq','test','video')),
  title text not null,
  institute_id bigint,
  url text,
  copyright_ok boolean default false,
  created_at timestamptz default now()
);

create table institutes (
  id bigserial primary key,
  name text unique not null,
  description text default '',
  logo_url text default ''
);

create table notifications (
  id bigserial primary key,
  title text not null,
  body text default '',
  level text default 'info' check (level in ('info','warn','critical')),
  enabled boolean default true,
  scheduled_at timestamptz,
  created_at timestamptz default now()
);

create table feature_flags (
  key text primary key,
  enabled boolean default false,
  note text default ''
);

create table configuration_versions (
  version bigserial primary key,
  published_at timestamptz default now(),
  snapshot jsonb not null,
  publisher uuid references admin_users(id)
);

create table configuration_audit_logs (
  id bigserial primary key,
  ts timestamptz default now(),
  actor uuid references admin_users(id),
  action text not null,
  detail jsonb default '{}'
);

create table server_health_history (
  id bigserial primary key,
  server_id bigint references app_servers(id) on delete cascade,
  ts timestamptz default now(),
  ok boolean,
  http_code int,
  ms int
);

create table feedback_reports (
  id bigserial primary key,
  device_id text,
  kind text default 'bug' check (kind in ('bug','feedback','broken_site')),
  message text not null,
  meta jsonb default '{}',
  created_at timestamptz default now()
);

create table system_settings (
  key text primary key,
  value text
);

-- Row Level Security: public read of catalog only, admin writes only
alter table app_cards enable row level security;
alter table app_servers enable row level security;
alter table categories enable row level security;
alter table notifications enable row level security;
alter table configuration_versions enable row level security;
alter table admin_users enable row level security;

create policy "public read cards" on app_cards for select using (enabled = true);
create policy "public read servers" on app_servers for select using (enabled = true);
create policy "public read categories" on categories for select using (true);
create policy "public read notifications" on notifications for select using (enabled = true);
create policy "public read versions" on configuration_versions for select using (true);
-- admin writes require service_role / admin JWT (default deny without policy)

-- Hybrid YouTube video discovery: approved sources and an admin-reviewed queue.
create table if not exists public.video_discovery_sources (
  id uuid primary key default gen_random_uuid(),
  source_type text not null check (source_type in ('channel','playlist')),
  source_key text not null,
  label text not null check (char_length(label) between 1 and 120),
  enabled boolean not null default true,
  last_checked_at timestamptz,
  created_at timestamptz not null default now(),
  unique (source_type, source_key)
);

create table if not exists public.video_discovery_queue (
  id uuid primary key default gen_random_uuid(),
  video_id text not null unique check (char_length(video_id) between 6 and 32),
  video_url text not null,
  video_title text not null,
  creator_name text,
  thumbnail_url text,
  source_id uuid references public.video_discovery_sources(id) on delete set null,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  discovered_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create index if not exists video_discovery_queue_pending_idx
  on public.video_discovery_queue(status, discovered_at desc);
create index if not exists video_discovery_sources_enabled_idx
  on public.video_discovery_sources(enabled, created_at desc);

alter table public.video_discovery_sources enable row level security;
alter table public.video_discovery_queue enable row level security;

drop policy if exists "Admins manage video discovery sources" on public.video_discovery_sources;
create policy "Admins manage video discovery sources" on public.video_discovery_sources
  for all to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = auth.uid()))
  with check (exists (select 1 from public.admin_users a where a.user_id = auth.uid()));

drop policy if exists "Admins manage video discovery queue" on public.video_discovery_queue;
create policy "Admins manage video discovery queue" on public.video_discovery_queue
  for all to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = auth.uid()))
  with check (exists (select 1 from public.admin_users a where a.user_id = auth.uid()));

grant select, insert, update, delete on public.video_discovery_sources to authenticated;
grant select, insert, update, delete on public.video_discovery_queue to authenticated;

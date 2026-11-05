-- Video Discovery Engine 2.0: enriched candidates and searchable discovery history.
alter table public.video_discovery_queue
  add column if not exists category text,
  add column if not exists relevance_score integer,
  add column if not exists description text,
  add column if not exists creator_avatar_url text,
  add column if not exists view_count bigint,
  add column if not exists published_at timestamptz,
  add column if not exists duration text,
  add column if not exists search_query text,
  add column if not exists search_category text;

create index if not exists video_discovery_queue_score_idx
  on public.video_discovery_queue(status, relevance_score desc, discovered_at desc);
create index if not exists video_discovery_queue_published_idx
  on public.video_discovery_queue(published_at desc);

create table if not exists public.video_discovery_runs (
  id uuid primary key default gen_random_uuid(),
  run_type text not null check (run_type in ('youtube_search','channel_feed','playlist_feed')),
  query text,
  category text,
  source_label text,
  found_count integer not null default 0,
  queued_count integer not null default 0,
  status text not null default 'completed' check (status in ('completed','failed')),
  error_message text,
  created_at timestamptz not null default now()
);
create index if not exists video_discovery_runs_created_idx
  on public.video_discovery_runs(created_at desc);

alter table public.video_discovery_runs enable row level security;
drop policy if exists "Admins read video discovery history" on public.video_discovery_runs;
create policy "Admins read video discovery history" on public.video_discovery_runs
  for select to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));
grant select on public.video_discovery_runs to authenticated;
grant insert on public.video_discovery_runs to service_role;

-- Create the report store if this project does not have one yet.
-- The Edge Function is the only public submission path.
create table if not exists public.video_reports (
  id uuid primary key default gen_random_uuid(),
  video_id text not null check (char_length(video_id) between 1 and 200),
  video_url text not null check (video_url ~* '^https?://'),
  video_title text,
  reason text not null check (reason in (
    'Video is broken or unavailable',
    'Wrong title or information',
    'Inappropriate content',
    'Spam or misleading',
    'Copyright concern',
    'Something else'
  )),
  details text,
  reporter_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint video_reports_title_length check (video_title is null or char_length(video_title) <= 300),
  constraint video_reports_details_length check (details is null or char_length(details) <= 500)
);
create index if not exists video_reports_created_at_idx on public.video_reports (created_at desc);
create index if not exists video_reports_video_id_idx on public.video_reports (video_id);
alter table public.video_reports enable row level security;
revoke all on public.video_reports from public, anon, authenticated;
grant insert, select, update, delete on public.video_reports to service_role;

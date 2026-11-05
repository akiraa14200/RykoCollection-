-- Public RykoTube source directory and source attribution for published videos.
alter table public.related_videos
  add column if not exists source_id uuid references public.video_discovery_sources(id) on delete set null;

create index if not exists related_videos_source_id_idx
  on public.related_videos(source_id) where source_id is not null;

update public.related_videos rv
set source_id = q.source_id
from public.video_discovery_queue q
where q.video_url = rv.video_url
  and q.status = 'approved'
  and q.source_id is not null
  and rv.source_id is null;

drop policy if exists "Public can read enabled video discovery sources" on public.video_discovery_sources;
create policy "Public can read enabled video discovery sources"
  on public.video_discovery_sources
  for select to anon, authenticated
  using (enabled = true);

grant select on public.video_discovery_sources to anon, authenticated;

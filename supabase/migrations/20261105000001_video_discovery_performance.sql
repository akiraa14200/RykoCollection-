-- Index the discovery queue source FK and cache auth.uid() once per policy statement.
create index if not exists video_discovery_queue_source_id_idx
  on public.video_discovery_queue(source_id);

drop policy if exists "Admins manage video discovery sources" on public.video_discovery_sources;
create policy "Admins manage video discovery sources" on public.video_discovery_sources
  for all to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

drop policy if exists "Admins manage video discovery queue" on public.video_discovery_queue;
create policy "Admins manage video discovery queue" on public.video_discovery_queue
  for all to authenticated
  using (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())))
  with check (exists (select 1 from public.admin_users a where a.user_id = (select auth.uid())));

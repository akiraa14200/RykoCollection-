-- RykoCollection entry likes and period-based trending charts
create table if not exists public.anime_entry_likes (
  id uuid primary key default gen_random_uuid(),
  anilist_id bigint not null check (anilist_id > 0),
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint anime_entry_likes_unique_user_entry unique (anilist_id, user_id)
);
create index if not exists anime_entry_likes_entry_created_idx on public.anime_entry_likes (anilist_id, created_at desc);
create index if not exists anime_entry_likes_created_idx on public.anime_entry_likes (created_at desc);
alter table public.anime_entry_likes enable row level security;
drop policy if exists "Anyone can read entry like counts" on public.anime_entry_likes;
create policy "Anyone can read entry like counts" on public.anime_entry_likes for select to anon, authenticated using (true);
drop policy if exists "Signed-in users like entries as themselves" on public.anime_entry_likes;
create policy "Signed-in users like entries as themselves" on public.anime_entry_likes for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Users remove their own entry likes" on public.anime_entry_likes;
create policy "Users remove their own entry likes" on public.anime_entry_likes for delete to authenticated using ((select auth.uid()) = user_id);
grant select on public.anime_entry_likes to anon, authenticated;
grant insert, delete on public.anime_entry_likes to authenticated;

create or replace function public.get_ryko_top_trending(period_key text default 'month', result_limit integer default 30)
returns table (anilist_id bigint, likes bigint)
language sql
stable
security invoker
set search_path = public
as $$
  select l.anilist_id, count(*)::bigint as likes
  from public.anime_entry_likes l
  where case lower(coalesce(period_key, 'month'))
    when 'month' then l.created_at >= date_trunc('month', now())
    when 'year' then l.created_at >= date_trunc('year', now())
    when 'all' then true
    else false
  end
  group by l.anilist_id
  order by count(*) desc, l.anilist_id asc
  limit greatest(1, least(coalesce(result_limit, 30), 100));
$$;
revoke all on function public.get_ryko_top_trending(text, integer) from public;
grant execute on function public.get_ryko_top_trending(text, integer) to anon, authenticated;

-- Keep individual like identities private while exposing aggregate chart counts only.
drop policy if exists "Anyone can read entry like counts" on public.anime_entry_likes;
drop policy if exists "Users can read their own entry likes" on public.anime_entry_likes;
create policy "Users can read their own entry likes" on public.anime_entry_likes
  for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.anime_entry_likes from anon;
grant select on public.anime_entry_likes to authenticated;

create or replace function public.get_ryko_entry_like_count(entry_id bigint)
returns bigint language sql stable security definer set search_path = public
as $$ select count(*)::bigint from public.anime_entry_likes where anilist_id = entry_id; $$;
revoke all on function public.get_ryko_entry_like_count(bigint) from public;
grant execute on function public.get_ryko_entry_like_count(bigint) to anon, authenticated;

create or replace function public.get_ryko_top_trending(period_key text default 'month', result_limit integer default 30)
returns table (anilist_id bigint, likes bigint)
language sql stable security definer set search_path = public
as $$
  select l.anilist_id, count(*)::bigint
  from public.anime_entry_likes l
  where case lower(coalesce(period_key, 'month'))
    when 'month' then l.created_at >= date_trunc('month', now())
    when 'year' then l.created_at >= date_trunc('year', now())
    when 'all' then true
    else false end
  group by l.anilist_id order by count(*) desc, l.anilist_id asc
  limit greatest(1, least(coalesce(result_limit, 30), 100));
$$;
revoke all on function public.get_ryko_top_trending(text, integer) from public;
grant execute on function public.get_ryko_top_trending(text, integer) to anon, authenticated;
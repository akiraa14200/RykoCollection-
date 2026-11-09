-- Separate entry likes by media type so anime and manga IDs never collide.
alter table public.anime_entry_likes
  add column if not exists media_type text not null default 'anime';

alter table public.anime_entry_likes
  drop constraint if exists anime_entry_likes_unique_user_entry;

alter table public.anime_entry_likes
  add constraint anime_entry_likes_unique_user_entry
  unique (anilist_id, media_type, user_id);

alter table public.anime_entry_likes
  drop constraint if exists anime_entry_likes_media_type_check;

alter table public.anime_entry_likes
  add constraint anime_entry_likes_media_type_check
  check (media_type in ('anime', 'manga'));

create index if not exists anime_entry_likes_type_entry_created_idx
  on public.anime_entry_likes (media_type, anilist_id, created_at desc);

create or replace function public.get_ryko_entry_like_count(entry_id bigint, entry_type text default 'anime')
returns bigint
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::bigint
  from public.anime_entry_likes
  where anilist_id = entry_id
    and media_type = lower(coalesce(entry_type, 'anime'));
$$;

revoke all on function public.get_ryko_entry_like_count(bigint, text) from public;
grant execute on function public.get_ryko_entry_like_count(bigint, text) to anon, authenticated;

create or replace function public.get_ryko_top_trending(period_key text default 'month', result_limit integer default 30, entry_type text default 'anime')
returns table (anilist_id bigint, likes bigint)
language sql
stable
security definer
set search_path = public
as $$
  select l.anilist_id, count(*)::bigint
  from public.anime_entry_likes l
  where l.media_type = lower(coalesce(entry_type, 'anime'))
    and case lower(coalesce(period_key, 'month'))
      when 'month' then l.created_at >= date_trunc('month', now())
      when 'year' then l.created_at >= date_trunc('year', now())
      when 'all' then true
      else false
    end
  group by l.anilist_id
  order by count(*) desc, l.anilist_id asc
  limit greatest(1, least(coalesce(result_limit, 30), 100));
$$;

revoke all on function public.get_ryko_top_trending(text, integer, text) from public;
grant execute on function public.get_ryko_top_trending(text, integer, text) to anon, authenticated;

-- General community opinions for both anime and manga, kept in separate partitions.
create table if not exists public.ryko_media_opinions (
  id uuid primary key default gen_random_uuid(),
  media_type text not null check (media_type in ('anime', 'manga')),
  anilist_media_id bigint not null check (anilist_media_id > 0),
  media_title text not null,
  cover_url text,
  user_id uuid not null references auth.users(id) on delete cascade,
  opinion smallint not null check (opinion between 1 and 5),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ryko_media_opinions_one_per_user unique (media_type, anilist_media_id, user_id)
);

create index if not exists ryko_media_opinions_type_created_idx
  on public.ryko_media_opinions (media_type, created_at desc);
create index if not exists ryko_media_opinions_type_media_idx
  on public.ryko_media_opinions (media_type, anilist_media_id);

alter table public.ryko_media_opinions enable row level security;
drop policy if exists "Users read own Ryko media opinions" on public.ryko_media_opinions;
create policy "Users read own Ryko media opinions"
  on public.ryko_media_opinions for select to authenticated
  using ((select auth.uid()) = user_id);
drop policy if exists "Users submit own Ryko media opinions" on public.ryko_media_opinions;
create policy "Users submit own Ryko media opinions"
  on public.ryko_media_opinions for insert to authenticated
  with check ((select auth.uid()) = user_id);
drop policy if exists "Users update own Ryko media opinions" on public.ryko_media_opinions;
create policy "Users update own Ryko media opinions"
  on public.ryko_media_opinions for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

grant select, insert, update on public.ryko_media_opinions to authenticated;

create or replace function public.submit_ryko_media_opinion(
  p_media_type text,
  p_media_id bigint,
  p_media_title text,
  p_cover_url text,
  p_opinion smallint
)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in required';
  end if;
  if lower(p_media_type) not in ('anime', 'manga') then
    raise exception 'Unsupported media type';
  end if;
  if p_opinion < 1 or p_opinion > 5 then
    raise exception 'Opinion must be between 1 and 5';
  end if;

  insert into public.ryko_media_opinions
    (media_type, anilist_media_id, media_title, cover_url, user_id, opinion)
  values
    (lower(p_media_type), p_media_id, p_media_title, nullif(p_cover_url, ''), auth.uid(), p_opinion)
  on conflict (media_type, anilist_media_id, user_id)
  do update set
    media_title = excluded.media_title,
    cover_url = excluded.cover_url,
    opinion = excluded.opinion,
    updated_at = now();
end;
$$;

revoke all on function public.submit_ryko_media_opinion(text, bigint, text, text, smallint) from public;
grant execute on function public.submit_ryko_media_opinion(text, bigint, text, text, smallint) to authenticated;

create or replace view public.ryko_media_opinion_rankings
with (security_invoker = true)
as
select
  media_type,
  anilist_media_id,
  max(media_title) as media_title,
  max(cover_url) as cover_url,
  count(*)::bigint as vote_count,
  round(avg(opinion)::numeric, 2)::numeric(4,2) as average_opinion,
  count(*) filter (where opinion = 1)::bigint as bad_count,
  count(*) filter (where opinion = 2)::bigint as normal_count,
  count(*) filter (where opinion = 3)::bigint as good_count,
  count(*) filter (where opinion = 4)::bigint as very_good_count,
  count(*) filter (where opinion = 5)::bigint as peak_count
from public.ryko_media_opinions
group by media_type, anilist_media_id;

grant select on public.ryko_media_opinion_rankings to authenticated;

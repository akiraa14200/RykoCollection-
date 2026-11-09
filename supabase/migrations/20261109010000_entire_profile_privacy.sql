-- One visibility setting controls the whole public reader profile.
-- Preserve any existing explicit list-privacy choice during the transition.
alter table public.profiles
  add column if not exists is_public boolean not null default true;

update public.profiles
set is_public = coalesce(public_lists, true)
where is_public is distinct from coalesce(public_lists, true);

create index if not exists profiles_public_lookup_idx
  on public.profiles (id)
  where is_public = true;

-- Profile metadata is visible only when the owner has a public profile.
drop policy if exists "Public profiles are readable" on public.profiles;
create policy "Public profiles or owners can read"
  on public.profiles for select
  to anon, authenticated
  using (is_public = true or id = (select auth.uid()));

-- Reviews are part of the profile, so hide them when the profile is private.
drop policy if exists "Public read visible reviews" on public.title_reviews;
create policy "Public reviews for public profiles or own reviews"
  on public.title_reviews for select
  to anon, authenticated
  using (
    (is_hidden = false and exists (
      select 1 from public.profiles p
      where p.id = title_reviews.user_id and p.is_public = true
    ))
    or user_id = (select auth.uid())
    or exists (
      select 1 from public.admin_users a
      where a.user_id = (select auth.uid())
    )
  );

-- A public profile exposes its library; private profiles remain owner-only.
create policy "Public libraries for public profiles"
  on public.user_library for select
  to anon, authenticated
  using (
    exists (
      select 1 from public.profiles p
      where p.id = user_library.user_id and p.is_public = true
    )
  );

-- Community opinion/rating history follows the same profile visibility.
drop policy if exists "Users read own Ryko media opinions" on public.ryko_media_opinions;
create policy "Opinions for public profiles or own opinions"
  on public.ryko_media_opinions for select
  to anon, authenticated
  using (
    user_id = (select auth.uid())
    or exists (
      select 1 from public.profiles p
      where p.id = ryko_media_opinions.user_id and p.is_public = true
    )
  );

drop policy if exists "Opinions are publicly readable" on public.manhwa_opinions;
create policy "Manhwa opinions for public profiles or own opinions"
  on public.manhwa_opinions for select
  to anon, authenticated
  using (
    user_id = (select auth.uid())
    or exists (
      select 1 from public.profiles p
      where p.id = manhwa_opinions.user_id and p.is_public = true
    )
  );

-- Hide follow relationships involving private profiles, except to either participant.
drop policy if exists "Reader follows are visible" on public.reader_follows;
create policy "Follow relationships for public profiles or participants"
  on public.reader_follows for select
  to anon, authenticated
  using (
    follower_id = (select auth.uid())
    or followed_id = (select auth.uid())
    or (
      exists (
        select 1 from public.profiles follower_profile
        where follower_profile.id = reader_follows.follower_id
          and follower_profile.is_public = true
      )
      and exists (
        select 1 from public.profiles followed_profile
        where followed_profile.id = reader_follows.followed_id
          and followed_profile.is_public = true
      )
    )
  );

grant select on public.profiles to anon, authenticated;
grant select on public.user_library to anon, authenticated;
grant select on public.title_reviews to anon, authenticated;
grant select on public.ryko_media_opinions to anon, authenticated;
grant select on public.manhwa_opinions to anon, authenticated;
grant select on public.reader_follows to anon, authenticated;

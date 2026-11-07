-- Shared, atomic rate limits for public Supabase Edge Functions.
-- Only service_role may invoke the RPC or access the backing table.
create table if not exists public.ryko_edge_rate_limits (
  rate_key text primary key check (char_length(rate_key) between 1 and 160),
  window_started_at timestamptz not null,
  request_count integer not null check (request_count >= 0)
);

alter table public.ryko_edge_rate_limits enable row level security;
revoke all on public.ryko_edge_rate_limits from public, anon, authenticated;
grant all on public.ryko_edge_rate_limits to service_role;

create or replace function public.consume_ryko_rate_limit(
  p_scope text,
  p_subject text,
  p_window_seconds integer,
  p_max_requests integer
)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_key text;
  v_started timestamptz;
  v_count integer;
  v_window integer := greatest(1, least(coalesce(p_window_seconds, 60), 86400));
  v_limit integer := greatest(1, least(coalesce(p_max_requests, 20), 1000));
begin
  if p_scope is null or p_subject is null or length(p_scope) > 40 or length(p_subject) > 100 then
    return query select false, v_window;
    return;
  end if;

  v_key := p_scope || ':' || p_subject;
  insert into public.ryko_edge_rate_limits as current_limit
    (rate_key, window_started_at, request_count)
  values (v_key, now(), 1)
  on conflict (rate_key) do update
    set window_started_at = case
          when current_limit.window_started_at <= now() - make_interval(secs => v_window) then now()
          else current_limit.window_started_at
        end,
        request_count = case
          when current_limit.window_started_at <= now() - make_interval(secs => v_window) then 1
          else current_limit.request_count + 1
        end
  returning window_started_at, request_count into v_started, v_count;

  return query select
    v_count <= v_limit,
    greatest(1, ceil(extract(epoch from (v_started + make_interval(secs => v_window) - now())))::integer);
end;
$$;

revoke all on function public.consume_ryko_rate_limit(text, text, integer, integer) from public, anon, authenticated;
grant execute on function public.consume_ryko_rate_limit(text, text, integer, integer) to service_role;

-- Keep old rate-limit rows bounded. A scheduled cleanup can run periodically;
-- this index makes that maintenance inexpensive.
create index if not exists ryko_edge_rate_limits_window_idx
  on public.ryko_edge_rate_limits (window_started_at);

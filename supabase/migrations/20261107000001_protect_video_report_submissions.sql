-- Force all public video reports through the rate-limited Edge Function.
-- The function uses the service role only after validating the payload and rate limit.
drop policy if exists "Anyone can submit a video report" on public.video_reports;
revoke insert on public.video_reports from anon, authenticated;
grant insert on public.video_reports to service_role;

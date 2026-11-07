# Production security hardening checklist

This branch is isolated from production. Review the preview before merging or applying the migration.

## Included
- Shared, atomic per-IP request limiting for the public video metadata endpoint (20 requests per minute per IP).
- SHA-256 hashes the client address before storage; raw IP addresses are not stored in the rate-limit table.
- Caps metadata batches at 20 unique URLs and request bodies at 16 KiB.
- Adds outbound request timeouts and bounds in-memory metadata cache size.
- Keeps the service-role credential server-side; it is never referenced by frontend code.
- Routes video reports through a server-side Edge Function with payload validation, optional verified reporter identity, hashed trusted-IP limiting (5 reports per 10 minutes), and service-role-only inserts.

## Required dashboard action
Enable **Leaked password protection** in Supabase Dashboard → Authentication → Security and review the password policy. This Auth configuration setting is not changed by the SQL migration.

## Deployment order (only after approval)
1. Apply `supabase/migrations/20261107000000_ryko_edge_rate_limits.sql`.
2. Deploy `supabase/functions/video-metadata` and `supabase/functions/video-report`.
3. Deploy the web app containing the updated report client.
4. Apply `supabase/migrations/20261107000001_protect_video_report_submissions.sql` only after the updated web app and Edge Function are confirmed working.
5. Confirm the Edge Functions have `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, and `SUPABASE_ANON_KEY` available (standard Supabase Edge Function secrets).
6. Test valid/invalid reports, anonymous and authenticated reports, oversized payloads, six reports inside ten minutes, and rate-limit database outage behavior.
7. Re-run Supabase security and performance advisors.

## Not changed
- No production database migration was applied.
- No production deployment, domain, Vercel environment variable, or Auth setting was changed.
- Anonymous video reports remain supported through the new server endpoint; direct table inserts are revoked by the follow-up migration.
- Leaked-password protection remains a dashboard action and is not yet enabled by this branch.

# Production security hardening checklist

This branch is isolated from production. Review the preview before merging or applying the migration.

## Included
- Shared, atomic per-IP request limiting for the public video metadata endpoint (20 requests per minute per IP).
- SHA-256 hashes the client address before storage; raw IP addresses are not stored in the rate-limit table.
- Caps metadata batches at 20 unique URLs and request bodies at 16 KiB.
- Adds outbound request timeouts and bounds in-memory metadata cache size.
- Keeps the service-role credential server-side; it is never referenced by frontend code.

## Required dashboard action
Enable **Leaked password protection** in Supabase Dashboard → Authentication → Security and review the password policy. This Auth configuration setting is not changed by the SQL migration.

## Deployment order (only after approval)
1. Apply `supabase/migrations/20261107000000_ryko_edge_rate_limits.sql`.
2. Deploy `supabase/functions/video-metadata`.
3. Confirm the Edge Function has `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` available (the standard Supabase Edge Function secrets).
4. Test valid YouTube URLs, invalid hosts, empty/oversized batches, 21+ URLs, the 21st request within one minute, and rate-limit database outage behavior.
5. Re-run Supabase security and performance advisors.

## Not changed
- No production database migration was applied.
- No production deployment, domain, Vercel environment variable, or Auth setting was changed.
- Anonymous video reports remain supported. A reliable anonymous per-person limit requires moving report creation behind a server endpoint with trusted client-IP handling; client-side throttling alone is not security.
- Leaked-password protection remains a dashboard action and is not yet enabled by this branch.

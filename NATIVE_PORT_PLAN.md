# RykoCollection Kotlin Native Port

## Objective
Build a real Android application using Kotlin and Jetpack Compose. The React/Vite/Capacitor app remains unchanged. This work lives on the isolated `kotlin-native` branch and in `native-android/`.

## UI preservation rules
- Preserve Ryko's black canvas, charcoal surfaces, soft violet accent, rounded cards, typography hierarchy, poster ratios, translucent top bar, and five-item bottom navigation.
- Keep the menu-based Anime/Manga switch and menu destinations for Ryko Top Trending, Newsroom, and Settings.
- Recreate layouts with native Compose components; do not embed the website in WebView.
- Match interaction and spacing on phone layouts before adding tablet adaptations.

## Feature parity roadmap
1. **Foundation:** Compose theme, navigation shell, AniList transport, Supabase client, CI APK workflow.
2. **Discover and Browse:** home hero/carousels, trending/popular/seasonal shelves, anime/manga mode, search, filters, pagination, entry navigation.
3. **Entry details:** Overview, Characters, Details, related trailers, comments, spoiler controls, likes and counts.
4. **RykoTube:** saved videos, AniList trailers, channels, playlists, Shorts, search, pagination, player, save/share/report actions, playback state and visible-only loading.
5. **Library:** anime/manga/video collections, watching/plan-to-watch/completed/waiting states, search, filters, progress and persisted scroll.
6. **Account:** Supabase auth, Google sign-in, profile, avatar/banner uploads, session restoration and logout.
7. **Community and admin:** entry comments, moderation, reports, admin role checks, catalogue editing, video/channel/playlist approvals and sync tools.
8. **Newsroom:** RSS aggregation, caching, source attribution, categories, search, refresh and offline fallback.
9. **Settings:** language, theme, accent colors, notifications, cache management, About and social links.
10. **Quality:** offline behavior, independent tab scroll/state, accessibility, Android back navigation, performance profiling, emulator tests and release signing.

## Backend compatibility
- Reuse the existing Supabase project, tables, RPCs, storage buckets and RLS policies.
- Keep the existing AniList GraphQL integration and adult-content exclusion.
- Use only the Supabase publishable key in the mobile client. Never ship service-role or secret keys.
- Do not make schema changes as part of the initial native shell.

## Current milestone boundary
The first native foundation commit is not feature complete. It provides a standalone Compose shell, an AniList trending shelf, Supabase client initialization, and an independent Android CI workflow. Remaining pages are intentionally marked as roadmap work and must be implemented before claiming feature parity.

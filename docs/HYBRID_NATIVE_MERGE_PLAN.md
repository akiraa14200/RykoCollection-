# Hybrid-Native Android Merge

## Target

- Destination: `akiraa14200/RykoCollection-`
- Development branch: `hybrid-native-merge`
- Base branch: `feature/hybrid-video-catalogue`
- Application ID: `com.rykocollection.app`
- Production branches and source repositories must remain unchanged.

## Architecture

RykoCollection remains the product and frontend. The existing React + TypeScript + Vite application is packaged with Capacitor. Kotlin/Android code is added only for capabilities that need native Android integration. Supabase, AniList, and the existing Ryko API integrations remain owned by the Ryko application.

## Source compatibility finding

The referenced Musify-App workflow artifact was built from the `StreamGurd` branch and outputs an Android app from `android/StreamGurd`. Its Gradle configuration identifies the app as `com.streamgurd.browser`, its manifest labels the launcher app `Strawhat Pirate`, and its listed app dependency is AndroidX WebKit. This is not evidence of a native music-player implementation. Do not import its app identity, browser screens, manifest branding, or unrelated Cloudflare transcription backend into RykoCollection.

The StreamGurd MainActivity source contains an in-app WebView and a YouTube Music search/embed panel that depends on a user-supplied YouTube Data API key. It does not establish a standalone native audio engine, Media3/ExoPlayer integration, or a foreground media playback service. Its browser activity and UI must not be transplanted into RykoCollection. Its Gradle/Kotlin configuration may be used only as a reference. Any native audio service must be designed and verified independently.

## Phased implementation

1. Establish this isolated branch and correct the Ryko Android application ID.
2. Audit the current Capacitor build workflow and Android generation strategy.
3. Make the Android project reproducible and persistent in source control, without changing the web production deployment.
4. Add a narrow, documented Kotlin-to-web bridge for native capabilities.
5. Integrate native notifications, Android lifecycle handling, and Storage Access Framework file selection where appropriate.
6. Preserve separate audio and video playback ownership; do not create competing playback services.
7. Build and test the debug APK and verify catalogue, authentication, navigation, playback, imports, and permissions.

## Safety / non-regression rules

- Never push changes to `main` or `feature/hybrid-video-catalogue`.
- Never modify the Musify-App repository.
- Never copy secrets, signing keys, local environment files, or backend credentials.
- Do not replace the existing Ryko frontend or its backend.
- Do not claim a feature is complete until source inspection and a successful build/test verify it.

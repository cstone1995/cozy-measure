# Cozy Measure — mobile source development package

Status: SOURCE IMPLEMENTATION, NOT A VERIFIED OR PUBLISHED APP.
No APK or iPhone installer has been produced. Do not advertise this as available.

## Included

- Android: ReMeasure AR distance capture, Cozy brand colours, a carpet-size tab, explicit choice of recorded length and width, confirmed dimensions, fitting allowance, roll-width selection, saved rooms and shop links carrying the room dimensions.
- iPhone/iPad: SwiftUI and ARKit floor-plane point capture, guided length/width sequence, review and manual correction, carpet cut calculation, up to 20 locally saved named rooms, sharing, and shop links carrying dimensions.
- Website: a JavaScript bridge receiving dimensions from app shop links, displaying the saved room throughout browsing, restoring it for up to 30 days in the same browser, and prefilling the existing manual measuring form. Forget-room control included. Local storage does not sync devices or browser profiles.
- CI build workflow for Android and an unsigned iOS simulator build. This workflow has NOT run.

## Current scope and gaps

Camera capture currently targets rectangular rooms by their longest length and widest width. It does NOT scan a complete arbitrary floor plan, detect every recess automatically, handle stairs or calculate seam/pattern layouts. Android requires the customer to select the correct two recorded measurements. A complete perimeter capture flow and plan preview remain to implement before calling this a full-room scanner.

The website bridge is NOT installed. Product-specific calculator application remains to integrate and verify: the bridge displays room sizes but deliberately does not push a generic 4m/5m recommendation into every product. A product may be 4m only, have a minimum cut length, a pattern or pile direction constraint. Existing theme code for rec_width/rec_length_m should be reviewed before enabling auto-fill.

Neither native project has compiled in this environment. Android Gradle download failed with Network is unreachable. Java 21 and Android SDK are needed; this host has Java 17 and no Android SDK. The iOS version needs macOS/Xcode. Both need physical-device AR testing, camera-denial/interruption checks, floor-level validation, and measurement comparisons with a tape. iOS uses plane detection rather than LiDAR-only RoomPlan.

No account, paid hosting, image AI service or cloud image upload is required for the code as written. Apple/Google distribution accounts and signing, any hosted CI charges and store policies are separate and have not been set up. No keys or credentials are included.

## Build Android

Use Android Studio with Android SDK 35 and JDK 21. Open android/ and sync the Gradle project. Run on an ARCore-compatible physical device. Debug build: ./gradlew :app:assembleDebug. Release signing must be configured privately; do not use a debug APK as a public release.

## Build iOS

On a Mac with Xcode and XcodeGen, enter ios/ and run xcodegen generate. Open CozyMeasure.xcodeproj, choose your signing team, and run on a compatible physical iPhone. The simulator build only verifies compilation; it cannot verify room measurements. App icons, production signing, accessibility QA, store metadata and privacy declarations remain release work.

## Website integration

Upload website/room-bridge.js as a Shopify theme asset in an unpublished test theme. Load it once using the asset_url and script_tag Liquid filters. Review the bridge against the actual theme and verify the entire product flow before publishing. Example app handoff:

https://cozyflooringstore.co.uk/collections/all?cozy_room=Living%20room&cozy_length=4.12&cozy_width=3.24

Only room name and dimensions are passed. Do not put addresses or customer identity in these links. Devices using different default browsers will not necessarily share saved website data. The native app retains its own saved rooms.

## Validation completed

node --test tests/room-bridge.test.cjs passes three tests covering valid and invalid handoffs, rotations, fitting allowance, roll widths, and no-fit cases. These are JavaScript tests; the equivalent native implementations still need their own compiled tests. XML and YAML are checked for parse errors. This is not proof of native camera accuracy or production readiness.

## Attribution

Android is derived from the supplied ReMeasure project, Apache License 2.0. The full upstream licence remains at android/LICENSE. Modified Android files carry change notices. Preserve copyright and licence notices when distributing. ReMeasure's upstream README is retained at android/README.md. Original app art remains a placeholder; a separate Cozy icon set is still required.

## Continuation checklist

1. Run Android and iOS CI builds and correct compiler issues.
2. Add full perimeter capture, floor-plan preview and explicit shape support.
3. Test tracking, point drift, interrupted sessions, floor-vs-furniture selection and accuracy on real devices.
4. Replace upstream icon assets and remaining generic copy with approved Cozy assets.
5. Connect and test the actual Shopify product calculator per available roll width and minimum length.
6. Complete signing and a private device test distribution before release.

# Cozy Measure

Cozy-branded Android and iOS room-planner source. This is a development build, not a published or field-verified measuring service.

## Customer flow

Start screen → choose a room name → three illustrated instructions → camera → add floor corners in sequence → finish the outline → review wall lengths, area and plan → confirm measurements → save locally → calculate a carpet cut → shop.

Saved rooms and manual tape entry are accessible from the start screen. No account or AI service is required for the local measuring features.

## Implemented

- Android ARCore and iOS ARKit horizontal-surface capture.
- Corner-by-corner closed outlines, undo, reset, overlap rejection and a maximum of 40 corners.
- Floor-plan drawings with wall-length labels, measured floor area and enclosing dimensions.
- Concave outlines such as L-shaped rooms can be represented; stairs and multiple floor levels are outside this flow.
- Carpet roll widths, trimming allowance, orientation comparison, cut rounding and separate purchased area. These remain estimates requiring range-specific checks.
- Locally saved named rooms and outlines; sharing; shop links carrying room dimensions and outline.
- Cozy welcome screens and illustrated onboarding on both platforms.

## Important remaining work

- Real-phone camera, tracking, drift and accuracy checks, including the Galaxy S20 FE. The environment used for development cannot perform these.
- Automated visual UI checks and accessibility review. Compilation does not prove the appearance or customer flow.
- Storefront bridge installation and per-product calculator integration. Shop links carry measurements, but the live store does not yet automatically apply them.
- Product minimum lengths, available widths and pile/pattern constraints need to be enforced using the actual selected product.
- iOS production icons, signing and TestFlight distribution. The successful CI iOS build targets a simulator and is not installable on an iPhone.
- Stable Android release signing and release packaging. Debug builds may require uninstalling an older build because CI-generated debug signing keys can differ; uninstalling clears saved rooms.
- This does not reproduce the reference application's furniture planning, virtual objects or full commercial feature set.

## Validation

The V2 Android build, five native room-geometry tests, iOS simulator compilation and three website sizing/handoff tests passed in GitHub Actions. The guided start-screen update also passed Android compilation, iOS simulator compilation and the existing tests in run 38876339887. No camera accuracy claims have been verified.

## Builds

Android: open android/ in Android Studio using JDK 21 and SDK 35, or run ./gradlew :app:assembleDebug :app:testDebugUnitTest there.

iOS: on macOS with Xcode and XcodeGen, run xcodegen generate in ios/, open the project, select a signing team and run on a compatible physical device.

The GitHub workflow builds both platforms and runs tests on source pushes. No backend hosting or image AI API is needed by the current native code. Distribution accounts and release policies are separate.

## Website bridge

website/room-bridge.js is prepared source only. It accepts room names/dimensions from app shop links, stores them locally for up to 30 days in that browser, and displays a room summary. It requires reviewed installation in the actual Shopify theme. Do not blindly write the same cut into every product calculator.

## Attribution

Android derives from the supplied Apache-2.0 ReMeasure source. Preserve android/LICENSE and upstream notices. Modified files carry change notices.

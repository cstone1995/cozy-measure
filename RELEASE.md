# Release preparation

Status: not ready for public download. Debug builds are for phone testing only.

## Permanent Android signing

Create one private release keystore on the owner's trusted computer and keep an encrypted backup. Do not commit it, its passwords, or a base64 copy to this public repository. Use the same key for every future update. An APK signed with this key cannot update the existing debug app; migration must be tested before public release.

Configure the GitHub environment `android-release` with secrets `COZY_KEYSTORE_BASE64`, `COZY_STORE_PASSWORD`, `COZY_KEY_ALIAS`, and `COZY_KEY_PASSWORD`. The workflow `Signed Android release` decodes the key into a temporary private file, builds and tests, verifies the APK signature, emits an APK checksum, and removes the temporary key. It does not publish automatically. No permanent signing key has been created or configured by this change.

Use the resulting `Cozy-Measure.apk` for customer downloads, never a debug ZIP. Verify that an update signed with the same key preserves saved rooms. Confirm certificate fingerprints across both builds. Build artifacts expire; a stable HTTPS download host still needs configuring after acceptance.

## Acceptance before website download

- On the Samsung S20 FE: repeat three independent scans of the known 194 cm wall, then test longer walls in a furnished room against a tape. Record each result, device, lighting, and app version. No fixed accuracy guarantee is established yet.
- Ceiling mode: scan a broad patch near a light fitting first, then corners. The overlay shows detected planes. If a plain ceiling cannot be detected, use floor mode. Test sloped ceilings and floor-only recesses as unsupported examples.
- Save, restart, load, share, delete app data deliberately, and confirm expected persistence. Current saved rooms remain on the device only.
- Website: verify outline and room dimensions survive the handoff, navigation, reload, forgetting the room, and 30-day expiry. Browser storage does not synchronize across devices, browsers, or an in-app browser and an external browser.
- Product calculator: verify the current app's units, actual roll widths, minimum length, rounding, recalculated price, and cart line properties on at least one 4m-only and one 4m/5m product. Never replace the third-party calculator's pricing/cart logic or infer widths from a title. Product auto-fill is installed on unpublished theme 206108197204 only. Cozy Felt 4m-only and Belmont 4m/5m were checked in that preview; the live theme remains unchanged. See website/INTEGRATION.md for evidence and remaining cases.
- Publish the room bridge on a current unpublished theme first. Review that exact preview before modifying the live theme. Do not publish the old draft theme wholesale.
- Add the Android download page only after testing. Show install instructions, camera permission purpose, local storage/privacy information, device requirements, and estimate limitations. iPhone distribution still requires signing and TestFlight/App Store setup; do not offer the Android APK to iPhones.

## Privacy behavior to verify against the final build

The native code uses camera frames locally for AR tracking and stores named rooms on the phone. It has no account or room-upload service. Tapping Shop sends the room name, dimensions, and outline to the store in the URL. The bridge saves them in that browser for 30 days and offers Forget this room. Clearing the URL after import does not remove data already received in server logs or by scripts that ran earlier. Store privacy wording must explain this handoff before customer launch. No cross-device synchronization is implemented.

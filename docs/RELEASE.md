# Release gate — deliberately closed

The release decision and Windows Studio branch audit are recorded in the [release architecture handoff (Persian)](interop/RELEASE_ARCHITECTURE_FA.md).

`assembleRelease`, `bundleRelease` and release packaging fail intentionally. Do not remove the guard just to produce a public APK.

Before enabling release:

1. Build/lint/test the Android project on a real SDK and exercise the acceptance matrix in `reference/TEST_PLAN.md` on API 26 and a current Android version. Update compile/target SDK to the store requirement at publishing time.
2. Finish the production updater/installer, strict ZIP and schema/semantic validation, private streaming staging, disk checks, cancellation, recovery and active-version transaction. The current small fixture decryptor is NOT a general installer.
3. Obtain the owner's fixed HTTPS manifest URLs and approved host allowlist; replace the intentionally empty bootstrap catalog with schema-valid real entries. Never ship example.com, test fixture endpoints or arbitrary URL entry.
4. Inject production raw32 Ed25519 PUBLIC verification keys and AES-256 distribution keys via an external secret build mechanism. No private signing key in Android. Never reuse `keys.test.json` or ship it as an Android asset. Implement key rotation and missing-key “app update required” states. AES embedded for offline playback is extractable, not DRM.
5. Complete encrypted-package interoperability with a NEW Windows Studio export and fresh nonce, then v1→v2 updates preserving bookmarks/reviews/history. Golden fixture verification alone does not establish this.
6. Establish licensed audio/lyrics/art permissions, offline distribution rights and credits. Demo catalog titles are not licensed packages. Review the source artwork before public distribution.
7. Complete app signing outside Git, backup/privacy policy, dependency/license/security audits, accessibility and store review. Disable debug demo paths automatically in release and inspect the final artifact.
8. Replace this unconditional guard only with a tested fail-closed validation task enforcing real endpoints/hosts/keys, no test assets and all acceptance gates.

No production URL/key was invented or requested in chat. No Supabase backend is necessary for the documented static-host/offline v1.

## Installer implementation follow-up

A real WorkManager/staging installer is now implemented; see
[provisioning, implemented checks and remaining gates](interop/ANDROID_INSTALLER.md).
This supersedes “fixture-only path” descriptions for source implementation, **not**
release readiness. Real Gülüm II authentication/playback and native/device validation
remain blocked/unverified; release guard stays closed.

## Download size

Release uses R8 code optimization and resource shrinking. Once the release gate
is satisfied, prefer the Android App Bundle for stores that support it so users
receive device-specific APKs instead of a universal APK. Measure download size
with the store console or `bundletool get-size total` on the generated APK set;
the Android workflow reports debug APK bytes separately for tracking only.

Keep lesson media in downloadable packs, not the base app. Resource shrinking
removes unreferenced Android resources, not files under `src/main/assets`.
Review the base APK with APK Analyzer before publishing: the current app does
bundle some referenced artwork, fonts, and a game sound in addition to icons
and text, and dependencies also contribute to download size.

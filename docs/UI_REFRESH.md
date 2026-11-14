# Native UI refresh

## Implemented

- Bundled Vazirmatn Regular + actual Bold weights applied to every Material typography slot (both UI languages); no remote font loading. Brand `Reedance` uses an explicitly instantiated static Manrope ExtraBold font, independent of UI locale.
- Removed the catalog search field and unused search state/filtering.
- Same Uicons Regular Rounded **3.3.1** geometry as `src/icons.jsx` in the reference web branch, drawn with native Android vectors. Back arrow auto-mirrors; playback icons do not. Removed the large Material extended-icons dependency.
- Deleted the license paragraph from About. Original license remains bundled, with required **Uicons by Flaticon** linked attribution at the catalog footer. Removing that credit requires an appropriate license from the publisher.
- Only Android's splash remains, kept until `AppModel.ready`; removed the second Compose branded splash. No extra timed splash. Setup is still a one-time language picker, not another splash.
- 48dp circular country-flag images for GB/ES/KR (plus All), accessible with localized language descriptions. App UI language remains a separate setting.
- Shared pill tabs replace basic filter chips: rounded shapes, selected tint/elevation/weight, 150ms color/scale changes, 48dp minimum touch height, Tab role/selected semantics. Reduced-motion changes are immediate. This also updates the song/starter/profile choice rows.

## Performance changes (not a measured benchmark)

- Immediate page switching instead of a 320ms whole-page AnimatedContent/size transition that composed both pages concurrently.
- Saveable state per destination preserves Home/Explore filters and grid position on return.
- LazyVerticalGrid composes visible catalog items rather than eagerly laying out all cards and covers in a scrolling Column.
- Original 960px artwork moved byte-for-byte to drawable-nodpi, avoiding Android density-based enlargement. Decoded at 1/2 dimensions on Dispatchers.IO with a shared, bounded 6MiB bitmap LRU; no synchronous full-resolution cover decoding during navigation. The song gate uses the same cache.
- Logo downscaled once to 512px and marked nodpi rather than decoding/density-upscaling the 1254px original.
- Filter result computation memoized by catalog/language/access.

## Reproducibility and verification

To regenerate the native icon vectors from the official npm tarball:

```sh
# Extract @flaticon/flaticon-uicons 3.3.1 from registry.npmjs.org first.
pip install fonttools brotli
python scripts/import_uicons.py /path/to/extracted/package
```

`assets/uicons-provenance.json` records each CSS glyph, codepoint and Android drawable; `assets/Uicons-LICENSE.txt` is the original package license. Source outlines are converted, not traced/replaced with lookalike Material icons.

Host validation: 13 asset/schema/source-guard tests pass; Kotlin/KTS syntax and XML checked. New flag-tab interaction instrumentation test is supplied but not executed here. The environment still has no JDK or Android SDK, so this update is **not claimed as compiled or profiled**.

On-device acceptance:

1. Cold start and relaunch: one splash only, no flash of a second logo screen; English/Farsi setup remains functional.
2. Switch Home ↔ Explore rapidly and after scrolling; filters/position persist, no whole-page fade or resize delay. Use Android Studio System Trace/JankStats to compare frame times in a profileable/release-equivalent build; debug/JIT performance alone is not representative.
3. Toggle every language flag/access pill in both RTL/LTR and dark/light; verify selection, TalkBack names and reduce-motion.
4. Inspect vector icons, bookmark tint, star tint and all playback controls against web. Verify Persian bold, Latin branding and Korean fallback text on API26 and current Android.
5. Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `:app:connectedDebugAndroidTest` before declaring build/device acceptance.

## Filter and navigation follow-up

- Lazy-grid item fade-in (180ms), fade-out (140ms) and placement (220ms) animate filter changes with stable song IDs; reduced motion sets all three durations to zero. This does not reintroduce a whole-page transition.
- Downloaded replaces Playable and is computed from the currently installed lesson IDs, not the demo catalog marker. Installing/removing the demo updates it reactively; a fresh install has zero downloaded songs.
- Four actual Solid Rounded native vectors complement the outline nav icons; the selected item uses its filled counterpart and primary tint. No synthetic stroke thickening.
- FloatingNavigation has a 50%-radius capsule in both sizes and 2dp shadow. Horizontal insets animate from 20dp to 52dp and labels fade/shrink over 190ms. All four icon targets remain accessible when collapsed.
- A parent NestedScrollConnection observes vertical USER-consumed scroll in every scrollable main destination without consuming it. After a 36dp direction threshold, downward scroll compacts; upward scroll expands. Changing pages resets it to expanded; scrolling against the top also expands. This hysteresis prevents twitching during tiny scrolls. Mini-player is a separate full capsule.
- Android navigation-bar scrims/contrast enforcement disabled where supported; transparent SystemBarStyle plus theme-aware button/gesture contrast keeps system and app backgrounds continuous. Android/OEM-owned gesture handles or three-button icons are not hidden. Test both gesture and three-button modes on API26/29/35+.
- Circular flag assets are derived from the reference public/flags SVG files, rasterized at 192px. Korean artwork is fitted onto the white circle to retain all four trigrams. Labels remain localized semantic descriptions, not emoji.

Added host guards for filled variants, circular PNG dimensions, installed-only filtering, item animations and transparent/capsule navigation. A compact-navigation interaction instrumentation test is supplied but not run in the sandbox. APK compilation and on-device animations still require Android Studio.

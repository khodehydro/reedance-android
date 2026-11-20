# Android performance audit

## Static findings and changes

Priority indicates **scope and likely impact**, not a measured contribution to jank. We can verify the work in the old code paths, but not its frame-time cost without an Android trace. No palette, typography, spacing, animation timing, or dependency changes were made.

| Priority | Verified code path | Change |
| --- | --- | --- |
| High · shared navigation | The shell eagerly composed routes on timers after startup, explicitly waited for a frame on the first visit to a route, and measured visited pages at full constraints even when positioned offscreen. | `App.kt`: mount routes on demand and select in the same state update. Keep visited state and existing entry transitions, but skip measuring and placing hidden page content. Mount the saved route on recreation. |
| High · scroll/layout | The bottom-bar width animation was read in the shell composition even though only the bar and mini-player need that value. | `App.kt`, `FloatingNavigation.kt`, `MiniPlayer.kt`: read animated width in the two layout modifiers; measure the same width without frame-by-frame invalidation of the shell composition. |
| High · long quiz results | Finishing a lesson eagerly composed all results and copied the growing answer list on each answer. A listening exercise can exist for each lyric line. | `Quiz.kt`: append to a snapshot list and lazily compose answer cards with keys. Preserve the headings, spacing, feedback, and actions. |
| Medium · lesson and artwork | Installed-lesson reads parsed the same JSON even when the bounded cache already held that installation. The mini-player and song artwork independently decoded the same cover. | `LocalContentRepository.kt`, `AppModel.kt`: use the existing four-entry cache on a validated installed-row match, separate JSON parsing from disk I/O, assemble the catalog on Default, and move startup installer construction/recovery to IO. `InstalledArtwork.kt` and its callers share a 2 MiB cache with serialized cache misses, the existing sampling size, and a path check against stale artwork. Active package directories are immutable generations. |
| Medium · playback | Lyric-boundary updates invalidated the playback screen's parent composition. Wave/banner drawing recreated paths and the banner recreated its contrast brush on redraws. | `Song.kt`: derive lyric activation at the row with an equality boundary. `WaveSeekBar.kt`, `BannerGradient.kt`: reuse draw objects; keep waveform, shader, colors, and pacing. |

## What was reviewed and left in place

- **Framework/startup:** Gradle/Compose configuration, splash/readiness, Activity and ViewModel initialization, Room/DataStore, pack recovery and dispatchers. Media3 controller calls remain on its owning looper. No startup tasks are bypassed. A cold-start trace should determine how much time is in recovery, installed catalog assembly, starter assets, and first composition before changing readiness sequencing.
- **Lists and navigation:** Home/Explore grids, Library pager and rolling prefetch, playback list, story pagination, download/quiz/practice, and retained tab state. Most large lists already have item keys and content types. Library's bounded prefetch and inline table gesture behavior were not retuned. Use large installed and bookmarked datasets to measure Explore filtering, deferred first-entry work, and retained-page memory before adjusting caching/prefetch.
- **Motion and rendering:** Page/item entrances already use graphics layers; sheets deliberately pause background motion. Player and Home graphics use draw-phase clocks. Honeycomb board generation, RuntimeShader fill cost, layout-height animations, Story pagination, and match-board connector layout are candidates to profile, not diagnosed causes of missed frames. Do not reduce visual fidelity without a main-thread/RenderThread/GPU trace showing the trade-off.
- **Artwork and memory:** Bundled song covers already use background decoding and a bounded cache. Installed artwork retains its prior sampling and crop. Profile memory after repeated route visits and cover swaps to see whether the retained tabs or large already-referenced static resources dominate.

## Verification in this checkout

- No Java, Android SDK/ADB, emulator, device, or Perfetto executable was available. The Gradle test/lint/build command stopped before configuration. No FPS, startup, memory, or jank numbers were collected, and runtime/device regression remains pending.
- Added a Compose layout regression test for an inactive page (initially not measured, state preserved when shown again). Existing navigation, compact-tab and mini-player tests cover relevant interactions; instrumentation was not run here.
- `git diff --check` passed. `scripts/test_inspect_rdpack.py`: 8 passed. `scripts/test_assets.py`: 76 tests with the same 35 failures and 3 errors on the baseline and the audited tree (matched failing test names). The previous GitHub Android job stopped at Android SDK setup, before Gradle.

## Repeatable Android acceptance pass

1. Build the baseline and the changed revisions with identical settings (ideally profileable release-like builds after the existing publishing gate is satisfied; debug results are directional only). Record device model, API, actual refresh rate, thermal state, build type, dataset and whether caches are cold or warm. Test API 26 plus a recent device at 60 Hz and, where supported, 90/120 Hz. Disable unrelated notifications and background downloads for the controlled run, then explicitly repeat with an active download.
2. On each build, trace identical scripted journeys at least five times: cold/warm launch; first and repeat tab navigation; fast Home/Explore/Library scroll and search with large data; Artist → Song → back; play through several lyrics while collapsing the player; Listening/Match/long results; solve a Game level; Story paging and font changes; open and close a bottom sheet and dialog; download transitions; background/foreground and rotation/process recreation. Verify destinations, route and scroll restoration, audio focus, playback state, and selected tab/accessibility along the way.
3. Capture Android Studio System Trace/Perfetto with FrameTimeline on supported APIs. Compare app-vs-compositor deadline misses, UI/RenderThread slices, measure/layout, Binder/disk activity, and GC. For per-journey supplemental data, reset and capture `adb shell dumpsys gfxinfo com.hydro.reedance.debug reset` / `adb shell dumpsys gfxinfo com.hydro.reedance.debug framestats`. Use `am start -W` or Macrobenchmark for startup; capture steady and peak heap after repeating tab and artwork cycles. Compare P50/P95/P99 frame duration and deadline misses at the device's actual frame budget (16.67/11.11/8.33 ms), not just average FPS.
4. Use Layout Inspector recomposition counts in a separate diagnostic run (not as timing data). Verify hidden pages aren't laying out, nav width changes don't recompose the shell every frame, and lyric advancement only invalidates affected rows. Add Macrobenchmark journeys and consider a Baseline Profile once startup and navigation traces identify the hot code paths. Do not claim a target refresh rate until the same journeys pass on hardware.

## Interaction-latency follow-up

This pass traced pointer routing, scroll mutations, and animation timing from static code. Confirmed code paths (not measured frame-time attribution):

- `SearchDismissHost` cleared focus and hid the IME on the **initial down**, before the scrollable began tracking movement. It now observes the outside gesture through release at the Final pass without consuming events, so IME changes are no longer initiated during that drag. Back and keyboard search actions keep their existing behavior.
- `Deck` consumed **every** pointer change on outgoing sheets at the Initial pass. It now disables outgoing controls and hides their semantics without intercepting motion; the incoming sheet has hit-test precedence. Fade, slide and scale start together rather than waiting out the outgoing 220 ms animation. Disposed sheets are removed by Compose lifecycle, not a timer. `Quiz` advances immediately on an explicit Continue; the existing success-reading interval is intentional and does not intercept scrolling. Leitner duplicate-submission protection now follows the persistence job, not an animation timer.
- Library issued suspending scroll resets on first composition and when a pager page became inactive. It now chooses a fresh list state on an actual filter request and resets a previously scrolled page only when both the pager and that inactive list are idle. Explore no longer restarts the grid reset merely because the artist detail closed. Library search starts without a fixed debounce; matching/sorting runs on Default and obsolete requests can cancel. Library's short coalescing window still applies only to background prefetch, not to touch or visible results.
- Shell bar collapse and mini-player playback updates now read state in Scaffold's bottom-bar slot, not the main shell. The nested-scroll observers were checked: both return zero consumption, and their thresholds trigger chrome changes only, not scroll recognition. Native touch slop was left unchanged.

Explore/Library search chrome and the compact song header still animate height, so they can require viewport remeasurement. Their contribution to reported frame misses is unmeasured. Before replacing these effects, trace a sustained vertical drag through the exact collapse threshold with IME closed/open, then do a second drag on outgoing quiz cards. Compare input events, main measure/layout slices, frame deadlines, and actual offset movement before and after. Also verify no duplicate submissions during rapid Continue taps, IME dismissal after outside taps and drags, Library filter/tab resets, and Explore artist return scroll state.

`DeckInteractionTest` exercises a drag before the outgoing transition ends with the test clock paused. Android build/instrumentation and before/after traces remain blocked by missing Java/SDK/device in this workspace. `git diff --check` and the eight pack checks passed; the asset suite reported the same failing test names as the preceding commit (35 failures, 3 errors).

## Concurrent animation / input follow-up

This patch stays in animation code. Static inspection established these sources of work, but cannot establish which one misses a device's deadline:

| Component | Work inside the overlap | Targeted change |
| --- | --- | --- |
| `FloatingNavigation.kt` | `BoxWithConstraints` was *inside* the width-animated capsule, so changing constraints could subcompose the strip at every width step. Each selection-color sample was also read in composition. | Calculate compact geometry from the stable outer slot; keep actual capsule width and label animations. Read color in the tab's draw modifier. |
| `MainHeader.kt`, `BannerMessages.kt` | Two decorative slide/fade transitions also animated the size of their content inside clipped, independently sized viewports. | Remove the redundant size tween; keep the slide/fade, easing, durations, and surrounding viewport clipping. |
| `HoneycombPuzzle.kt` | Tile background and letter colors were read in the board's composition during a solve animation. | Sample background color in drawing; isolate foreground color to the glyph's composition. The shared entrance wave, tile transforms, and colors are unchanged. |
| `BannerGradient.kt`, `SongDownloadScreen.kt` | The fallback banner recreated size-dependent draw objects on each redraw. The ring allocated a path and could keep its infinite clock alive while the route was retained but hidden. | Cache fallback gradient, strokes, and path per draw size; keep time reads draw-only. Reuse the ring path and run its clock only on the visible route. |

**Still needs device evidence:** the Explore/Library height-changing search headers and song compact controller intentionally move and resize their neighboring content. Replacing them with overlays would change viewport/scroll behavior. Their per-frame measurement, the nav width's necessary row layout, and the cost of the large RuntimeShader on RenderThread/GPU are the next suspects to distinguish, not proven sources of stutter.

### Four separate traces

Use the same physical device, refresh rate, build type, and sample data for the baseline and patch. Run each scenario several times. Use a separate filename per run; start the interaction as soon as capture starts:

```sh
adb shell perfetto --txt -c - -o /data/misc/perfetto-traces/reedance-A.pftrace < scripts/performance/interaction-overlap.pbtxt
adb pull /data/misc/perfetto-traces/reedance-A.pftrace .
```

| Capture | Reproduce | Compare |
| --- | --- | --- |
| A · scroll only | Drag/fling the same list once chrome is settled, away from the rotating hero. | Actual scroll frame durations and missed deadlines. |
| B · animation only | Hold still through a full header/banner cycle; separately trigger a card or tile animation and release. | Standalone measure/layout, draw, RenderThread and GPU cost. |
| C · scroll + animation | Repeat A through the collapse threshold; separately drag with the hero or card animation still on screen. | Additional cost versus A and B, input timestamps, long measure/layout slices, app versus compositor jank. |
| D · tap + animation | Tap another actionable control while the card, nav width, or tiles are already animating. | Time from dispatched tap to first frame containing the response; queued main-thread work and input handling. |

Inspect FrameTimeline expected/actual tracks, UI vs RenderThread and input events. Compare P95/P99 frame duration and missed-deadline count for each scenario separately; do not infer input latency from FPS alone. Run Compose recomposition diagnostics separately so they do not bias timing. Android 12+ provides FrameTimeline; use Studio System Trace/gfxinfo on earlier versions.

No Android capture or build was possible in this workspace: no Java, SDK/ADB, emulator, or attached device. Diff checks and all eight pack inspection tests passed. The 76-test asset suite showed the same failing test names as the preceding commit. Per-scenario traces and visual/device verification remain open; there is no measured before/after improvement to report yet.

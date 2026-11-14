# Coroutine / responsiveness pass

## What moved, and what must NOT move

| Work | Execution/lifetime |
|---|---|
| Asset text/file reads, cancellable audio copy + incremental SHA, private file removal | LocalContentRepository suspend API, Dispatchers.IO |
| JSON parsing, quiz generation/grading, result serialization, active lyric derivation | Dispatchers.Default / flowOn(Default) |
| Startup catalog/starter/installed-demo reads | Structured async children in coroutineScope; awaited before removing the single splash |
| Room queries / transactions | Suspend DAO + Room executors; serialized writes inside IO transactions |
| Preferences | DataStore's suspend edit/Flow (DataStore already owns background disk I/O) |
| Bitmap decode | Existing IO produceState + bounded shared LRU |
| MediaController commands, callbacks, progress samples, Compose state/rendering | MAIN looper, required by Media3 / Compose; no blocking future.get |
| TTS readiness | Lazy engine, CompletableDeferred.await with timeout; voice lookup/speech binder calls on IO, serialized and cached by locale |

Putting Compose rendering or Media3 methods on Dispatchers.IO is incorrect; wrapping every tiny catalog lookup in launch would add overhead. Six-song filtering remains memoized on UI; heavy lesson work and disk operations have explicit main-safe boundaries.

## Removed avoidable work

- No MediaSessionService connection or TextToSpeech engine boot on an ordinary cold Home launch. A prepared lesson connects Media3 when the song is opened; a pronunciation request initializes TTS on demand. Player setup uses a suspending future completion listener; get() is invoked ONLY by the completed-future callback on main. One mutex prevents duplicate connects; ViewModel teardown releases even a late future result. Entering Quiz cancels pending play commands, so late preparation cannot start playback in the quiz.
- Removed the unconditional 700ms poll/write loop. UI progress samples every 500ms ONLY while playing AND position is observed. Hidden/paused screens do not keep it ticking (active-lyric flow has a 5s subscription grace period).
- Durable playback is separately checkpointed every 5s while playing, plus pause/seek/onStop. A pure PlaybackCheckpoint gate avoids unchanged/tiny repeated writes; one conflated IO channel serializes pending saves. No timer writes while paused. On abrupt process/device death, up to about five seconds of most-recent playback position may be lost, rather than continuously writing to disk. Save errors are surfaced; this is not a durability guarantee on process kill.
- Song root observes distinct active lyric IDs, not every millisecond/progress tick. Only PlayerPanel observes progress. Lyric arrays and lexeme lookup map are memoized by lesson, removing repeated allocation/scans at each tick.
- Profile reads SELECT COUNT(*) Flow, not every historical answer/JSON blob. Bookmark/review/starter Room emissions use distinctUntilChanged. These three small streams stay eager intentionally: mutation/review behavior must not depend on UI subscription timing.
- Review quiz builds a session once off-main using a fresh suspend DAO snapshot of due cards, not an initially-empty hot-flow snapshot. Grading is a structured cancellable coroutine; repeated check taps are blocked while it runs. Responses are persisted after grading without blocking rendering.
- Bookmark/step mutations read current DB state inside a serialized transaction, rather than a potentially stale UI Flow cache. Wrong-word inserts and the quiz answer commit together. Repeated stale flashcard grades are ignored.
- File install/delete/restore serialized by a repository mutex. Audio copy checks ensureActive per 64KiB chunk and does not allocate/read the entire WAV. Only the short final move/database-pointer commit and staging cleanup are NonCancellable. This remains a LOCAL DEBUG DEMO, not the production rdpack installer.
- CancellationException is rethrown, not displayed as an error or swallowed by runCatching. Database is a single application-context instance; no synchronous database close/executor join on the main thread at ViewModel teardown.

## Verification / remaining boundaries

17 host asset/schema/source guard tests passed. Added four pure JVM PlaybackCheckpoint unit tests; they have NOT been run in this sandbox. Kotlin syntax/XML/resource checks are not compilation. No JDK/Android SDK is installed here, so no APK build or measured speed/FPS claim is made.

On Android Studio / device:

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use CPU Profiler/System Trace to compare cold startup, first Home→Explore, first song opening, resumed/background audio and long scrolls. Observe Main thread slices, frame time/jank and database I/O, not just visual impressions. Test on a slower physical phone; debug JIT/instrumentation can be slower than an optimized shipping build. The release gate remains closed pending the production security/installer work.

Acceptance scenarios: play/pause/seek/repeat/mute; open quiz while connection is pending; pause from media notification; background playback/checkpoint after onStop; reinstall/delete without losing progress; rapid bookmark/step taps; grading/due queues; canceled install; corrupt local lesson; offline/no-TTS-engine and engine initialization timeout. Profile cold vs warm TTS separately. Playback continuity on Android lifecycle/process death and cancellation races still require device/instrumented tests.

## Scroll follow-up

Source inspection found two additional UI-side jank risks that dispatchers do not fix:

- Song used an eager vertically scrolling Column: every lyric/token FlowRow, word,
  grammar and phrase card in the selected tab was composed and measured even off-screen.
  It now uses LazyColumn with keyed lesson rows and content types. The collapse check
  uses a derived lazy-list index/offset, not a per-pixel root composition read.
- Starter similarly composed every card in a section up front. Its groups, sound,
  grammar, word and table cards are now separate lazy items. Individual alphabet
  groups/tables remain atomic items, not nested vertical scrolling containers.
- The floating navigation previously shrank its Scaffold bottom slot during the
  label animation. Because that slot also supplies each page's trailing content
  padding, every animation frame invalidated list layout. An expanded-height slot,
  computed from the current label line height/font scale, now stays stable while
  the capsule still animates its own width/labels/height within it. The slot remains
  transparent and content still scrolls underneath; final items remain reachable.

19 host source/asset checks pass after this follow-up. These checks verify structure,
not frame performance or native compilation. No on-device trace was available, so
these are code-level fixes for identified risks, not proof that all jank is resolved.
Compare the same Home/Explore fling with navigation expansion, then long Lyrics,
Words and Starter scrolls on a physical phone using System Trace / FrameTimeline.
Check compact/expanded navigation at all three app font sizes, system font scaling,
RTL/LTR and reduced motion. Preserve filter reflow and press animations.

## Centered header / target-language selection

The shared main header now has equal 48dp side controls, with an isolated centered
brand slot. Every three seconds the wordmark exits upward while the logo enters
from below, then reverses the content on the next cycle (same upward direction).
The slot is fixed-height and clipped, so this does not resize the header or lists.
The timer only runs while the destination lifecycle is STARTED; reduced motion
keeps the wordmark static. Accessibility exposes one stable Reedance label.

The former logo position is a circular target-language flag (English by default).
Its modal bottom sheet selects English, Spanish or Korean. This is a persisted
DataStore setting independent of the interface language, shared by Home/Explore.
The inline language pills / All languages option are removed; access/downloaded
pills and item-reflow animations remain. App-language selection is only in initial
setup and Profile settings, not the header. Song/quiz contextual headers retain
their back controls.

20 host checks and 31 Kotlin/KTS syntax checks passed. A new instrumentation test
covers opening the sheet, selecting Spanish and opening Profile; it has NOT run.
Native build, visual centering, animation timing and TalkBack still need device QA.

## Cover clipping and search interaction follow-up

CoverImage now clips its placeholder and image together with a cached rounded path
at draw time rather than relying on a retained graphics-layer outline beneath lazy
item placement/fade animation. This makes rounded rendering independent of page
switches/item reuse; filter placement animation remains enabled. The intermittent
square-corner report has not been reproduced on a device here, so this is a targeted
rendering safeguard, not a verified diagnosis of an Android renderer defect.
Resource-keyed image loading also assigns the correct cache/decode result on EVERY
resource change (produceState's initial value alone does not reset an existing
non-null state when its key changes).

Explore search uses a 20dp field shape. A shell-level pointer observer clears focus
and hides IME on outside touch-down without consuming the touch, including toolbar
and bottom navigation. Focused search handles Back before destination navigation;
IME Search also dismisses. Query text/results stay intact after dismissal. The
observer and geometry do not rebuild list contents on every pointer movement.
Native/device QA must verify focus/IME predictive Back, outside taps, keyboard Search,
RTL/LTR, long text, switching Home/Explore repeatedly and filtering with warm/cold
cover cache. Source tests do not replace frame capture or native compilation.

## Bottom-sheet smoothness follow-up

Code inspection found a definite abrupt-dismissal path: language selection set the
sheet's owning Boolean false immediately, disposing the dialog without awaiting
its hide animation. Both language pickers now use a shared dismissal action that
awaits SheetState.hide(), applies the language change only once hidden, then removes
the dialog. Repeated selection while closing is guarded. System/back/scrim and drag
dismissal remain handled by Material3. Shared sheets explicitly skip the partial
anchor so content goes directly to the expanded state.

A reference-counted motion budget under ReedanceTheme pauses decorative background
work while any shared bottom sheet is composed, from enter through hide/disposal:
fluid shader time, banner copy/progress, Starter floating artwork and brand cycling.
They retain their current visual state and resume after disposal; reduced-motion
preferences are not changed. This prevents simultaneous decorative redraws during
sheet motion, but is a code-level mitigation rather than measured proof of GPU jank.
Sheet animation itself is not disabled or artificially accelerated, and content is
not duplicated offscreen to precompose dialogs.

36 host source guards and 51 Kotlin/KTS syntax checks passed. No native build or
physical-device frame trace was available; source checks do not measure smoothness.
Remaining investigation requires model/Android version, debug-vs-optimized build
and a System Trace/FrameTimeline capture: compare first and repeated open, main
language/Starter language/word sheets, select/back/scrim/drag, rapid taps, app
backgrounding, RTL/LTR, large font and reduced motion. Capture Main, RenderThread,
GPU deadlines and allocations; do not assume that faster animation duration or
moving Compose rendering to Dispatchers.IO fixes missed frames. Debug APK/JIT
behavior must not be presented as measured release performance. The production
release gate remains unchanged.

# Language-scoped library and reviews

The selected **target language**, not the UI language, scopes library bookmarks,
review cards, answer counts, installed-library rows and reminder counts. Song IDs
are linked to target languages through bundled/imported catalog metadata. Switching
language changes the visible scope; it never deletes another language's records.

ReviewCard is now keyed by `(songId, lexemeId)` instead of just `lexemeId`. Two
packages can reuse a vocabulary ID without overwriting each other's schedules.
Review and Library resolve content from each saved item's originating package,
not whichever song happens to be open. Review quizzes use due cards from their
source song and selected language.

## Upgrade from database v1

Room migration 1→2 copies schedules, due times and saved times into song-scoped
rows by joining existing vocabulary bookmarks. If a legacy ID was bookmarked in
multiple songs, the previous shared schedule is copied to each; subsequent
changes are independent. Previously overwritten history cannot be reconstructed.
Unassignable old rows are preserved with an empty song ID, excluded from all
language views rather than assigned to a guessed language. No destructive
migration/fallback is used. Histories for unavailable packages remain stored;
they may be hidden until catalog/package metadata is available again.

## Written pronunciation

Song vocabulary/review/word sheets show supplied `ipa`, `reading` and `faReading` when nonblank. Existing v1 song export contracts specify `ipa` and
`faReading`; `reading` is also used by Starter data. Missing values produce no
empty row or fabricated pronunciation. TTS actions have been removed from song
lessons, notes, quizzes and Starter. The legacy unused speech helper is not called.

## Validation

Host SQLite tests execute the migration SQL against duplicate-lexeme and orphan
fixtures and verify independent updates. A Room instrumentation test covers
independent schedules and bookmarks for two songs with the same lexeme ID.
Native build/instrumentation require Java/Android tooling and were not runnable
in this sandbox. Before release test v1 upgrade, language switching and restart,
multiple songs per language, cross-song duplicate IDs, saved phrases/removal,
review quiz attribution and light/dark compact controls on a device.

## Practice sentence translations

Question objects may supply optional `translationFa` / `translationEn` containing
the **full sentence meaning** for fill-in-the-blank exercises. These fields are
now explicitly documented in the canonical and Android schemas. Older packages
remain valid. When absent, Android looks for an exact corresponding full sentence
in the lesson's lyrics, phrases or vocabulary examples (using accepted blank
answers to find the sentence). It does not guess a translation or use the answer
explanation as a pre-answer hint. Review-generated questions copy the original
vocabulary example's translations.


## Interface language is not teaching language

All teaching meanings, explanations, lesson tables, lyric translations, review
cards, written Persian readings, and practice hints/options are Persian, even
when interface language is English. Original target-language words/sentences
remain in their original language. `LearningLanguage` defines this policy;
`LocalFa` controls interface labels only. English translations in existing packs
are retained for compatibility but not selected for teaching in this app.
Matching equivalence and grading always use the displayed Persian meanings, not
hidden English translations. An English-UI instrumentation regression asserts
English navigation labels alongside Persian learning content.

### Bidirectional layout

Persian teaching paragraphs explicitly use an RTL paragraph base and right
alignment regardless of interface language, including prompts that start with a
Latin word. Original song sentences use explicit LTR paragraphs and left
alignment; typing fields for the currently supported LTR target languages do as
well. Lyric translations retain centered alignment in single-line mode.

Tables preserve column order across UI locales. Each cell respects an explicit
exported `dir` (`rtl`/`ltr`); otherwise paragraph direction is inferred from the cell content. All table
headers and cell text are center-aligned, while Persian/target-language bidi
direction remains independent of alignment. UI/navigation labels retain UI-locale
behavior. Device checks should cover mixed Persian/Latin/numbers/parentheses,
English and Persian interface, wrapped table cells and large text sizes.

## Review-question quality guards

Review questions are generated in `domain/ReviewQuestions.kt`, not inside the UI.
Cloze generation uses Unicode-aware word boundaries (the old over-escaped `\\b`
pattern did not remove the lemma). A cloze is created only when the lemma occurs
exactly once as a complete word/phrase and a full Persian sentence translation is
available. The accepted answer is the actual removed text. Inflected, absent or
repeated lemmas fall back to recall from the Persian meaning, never a prompt that
claims a complete sentence contains a blank. No morphology is guessed.

Choice distractors must have distinct Persian meanings and different lemmas; a
single-option question falls back to recall. Missing vocabulary fields cause the
question to be omitted. Generated and authored questions pass a structural guard:
unique IDs, usable Persian prompts/meanings, a unique valid choice answer,
consistent order/matching permutations, and a real single blank for cloze input.
Malformed/ambiguous questions are skipped with a visible count and do not receive
learning mutations. Session results are tied to their source lesson and review
mode, avoiding reuse of a previous asynchronously prepared session.

These checks do **not** prove semantic correctness of authored translations or
that a sentence naturally illustrates a word. That requires content review and
correction in the Windows exporter. Keep valid legacy package content unchanged.

## Large saved libraries

The UI keeps stable-keyed lazy rows and only composes visible/nearby cards. Tab
filtering/sorting and language filtering run on Dispatchers.Default. Source-song
metadata uses an ID map, not a full catalog scan per card.

LibraryContentLoader builds a per-song item index off the UI thread, shared across
cards through a Mutex. It retains at most four indexed packages, resolving each
bookmark by (type, ID) without scanning its lesson array. Cache reuse checks the
installed version/hash/path; missing/uninstalled packages invalidate that entry.
The library observes the visible range and preloads a small rolling window (up to
24 bookmark references / four songs), cancelling obsolete prefetch on scroll or
filter changes. It does not eagerly decode every saved item. Cold cards use a
quiet placeholder; cached cards reuse indexed content immediately.

5,000-bookmark ordering and concurrent-load/package-update instrumentation test
cases have been added. They are not measured device-performance results: native
tests are unavailable here. Room still emits the lightweight bookmark metadata
list (not a paging source); device frame/memory tests are needed before claiming
smoothness for arbitrarily large libraries or many very large packages.

### Library scroll jank mitigation

Compact tab groups now measure at content width inside a centered full-width
container. Removed the additional per-pill `animateContentSize` that overlapped
label width animations. Header title/count use opacity transitions rather than
simultaneously animating two heights plus outer padding, avoiding a lazy viewport
resize on every animation frame. Header space is released when the fade finishes.

Dense library rows skip the generic per-card entrance layer/coroutine; page entry,
tab labels/colors and bookmark feedback remain animated. Range prefetch coalesces
for 100ms during flings and skips packages already in the bounded index cache,
instead of repeatedly validating the same installed rows as the range changes.

These are source-level mitigations, not a measured fix for the reported lag.
Before calling this smooth, profile release/profileable builds on the reporting
device (model/API, dataset/song count, cold and warm scroll, rapid tab switching,
large text, RTL/LTR); inspect FrameTimeline/main/RenderThread, allocations and
package-read counts. Kotlin syntax and host source checks cannot measure jank.

### Follow-up: fixed viewport during tab collapse

The previous library implementation still changed the lazy viewport when heading
visibility completed, and animated label widths individually. Replace that path:

- Heading/count are normal lazy content and scroll out naturally.
- The tab bar is one fixed 56dp sticky header; the list remains full-size.
- Two premeasured strips (labels/icons) crossfade/translate through graphicsLayer.
  Animation progress is read only in layer lambdas, never in width/height/padding.
- Only the current strip accepts input and exposes accessibility semantics. Hidden
  horizontal-scroll controls cannot steal touches.
- Prefetch ignores sticky-header indexes when calculating the rolling item window.

This targets the reported minimize transition without shortening animation to
mask it. Device frame traces and native tests are still required for confirmation.

### Initial Library population

Previously, publishing bookmark metadata immediately composed placeholder cards;
independent row effects then replaced those with different-height learning cards.
This produced multiple lazy measurement passes during page entry even though the
visible data is text. First-window preparation now resolves at most four rows
before publishing the list. Prepared results (including unavailable/null results)
are passed directly to those cards, avoiding a second load and placeholder pass.
Remaining rows stay lazy and use the existing bounded nearby prefetch/cache.
Songs do not load lesson packages. Cancellation on tab change is preserved.

Page entrance translation now reads progress in graphicsLayer, not layout offset:
no animation-frame placement invalidation, and still no full-screen alpha layer.
This is a source-level reduction of initial loading/layout churn, not a measured
frame-time result. Native unit/device tests and cold-entry frame traces remain
necessary; this environment has no Java/Android runtime.

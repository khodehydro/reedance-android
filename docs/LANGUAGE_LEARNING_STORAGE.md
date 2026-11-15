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
exported `dir` (`rtl`/`ltr`); otherwise Persian-script content is right-aligned and
original-language cells are left-aligned. UI/navigation labels retain UI-locale
behavior. Device checks should cover mixed Persian/Latin/numbers/parentheses,
English and Persian interface, wrapped table cells and large text sizes.

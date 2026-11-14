# Home feed and premium educational previews

## Implemented presentation and policy

- Home has no access/download filter controls and ignores saved Explore filters.
  It lists at most 40 songs for the selected target language. Publication order is
  descending ISO-8601 `publishedAt`; entries without a valid date retain feed order
  after dated entries. Current six preview records have no dates, so their order
  is preserved rather than inventing publication dates or padding to 40 songs.
  The original hero banner and Starter card remain at the top of Home, before
  the latest-song grid. Only the filter controls are removed from Home.
- Explore retains All / Free / Premium / Downloaded filters. No visible lock icon
  is used. Only premium titles carry a small yellow Solid Rounded crown beside
  the song name, with no background or cover overlay. Its size follows the title
  font scale; free titles have no badge.
- LessonAccess separates educational preview policy from media availability.
  Unsubscribed premium limits: first 5 vocabulary entries, 3 lyric lines, 2 grammar
  notes and 2 entries per expressions/sentences section. Free or subscribed policy
  has no cap. Two means per section, not two pooled across all sections.
- Song renders the allowed entries and blurred placeholder bars for the rest.
  Hidden text and actions are not composed: this prevents accessibility, word
  lookup, saved-item actions or TTS in hidden rows from exposing their contents.
  Vocabulary tables are withheld in preview to avoid bypassing the five-word cap.
  Quiz entry points in a preview open the subscription notice rather than exposing
  complete educational material. Actual subscription status is not faked.
- A yellow crown subscription offer is the final list item on premium song pages,
  including catalog-only songs. Its button explicitly reports checkout is not
  connected; it never claims to charge or activate a subscription.
- Blur is supported on API 31+. Earlier supported versions show neutral concealed
  bars with the same subscription label; they do not show readable protected text.

## Not yet available — do not treat as a production paywall

All songs should eventually support full audio playback/download without payment;
only full education should require premium entitlement. There is no access-based
media gate added here. However the existing app has ONLY the original free local
Midnight Avenue debug demo, not licensed audio or lessons for the five commercial
catalog previews. Those remain clearly labeled unavailable. Paid preview rendering
will apply when a real installed premium lesson loader is integrated; this change
DOES NOT install fictitious audio or pass an unrelated demo off as a commercial song.

No backend, billing provider, receipt verification, account entitlement or production
package installer is supplied. The UI currently constructs an unsubscribed policy;
there is no editable local flag granting a paid entitlement. Verified subscription
state must eventually drive policy, and access must also be enforced across library,
review, quizzes, export and content delivery. Client blur alone is NOT DRM or
security. No backend/app connection is required or modified by this UI change.

## Verification

21 Python host source/asset checks pass. Added four JVM policy/feed tests covering
limits, subscriber/free uncapped policy, newest-first 40 cap and missing-date order;
these have NOT run (no local JDK/SDK). Kotlin syntax checks are not compilation.
Device QA is still required for RTL/LTR, text scaling, bottom-offer reachability,
older-Android placeholders, TalkBack, frame time and a supplied premium payload.

# Home banner pager and Persian activity month

Home keeps its hero item, now a two-page HorizontalPager. Page one contains the
original banner text/action; page two shows the current Persian (Solar Hijri)
month. Labeled page controls also permit switching without a swipe. Starter and
the latest-song grid remain below the pager; Explore is unchanged.

Android ICU's `fa_IR@calendar=persian` calendar determines the device-local year,
month, first weekday and actual month length. No fixed 30-day grid or guessed leap
year formula is used. Saturday is the first weekday, following RTL/LTR presentation.
Days map to local Gregorian epoch-day keys internally so time spent and the Persian
view refer to the same civil day. The month refreshes while shown, including device
date/timezone changes, with a maximum normal refresh delay of about 30 seconds.

Each day is a small numbered square. Green means at least two cumulative minutes
of resumed/visible app usage OR a successfully saved Starter step completion,
quiz answer grade or review-card response. Undoing a Starter completion, opening
the app briefly, a stale review action or failed database transaction does not
qualify as an exercise. Wrong answers still count as practice. The current day is
neutral until qualified, past unqualified days are red, and future days are neutral
regardless of stored history. A legend, accessible status labels and threshold copy
avoid communicating status with color alone.

Activity is stored locally in a separate DataStore, with no schema migration,
account or server. Resumed time uses elapsedRealtime, sampled every five seconds
and flushed on pause. No time is added for background media, a paused/locked app
or wall-clock jumps; samples crossing local midnight are split between civil days.
The last unsaved few seconds may be lost on abrupt process kill. A device timezone
change does not rewrite already recorded civil-day history. Foreground time is
resumed duration, not proof of continuous gestures/attention. No past activity is
fabricated/backfilled for dates before this feature existed: unrecorded past days
appear red. Clearing app data clears the ledger.

One active timing job is allowed; a resumed session joins a canceled prior job's
final flush before starting, avoiding duplicate counts during rotation/quick
background/foreground changes. Completion qualification is recorded only after a
successful serialized Room transaction. Ledger emissions are observed by the
calendar page, not the entire home catalog.

Validation: 28 host source/asset checks and 43 Kotlin/KTS syntax checks pass. Four
Android instrumentation tests were added for 31-day Farvardin, 30-day Mehr, leap
Esfand (1403) and ordinary Esfand (1401). These tests and native builds have NOT run
here (no Java/Android SDK); syntax parsing is not Kotlin type checking.

Device QA remains: current Solar Hijri dates and leap boundaries on supported ICU
versions, midnight/DST/timezone changes, cumulative foreground sessions, sleep and
background playback exclusion, successful/failed/undone exercise actions, process
kill, persistent state after relaunch, pager RTL/LTR/swipe/controls, reduced motion,
large fonts/narrow screens and vertical scrolling inside taller banner/calendar
pages. Notification timing is untouched and remains in real-time mode.

## Banner motion / Explore magnifier follow-up

Explore search now has the official Uicons Regular Rounded `search` magnifier in
its leading slot (20dp). Provenance and the import script include the glyph.

The welcome banner now follows the supplied blue/cyan liquid reference instead
of the original two moving radial blobs. On API 33+ an AGSL RuntimeShader uses
four-octave domain-warped noise to form branching cyan contours, broad cobalt
fields and near-black pools, with very subtle grain. Time advects the field slowly;
it is not a translation/rotation of a static image. A dark scrim preserves text
contrast. Text, action and layout stay stationary. No new imagery or dependency.

On API 26–32, or if shader construction fails, a lower-cost Canvas fallback uses
soft nested flowing ribbons over a black/cobalt field. This is an approximation,
not identical pixel output. No per-frame CPU bitmap/noise rendering is used.
Animation state is read only during drawing, updates are capped near 30fps, and
visibility/lifecycle/reduced-motion guards remain intact. The rounded banner clip
also clips the fluid field. GPU performance still needs physical-device profiling.

An API-33+ instrumentation test checks runtime shader compilation/uniforms. It has
NOT run here; neither the AGSL compilation nor visual fidelity to the supplied
reference has been verified on Android in this environment.

29 host checks and 45 Kotlin/KTS syntax checks pass after this follow-up. Native
build/device visual, contrast and frame-time validation remain pending.

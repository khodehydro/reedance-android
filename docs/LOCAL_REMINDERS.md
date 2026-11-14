# Local reminders, permission onboarding and UI follow-up

## Behavior

- Premium crowns are now before the title in the song-name LTR row, still filled,
  yellow, background-free and sized relative to text.
- Explore alone has a saved-state search field for title/artist, combined with its
  target language and existing access/download filters. Clear resets the query;
  Home ignores the query and retains its banner, Starter and latest-song list.
- Both developer credits trigger a root-level Canvas burst. Twelve hearts rise
  from the tapped credit to the top of the window, with horizontal drift/fade.
  This is not clipped by the grid/footer and does not intercept touches. Repeated
  taps restart one bounded burst (not an unbounded particle/job queue). Drawing
  observes animation state without rebuilding the catalog. Reduced motion shows
  a single fading heart at the origin, without travel.

## Optional permission setup

After interface-language setup, a one-time persisted screen explains optional
reminders only; there is no download/storage permission card. Users can continue without enabling notifications.
Existing installations see this screen once too. Settings in My space expose the
same opt-in toggle. Default is OFF. Android 13+ POST_NOTIFICATIONS is requested
only on explicit enable; denial leaves reminders off. A system notification-settings
link handles permanently denied / OS-blocked app or channel notifications. Returning
from Android settings refreshes permission status; the user can enable the toggle.

INTERNET is a manifest permission, not a runtime prompt. Downloads into app-private
storage require NO READ/WRITE_EXTERNAL_STORAGE, photo, all-files or exact-alarm
permission. No such permissions are added, and no unrelated access is requested.
The existing debug-only media availability / production download limitations remain.

## Scheduling and notification content

A unique chain of one-time WorkManager requests implements inactivity milestones:
12h, 24h, 36h, 72h, 7 days, then weekly since the last visit. Each stage has its own
supportive Persian/English invitation, enriched with due words or Starter progress.
Only the latest elapsed stage can fire if Android delays execution; there is no burst
of catch-up messages. The persisted last-notification timestamp prevents duplicate
notifications within a stage. Returning resets the inactivity journey and replaces
the pending request. Disabling cancels the chain and visible notification. The old
periodic work name is explicitly canceled for migration.

TEMPORARY DEBUG TEST MODE: BuildConfig.DEBUG uses a ten-second unit in place of
12 hours, so stages are approximately 10s, 20s, 30s, 60s, 140s and then every 140s
since the last visit. Release policy retains the real schedule (release publishing
is still blocked by the existing gate). Remove this debug scale in a later revision.
The onboarding/settings description intentionally has no timings or implementation
jargon. Enable notifications, continue past setup and leave the app with Home;
do not force-stop. The first debug reminder is eligible after ten seconds, not
while the app remains foreground. WorkManager scheduling/Doze may delay it; a
one-time request is used because periodic work cannot run every ten seconds.
There is no exact alarm, foreground test service or battery-exemption request.

DataStore holds opt-in, completed setup, last visit and last notification time.
Activity start/stop update the visit and reschedule. Clock rollback, no first visit,
incomplete setup, denied permissions and foreground state suppress delivery. The
worker rechecks the visit timestamp immediately before posting so a stale lesson
snapshot cannot notify after a return. A blocked permission/setup is checked again
later without a tight real-time retry loop. WorkManager handles normal process death
and reboot; force-stop/battery restrictions can prevent delivery.

Local Room COUNT queries choose a message: due-review count first, then existing
Starter progress, otherwise a gentle return-to-music prompt. Content uses app
interface language, not learning language. One stable notification ID prevents a
stack of old reminders; a proper white-vector small icon, notification channel,
immutable PendingIntent, auto-cancel and app-opening tap are supplied. Revocation
between permission check and posting is caught without crashing. Transient worker
failures retry; cancellation is rethrown. There is no fabricated streak statistic.

## Validation and remaining device checks

24 host source/asset checks passed; five pure JVM ReminderPolicy tests cover
milestone boundaries, the temporary debug scale, stage deduplication, reset/clock
handling and stage-specific activity-aware messages. JVM/native
build and instrumentation have NOT run: JDK/Android SDK are absent. Syntax parsing
is not Kotlin type checking or an Android build.

Required device QA:
- API 26/32 (no notification runtime prompt), API 33+ grant/deny/permanent denial,
  channel blocking, return from system settings and OS permission revocation.
- Fresh install and upgrade onboarding; continue without opt-in; toggle on/off;
  no reminders in foreground, after a recent visit or twice within a stage; reboot,
  Doze and process death. Verify worker using Android Studio Background Task
  Inspector on a debug build rather than claiming exact-time delivery.
- English/Persian messages, zero/due words and Starter activity; notification tap.
- Crown/title order and long titles in RTL/LTR; Explore search/clear/empty results;
  Home remains filter/search-free with banner and Starter intact.
- Credits in both Catalog/Profile, repeated taps, scroll footer coordinates,
  edge-to-edge/top reach, reduced motion, font scaling and frame-time tracing.

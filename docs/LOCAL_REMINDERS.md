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

After interface-language setup, a one-time persisted screen explains reminders
and private offline storage. Users can continue without enabling notifications.
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

WorkManager 2.10.1 (already in the project) schedules one unique periodic worker:
12-hour repeat, one-hour flex, initial delay 12 hours; KEEP prevents resets on each
launch. No network constraint, remote push, server call or cloud account is used.
WorkManager persists the schedule across normal process death/reboots. Android
Doze, battery restrictions and force-stop can delay/prevent execution; this is not
an exact 12-hour alarm and no battery-optimization exemption is requested.

DataStore holds opt-in, completed setup, last visit and last notification time.
Activity start/stop update the visit timestamp. Worker requires at least 12 hours
since BOTH the last visit and prior reminder. It skips users without a first visit,
clock rollback, incomplete setup, blocked permission and foreground use. Opening
the app dismisses its old reminder. Turning off cancels unique work and the visible
notification; worker rechecks opt-in/permission just before posting.

Local Room COUNT queries choose a message: due-review count first, then existing
Starter progress, otherwise a gentle return-to-music prompt. Content uses app
interface language, not learning language. One stable notification ID prevents a
stack of old reminders; a proper white-vector small icon, notification channel,
immutable PendingIntent, auto-cancel and app-opening tap are supplied. Revocation
between permission check and posting is caught without crashing. Transient worker
failures retry; cancellation is rethrown. There is no fabricated streak statistic.

## Validation and remaining device checks

24 host source/asset checks passed; four pure JVM ReminderPolicy tests were added
for interval boundaries, cooldown, no first visit and clock rollback. JVM/native
build and instrumentation have NOT run: JDK/Android SDK are absent. Syntax parsing
is not Kotlin type checking or an Android build.

Required device QA:
- API 26/32 (no notification runtime prompt), API 33+ grant/deny/permanent denial,
  channel blocking, return from system settings and OS permission revocation.
- Fresh install and upgrade onboarding; continue without opt-in; toggle on/off;
  no reminders in foreground, after a recent visit or within cooldown; reboot,
  Doze and process death. Verify worker using Android Studio Background Task
  Inspector on a debug build rather than claiming exact-time delivery.
- English/Persian messages, zero/due words and Starter activity; notification tap.
- Crown/title order and long titles in RTL/LTR; Explore search/clear/empty results;
  Home remains filter/search-free with banner and Starter intact.
- Credits in both Catalog/Profile, repeated taps, scroll footer coordinates,
  edge-to-edge/top reach, reduced motion, font scaling and frame-time tracing.

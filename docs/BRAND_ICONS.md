# Supplied header and notification artwork

Sources: `khodehydro/reedance` main branch `ico_header.png` and `ico_notif.png`.
Source URLs and SHA-256 hashes are recorded in
`android/app/src/main/assets/brand-icons-provenance.json`.

- The cycling header uses `drawable-nodpi/reedance_header.png`, a 238×256
  proportional downsample of the supplied transparent 909×976 image, displayed
  within the existing 40dp slot. The wordmark, animation, launcher, setup and
  splash artwork are unchanged.
- Reminder small icons use `drawable-nodpi/ic_learning_reminder.png`, derived
  from the supplied 45×49 notification PNG. Its alpha mask is preserved exactly;
  RGB is white, as required for Android's system-tinted status-bar small icons.
  The old musical-note vector is removed. Android renders this as a monochrome
  silhouette rather than a full-color image; color/detail belongs in a large icon,
  not a status-bar small icon.

No notification schedule, debug timing, message or permission behavior is changed.
The subsequent reminder-timing revision removed the temporary test scale; all builds now use real inactivity milestones. Actual status-bar
rendering and header appearance still need Android device validation.

## Starter illustration

`ico_starter.png` from the same reference repository is now used beside the Home
Starter card title and the Starter course introduction. The 2048px transparent
source is proportionally downsampled to a 384px bundled PNG; SHA-256/source URL are
in the shared provenance file. ContentScale.Fit preserves the entire mascot,
headphones and ears with no circular cropping. It is decorative beside existing
localized headings, so accessibility does not announce a redundant image label.
The subtitle and entry button remain intact; course controls and progress are
unchanged. Validate both themes, RTL/LTR, narrow devices and large fonts on Android.
31 host checks and 47 Kotlin/KTS syntax checks pass; native/visual tests are pending.

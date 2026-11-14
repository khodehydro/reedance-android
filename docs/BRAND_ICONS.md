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

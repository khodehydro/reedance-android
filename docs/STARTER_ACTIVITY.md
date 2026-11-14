# Standalone Starter Activity

Open Starter launches an explicit Intent to non-exported StarterActivity. The old
MainShell Starter destination is removed; this is a separate ComponentActivity,
not a hidden-bottom-bar route or fragment. Back from its header calls finish();
system/predictive Back uses the normal activity task stack and returns to the
existing MainActivity. The main catalog's saveable page/scroll state is not replaced.

StarterDestination owns a Scaffold with an opaque status-inset header: centered
Starter title, Back button and a circular 32dp flag within a 48dp touch target.
The flag opens an English/Spanish/Korean modal language sheet. Initial language is
the selected catalog target language; subsequent selection belongs to this Starter
session and is saved across configuration recreation. It does not change app UI
language or catalog preference. The old inline language text tabs are removed.
Changing course language resets the selected lesson step; each language's Room
progress remains separate. The illustration and learning controls remain intact.

ReedanceApp accepts an optional standalone content slot so settings, font size,
RTL/LTR, reduced motion, themed system bars and loading/onboarding gates are shared
without composing MainShell. StarterActivity uses Theme.Reedance (not the launch
splash theme), adds no splash handler, and renders neither the global header,
floating menu nor mini-player. OS system navigation is NOT hidden; its safe inset
is still respected. The system navigation inset is trailing list content space, not viewport bottom
padding; rows can scroll underneath the transparent system bar while the final
controls remain reachable.

The activity owns its own lifecycle AppModel, with the existing singleton Room and
shared application DataStores. Pronunciation is lazy and released with its model.
Resumed activity time and reminder visit timestamps are recorded from its lifecycle;
MainActivity stops its activity timer while paused. The foreground-only activity
ledger therefore continues to include actual Starter time rather than treating
Starter as app inactivity. Main observes progress changes through Room on return.

Validation: 33 host source/asset checks and 49 Kotlin/KTS syntax checks plus manifest
XML parsing passed. These are not native compilation. Device QA/build remain
pending (no JDK/Android SDK here): launch/finish and predictive Back, rotation and
process restoration, language sheet dismissal and course switching, progress/TTS,
activity ledger continuity, RTL/LTR/font scaling, status/navigation safe areas,
return-to-home scroll preservation and absence of duplicate splash/global menu.


## Warm launch and navigation-bar follow-up

StarterActivity disables Android navigation-bar contrast enforcement immediately
following enableEdgeToEdge, matching MainActivity even before Compose's first
SideEffect. The root theme still applies transparent system bars and light/dark
icon appearance. Starter's content viewport extends beneath system navigation,
with its measured safe inset appended inside the list instead of reserving a
separate bottom strip. Test both gesture and three-button navigation on Android.

Main startup now warms a process-only Starter snapshot (parsed course JSON, actual
settings and current step progress). The repository memoizes course parsing behind
a shared mutex; both activities use the same read-only JSON data. Live DataStore
and Room flows refresh settings/progress snapshots. A Starter-specific AppModel
factory seeds its first states and ready flag from that snapshot, removing the
second blank/loading gate on a normal Home-to-Starter entry. No Activity/ViewModel
or Compose tree is cached and no Activity is created offscreen.

Cold process restoration still reads Starter prerequisites asynchronously, but no
longer waits for catalog loading or demo-file restore/staging cleanup. The default
single-Application AppModel constructor is retained via @JvmOverloads for the main
AndroidViewModel factory. Room/DataStore remain authoritative; snapshots are not
persisted independently and disappear when the process ends. This removes known
redundant I/O, not Android Activity/first-frame rendering cost; zero-ms startup is
not claimed.

34 host source/asset checks and Kotlin syntax/manifest checks cover the follow-up.
No native build or timing measurement was possible here. Compare warm entry and
process-restored entry on-device, check current settings/progress without flashes,
rotation, TTS, both system navigation modes and final-row reachability.

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
is still respected. LocalOverlayBottomInset is explicitly zero for this screen.

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

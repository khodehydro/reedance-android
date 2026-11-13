# Reference snapshot and native mapping

- Repository: `khodehydro/reedance`
- Branch: `arena/e6a6294d-reedance`
- Inspected snapshot: `fbe07e4ed8356fa8f23f5376d8b3618409ec6743`
- Imported on: 2026-11-13
- Architecture briefs preserved under `docs/reference/`; contracts under `contracts/v1/` are unchanged copies.

Reviewed sources: README, all seven architecture documents, schema/fixture files, App/Starter/Quiz/Profile/CardDeck/LearningBlocks components, data modules, review/offline/splash helpers and associated CSS. Reference runtime is React/Vite; this app does not execute it.

## Data and assets

- Exact local `logo.png` → `res/drawable/reedance_logo.png`; six original reference covers → Android drawables, with underscores required by resource names.
- `starterData.js` → `assets/starter.json`, including all three languages, Persian approximate readings and tables.
- `midnight-avenue.json` + `enrichDemoLesson` + `demoQuiz` → debug-only lesson JSON using unchanged v1 schema. The converter removes web-only keys and supplies stable line/lexeme references, examples, audio metadata and credits. It is a developer demo migration, NOT a substitute for human approval/Windows publishing.
- Original 64-second generated instrumental WAV is debug-only. Demo text is not sung lyrics. No famous-song audio was introduced.
- Vazirmatn from `rastikerdar/vazirmatn`, Manrope from `google/fonts`, both with bundled OFL notices. Korean uses Android fallback where these fonts have no glyphs; device QA remains required.
- Android icons use Material Icons Rounded under Apache 2.0, not the web Flaticon font. Thus icon fidelity is intentionally not claimed. Source web attribution is retained in About as context, not a claim of redistributing that icon pack.

## Motion translation

Reference deck timing: arrive 390ms with cubic bezier (0.19,0.85,0.3,1), leave 220ms, reveal 260ms; entry from inline-start and exit toward inline-end, mirrored in RTL; two decorative back sheets. The native deck uses those timing values, scale and mirrored slide; back sheets remain decorative and outgoing sheet loses input/semantics. Exact tilt/promotion choreography and interaction timing still need native/React video comparison. Page entry uses a short native fade/vertical slide. Reduced-motion preference and device animator setting disable custom deck/page entry motions.

## Native boundaries

Shared UI model is an Android ViewModel; Room stores user state, DataStore preferences, Media3 service audio. Files live in private app storage. No server account is implied by “guest”. The app currently ships one Android module with package-level boundaries; splitting into Gradle feature/core modules should follow when the production installer and more songs are added.

Current demo is intentionally labelled as a local install, not “download complete”. A local file hash is not a publisher signature. The release build is blocked rather than pretending that missing endpoints/keys, updater and security gates are ready.

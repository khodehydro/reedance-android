# Android package installer implementation / provisioning

The reference `contracts/v1` files are unchanged. Android's bundled
`lesson.schema.json` explicitly extends only `language`, `locale`, and `lexemeId`
for Turkish (`tr`); this is an Android compatibility extension, not a claim that
an unchanged desktop v1 exporter accepts Turkish. Coordinate that extension with
the publisher before exporting Turkish content.

## Trusted local provisioning

Provide `android/app/src/main/assets/pack_distribution.json` through a secure local
build/CI secret-file mechanism. This exact path is gitignored. Do not paste AES
keys in chat, commit them, or provide the private signing key. Shape (notation,
not usable keys/endpoints):

- `allowedHosts`: explicit HTTPS host allowlist.
- `publicKeys`: signatureKeyId → independently trusted raw32 Ed25519 public key,
  canonical unpadded Base64URL.
- `aesKeys`: encryptionKeyId → matching32-byte AES distribution key, same encoding.
- `songs`: actual authenticated songId → object with `manifestUrl` (real HTTPS
  latest.json URL without query/fragment) and `language`.
- `catalogBindings`: `gulum-ii` → that same real songId from publisher metadata.

Gülüm II's `pending-gulum-ii` is **only a UI placeholder**, not a fabricated pack
identity. The binding replaces it when provisioning is available. Its source
cover is the user-supplied repository JPEG, resized to WebP; no song audio, keys,
or encrypted pack are bundled. The displayed title and artist came from the user.

## Implemented path

WorkManager persists installation requests; retries restart transfer from zero
(no unsafe partial resume). A process-wide mutex serializes installers/recovery.
HTTPS downloads are bounded, forbid cross-origin redirects and require pinned
hosts. Manifest signature and antirollback observation precede pack transfer.
SHA/length precede streaming BouncyCastle AES-GCM into private staging; extraction
never begins before tag verification. The strict ZIP reader checks central/local
agreement, root entry allowlist, CRCs, limits and deflate completion. Local schema
and semantic checks, cross-installed lexeme identity, file hash/magic, Android
media-duration probing and image bounds precede activation.

Files and directories are fsynced, renamed on the same filesystem, then Room's
active pointer and seen version are committed transactionally. A pending marker
allows startup/next-install recovery to remove unreferenced interrupted activation
or finish a committed one. Staging is cleared under the same lock. Previous
committed generations remain intact to avoid removing files still in use by Media3.
Cancellation cleans staging and cannot interrupt the short activation commit.
Network cancellation may wait for the bounded socket timeout. UI shows work phase,
bytes and cancellation; missing configuration fails explicitly. Room installed IDs
drive Downloaded filtering. Lesson, audio path, metadata, bookmarks/checkpoints and
quiz version no longer assume the demo song.

## Known remaining gates — do not label production-ready

- No supplied signed manifest / trusted public key / AES distribution key for the
  real Gülüm II package: it has **not** been decrypted, installed or played.
- Native compilation and the new JVM tests could not run here: Java/Android SDK
  unavailable. Host checks and syntax parsing are not native execution evidence.
- Device tests still required: provider media format differences, corruption,
  low storage, process death at each activation boundary, cancellation, offline
  restart, upgrades and background-work lifecycle. No foreground-service progress
  notification implemented; normal WorkManager execution limits apply.
- Alias-bearing lessons fail closed pending a real learning-history migration.
  Old committed generations are retained; automatic in-use-safe garbage collection
  and user-facing package disk-usage/deletion controls remain to implement.
- Reattaching the progress panel after Activity/process recreation, and playing
  library/review items across multiple installed songs still need full end-to-end
  UI coverage (some legacy library navigation still targets the demo).
- Legal publication approval and production hosting/key-injection audit remain
  release requirements. The release Gradle guard is deliberately unchanged.

Tests added: `PackInstallValidationTest` covers golden authenticated ZIP extraction,
trailing bytes rejection, bundled schema/semantics, unknown fields and timeline
rejection. Run Android CI before relying on these tests; they were not executable
in this sandbox. Existing host suite:36 checks; transport inspector:8 checks.

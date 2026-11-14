# Gülüm II — real export transport/header inspection

Song supplied by owner: **Gülüm II — Ayaz Erdoğan**.
This is an inspection report, NOT a successful Android installation/extraction.

> Historical **legacy-key** export. Desktop now derives `enc-main` from a file
> password (reviewed commit `0d1f612`). Re-export this song with the new Studio;
> this file's `enc-studio-e896d1` key cannot be recovered from its hash or a new
> password. See [current Android password/import workflow](ANDROID_INSTALLER.md).

## Sources inspected

- Desktop branch: `khodehydro/reedance-desktop`, `arena/132cfe2a-reedance-desktop`;
  resolved commit `d1b3159563a49e65e6bbe82bf0a78c8a2029f393`.
- Desktop `src/main/crypto/pack.ts`, `exporter.ts`, `publication.ts`, `keys.ts`
  and `contracts/lesson.schema.json`.
- Package supplied:
  `https://github.com/khodehydro/reedance/raw/refs/heads/main/v000001-e83715d2eb2fec5205fdc0bec16267858ea4a7d04ae9b1ceb3f1c0d50aa092af.rdpack`
- Cover supplied:
  `https://github.com/khodehydro/reedance/blob/main/G%C3%BCl%C3%BCm%20II.jpg`

The direct package URL redirects to raw.githubusercontent.com, which is not in this
sandbox's outbound allowlist; direct curl failed there. The actual bytes were
retrieved through the GitHub Git Blob API instead, using blob
`4c8e068bae3fe1d4b3b2361e5c171a9415f63939`. This proves access via API, NOT that
Android's downloader, redirect policy or the original URL works on-device.

## Observed results

| Check | Result |
|---|---|
| Actual downloaded package | 7,583,238 bytes |
| SHA-256 | `e83715d2eb2fec5205fdc0bec16267858ea4a7d04ae9b1ceb3f1c0d50aa092af` |
| Matches filename hash | Yes |
| Magic | `RDPACK1\0` |
| Encryption | AES-256-GCM |
| Encryption key ID | `enc-studio-e896d1` (public identifier, NOT a key) |
| Key ID length | 17 bytes |
| Header / GCM tag | 46 / 16 bytes |
| Declared ZIP plaintext length | 7,583,176 bytes |
| Container length arithmetic | 46 + 7,583,176 + 16 = 7,583,238, valid |
| Publisher signature / anti-rollback | Not verified: matching signed manifest absent |
| GCM authentication / decryption | Not run: matching AES distribution key absent |
| ZIP listing, extraction, schema, media hashes | Not run: plaintext inaccessible |
| Android install / playback | Not tested |

Cover transfer returned 152,004 bytes with JPEG start/end markers. SHA-256:
`dd8d6a4ea503cbeadd1d73d05dbc15fdb560c5abb6edabcaf8c61d0d36f25680`.
This is not a comparison against signed lesson media metadata; that remains hidden
inside the encrypted package. No claim is made that the cover matches the pack.

Machine-readable results: `gulum-ii-inspection.json` next to this report.
No real audio/package, cover, keys or decrypted content is committed or bundled.

## Why extraction cannot yet be confirmed

The desktop exporter signs a separate manifest and encrypts ZIP bytes; it does NOT
put AES key bytes into an export. Its keys.ts explicitly keeps AES/private signing
keys local (safeStorage/DPAPI where available). A .rdpack is not an ordinary ZIP,
and renaming it to .zip cannot extract it. Matching a hash encoded in a filename
checks transfer consistency, not publisher authenticity or GCM integrity.

The user's source repository tree contained the supplied package/cover, but no
matching published latest.json was found during this check. Android's bootstrap
catalog is still empty, and approved production verification/distribution keys
are not provisioned. PackVerifier is a bounded fixture primitive, not the full
streaming installer. The current player/demo loader remains Midnight-Avenue-only;
adding this song to a preview list would not make real installation work.

Both inspected desktop and Android lesson schemas currently restrict targetLanguage
to en/es/ko. If this lesson is Turkish (`tr`), that is an additional contract/UI
compatibility gap to fix in BOTH projects. The encrypted header does not reveal
songId/targetLanguage, so those values have not been guessed from the title.

## Required next artifacts / work

1. Upload the corresponding exported **signed latest.json**, generated alongside
   the pack in the desktop song archive directory (or its immutable manifest copy).
   This supplies actual songId, targetLanguage, version, expected size/hash and
   signatureKeyId. Do not reconstruct/sign a replacement from assumptions.
2. Supply the trusted **Ed25519 public** verification key/key ID via approved app
   configuration, independently of the untrusted downloaded manifest.
3. Provision the matching AES-256 distribution key for `enc-studio-e896d1` via an
   external secret/local ignored build configuration. Do NOT paste secrets into
   chat, commit a keystore, upload a private signing key or reuse golden test keys.
   A key ID alone cannot decrypt a package.
4. Complete/debug-test the real installer: bounded download to private staging,
   signed-manifest verification, approved host/redirect handling, streaming GCM
   authentication, then strictly bounded ZIP/path/duplicate/symlink checks, lesson
   schema/semantic validation and media hashes, atomic activation and recovery.
   Do not expose unauthenticated plaintext as installed content.
5. Wire the real song ID, language, cover, installed media and lesson into catalog,
   playback and learning UI. Test on Android, not just the host script. The current
   release gate stays closed; no security bypass or fabricated successful download
   was added for this test.

## Reproduce bounded inspection

Retrieve the package bytes using your approved transport, then:

```sh
python3 scripts/inspect_rdpack.py /path/to/v000001-e83715d2eb2fec5205fdc0bec16267858ea4a7d04ae9b1ceb3f1c0d50aa092af.rdpack
python3 scripts/test_inspect_rdpack.py
```

The tool uses 64KiB hash chunks and a maximum 61-byte header buffer, enforces v1
size/filename/key-ID/length limits, and never extracts or requests credentials.
Eight host tests cover the golden fixture header, explicit unauthenticated status,
wrong hash/magic/ID/length, invalid plaintext lengths and truncation. They pass;
36 existing host checks also pass. New host tests are wired into CI. None of these
constitute native Android downloader/install/cryptographic interoperability tests.

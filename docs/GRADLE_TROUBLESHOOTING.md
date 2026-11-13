# Gradle plugin resolution (Windows / Android Studio)

## KSP plugin not found

The project pins Kotlin `2.1.20` and KSP `2.1.20-1.0.32`. That KSP version is an upstream release:
https://github.com/google/ksp/releases/tag/2.1.20-1.0.32

Gradle normally resolves a plugin marker first:

```text
com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.20-1.0.32
```

Some mirror/cache configurations may lack that marker. `android/settings.gradle.kts` now maps the KSP plugin ID directly to its published implementation, with the same pinned version:

```text
com.google.devtools.ksp:symbol-processing-gradle-plugin:2.1.20-1.0.32
```

Myket remains the first plugin and dependency repository. This mapping avoids the marker lookup; it does NOT bypass TLS, proxy restrictions or make a missing implementation artifact available in a mirror.

After updating the checkout, turn OFF Gradle Offline Mode in Android Studio and run in PowerShell from the repository root:

```powershell
git pull --ff-only origin arena/f7c293e1-reedance-android
cd android
.\gradlew.bat --stop
.\gradlew.bat help --refresh-dependencies --info --stacktrace
```

Then run **Sync Project with Gradle Files**. `help` isolates configuration/plugin resolution without requiring a full APK build.

If resolution still fails, inspect the FIRST underlying download error and repository URL, not just the final `UnknownPluginException`:

- `Could not GET/HEAD`, timeout, HTTP 403/407: check network/proxy/repository access.
- `PKIX path building failed` / TLS errors: check JDK trust/proxy certificates; do not disable certificate verification.
- Offline-cache messages: disable Gradle Offline Mode.
- Implementation POM/JAR unavailable: the mirror may not contain it; use an approved accessible repository/proxy that carries the exact coordinates above.

Configure a proxy using Android Studio's HTTP Proxy settings and, if required by your environment, the private user-level `%USERPROFILE%\.gradle\gradle.properties`. Never commit proxy credentials or paste them into an issue/chat.

Do not delete the entire Gradle cache or change Kotlin/KSP versions at random as the first workaround. Send the relevant repository URL/status and first underlying error, with credentials redacted.

## AppModel Kotlin compilation: unresolved `app` and File members

If `:app:compileDebugKotlin` reports `AppModel.kt: Unresolved reference 'app'`
followed by unresolved `isFile`, `toURI`, `parentFile`, `exists` or `path`, update
the checkout. The original `demoDir` custom getter incorrectly referred to the
non-property constructor parameter `app`. It is now an explicitly typed `File`
initialized during construction, where that parameter is in scope. The other
File-member diagnostics are cascading type-resolution errors from that getter;
this does not require changing KSP, Room or dependency versions.

From the repository root in PowerShell:

```powershell
git pull --ff-only origin arena/f7c293e1-reedance-android
cd android
.\gradlew.bat :app:assembleDebug
```

No cache deletion or `--refresh-dependencies` is necessary for this source fix.
Local APK compilation has not been verified in the sandbox (no JDK/Android SDK).

## Verification boundary

The direct-module mapping has been inspected statically. Local Gradle execution in the coding sandbox remains blocked by the missing JDK/Android SDK; successful sync/build must be confirmed in Android Studio or CI. An upstream release's existence alone does not prove accessibility from your machine or a given mirror.

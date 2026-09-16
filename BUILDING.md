# Building Visual Routines

## Requirements

- a 64-bit environment supported by the Android Gradle Plugin;
- OpenJDK 21;
- Android SDK Platform 37;
- Android SDK Build Tools and command-line tools compatible with the project;
  and
- Git plus a POSIX shell for the documented commands.

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the Android SDK. Do not commit
`local.properties`; a local checkout may use it to point Gradle at the SDK.

The Gradle Wrapper downloads Gradle 9.5.0 and verifies the distribution with
the checksum recorded in `gradle/wrapper/gradle-wrapper.properties`.

## Build And Test

From the repository root:

```bash
./gradlew \
  :app:compileDebugKotlin \
  :app:compileReleaseKotlin \
  :app:testDebugUnitTest \
  :app:verifyRoborazziDebug \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:assembleDebugAndroidTest \
  --no-daemon \
  --no-build-cache
```

Audit the unsigned, minified source-release APK:

```bash
./scripts/audit-release-baseline.sh --apk-only
```

The release output is
`app/build/outputs/apk/release/app-release-unsigned.apk`. It is an unsigned test
artifact, not an installable project release and not a distribution file.

## Instrumented Tests

Instrumented tests require an explicitly selected compatible Android target.
Do not run them against a device that contains data you are not prepared to
protect and restore.

The repository's automated public CI compiles the instrumentation APK but does
not connect to a device. Release acceptance also uses separately controlled
API-level, phone, tablet, large-text, and human-listened TalkBack validation.

## F-Droid Build

The intended F-Droid recipe checks out the public `v0.1.0` source tag and runs
the standard Gradle release-APK task. F-Droid performs its own source scan,
isolated build, signing, and publication. The upstream project supplies no
signed reference APK and makes no reproducibility claim without separate
matching-build evidence.

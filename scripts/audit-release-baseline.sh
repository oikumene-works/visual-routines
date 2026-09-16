#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$PROJECT_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
AAB="$PROJECT_ROOT/app/build/outputs/bundle/release/app-release.aab"
EXPECTED_PERMISSION="io.github.ewoc2026.visualroutines.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

fail() {
  printf 'Release baseline audit failed: %s\n' "$1" >&2
  exit 1
}

AUDIT_MODE="apk-and-bundle"
if [[ "$#" -eq 1 && "$1" == "--apk-only" ]]; then
  AUDIT_MODE="apk-only"
elif [[ "$#" -ne 0 ]]; then
  fail "usage: ./scripts/audit-release-baseline.sh [--apk-only]"
fi

find_sdk_tool() {
  local tool_name="$1"
  local sdk_root
  local candidate

  if command -v "$tool_name" >/dev/null 2>&1; then
    command -v "$tool_name"
    return
  fi

  for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [[ -n "$sdk_root" ]] || continue

    if [[ "$tool_name" == "apkanalyzer" && -x "$sdk_root/cmdline-tools/latest/bin/apkanalyzer" ]]; then
      printf '%s\n' "$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
      return
    fi

    candidate="$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name "$tool_name" 2>/dev/null | sort -V | tail -n 1)"
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  fail "Android SDK tool not found: $tool_name"
}

find_apksig_jar() {
  local sdk_root
  local resolved_apksigner
  local candidate

  for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [[ -n "$sdk_root" ]] || continue

    candidate="$(
      find "$sdk_root/build-tools" \
        -mindepth 3 \
        -maxdepth 3 \
        -type f \
        -path '*/lib/apksigner.jar' \
        2>/dev/null | sort -V | tail -n 1
    )"
    if [[ -n "$candidate" && -r "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  if command -v apksigner >/dev/null 2>&1; then
    resolved_apksigner="$(readlink -f "$(command -v apksigner)")" ||
      fail "could not resolve apksigner from PATH"
    candidate="$(dirname "$resolved_apksigner")/lib/apksigner.jar"
    if [[ -r "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  fi

  if [[ -r /usr/share/java/apksigner.jar ]]; then
    printf '%s\n' /usr/share/java/apksigner.jar
    return
  fi

  fail "public apksig library was not found in Android Build Tools"
}

require_contains() {
  local content="$1"
  local expected="$2"
  local description="$3"

  grep -Fq "$expected" <<<"$content" || fail "$description"
}

require_absent() {
  local content="$1"
  local unexpected="$2"
  local description="$3"

  if grep -Fq "$unexpected" <<<"$content"; then
    fail "$description"
  fi
}

require_unsigned_bundle() {
  local entries
  local verification

  command -v jarsigner >/dev/null 2>&1 ||
    fail "jarsigner is required to inspect the release app bundle"

  entries="$(unzip -Z1 "$AAB")" ||
    fail "could not list release app bundle entries"
  if grep -Eq '^META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))$' <<<"$entries"; then
    fail "release app bundle contains JAR signing metadata"
  fi

  verification="$(LC_ALL=C jarsigner -verify -verbose -certs "$AAB" 2>&1)" ||
    fail "jarsigner could not inspect the release app bundle"
  require_contains "$verification" "jar is unsigned." \
    "release app bundle is signed or its unsigned state is unrecognized"
}

require_receiver_permission() {
  local content="$1"
  local receiver_name="$2"
  local expected_permission="$3"

  command -v python3 >/dev/null 2>&1 ||
    fail "python3 is required to inspect the release manifest"

  printf '%s\n' "$content" | python3 -c '
import sys
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
receiver_name = sys.argv[1]
expected_permission = sys.argv[2]

root = ET.fromstring(sys.stdin.read())
matches = [
    element
    for element in root.iter("receiver")
    if element.get(android + "name") == receiver_name
]

if len(matches) != 1:
    raise SystemExit(
        f"expected exactly one receiver {receiver_name!r}, found {len(matches)}"
    )

actual_permission = matches[0].get(android + "permission")
if actual_permission != expected_permission:
    raise SystemExit(
        f"{receiver_name} permission is {actual_permission!r}, "
        f"expected {expected_permission!r}"
    )
' "$receiver_name" "$expected_permission" ||
    fail "$receiver_name is missing or is not protected by $expected_permission"
}

read_packaged_xml_resource() {
  local resource_name="$1"
  local resource_config="$2"
  local resource_path

  resource_path="$($APKANALYZER resources value \
    --config "$resource_config" \
    --type xml \
    --name "$resource_name" \
    "$APK")" || fail "could not resolve xml/$resource_name for $resource_config"
  [[ "$resource_path" == res/*.xml ]] ||
    fail "xml/$resource_name for $resource_config resolved to an unexpected path: $resource_path"

  "$APKANALYZER" resources xml --file "$resource_path" "$APK" ||
    fail "could not read packaged xml/$resource_name for $resource_config"
}

require_backup_policy() {
  local content="$1"
  local policy="$2"

  command -v python3 >/dev/null 2>&1 ||
    fail "python3 is required to inspect packaged backup rules"

  printf '%s\n' "$content" | python3 -c '
import collections
import sys
import xml.etree.ElementTree as ET

policy = sys.argv[1]
root = ET.fromstring(sys.stdin.read())

legacy_disabled = collections.Counter(
    ("exclude", (("domain", domain), ("path", ".")))
    for domain in ("root", "file", "database", "sharedpref", "external")
)
legacy_disabled_with_device_storage = collections.Counter(
    ("exclude", (("domain", domain), ("path", ".")))
    for domain in (
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )
)
device_transfer_includes = collections.Counter(
    [
        ("include", (("domain", "file"), ("path", "routines.json"))),
        ("include", (("domain", "file"), ("path", "user_images/"))),
        ("include", (("domain", "sharedpref"), ("path", "active_routine.xml"))),
        ("include", (("domain", "sharedpref"), ("path", "home_settings.xml"))),
    ]
)
legacy_device_transfer = collections.Counter(
    (
        tag,
        tuple(sorted(attributes + (("requireFlags", "deviceToDeviceTransfer"),))),
    )
    for tag, attributes in device_transfer_includes.elements()
)
cloud_disabled = collections.Counter(
    ("exclude", (("domain", domain), ("path", ".")))
    for domain in (
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )
)

def entries(parent):
    return collections.Counter(
        (child.tag, tuple(sorted(child.attrib.items())))
        for child in parent
    )

def reject(message, actual=None, expected=None):
    print(message, file=sys.stderr)
    if actual is not None:
        print(f"actual: {actual}", file=sys.stderr)
    if expected is not None:
        print(f"expected: {expected}", file=sys.stderr)
    raise SystemExit(1)

if policy == "legacy-disabled":
    if root.tag != "full-backup-content" or root.attrib:
        reject("unexpected API 23 backup root", (root.tag, root.attrib))
    actual = entries(root)
    if actual != legacy_disabled:
        reject("API 23 backup policy changed", actual, legacy_disabled)
elif policy == "legacy-device-transfer":
    if root.tag != "full-backup-content" or root.attrib:
        reject("unexpected API 28-30 backup root", (root.tag, root.attrib))
    actual = entries(root)
    if actual != legacy_device_transfer:
        reject("API 28-30 backup policy changed", actual, legacy_device_transfer)
elif policy == "legacy-disabled-device-storage":
    if root.tag != "full-backup-content" or root.attrib:
        reject("unexpected API 24-27 backup root", (root.tag, root.attrib))
    actual = entries(root)
    if actual != legacy_disabled_with_device_storage:
        reject(
            "API 24-27 backup policy changed",
            actual,
            legacy_disabled_with_device_storage,
        )
elif policy == "modern":
    if root.tag != "data-extraction-rules" or root.attrib:
        reject("unexpected API 31+ backup root", (root.tag, root.attrib))
    sections = {child.tag: child for child in root}
    if len(sections) != len(list(root)) or set(sections) != {
        "cloud-backup",
        "device-transfer",
    }:
        reject("API 31+ backup sections changed", list(sections))
    if sections["cloud-backup"].attrib or sections["device-transfer"].attrib:
        reject("API 31+ backup section attributes changed")
    actual_cloud = entries(sections["cloud-backup"])
    if actual_cloud != cloud_disabled:
        reject("API 31+ cloud policy changed", actual_cloud, cloud_disabled)
    actual_transfer = entries(sections["device-transfer"])
    if actual_transfer != device_transfer_includes:
        reject("API 31+ device-transfer policy changed", actual_transfer, device_transfer_includes)
else:
    reject(f"unknown backup policy check: {policy}")
' "$policy" || fail "packaged backup policy does not match $policy"
}

[[ -f "$APK" ]] || fail "unsigned release APK is missing; run :app:assembleRelease first"
if [[ "$AUDIT_MODE" == "apk-and-bundle" ]]; then
  [[ -f "$AAB" ]] || fail "unsigned release app bundle is missing; run :app:bundleRelease first"
fi

APKANALYZER="$(find_sdk_tool apkanalyzer)"
APKSIG_JAR="$(find_apksig_jar)"
PERMISSIONS="$("$APKANALYZER" manifest permissions "$APK")"
MANIFEST="$("$APKANALYZER" manifest print "$APK")"
SOURCE_MANIFEST="$(<"$PROJECT_ROOT/app/src/main/AndroidManifest.xml")"
BACKUP_RULES_API_23="$(read_packaged_xml_resource backup_rules default)"
BACKUP_RULES_API_24="$(read_packaged_xml_resource backup_rules v24)"
BACKUP_RULES_API_28="$(read_packaged_xml_resource backup_rules v28)"
DATA_EXTRACTION_RULES="$(read_packaged_xml_resource data_extraction_rules default)"

[[ "$PERMISSIONS" == "$EXPECTED_PERMISSION" ]] || {
  printf 'Expected permission:\n%s\nActual permissions:\n%s\n' \
    "$EXPECTED_PERMISSION" "$PERMISSIONS" >&2
  fail "release permissions changed"
}

require_contains "$MANIFEST" 'android:minSdkVersion="23"' "release minSdk is not 23"
require_contains "$MANIFEST" 'android:targetSdkVersion="36"' "release targetSdk is not 36"
require_contains "$MANIFEST" 'android:name="io.github.ewoc2026.visualroutines.MainActivity"' \
  "MainActivity is missing from the release manifest"
require_contains "$MANIFEST" 'android:name="androidx.startup.InitializationProvider"' \
  "AndroidX Startup provider is missing from the release manifest"
require_receiver_permission \
  "$MANIFEST" \
  "androidx.profileinstaller.ProfileInstallReceiver" \
  "android.permission.DUMP"
require_contains "$MANIFEST" 'android:allowBackup="true"' \
  "platform backup behavior changed without updating the baseline"
require_contains "$MANIFEST" 'android:dataExtractionRules=' \
  "release manifest is missing data extraction rules"
require_contains "$MANIFEST" 'android:fullBackupContent=' \
  "release manifest is missing legacy backup rules"
require_contains "$SOURCE_MANIFEST" 'android:dataExtractionRules="@xml/data_extraction_rules"' \
  "source manifest does not link the accepted data extraction rules"
require_contains "$SOURCE_MANIFEST" 'android:fullBackupContent="@xml/backup_rules"' \
  "source manifest does not link the accepted legacy backup rules"
require_backup_policy "$BACKUP_RULES_API_23" legacy-disabled
require_backup_policy "$BACKUP_RULES_API_24" legacy-disabled-device-storage
require_backup_policy "$BACKUP_RULES_API_28" legacy-device-transfer
require_backup_policy "$DATA_EXTRACTION_RULES" modern
require_absent "$MANIFEST" 'DebugIntentActivity' \
  "debug validation activity leaked into the release manifest"
require_absent "$MANIFEST" 'RunnerValidationActivity' \
  "runner image validation activity leaked into the release manifest"
require_absent "$MANIFEST" 'android:debuggable="true"' \
  "release APK is debuggable"
java -cp "$APKSIG_JAR" "$PROJECT_ROOT/scripts/CheckUnsignedApk.java" "$APK" ||
  fail "release APK has a valid or recognized invalid app signer, or apksig could not inspect it"

VCS_INFO="$(unzip -p "$APK" META-INF/version-control-info.textproto)"
APP_METADATA="$(unzip -p "$APK" META-INF/com/android/build/gradle/app-metadata.properties)"
HEAD_REVISION="$(git -C "$PROJECT_ROOT" rev-parse HEAD)"

require_contains "$VCS_INFO" "revision: \"$HEAD_REVISION\"" \
  "embedded version-control revision does not match checked-out HEAD"
require_contains "$APP_METADATA" 'androidGradlePluginVersion=9.3.1' \
  "embedded Android Gradle Plugin version changed without updating the baseline"

if [[ "$AUDIT_MODE" == "apk-and-bundle" ]]; then
  require_unsigned_bundle
  AAB_VCS_INFO="$(unzip -p "$AAB" base/root/META-INF/version-control-info.textproto)"
  AAB_APP_METADATA="$(unzip -p "$AAB" BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties)"
  require_contains "$AAB_VCS_INFO" "revision: \"$HEAD_REVISION\"" \
    "app bundle embedded version-control revision does not match checked-out HEAD"
  require_contains "$AAB_APP_METADATA" 'androidGradlePluginVersion=9.3.1' \
    "app bundle embedded Android Gradle Plugin version changed without updating the baseline"
fi

printf '%s\n' 'Release baseline audit passed.'
printf 'Permissions:\n%s\n' "$PERMISSIONS"
printf 'Artifact metadata:\n%s\n' "$APP_METADATA"
printf 'Version-control metadata:\n%s\n' "$VCS_INFO"
sha256sum "$APK"
if [[ "$AUDIT_MODE" == "apk-and-bundle" ]]; then
  printf '%s\n' 'Release app bundle is unsigned and matches the checked-out HEAD.'
  sha256sum "$AAB"
fi

#!/usr/bin/env bash
set -euo pipefail

# Builds a signed App Store archive and IPA for ThaaLam.
# Usage: ./scripts/archive-ios.sh
# Requires: Xcode, XcodeGen, a signed-in Apple ID with team 9QBV46NATP.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./gradlew :shared:jvmTest

cd iosApp
xcodegen generate

cd "$ROOT"

ARCHIVE="$ROOT/build/ThaaLam.xcarchive"
EXPORT="$ROOT/build/export"

rm -rf "$ARCHIVE" "$EXPORT"

xcodebuild \
  -project "$ROOT/iosApp/ThaaLam.xcodeproj" \
  -scheme ThaaLam \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$ARCHIVE" \
  -allowProvisioningUpdates \
  archive

xcodebuild \
  -exportArchive \
  -archivePath "$ARCHIVE" \
  -exportPath "$EXPORT" \
  -exportOptionsPlist "$ROOT/iosApp/ExportOptions.appstore.plist" \
  -allowProvisioningUpdates

VERIFY="$ROOT/build/verify-ipa"
rm -rf "$VERIFY"
mkdir -p "$VERIFY"
unzip -q "$EXPORT/ThaaLam.ipa" -d "$VERIFY"
codesign --verify --deep --strict --verbose=2 "$VERIFY/Payload/ThaaLam.app"
rm -rf "$VERIFY"

echo
echo "Archive: $ARCHIVE"
echo "IPA:     $EXPORT/ThaaLam.ipa"
echo "Upload with Xcode Organizer or:"
echo "  xcrun altool --upload-app -f \"$EXPORT/ThaaLam.ipa\" -t ios --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>"

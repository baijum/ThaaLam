# ThaaLam — App Store submission guide

> Status: build and archive are prepared locally. The remaining blockers are the
> App Store Connect app record and Apple account/upload credentials.

## 1. Release facts

| Item | Value |
|---|---|
| App name | ThaaLam — Tala Metronome |
| Bundle ID | `com.baijum.thaalam` |
| SKU (suggested) | `thaalam-ios-001` |
| Team ID | `9QBV46NATP` |
| Version | `0.5.0` |
| Build | `5` |
| Category | Music |
| Age rating | 4+ |
| Price | Free (recommended; confirm) |
| Devices | iPhone and iPad |
| Minimum iOS | 16.0 |
| Privacy | Data not collected; no tracking; offline |
| Encryption | `ITSAppUsesNonExemptEncryption = false` |

## 2. What is already done in the repo

- **Privacy manifest** at `iosApp/ThaaLam/PrivacyInfo.xcprivacy`
  (`UserDefaults`, reason `CA92.1`; no tracking or collected data).
- **No network code** and no microphone/camera/location permission.
- **App icon**: 1024×1024, opaque, no alpha.
- **Background audio** declared for the metronome, with an explicit review note
  below.
- **Distribution signing** was exercised successfully with the team
  `9QBV46NATP`; the export produced a store-signed IPA.
- **App Store screenshots** for iPhone 6.9" and iPad 13" are in
  `docs/app-store/iphone-6.9/` and `docs/app-store/ipad-13/`.
- **Listing copy**, keywords and review notes are below.
- **Archive script**: `./scripts/archive-ios.sh`.

## 3. Before uploading: create the app record

1. Sign in to [App Store Connect](https://appstoreconnect.apple.com).
2. Accept any pending **Agreements, Tax, and Banking** items. A free app still
   needs the agreements completed.
3. Go to **Apps → + → New App**.
   - Platform: **iOS**
   - Name: **ThaaLam — Tala Metronome**
   - Primary language: **English (U.S.)**
   - Bundle ID: **com.baijum.thaalam**
   - SKU: **thaalam-ios-001**
   - User access: full access
4. If the bundle ID is not offered, register it in
   [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list)
   as an **App ID** (explicit, `com.baijum.thaalam`), then return to step 3.
5. In **App Information**, set:
   - Category: **Music**
   - Content rights: ThaaLam contains no third-party content; all click sounds
     are synthesized and the tala catalogue is traditional structure data.
   - Age rating: **4+** (no objectionable content, no user-generated content,
     no ads, no purchases).
6. In **App Privacy**, select **Data Not Collected** and **No tracking**.
7. In **Pricing and Availability**, choose **Free** and the countries you want.

## 4. Privacy policy and support URLs

The repository includes GitHub Pages–ready pages:

- `docs/privacy-policy.md`
- `docs/support.md`
- `docs/_config.yml`

**Status: Pages is already enabled** (18 October 2026) with source `main` /
`/docs`, and both pages returned HTTP 200 after the first build:

- Privacy: `https://baijum.github.io/ThaaLam/privacy-policy.html`
- Support: `https://baijum.github.io/ThaaLam/support.html`

Paste those URLs into **App Information → Privacy Policy URL** and
**Support URL**. A marketing URL is optional.

The equivalent GitHub CLI command is:

```bash
gh api --method POST repos/baijum/ThaaLam/pages \
  -f 'source[branch]=main' -f 'source[path]=/docs'
```

If GitHub Pages is not suitable, host the two pages at any stable HTTPS URL and
use those instead. The URLs must be live before submission.

## 5. Listing copy

### Name (30 max)

```
ThaaLam — Tala Metronome
```

### Subtitle (30 max)

```
Carnatic & Hindustani rhythm
```

### Promotional text (170 max)

```
Build polyrhythms, practise kalam and eduppu, and track your progress — with a
metronome that stays sample-accurate and completely offline.
```

### Keywords (100 max)

```
metronome,tala,carnatic,hindustani,rhythm,polyrhythm,tabla,mridangam,practice,swing
```

### Description

```
ThaaLam is a sample-accurate metronome and tala practice companion built for
Carnatic and Hindustani musicians. It runs entirely offline: no account, no ads,
no analytics, no network.

METRONOME
• 10–400 base BPM, fractional tempo, tap tempo
• Additive meters, 1–9 subdivisions, swing, four accent states
• Count-in, gap trainer, stochastic muting and stepped tempo ramp
• Visual latency calibration and haptics

TALAS
• All 35 Suladi Sapta Talas, Chapu talas and 9 Hindustani cycles
• Sam, tali and khali accents; nadai subdivisions
• Kalam 1×/2×/4×, kalai 1/2 and eduppu with a phase-aligned count-in
• Anga gesture sounds: clap, finger, wave, tali and khali
• Theka bols with live highlighting

LAYERS
• Up to three additional voices on the same clock
• Subdivision mixer, polyrhythm and polymeter
• Per-layer sound, gain, swing, offset and solo
• Per-pulse micro-timing and composite cycle rings

PRACTICE
• Local practice log, daily/weekly goals and streaks
• Gap 2.0 patterns with fade and downbeat re-entry
• Touch tap-accuracy grader with histogram and calibration
• CSV/JSON export through the share sheet

Everything stays on your device. No microphone, camera, contacts, location or
tracking permission is requested.
```

### Release notes for 0.5.0

```
First App Store release.

• Sample-accurate metronome with additive meters, subdivisions, swing and
  four accent states.
• All 35 Suladi Sapta Talas, Chapu talas and 9 Hindustani cycles.
• Kalam, kalai, eduppu and anga gesture sounds.
• Up to three polyrhythm/polymeter layers with solo and micro-timing.
• Local practice log, goals and streaks, Gap 2.0 and a tap-accuracy grader.
• Fully offline; no account, ads, analytics or network access.
```

## 6. Screenshots

Upload the flattened, opaque PNGs:

| Device set | Folder | Files |
|---|---|---|
| iPhone 6.9" (1320×2868) | `docs/app-store/iphone-6.9/` | 5 screenshots: tala playback, practice, tala library, tala detail, dark mode |
| iPad 13" (2064×2752) | `docs/app-store/ipad-13/` | 3 screenshots: metronome, practice, tala detail |

App Store Connect accepts 1–10 per device set. Keep the same order for both.

## 7. Build and export the IPA

The checked-in script performs the whole build:

```bash
./scripts/archive-ios.sh
```

It runs the shared tests, regenerates the Xcode project, creates
`build/ThaaLam.xcarchive`, exports `build/export/ThaaLam.ipa`, and verifies the
signature. Manual commands:

```bash
cd /Users/bmuthuka/baijum/ThaaLam
./gradlew :shared:jvmTest

cd iosApp
xcodegen generate
cd ..

xcodebuild -project iosApp/ThaaLam.xcodeproj \
  -scheme ThaaLam \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/ThaaLam.xcarchive \
  -allowProvisioningUpdates \
  archive

xcodebuild -exportArchive \
  -archivePath build/ThaaLam.xcarchive \
  -exportPath build/export \
  -exportOptionsPlist iosApp/ExportOptions.appstore.plist \
  -allowProvisioningUpdates
```

The exported app is signed with **Apple Distribution** and the store
provisioning profile. It is ready to upload; do not re-sign it.

## 8. Upload

### Option A — Xcode Organizer (easiest)

1. Open the archive: `open build/ThaaLam.xcarchive`
2. In Xcode: **Window → Organizer → Archives**.
3. Select the ThaaLam archive → **Distribute App** → **App Store Connect** →
   **Upload**.
4. Choose automatic signing and upload. Wait for processing.

### Option B — Transporter / altool

Open the **Transporter** app, drag `build/export/ThaaLam.ipa`, and upload.

Or, with an App Store Connect API key (App Manager role):

```bash
# Key: ~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8
xcrun altool --validate-app -f build/export/ThaaLam.ipa -t ios \
  --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>

xcrun altool --upload-app -f build/export/ThaaLam.ipa -t ios \
  --apiKey <KEY_ID> --apiIssuer <ISSUER_ID>
```

Never paste a private key into chat or commit it. Keep it in
`~/.appstoreconnect/private_keys/`.

If upload says **"No suitable application records were found"**, the app record
from section 3 does not exist yet or the bundle ID differs.

## 9. TestFlight and submission

1. Wait for the build to finish processing in App Store Connect.
2. Open **TestFlight**, add yourself as an internal tester, install on a real
   iPhone/iPad, and verify:
   - clean launch and audio after granting/denying the notification prompt;
   - background playback with the screen locked;
   - tala selection and kalam/eduppu;
   - layers while playing;
   - practice log, tap pad, gap 2.0 and export.
3. In **App Store → Prepare for Submission**:
   - select build `0.5.0 (5)`;
   - paste the listing copy from section 5;
   - upload screenshots from section 6;
   - set the privacy policy and support URLs;
   - answer **Export Compliance**: the app does not use non-exempt encryption
     (`ITSAppUsesNonExemptEncryption = false`);
   - answer **Advertising Identifier**: no;
   - answer **Content Rights**: it owns or has rights to all content;
   - age rating 4+.
4. Add the review note below.
5. Submit for review.

## 10. App Review notes

```
ThaaLam is an offline metronome and tala practice app. There is no login,
account, subscription, ad, analytics or network access.

Background audio is declared because the metronome must keep clicking while the
screen is locked or the musician switches apps during practice. No audio is
recorded and no microphone permission is requested.

To test:
1. Tap the large play button on Metronome.
2. Open Talas, choose Misra Chapu, and tap Start.
3. Open Practice to see the local session log and tap-accuracy pad.
4. The app works with no network connection.

The practice log stays on the device. The tap grader uses touch input only.
```

## 11. Remaining blockers

- [ ] Create the App Store Connect app record for `com.baijum.thaalam`.
- [x] Publish the privacy policy and support URLs (GitHub Pages is live).
- [ ] Confirm price (free recommended) and availability.
- [ ] Upload the IPA and complete TestFlight.
- [ ] Submit for review.

Once the app record exists, the archive and IPA in `build/export/` can be
uploaded without rebuilding. Bump `CFBundleVersion` for every subsequent upload.

## 12. What can be automated

| Task | Tool / API | Status |
|---|---|---|
| Enable GitHub Pages | `gh api` | **Done** |
| Verify pages | `curl` | **Done** (HTTP 200) |
| Create App Store Connect app record | Apple web UI only | **Cannot be done via public API** |
| Create App Store Connect API key | Apple web UI (Users and Access) | Needs your Apple account |
| Upload IPA | Xcode Organizer, Transporter, or `altool` with API key | Ready once the app record exists and a key is available |
| Submit for review | App Store Connect web UI | Needs your Apple account |

Apple does not expose an App Store Connect API endpoint for creating a new app
record. The record must be created in the web UI; the public API can then manage
builds and metadata. If you create an App Store Connect API key with the **App
Manager** role, place the `.p8` at
`~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8` and the IPA can be
validated and uploaded from the command line.

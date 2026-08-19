# InfoSec Feed

An open-source, security-only intelligence reader for Android and iOS. It keeps
the useful visual rhythm of a modern discovery feed while removing engagement
ranking, advertising, tracking, and unrelated news.

The applications fetch sources directly and work without an account or backend.
Actively exploited and critical vulnerabilities are deliberately ranked above
general headlines.

## Highlights

- 50+ government, vulnerability, threat-intelligence, research and community sources
- CISA Known Exploited Vulnerabilities, recent NVD CVEs and security repositories
- Up to 500 deduplicated items cached for offline reading
- Discover-style image and text cards with category filters and search on iOS
- Full-page, scrollable Android home-screen widget
- Bounded CISA KEV WidgetKit snapshot on iOS
- No account, advertising SDK, analytics SDK or behavioural ranking
- HTTPS-only application traffic and bounded image downloads

## Platforms

| Platform | Status | Minimum | Home-screen experience |
|---|---|---:|---|
| Android | Device-tested on Galaxy S21+ / Android 15 | Android 8 (API 26) | Scrollable, resizable feed widget |
| iOS | Native SwiftUI source with macOS CI | iOS 17 | Medium/large CISA KEV timeline widget |

iOS intentionally differs from Android: Apple controls WidgetKit refresh timing
and does not expose Android's scrollable collection-widget model. The iOS app
contains the full searchable feed; its widget presents a bounded high-signal
snapshot.

## Install the Android release

1. Download `InfoSec-Feed-1.1.0-release.apk` from the GitHub release.
2. Enable **Developer options → USB debugging** on the phone.
3. Connect the phone, accept its authorization prompt, then run:

```powershell
adb devices
adb install -r "InfoSec-Feed-1.1.0-release.apk"
```

The `-r` flag upgrades an existing installation without clearing its cached feed.

### Place the Android widget

1. Long-press an empty area of the home screen.
2. Select **Widgets → InfoSec Feed**.
3. Place and resize it. A full page recreates a security-only Discover-style view.

On Samsung devices, Discover can be disabled from the leftmost home-screen page.
The app cannot occupy Samsung's privileged minus-one provider slot; using a
normal full-page widget avoids rooting, Knox changes and proprietary permissions.

## Build Android

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew test lint assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Release signing is
loaded only from an ignored local `keystore.properties`; public CI never receives
the maintainer's signing key.

## Build iOS

Requirements: macOS, Xcode 16 or later, and XcodeGen.

```bash
brew install xcodegen
cd ios
xcodegen generate
open InfoSecFeed.xcodeproj
```

Select your Apple development team in Xcode before installing on an iPhone.
See [ios/README.md](ios/README.md) for signing and WidgetKit details.

## Source categories

- **Alerts:** CISA, NCSC UK, MSRC, ZDI, JPCERT
- **Vulnerabilities:** NVD, Full Disclosure, Exploit-DB
- **Threat intelligence:** Talos, Unit 42, Microsoft, CrowdStrike, SentinelOne and others
- **News:** The Hacker News, BleepingComputer, The Record, SecurityWeek and others
- **Research:** Project Zero, PortSwigger, watchTowr, Trail of Bits, Wiz and others
- **Community:** SANS ISC, selected security subreddits, GitHub security repositories

Sources can fail independently without breaking the feed. Edit Android's
`Sources.kt` and iOS's `Sources.swift` when proposing roster changes.

## Security and privacy

- Android release builds are non-debuggable, backup-disabled and signed with APK v2/v3.
- App-authored network requests use HTTPS; Android blocks cleartext application traffic.
- Feed images are size-bounded, downsampled and cached with a fixed disk budget.
- Android manual widget refresh uses a non-exported receiver.
- No personal data is collected by this project.
- Publishers receive the device IP address because feeds are fetched directly.

Android's merged manifest also contains the normal wake-lock, boot and foreground
service permissions contributed by AndroidX WorkManager. See [SECURITY.md](SECURITY.md)
for reporting and threat-boundary details.

## Documentation

- [Frequently asked questions](docs/FAQ.md)
- [iOS build and platform differences](ios/README.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## License

MIT — see [LICENSE](LICENSE).

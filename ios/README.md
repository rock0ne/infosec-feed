# iOS build

The iOS app is native SwiftUI and includes a WidgetKit companion. It targets
iOS 17 or later and does not require a backend, account, analytics SDK, or API
key.

## Requirements

- macOS with Xcode 16 or later
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)
- A free or paid Apple development team for installing on a physical iPhone

## Generate and run

```bash
cd ios
xcodegen generate
open InfoSecFeed.xcodeproj
```

In Xcode, select the **InfoSecFeed** target, choose your development team, then
run on a simulator or connected iPhone. The bundle identifiers can be changed
in `project.yml` if your signing team requires unique identifiers.

## Important platform difference

iOS does not permit an Android-style scrollable home-screen widget. WidgetKit
renders a bounded timeline snapshot controlled by the operating system. The
full app provides the searchable, filterable 500-item feed; the widget shows
the latest CISA Known Exploited Vulnerabilities in medium and large sizes.

The repository's macOS GitHub Actions job generates the Xcode project and runs
the iOS unit tests without code signing. App Store or TestFlight distribution
still requires the repository owner to use an Apple Developer account and
complete Apple's signing and review process.

# Frequently asked questions

## Is this a replacement for Google Discover or Apple News?

It is a focused alternative, not a system-provider replacement. The Android
widget can occupy a full normal home-screen page. Neither Android nor iOS grants
ordinary third-party apps ownership of a manufacturer's privileged discovery slot.

## Does it require an account or subscription?

No. The applications fetch public sources directly, store a bounded local cache,
and contain no analytics or advertising SDK.

## Why are exploited CVEs above newer news?

The ranking is based on defensive importance plus recency. CISA KEV entries,
critical CVEs and high-severity CVEs receive explicit time boosts. GitHub pushes
are demoted because repository activity is not necessarily security news.

## Why do some sources occasionally disappear?

Publishers sometimes rate-limit mobile clients, block a region, return an HTML
bot check, or temporarily fail. One source cannot make the overall refresh fail;
the last successful cache remains readable.

## Can I read the feed offline?

Yes, after at least one successful refresh. Opening linked articles still requires
network connectivity.

## How do Android alerts work?

Tap **Alerts: off** inside the app, select **CISA KEV only** (recommended) or
**CISA KEV + critical CVEs**, and approve Android's notification prompt. A
confirmation notification proves the channel is working. The app then checks in
the background approximately every 30 minutes. It does not notify for historical
items already present when alerts are enabled or when the threshold is widened.

If the control says **Alerts: blocked**, tap it to open the app's Android
notification settings. Manufacturer battery optimisation may delay background
checks, so alerts are useful defensive awareness rather than a guaranteed
emergency-warning service.

## Does Android search work offline?

Yes. Search filters the bounded local cache by CVE ID, title, summary and source.
It combines with category chips, so a learner can select **Vulnerabilities** and
then search for `oracle` or a specific `CVE-...` identifier. Search text never
leaves the device.

## Why is the widget not identical to the open application?

Android widgets use the restricted `RemoteViews` framework and cannot embed the
app's RecyclerView. The widget therefore has its own Discover-style card layout,
with images, severity, summary, source and age, plus a matching widget-picker
preview. It remains scrollable when placed on the home screen.

## Does the app hide my IP from publishers?

No. Direct fetching means each responding publisher can see the device's public
IP and normal request metadata. A future privacy relay would require a maintained
backend and a separate privacy and abuse review.

## Why is the iOS widget smaller and not scrollable?

WidgetKit uses operating-system-controlled timeline snapshots rather than
Android's collection-widget model. The iOS widget therefore shows a bounded CISA
KEV view while the full application contains the complete feed.

## How do Windows alerts and background refresh work?

Windows alerts are off by default. Choose **CISA KEV only** or **KEV + critical
CVEs** in the app. Closing or minimising the window leaves InfoSec Feed in the
notification area, where it refreshes every 30 minutes and raises a local alert
only for newly observed matching items. Choose **Exit** from the tray menu to
stop background refresh. No cloud notification service or account is involved.

## Why is there no Windows 11 widget in the first release?

Windows widgets are constrained cards backed by a packaged widget provider, not
arbitrary scrollable application windows. The first Windows release concentrates
on the complete feed, offline behaviour and alerts. A future signed/MSIX release
can add a bounded KEV/critical snapshot after its package lifecycle is tested.

## Why might Windows SmartScreen warn about the GitHub build?

The community build is self-contained but not Authenticode signed. Public code
signing requires a protected certificate and an established release process.
Build from source if you need to verify it immediately; a production installer
should be signed or distributed through the Microsoft Store.

## Can I install the iOS app directly from GitHub?

Source code can be downloaded from GitHub, but Apple requires an app to be signed
for a development team, TestFlight, or App Store distribution. Build it with
Xcode using the instructions in `ios/README.md` until a signed public release is
available through Apple's distribution channels.

## How do I add or remove a source?

Update both platform rosters:

- Android: `app/src/main/java/uk/cybertecpro/infosecfeed/Sources.kt`
- iOS: `ios/InfoSecFeed/Sources.swift`
- Windows: `windows/InfoSecFeed.Windows/Services/SourceCatalog.cs`

Please test the URL, content type, dates, redirects and rate-limit behaviour before
submitting a pull request.

## Why are there no API keys in the repository?

The default product uses public unauthenticated endpoints. Signing keys, service
credentials and personal configuration must never be committed.

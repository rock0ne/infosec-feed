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

## Does the app hide my IP from publishers?

No. Direct fetching means each responding publisher can see the device's public
IP and normal request metadata. A future privacy relay would require a maintained
backend and a separate privacy and abuse review.

## Why is the iOS widget smaller and not scrollable?

WidgetKit uses operating-system-controlled timeline snapshots rather than
Android's collection-widget model. The iOS widget therefore shows a bounded CISA
KEV view while the full application contains the complete feed.

## Can I install the iOS app directly from GitHub?

Source code can be downloaded from GitHub, but Apple requires an app to be signed
for a development team, TestFlight, or App Store distribution. Build it with
Xcode using the instructions in `ios/README.md` until a signed public release is
available through Apple's distribution channels.

## How do I add or remove a source?

Update both platform rosters:

- Android: `app/src/main/java/uk/cybertecpro/infosecfeed/Sources.kt`
- iOS: `ios/InfoSecFeed/Sources.swift`

Please test the URL, content type, dates, redirects and rate-limit behaviour before
submitting a pull request.

## Why are there no API keys in the repository?

The default product uses public unauthenticated endpoints. Signing keys, service
credentials and personal configuration must never be committed.

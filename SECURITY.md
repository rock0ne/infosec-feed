# Security policy

## Supported versions

Security fixes are applied to the latest tagged release.

## Reporting

Do not open a public issue for a vulnerability that could put users or signing
material at risk. Use GitHub's private vulnerability reporting feature for this
repository. Include the affected version, platform, reproduction steps and impact.

## Trust boundaries

- Feed and image content is untrusted publisher input.
- The applications do not execute publisher scripts or render publisher HTML.
- Article links leave the app and open in the user's browser.
- Android restricts image schemes, credentials, response size, decoded dimensions,
  private/local destinations and cache size.
- iOS uses App Transport Security and accepts HTTPS image URLs without credentials;
  its feed roster is compiled into the app.
- Windows permits only credential-free public HTTPS destinations, rejects
  private/local resolved addresses, prohibits XML DTDs and bounds feed/image sizes.
- Public build automation never has access to the Android release-signing key or
  Apple distribution credentials.
- Android security notifications are explicitly opt-in. The channel is created
  locally and the app stores only a bounded set of previously seen feed IDs to
  prevent duplicate or historical alert bursts.
- Windows tray alerts are explicitly opt-in and seed the selected threshold from
  the current cache before notifying, including when the threshold is widened.

No system can guarantee that every external publisher remains trustworthy. Keep
dependencies and platform versions current, and treat source-roster changes as
security-relevant changes.

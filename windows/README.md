# Windows client

InfoSec Feed for Windows is a native WPF application targeting Windows 10 and
Windows 11. It preserves the mobile product contract: direct public-source
fetching, no account or backend, defensive-importance ranking, local search,
offline cache and alerts that are off by default.

## Current capabilities

- The same 50 RSS sources plus CISA KEV, NVD critical/high and GitHub Security
- Discover-style image/text cards, category filtering and instant local search
- Bounded 500-item cache under `%LOCALAPPDATA%\CyberTecPro\InfoSecFeed`
- Ranking parity: KEV +72h, critical +18h, high +5h and GitHub activity -30h
- Optional CISA KEV or KEV-plus-critical alerts
- Thirty-minute refresh while the tray application is running
- HTTPS-only application requests, bounded documents/images and DTD-prohibited XML

Closing or minimising the window keeps the client in the Windows notification
area. Double-click the shield icon to reopen it; its menu also provides Refresh
and Exit commands.

## Build and test

Install the [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0), then
run from the repository root:

```powershell
dotnet test ".\windows\InfoSecFeed.Windows.Tests\InfoSecFeed.Windows.Tests.csproj" -c Release
dotnet publish ".\windows\InfoSecFeed.Windows\InfoSecFeed.Windows.csproj" `
  -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true -p:DebugType=None -p:DebugSymbols=false `
  -o ".\windows\artifacts\win-x64"
```

Launch `windows\artifacts\win-x64\InfoSecFeed.exe`. The application is
self-contained, but the small native runtime files beside the executable are
still required and must be distributed with it.

## Distribution status

GitHub Actions builds a self-contained x64 artifact. It is not yet Authenticode
signed, so Windows SmartScreen can warn when it is downloaded. A broadly
distributed production build should be signed with a protected code-signing
certificate and packaged as MSIX or published through the Microsoft Store.
Signing material must never be stored in this repository.

## Why there is no Windows widget yet

Windows 11 supports third-party widget providers, but they use a constrained
Adaptive Card surface and package-identity lifecycle. As with iOS WidgetKit, a
widget cannot honestly reproduce the full searchable feed. A later signed/MSIX
release can add a bounded CISA KEV and critical-CVE snapshot without changing
the full application's offline-first model.

# NetLens Privacy Policy

_Last updated: 2026-07-14_

NetLens ("the app") is a network diagnostics toolkit for Android. This policy
explains what data the app accesses, what it does with it, and what it never
does.

## Summary

- NetLens does not collect, transmit, or sell any personal data to its
  developer.
- NetLens has no user accounts, no analytics SDK, no crash reporting SDK, and
  no advertising SDK.
- All scan results, history, and settings are stored **only on your device**,
  in a local database. Nothing is uploaded anywhere by the app itself.
- Some tools make direct network requests to third-party services at your
  request (see below) — these go straight from your device to that service,
  never through NetLens's developer.

## Data stored on your device

NetLens stores the following locally, using Android's standard app storage.
It is never transmitted anywhere by the app:

- Scan history and results (ping, traceroute, DNS, LAN scan, port scan, and
  other tool history)
- Discovered LAN device inventory (IP/MAC address, hostname, vendor — from
  your own local network)
- Endpoint Monitor targets and their uptime/latency history
- Wake-on-LAN target list
- App settings (theme, widget configuration)

Uninstalling the app deletes this data. NetLens has no server component and
no account system, so there is nothing for the developer to retain after
uninstall.

## Permissions and why NetLens requests them

| Permission | Why |
|---|---|
| Internet / network state | Every network diagnostic tool needs to make network requests and read connectivity state to function. |
| Access Wi-Fi state / change Wi-Fi state / change Wi-Fi multicast state | Wi-Fi Analyzer, mDNS Browser, and LAN Scan need to read Wi-Fi state and enable multicast to discover devices and services on your local network. |
| Access fine location | Android requires this permission before an app can read Wi-Fi SSID/BSSID or cellular tower information — required for the Wi-Fi Analyzer and Cell Tower Info tools. NetLens does not use this permission to track your location; it is a side effect of Android's permission model for those APIs, and the data is only shown to you, on your device. |
| Post notifications | Used for optional alerts — e.g. a continuous ping finishing, or a new device appearing during LAN Scan — only if you enable them. |
| Foreground service | Keeps a continuous ping running reliably while you have the app open. |

## Third-party network requests (user-initiated only)

A few tools connect to third-party services to do their job. None of these
run automatically — only when you open the relevant screen or explicitly
enable a widget setting:

- **ipinfo.io** — IP Info screen, and (if you enable it) the home-screen
  widget's public-IP display
- **ipwho.is** — Traceroute hop geolocation
- **api.abuseipdb.com** — IP reputation lookup (only if you supply your own
  API key)
- **speed.cloudflare.com** — Speed Test tool

All of these connections are HTTPS and go directly from your device to that
third-party service. NetLens's developer never sees or stores this traffic.
Refer to each service's own privacy policy for how they handle requests they
receive.

## Pro purchase (Google Play version only)

The Google Play version of NetLens offers an optional one-time "Pro" purchase
via Google Play Billing to unlock share/export features. Purchase processing,
payment details, and receipt validation are handled entirely by Google Play —
NetLens never sees your payment information. The app only stores whether you
have an active purchase, encrypted, on your device, so it can unlock Pro
features locally. (The F-Droid build of NetLens has Pro features on by
default and has no billing code at all.)

## What NetLens does not do

- No ads
- No analytics or telemetry SDKs
- No crash reporting SDKs
- No user accounts or sign-in
- No selling or sharing of data with third parties for advertising purposes

## Open source

NetLens is licensed under AGPL-3.0. The full source code is available at
<https://github.com/bearyjd/netlens-android> — you can verify everything in
this policy directly against the code.

## Contact

Questions about this policy or the app: privacy@ventouxadvisoryco.com

Bug reports and feature requests: <https://github.com/bearyjd/netlens-android/issues>

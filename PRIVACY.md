# Privacy Policy

**Effective date:** 2026-04-25

NetLens is an open-source Android application developed by JD Beary. This policy describes what data the app accesses and how it is handled.

## Data Collection

NetLens does **not** collect, store, or transmit any personal data. There are no accounts, no analytics, no advertising SDKs, and no tracking of any kind.

## Network Requests

NetLens is a network diagnostics tool. By design, it sends network traffic to hosts and services **you** specify (ping targets, DNS servers, scan targets, etc.). The app also contacts the following third-party services:

| Service | Purpose | When |
|---------|---------|------|
| [ipinfo.io](https://ipinfo.io) | Public IP geolocation and network info | IP Info screen, Home screen widget |

These services receive your device's public IP address. They are governed by their own privacy policies.

No other network requests are made unless explicitly initiated by the user.

## On-Device Storage

All data — scan results, endpoint monitor history, network events, Wake-on-LAN targets, and widget preferences — is stored locally on your device in a Room database and DataStore. Nothing is synced to external servers.

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | Required for all network diagnostic operations |
| `ACCESS_NETWORK_STATE` | Detect current network connectivity |
| `ACCESS_WIFI_STATE` | Read Wi-Fi details for LAN tools |
| `CHANGE_WIFI_MULTICAST_STATE` | Enable mDNS discovery on local network |

NetLens requests no permissions beyond what is necessary for its core functionality.

## Third-Party Libraries

NetLens uses open-source libraries (Ktor, dnsjava, etc.) that do not collect user data. The full dependency list is available in the source repository.

## Children

NetLens does not knowingly collect information from children under 13.

## Changes

Updates to this policy will be posted in this file and noted in the changelog. The effective date at the top will be updated accordingly.

## Contact

Questions or concerns: [privacy@ventouxadvisoryco.com](mailto:privacy@ventouxadvisoryco.com)

## Source

NetLens is licensed under AGPL-3.0. The complete source code is available at [github.com/bearyjd/netlens-android](https://github.com/bearyjd/netlens-android).

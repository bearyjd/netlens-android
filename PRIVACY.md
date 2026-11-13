# Privacy Policy

**Effective date:** 2026-05-12

NetLens is an open-source Android application published by Ventoux Advisory Co. This policy describes what data the app accesses and how it is handled.

## Data Collection

NetLens has no accounts, no analytics, no advertising SDKs, and no tracking. The developer collects no data and operates no server that receives your data. Some tools look up your public IP address, or addresses you enter, with the third-party services listed below.

## Network Requests

NetLens is a network diagnostics tool. By design, it sends network traffic to hosts and services **you** specify (ping targets, DNS servers, scan targets, etc.). The app also contacts the following third-party services:

| Service | Purpose | When |
|---------|---------|------|
| [ipinfo.io](https://ipinfo.io) | Public IP geolocation and network info | IP Info screen, and the home screen widget after you grant its consent |
| [speed.cloudflare.com](https://speed.cloudflare.com) | Download/upload measurement | Speed Test |
| [ipwho.is](https://ipwho.is) | Geolocation of traceroute hop addresses | Traceroute |
| [abuseipdb.com](https://www.abuseipdb.com) | IP reputation check using your own API key | IP Info reputation check, only if you enter a key |
| Google Public DNS (8.8.8.8) | Default resolver for DNS Lookup, so the name you query is sent to it unless you choose another server; also a TCP connection to port 53 for the widget's latency reading | DNS Lookup, home screen widget refresh |
| Cloudflare DNS (1.1.1.1) | Default host for the home screen latency monitor; change it in settings | Latency monitor |

WHOIS lookups go to whois.iana.org and then to the WHOIS server it refers to for the domain or address you enter.

These services receive your device's public IP address, or for traceroute and reputation checks the address being looked up. They are governed by their own privacy policies.

Apart from the home screen widget's scheduled refresh and the latency monitor, which contact the services above, no other network requests are made unless you start them.

## On-Device Storage

All data — scan results, endpoint monitor history, network events, Wake-on-LAN targets, and widget preferences — is stored locally on your device in a Room database and DataStore. Nothing is synced to external servers.

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | Required for all network diagnostic operations |
| `ACCESS_NETWORK_STATE` | Detect current network connectivity |
| `ACCESS_WIFI_STATE` | Read Wi-Fi details for LAN tools |
| `CHANGE_WIFI_MULTICAST_STATE` | Enable mDNS discovery on local network |
| `CHANGE_WIFI_STATE` | Wi-Fi scanning for the Wi-Fi Analyzer |
| `ACCESS_FINE_LOCATION` | Android requires it to read Wi-Fi scan results, network names, and cell tower info; NetLens does not collect or send your location |

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

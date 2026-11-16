---
title: "Under a VPN that can't be bypassed, the app must read the Wi-Fi network directly, not the process-bound one"
author: Bearyjd
date: 2026-11-16
branch: fix/network-identity-vpn-lan-link
cost: { minutes: 24, tokens: 242310 }
id: l-aa36de36
scope: repo
status: active
src: agent
touches:
  - CHANGELOG.md
  - fastlane/metadata/android/en-US/changelogs/24.txt
  - gradle.properties
  - core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/network/NetworkIdentityImplTest.kt
---

## Decided
Network identity (gateway, subnet, keys, label) is read as: the process-bound network, else the Wi-Fi/Ethernet (non-VPN) network found directly, else the default network. The same lookup picks the network for binding and for identity, so they cannot disagree.

## Tried and ruled out
Trusting "bound, else default". With a non-bypassable VPN (Tailscale here) binding the process to Wi-Fi fails, nothing is bound, and the default network is the tunnel (gateway 0.0.0.0, a /32). Every scan then keyed to the tunnel, and a custom-range scan was rejected outright. Unit tests missed it because the shared fake binder always reports success. It only showed on a real phone: a chip scan filed 38 sightings under no network. An earlier AUTO scan that looked correct was actually keyed to the tunnel with the Wi-Fi name attached.

## Watch out
Tunnel-keyed sightings from before the fix stay orphaned (they were weak l3 hints, nothing strong to migrate). Under such a VPN the LAN binding itself does nothing, so scans reach the LAN only if the VPN leaves local traffic alone. Background watch now sees the real LAN under a VPN and sweeps unbound.

In the code: NetworkIdentityImpl.link(), NetworkLinkReader.lanLink(), findLanNetwork() shared with LanNetworkBinderImpl; regression test in NetworkIdentityImplTest. Check chip-scan attribution on a device with the VPN up.

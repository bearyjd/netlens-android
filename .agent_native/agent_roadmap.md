# Current agent-work status

The prior roadmap is preserved at [docs/archive/agent-roadmap-2026-09-13.md](../docs/archive/agent-roadmap-2026-09-13.md); it records completed, rejected, and deferred proposals so they are not rediscovered as open work.

Current verification priorities:

- Treat `SsdpScannerImpl.isSafeLocationUrl` as high-risk: preserve all restrictions and run independent security review for edits.
- Protect the `known_devices` scan/user write-path split. Real Room DAO and migration coverage is the authoritative check, not a fake alone.
- Compose/Paparazzi tests prove composition only. Glance RemoteViews layout, real Wi-Fi/cellular readings, and widget density require device verification; the active widget work is in [docs/handoffs/widget-4x2-device-forensics-2026-09-13.md](../docs/handoffs/widget-4x2-device-forensics-2026-09-13.md).
- Prefer interface seams and strong shared fakes for ordinary system-service code. Robolectric remains deliberately opt-in for framework-bound tests.

Do not promote an archive item to active work without confirming that its stated condition still exists in the current code.

#!/usr/bin/env python3
"""Acceptance test for the NetLens 4x2 widget on a real launcher.

Measures the placed widget's host view and asserts that the payload TextViews
(WAN public IP, LAN local IP) are actually present in the view tree. RemoteViews
drops children that don't fit rather than clipping them, so "absent from the
uiautomator tree" is exactly the failure mode this checks for -- a Paparazzi
render test cannot see it.

Usage:  verify_widget.py <adb-serial> [FourByTwo|Dashboard|Standard|Compact]
                         [--density 2.4375]
Exit 0 = pass, 1 = fail, 2 = widget not found on the current home screen page.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PKG = "com.ventouxlabs.netlens"
IPV4 = re.compile(r"^\d{1,3}(\.\d{1,3}){3}$")
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
SCAN_MARKERS = ("Scanned", "Stale", "Not scanned", "Scanning")


def adb(serial, *args):
    return subprocess.run(
        ["adb", "-s", serial, *args], capture_output=True, text=True, timeout=60
    )


def dump_ui(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/nl_verify.xml")
    out = adb(serial, "shell", "cat", "/sdcard/nl_verify.xml")
    if not out.stdout.strip().startswith("<"):
        sys.exit(f"could not read uiautomator dump: {out.stdout[:200]}")
    return ET.fromstring(out.stdout)


def rect(node):
    m = BOUNDS.match(node.attrib.get("bounds", ""))
    return tuple(int(g) for g in m.groups()) if m else None


def collect(node, out):
    """Every descendant node belonging to the NetLens widget subtree."""
    if node.attrib.get("package") == PKG:
        out.append(node)
    for child in node:
        collect(child, out)
    return out


def widget_groups(nodes):
    """Partition NetLens nodes into one group per placed widget.

    uiautomator reports only the package, so with two NetLens widgets on the same
    page every node lands in one undifferentiated pile. Group by the outermost
    NetLens rect each node falls inside instead: those rects are the host views and
    they do not overlap.
    """
    # Dedupe first: a host view and its immediate child routinely share identical
    # bounds, and an equal twin would otherwise disqualify its own rect from being
    # a root (every rect contains its twin), leaving no groups at all.
    uniq = list(dict.fromkeys(r for r in (rect(n) for n in nodes) if r))
    roots = [r for r in uniq if not any(o != r and contains(o, r) for o in uniq)]
    return [(root, [n for n in nodes if rect(n) and contains(root, rect(n))]) for root in roots]


def contains(outer, inner):
    return (outer[0] <= inner[0] and outer[1] <= inner[1]
            and outer[2] >= inner[2] and outer[3] >= inner[3])


def classify(texts, width_dp):
    """Which NetLens widget rendered this group: width, then scan footer.

    All four render into the same package, so uiautomator alone cannot name them.
    Content signatures alone are NOT enough -- once the 2x2 gained a LAN block it
    matched the 4x2's "scan footer + WAN/LAN labels" signature exactly. Width
    separates the 4-column widgets from the 2-column ones and cannot drift as the
    layouts change; the scan footer then separates the tall one from the short one
    in each pair (only FourByTwoHeader and StandardWidgetContent render it).

    The normal-width path stays deliberately simple. Narrow FourByTwo selection is
    target-aware and handled by [narrow_four_by_two_candidate] below so a dropped
    payload node can reach the assertions that report it as a FAIL.
    """
    tall = any(x.startswith(SCAN_MARKERS) for x in texts)
    if width_dp >= 300:
        return "FourByTwo" if tall else "Dashboard"
    return "Standard" if tall else "Compact"


def narrow_four_by_two_candidate(texts, width_dp, height_dp):
    """Whether the confirmed FourByTwo target can occupy this narrow, tall host.

    The 4x2 has a ~250dp minimum-width responsive bucket, so its group cannot be
    rejected solely for being narrower than the normal 300dp four-column cutoff.
    Do not require WAN, LAN, Portal, or a scan-footer text here: each is asserted
    later specifically so a missing RemoteViews child yields exit 1, not exit 2.

    Standard is also tall and now renders WAN and LAN, but it lacks Portal and its
    stats row always emits a latency value ending in " ms" (including "— ms"). A
    resized Dashboard can also be narrow and tall without that stats marker, so the
    reliable FourByTwo scan-header/footer marker is required as well. Before its first
    refresh, the same header instead reads exactly "Tap to scan", which is accepted too.
    Portal is itself an asserted 4x2 payload, so neither it nor WAN/LAN may be selection
    prerequisites. This predicate is only used after `dumpsys appwidget` confirms that
    the requested receiver is FourByTwo; two remaining candidates are treated as
    ambiguous rather than selecting one arbitrarily.
    """
    return (
        width_dp < 300
        and height_dp >= 200
        and any(
            text.startswith(SCAN_MARKERS) or text == "Tap to scan"
            for text in texts
        )
        and not any(text.endswith(" ms") for text in texts)
    )


def placed_providers(serial):
    """Which NetLens widget receivers are actually on a home screen.

    uiautomator reports only the package, so without this the script will
    happily measure a different NetLens widget and assert 4x2 rules against
    it -- which produced a false FAIL against DashboardWidget, whose content
    has neither a header nor a VPN label by design.
    """
    out = adb(serial, "shell", "dumpsys", "appwidget").stdout
    body = out.split("Widgets:", 1)[-1].split("Hosts:", 1)[0]
    return re.findall(r"netlens\.widget\.(\w+Receiver)", body)


def main():
    serial = sys.argv[1]
    density = float(sys.argv[sys.argv.index("--density") + 1]) if "--density" in sys.argv else 2.4375

    want = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith("--") else "FourByTwo"
    placed = placed_providers(serial)
    target = next((p for p in placed if p.startswith(want)), None)
    if target is None:
        print(f"placed NetLens widgets: {placed or 'none'}")
        print(f"No {want} widget on a home screen, so this check cannot run.")
        print(f"Add it from the widget picker, then re-run.  (arg 2 picks the widget)")
        return 2
    print(f"checking: {target}   (placed: {placed})")

    root = dump_ui(serial)
    nodes = collect(root, [])
    if not nodes:
        print("WIDGET NOT FOUND on the current home screen page.")
        print("Swipe to the page holding the NetLens widget and re-run.")
        return 2

    groups = widget_groups(nodes)
    normally_classified = [
        (r, g)
        for r, g in groups
        if classify(
            [n.attrib.get("text", "") for n in g if n.attrib.get("text")],
            round((r[2] - r[0]) / density),
        ) == want
    ]
    narrow_candidates = []
    if target.startswith("FourByTwo"):
        narrow_candidates = [
            (r, g)
            for r, g in groups
            if narrow_four_by_two_candidate(
                [n.attrib.get("text", "") for n in g if n.attrib.get("text")],
                round((r[2] - r[0]) / density),
                round((r[3] - r[1]) / density),
            )
        ]
    picked = normally_classified + narrow_candidates
    if len(narrow_candidates) > 1:
        print("Multiple narrow, tall FourByTwo candidates are visible; refusing to guess.")
        return 2
    if not picked:
        seen = [
            classify(
                [n.attrib.get("text", "") for n in g if n.attrib.get("text")],
                round((r[2] - r[0]) / density),
            )
            for r, g in groups
        ]
        print(f"{len(groups)} NetLens widget(s) rendered on this page: {seen or 'none'}")
        print(f"No {want} widget here. Swipe to its page, or add it from the picker.")
        return 2
    if len(groups) > 1:
        print(f"{len(groups)} NetLens widgets on this page; partitioned by host-view rect.")

    host_rect, nodes = picked[0]
    x0, y0, x1, y1 = host_rect
    w_dp, h_dp = round((x1 - x0) / density), round((y1 - y0) / density)

    texts = [n.attrib.get("text", "") for n in nodes if n.attrib.get("text")]
    ips = [t for t in texts if IPV4.match(t)]
    labels = {t for t in texts if t in ("WAN", "LAN")}
    # Both the pre- and post-shortening captions: the visible text was cut to fit a
    # 44dp column ("Protected" -> "VPN On", "Split Tunnel" -> "Split"), while
    # contentDescription keeps the long form. Checking only the old set produced a
    # false FAIL against a correctly-rendering widget.
    vpn_label = {"Protected", "Split Tunnel", "No VPN", "VPN On", "Split"} & set(texts)

    print(f"widget host view : [{x0},{y0}][{x1},{y1}]  =  {w_dp} x {h_dp} dp")
    print(f"labels present   : {sorted(labels) or 'none'}")
    print(f"IPv4 values      : {ips or 'NONE  <-- payload dropped'}")
    print(f"VPN label        : {sorted(vpn_label) or 'NONE  <-- dropped'}")
    print(f"Portal chip      : {'present' if 'Portal' in texts else 'NONE  <-- dropped'}")
    print(f"all text nodes   : {texts}")

    failures = []
    # Each widget renders a different payload; asserting the 4x2's set against
    # DashboardWidget is what produced a false FAIL earlier -- DashboardFullContent
    # has neither a header nor a VPN label by design.
    if target.startswith("FourByTwo"):
        if len(ips) < 2:
            failures.append(f"expected 2 IPv4 values (WAN + LAN), found {len(ips)}")
        if labels != {"WAN", "LAN"}:
            failures.append(f"expected WAN and LAN labels, found {sorted(labels)}")
        # No VPN caption assertion: the 4x2's standalone VPN column was removed and
        # flag+lock moved inline into the status row, matching the 4x1. The state is
        # carried by the badge colour and contentDescription, not by visible text.
        if "Portal" not in texts:
            failures.append("Portal chip dropped from ToolChipsRow")
        if h_dp < 200:
            failures.append(f"{h_dp}dp tall = one grid row; a real 4x2 is ~317dp here")
    elif target.startswith("Dashboard"):
        if len(ips) < 2:
            failures.append(f"expected WAN + LAN addresses, found {len(ips)}")
        if labels != {"WAN", "LAN"}:
            failures.append(f"expected WAN and LAN labels, found {sorted(labels)}")
        if not any(t.startswith("DNS") or t in ("No network", "Captive portal") for t in texts):
            failures.append("status line dropped")
    elif target.startswith("Compact"):
        # CompactFullContent: TopRow(flag/lock + WAN address), BottomRow(signal + LAN).
        if len(ips) < 2:
            failures.append(f"expected WAN + LAN addresses, found {len(ips)}")
        if labels != {"WAN", "LAN"}:
            failures.append(f"expected WAN and LAN labels, found {sorted(labels)}")
    elif target.startswith("Standard"):
        # StandardWidgetContent: header, WidgetIpRow (publicIp only -- no LAN by
        # design), StatsRow (latency - devices), a two-chip row, and a scan footer.
        if not ips:
            failures.append("public IP dropped")
        if not any(t.endswith(("ms",)) for t in texts):
            failures.append("latency dropped from StatsRow")
        if not any("device" in t for t in texts):
            failures.append("device count dropped from StatsRow")
        if not any(t.startswith(("Scanned", "Stale", "Not scanned", "Scanning")) for t in texts):
            failures.append("scan footer dropped")

    truncated = [t for t in texts if t.endswith("…")]
    if truncated:
        failures.append(f"text ellipsized (too little width): {truncated}")

    if failures:
        print("\nFAIL")
        for f in failures:
            print(f"  - {f}")
        return 1
    print(f"\nPASS - full payload renders at {w_dp} x {h_dp} dp")
    return 0


if __name__ == "__main__":
    sys.exit(main())

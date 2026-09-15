# Plan: preserve 4x2 core content at short launcher heights

Issue: #172

## Goal

Make the 4x2 widget retain the network payload and diagnostic actions at the
measured 245dp launcher height, while preserving the existing FULL design at
260dp and above.

## Device evidence

On Pixel 10's outer display at font scale 1.15, a 341x245dp 4x2 allocation
rendered the header, WAN/LAN, and status, but clipped the entire action row.
At 341x306dp all content rendered. The test display override was reset after
the measurement.

## Approach

1. Add a 260dp responsive bucket and select a new `SHORT` variant for
   200-259dp heights; keep the current `FULL` variant for 260dp and above.
2. Reuse the full-width address hierarchy for `SHORT`, but omit only the
   lowest-priority device/encryption detail row and use tight vertical spacing.
   Keep the header, WAN/LAN, status, and four-chip action footer.
3. Add variant-selection tests for 110dp (COMPACT), 200/245dp (SHORT), and
   260/306dp (FULL), then run widget tests and device verification at both
   measured heights.

## Acceptance criteria

- At 341x245dp and font scale 1.15, WAN, LAN, status, and Portal/action chips
  are visibly emitted without ellipsis or dropped RemoteViews.
- At 341x306dp, the existing FULL detail and all chips remain visible.
- No vertical `defaultWeight`, Row `fillMaxHeight`, nested `SectionGap`, or
  root child count above nine is introduced.
- Widget unit tests and the repository unit-test suite pass.

## Out of scope

- Changing the 4x2 width, VPN caption decision (#171), or production widget
  data behavior.
- Reworking COMPACT or FULL visual design beyond responsive variant routing.

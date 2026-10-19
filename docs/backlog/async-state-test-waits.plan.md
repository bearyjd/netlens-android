# Plan: eliminate asynchronous StateFlow snapshot races

Issue: #169

## Goal

Make ViewModel tests wait for the state they assert instead of sampling whichever
`StateFlow` value Turbine has received so far. This removes timing-dependent CI
failures when an operation leaves the test dispatcher.

## Approach

1. Add a regression test using a genuine off-scheduler suspension in a selected
   fake. It must demonstrate that a snapshot can observe stale state while a
   predicate wait reaches the expected terminal state.
2. Add a small `awaitStateWhere` Turbine helper in each affected test file and
   migrate every terminal async assertion in the issue's scope: speedtest,
   ping, whois, portscan, devices, traceroute, and mDNS.
3. Keep tests that intentionally assert an immediate synchronous reducer state
   as direct `awaitItem` assertions; do not turn every test into an unbounded
   wait.
4. Run affected module tests first, then the full three-task unit-test suite.

## Acceptance criteria

- The regression case fails under the old snapshot behavior and passes with the
  predicate wait.
- No issue #169 test file retains `expectMostRecentItem()` for a terminal
  asynchronous assertion.
- Affected module tests and the repository's three-task unit suite pass.

## Out of scope

- Widget sizing/caption issues #171 and #172.
- Production ViewModel behavior; this change hardens test synchronization only.
- Replacing safe, deliberately immediate StateFlow assertions without evidence
  that they race.

## Risks

The helper predicate must describe each test's actual terminal condition; a
generic `isLoading == false` check can accept the wrong error or stale result.
Turbine's timeout remains the bound for a missing terminal state.

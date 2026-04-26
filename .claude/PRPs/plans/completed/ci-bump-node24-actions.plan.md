# Plan: Bump CI Actions to Node.js 24

## Summary
Upgrade all four GitHub Actions in `.github/workflows/ci.yml` from Node.js 20 (deprecated June 2026) to their latest major versions which run on Node.js 24.

## User Story
As a maintainer, I want CI to run on supported Node.js versions, so that builds don't break when GitHub removes Node 20 runners in September 2026.

## Problem → Solution
CI warns: "Node.js 20 actions are deprecated" → Bump to latest action versions running Node.js 24.

## Metadata
- **Complexity**: Small
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 1

---

## UX Design

N/A — internal change.

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `.github/workflows/ci.yml` | all | The only file being changed |

---

## Patterns to Mirror

### CI_WORKFLOW
// SOURCE: .github/workflows/ci.yml:17-39
```yaml
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: 17
  - uses: gradle/actions/setup-gradle@v4
  - uses: actions/upload-artifact@v4
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `.github/workflows/ci.yml` | UPDATE | Bump 4 action versions |

## NOT Building

- No new CI jobs or steps
- No changes to build commands or test commands
- No workflow structure changes

---

## Step-by-Step Tasks

### Task 1: Bump actions/checkout
- **ACTION**: Update version tag
- **IMPLEMENT**: `actions/checkout@v4` → `actions/checkout@v6`
- **GOTCHA**: v6 is the latest (v5 was skipped or superseded). No config changes needed — `checkout` has no breaking changes in its default usage.
- **VALIDATE**: Workflow YAML is valid

### Task 2: Bump actions/setup-java
- **ACTION**: Update version tag
- **IMPLEMENT**: `actions/setup-java@v4` → `actions/setup-java@v5`
- **GOTCHA**: The `distribution` and `java-version` inputs are unchanged between v4 and v5.
- **VALIDATE**: Workflow YAML is valid

### Task 3: Bump gradle/actions/setup-gradle
- **ACTION**: Update version tag
- **IMPLEMENT**: `gradle/actions/setup-gradle@v4` → `gradle/actions/setup-gradle@v6`
- **GOTCHA**: v6 is the latest. The action path within the repo (`/setup-gradle`) stays the same.
- **VALIDATE**: Workflow YAML is valid

### Task 4: Bump actions/upload-artifact
- **ACTION**: Update version tag
- **IMPLEMENT**: `actions/upload-artifact@v4` → `actions/upload-artifact@v7`
- **GOTCHA**: v7 is the latest. The `name`, `path`, and `retention-days` inputs are unchanged.
- **VALIDATE**: Workflow YAML is valid

---

## Validation Commands

### Static Analysis
```bash
# Validate YAML syntax
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"
```
EXPECT: No errors

### Full CI
Push to a branch and verify CI passes on GitHub Actions.

---

## Acceptance Criteria
- [ ] All four actions bumped to latest major versions
- [ ] CI passes on GitHub Actions
- [ ] No Node.js 20 deprecation warning in CI output

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Breaking change in upload-artifact v7 | Low | Low | `name`/`path`/`retention-days` are stable inputs |
| Breaking change in checkout v6 | Very Low | Low | Default usage is unchanged |

## Notes
GitHub will force Node.js 24 on June 2, 2026 and remove Node 20 on September 16, 2026. This change gets ahead of both deadlines.

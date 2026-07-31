---
name: room-migration-schema-default-expertise
description: Why this repo's Room migrations use SQL DEFAULT with no matching entity @ColumnInfo default, and that it is correct
triggers:
  - MIGRATION_
  - ADD COLUMN
  - Room schema validation
  - NetLensDatabase version
  - exportSchema
  - defaultValue
  - IllegalStateException Migration didn't properly handle
---

# Room ADD COLUMN DEFAULT vs Exported Schema (this repo)

## The Insight
In this repo, every additive migration writes `ALTER TABLE ... ADD COLUMN x TYPE NOT NULL DEFAULT '<val>'`, but the entity uses a plain Kotlin default (`val x: String = "..."`) with NO `@ColumnInfo(defaultValue=...)`, and the exported schema JSON records the column with `notNull=true` and NO `defaultValue` key. This mismatch looks like it should trip Room's runtime `TableInfo` schema-validation crash — it does not. The exported schema `formatVersion: 1` used here does not track column defaults, so Room's validation is not sensitive to the SQL `DEFAULT` clause. The clause exists only to give pre-existing rows a value during the ALTER; fresh installs get the value from the entity's Kotlin default via `createSql` (which also has no DEFAULT).

## Why This Matters
A reviewer (or you) will flag "the migration has DEFAULT but the schema/entity doesn't — runtime crash risk" and either block a correct change or add a spurious `@ColumnInfo(defaultValue=)`. It's a false alarm here, and the codebase already ships this exact pattern.

## Recognition Pattern
- Adding a non-null column to an existing Room table via additive migration.
- The `<version>.json` field shows `notNull: true` with no `defaultValue`, while `MIGRATION_N_N+1` supplies a SQL `DEFAULT`.
- You're about to "fix" the mismatch.

## The Approach
Don't. This is the established, shipped pattern — precedents: `watched_networks.watchEnabled DEFAULT 1` (12→13), `history_ping.mode DEFAULT 'FIXED'`, `monitored_endpoints.latencyThresholdMs DEFAULT 1000`, `history_speedtest.latencyMethod DEFAULT 'LEGACY_HTTP'` (13→14). Keep migrations additive-only (single `ADD COLUMN`, no destructive fallback), bump `NetLensDatabase` version, register in `.addMigrations(...)`, and run `assembleFossDebug` to regenerate/export `<version>.json`. The one gap the repo genuinely has: no on-device `MigrationTestHelper` test (no Robolectric/androidTest anywhere), so migration correctness is covered by schema export + entity test only — flag that as a device-gated verification gap, not a blocker.

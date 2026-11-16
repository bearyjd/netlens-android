---
title: "A device edit saves only what changed, and only if the device still holds the values the editor opened with"
author: Bearyjd
date: 2026-11-16
branch: feat/merge-union-and-edit-guard
cost: { minutes: 25, tokens: 53345 }
id: l-02f24eda
scope: repo
status: active
src: agent
touches:
  - core/data/src/test/kotlin/com/ventouxlabs/netlens/core/data/dao/MergeLogTest.kt
---

## Decided
Saving a device's details sends the values the editor opened with. One transaction writes only the changed fields, and only if each still holds its opening value; otherwise nothing is written. A merged-away device is found through the merge log. The sheet then shows the user's changes on top of the current device for review and never saves into the survivor by itself, since a spoofed identifier could pick that survivor. The trust toggle is a compare-and-set. Merging unions tags and notes (by paragraph); name, location and type/OS/model overrides stay survivor-wins.

## Tried and ruled out
Writing the whole row from the editor's copy: a save after an automatic merge filled the survivor wiped the fill, and saving a merged-away device was lost silently. Substring matching for "notes already contained": a short note vanished inside a longer word.

## Watch out
A save that finishes after the sheet was reopened must be dropped (editor-session counter). Auto-merge still refuses when both rows carry the user's details, so it never combines two of their values.

In the code: KnownDeviceDao.saveUserDetails, mergeUserFields, DevicesViewModel session counter and rebase.

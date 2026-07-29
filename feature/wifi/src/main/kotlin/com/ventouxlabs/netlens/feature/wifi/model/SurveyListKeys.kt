package com.ventouxlabs.netlens.feature.wifi.model

/**
 * Lazy-list keys for the Coverage tab.
 *
 * Captured spots and past sessions are rendered by two `items()` calls inside the *same*
 * `LazyColumn`, but they come from two tables with independent autoincrement sequences. Keying
 * both on the raw row id means point 1 and session 1 collide, and Compose throws
 * `IllegalArgumentException: Key "1" was already used` — a crash on the very first capture, since
 * that is the moment a survey has both a point and a session.
 *
 * Namespacing the ids makes the two ranges disjoint by construction.
 */
internal fun surveyPointKey(id: Long): String = "survey-point-$id"

internal fun surveySessionKey(id: Long): String = "survey-session-$id"

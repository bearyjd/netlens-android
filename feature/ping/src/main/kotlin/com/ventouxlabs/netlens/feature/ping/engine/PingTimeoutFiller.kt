package com.ventouxlabs.netlens.feature.ping.engine

import com.ventouxlabs.netlens.feature.ping.model.PingResult

/**
 * Makes lost packets visible.
 *
 * Android's `ping` (toybox) prints **nothing** for a packet that gets no reply — no "timed out"
 * line, no error, just a missing `icmp_seq`. [PingOutputParser] therefore never produces an
 * `isTimeout` result on a mainline device, and a fully dead host used to end a fixed run with an
 * empty results list and no summary at all. The failure has to be *derived*: a sequence number
 * that never showed up is a timeout.
 *
 * Three derivations, all pure so they are directly testable:
 *  - [reconcile] — a reply for seq N after seq M (< N-1) proves M+1..N-1 were lost; synthesize
 *    them. A real reply for a seq already shown as a synthetic timeout **replaces** that row —
 *    `PingScreen` keys its list on `sequenceNumber`, so appending a duplicate would crash the
 *    LazyColumn (this repo's known duplicate-key class).
 *  - [completionFill] — when a fixed-count run ends short, the remainder never got replies.
 *  - the watchdog in `PingViewModel` — covers silence with no later reply to reveal the gap
 *    (dead host in continuous mode); it synthesizes the next expected seq on a timer and feeds
 *    it back through [reconcile].
 */
internal object PingTimeoutFiller {

    /** A reconciled list plus the packet-accounting deltas the counters need. */
    internal data class Reconciled(
        val results: List<PingResult>,
        /** Rows added — each one is a packet newly accounted for (sent). */
        val sentDelta: Int,
        /** Replies newly accounted for: appended real replies + timeouts upgraded by late replies. */
        val receivedDelta: Int,
    )

    /**
     * Appends [result] to [existing], synthesizing timeout rows for any skipped sequence numbers
     * and replacing a synthetic timeout when its real reply arrives late.
     *
     * A result whose seq is already present and not upgradeable is dropped — never duplicated.
     */
    internal fun reconcile(existing: List<PingResult>, result: PingResult): Reconciled {
        // A seq below the window's first row is unplaceable: in continuous mode the list is a
        // rolling window (its own row for that seq may have been truncated away), so appending
        // would break the monotonic order the screen's list and latency chart both assume, and
        // count a packet that was already counted before it scrolled out. Drop it.
        val firstSeq = existing.firstOrNull()?.sequenceNumber
        if (firstSeq != null && result.sequenceNumber < firstSeq) {
            return Reconciled(existing, sentDelta = 0, receivedDelta = 0)
        }

        val index = existing.indexOfFirst { it.sequenceNumber == result.sequenceNumber }
        if (index >= 0) {
            val current = existing[index]
            return if (current.isTimeout && !result.isTimeout) {
                // Late reply beat the row we already gave up on: upgrade in place. One more
                // reply received; no new packet sent.
                Reconciled(
                    results = existing.toMutableList().apply { set(index, result) },
                    sentDelta = 0,
                    receivedDelta = 1,
                )
            } else {
                Reconciled(existing, sentDelta = 0, receivedDelta = 0)
            }
        }

        val lastSeq = existing.lastOrNull()?.sequenceNumber
        val gap = if (lastSeq != null && result.sequenceNumber > lastSeq + 1) {
            ((lastSeq + 1) until result.sequenceNumber).map { seq ->
                PingResult(sequenceNumber = seq, isTimeout = true)
            }
        } else {
            emptyList()
        }

        return Reconciled(
            results = existing + gap + result,
            sentDelta = gap.size + 1,
            receivedDelta = if (result.isTimeout) 0 else 1,
        )
    }

    /**
     * Fills a finished fixed-count run out to [expectedCount] rows with trailing timeouts.
     *
     * Sequence numbering continues from the last seen row so a 0-based (toybox) and a 1-based
     * (iputils) run both stay consistent with themselves; a run with no replies at all is
     * numbered 1..count.
     */
    internal fun completionFill(existing: List<PingResult>, expectedCount: Int): Reconciled {
        val missing = expectedCount - existing.size
        if (missing <= 0) return Reconciled(existing, sentDelta = 0, receivedDelta = 0)
        val nextSeq = (existing.lastOrNull()?.sequenceNumber ?: 0) + 1
        val fill = (0 until missing).map { offset ->
            PingResult(sequenceNumber = nextSeq + offset, isTimeout = true)
        }
        return Reconciled(existing + fill, sentDelta = missing, receivedDelta = 0)
    }

    /** The synthetic timeout the watchdog reports when the stream has gone silent. */
    internal fun nextWatchdogTimeout(existing: List<PingResult>): PingResult =
        PingResult(sequenceNumber = (existing.lastOrNull()?.sequenceNumber ?: 0) + 1, isTimeout = true)
}

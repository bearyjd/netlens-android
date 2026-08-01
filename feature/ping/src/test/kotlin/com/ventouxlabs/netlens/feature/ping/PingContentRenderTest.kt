package com.ventouxlabs.netlens.feature.ping

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.ping.model.PingMode
import com.ventouxlabs.netlens.feature.ping.model.PingResult
import com.ventouxlabs.netlens.feature.ping.model.PingSummary
import com.ventouxlabs.netlens.feature.ping.model.PingUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`. No golden images, and these
 * assert nothing on purpose: a render failure escapes through the JUnit rule.
 *
 * Unlike the other screens, `PingContent` is not purely presentational: it calls
 * `rememberLauncherForActivityResult` for the notification permission, so composing it requires
 * an `ActivityResultRegistryOwner` that Paparazzi does not provide. The no-op owner below supplies
 * one. `onLaunch` is never reached — nothing in a render test taps the button that would fire it —
 * so a stub is honest here rather than a shortcut.
 */
class PingContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private val noopRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }

    private fun reply(seq: Int, ms: Float?) =
        PingResult(sequenceNumber = seq, latencyMs = ms, isTimeout = ms == null, ttl = 56, ip = "93.184.216.34")

    private fun render(state: PingUiState) = paparazzi.snapshot {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides noopRegistryOwner) {
            PingContent(
                state = state,
                onHostChange = {},
                onModeChanged = {},
                onStartPing = { _, _ -> },
                onStopPing = {},
                onCopyResults = {},
            )
        }
    }

    @Test
    fun `the idle empty state renders`() {
        render(PingUiState())
    }

    @Test
    fun `a completed run with a summary renders`() {
        render(
            PingUiState(
                host = "example.com",
                results = (1..4).map { reply(it, 20f + it) },
                summary = PingSummary(
                    transmitted = 4,
                    received = 4,
                    lossPercent = 0f,
                    minMs = 21f,
                    avgMs = 22.5f,
                    maxMs = 24f,
                    jitterMs = 1.2f,
                ),
                totalSent = 4,
                totalReceived = 4,
                elapsedMs = 4_000,
            ),
        )
    }

    @Test
    fun `packet loss renders`() {
        render(
            PingUiState(
                host = "example.com",
                results = listOf(reply(1, 21f), reply(2, null), reply(3, null), reply(4, 24f)),
                summary = PingSummary(transmitted = 4, received = 2, lossPercent = 50f, minMs = 21f, avgMs = 22.5f, maxMs = 24f),
                totalSent = 4,
                totalReceived = 2,
            ),
        )
    }

    @Test
    fun `a continuous run in flight renders`() {
        render(
            PingUiState(
                host = "example.com",
                results = (1..30).map { reply(it, 20f + (it % 5)) },
                isPinging = true,
                mode = PingMode.CONTINUOUS,
                totalSent = 30,
                totalReceived = 30,
                elapsedMs = 30_000,
            ),
        )
    }

    @Test
    fun `an error renders`() {
        render(PingUiState(host = "nope.invalid", error = "Unknown host"))
    }
}

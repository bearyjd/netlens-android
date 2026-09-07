package com.ventouxlabs.netlens.widget.action

import android.content.ContextWrapper
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.widget.util.ChipCatalog
import com.ventouxlabs.netlens.widget.util.Deeplink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * `OpenDeeplinkAction` is the widget module's one untested security boundary: it launches
 * whatever URI a Glance action parameter hands it, gated only by [isAllowedDeeplinkUri]. Needs
 * Robolectric (unlike [WidgetRefreshTest][com.ventouxlabs.netlens.widget.WidgetRefreshTest]) —
 * `Uri.parse` is a genuine stub in the compileSdk 35 android.jar.
 */
@RunWith(RobolectricTestRunner::class)
class DeeplinkActionTest {

    private object FakeGlanceId : GlanceId

    // --- isAllowedDeeplinkUri: the security predicate itself ---

    @Test
    fun `valid deeplinks are allowed`() {
        assertTrue(isAllowedDeeplinkUri(Uri.parse(Deeplink.HOME)))
        assertTrue(isAllowedDeeplinkUri(Uri.parse(Deeplink.POSTURE)))
        assertTrue(isAllowedDeeplinkUri(Uri.parse(Deeplink.pingHost("192.168.1.1"))))
    }

    @Test
    fun `wrong scheme is rejected`() {
        assertTrue(!isAllowedDeeplinkUri(Uri.parse("https://${Deeplink.HOST}/home")))
        assertTrue(!isAllowedDeeplinkUri(Uri.parse("netlens2://${Deeplink.HOST}/home")))
    }

    @Test
    fun `wrong host is rejected`() {
        assertTrue(!isAllowedDeeplinkUri(Uri.parse("${Deeplink.SCHEME}://evil.com/home")))
        assertTrue(!isAllowedDeeplinkUri(Uri.parse("${Deeplink.SCHEME}://feature.evil.com/home")))
    }

    // The actual reason this test class exists: a naive string-contains check on the raw URI
    // could be fooled by userinfo/authority confusion. Uri.host must resolve to the real
    // authority host, not something that merely contains "feature" somewhere in the string.
    @Test
    fun `userinfo authority confusion is rejected`() {
        // "feature" here is the userinfo (before @), not the host — the real host is evil.com.
        val spoofedUserinfo = Uri.parse("${Deeplink.SCHEME}://feature@evil.com/home")
        assertEquals("evil.com", spoofedUserinfo.host)
        assertTrue(!isAllowedDeeplinkUri(spoofedUserinfo))

        // Reverse: evil.com as userinfo, feature as the real host — this one IS legitimately
        // allowed, since the actual authority host is exactly Deeplink.HOST.
        val evilAsUserinfo = Uri.parse("${Deeplink.SCHEME}://evil.com@feature/home")
        assertEquals(Deeplink.HOST, evilAsUserinfo.host)
        assertTrue(isAllowedDeeplinkUri(evilAsUserinfo))
    }

    // Documents actual behavior rather than assuming it — Uri does not normalize case, so this
    // check is case-sensitive. Not asserting this is "correct," just pinning what it does today.
    @Test
    fun `scheme and host matching is case-sensitive`() {
        assertTrue(!isAllowedDeeplinkUri(Uri.parse("NETLENS://FEATURE/home")))
    }

    @Test
    fun `extra path and query on an otherwise-valid uri is still allowed`() {
        assertTrue(isAllowedDeeplinkUri(Uri.parse(Deeplink.lanScanForDevice("10.0.0.5"))))
        assertTrue(isAllowedDeeplinkUri(Uri.parse(Deeplink.issue("abc-123"))))
    }

    // Deeplink.forRoute (widget chips) isn't exercised by isAllowedDeeplinkUri's other cases
    // above, which all use hand-built URIs — this proves the two actually compose correctly for
    // every real catalog entry: each route survives Uri.parse and then passes the allow-check,
    // not just in theory.
    @Test
    fun `every ChipCatalog route builds an allowed, parseable deeplink`() {
        ChipCatalog.ELIGIBLE.forEach { chip ->
            val uri = Uri.parse(Deeplink.forRoute(chip.route))
            assertTrue("Route '${chip.route}' failed the allow-check", isAllowedDeeplinkUri(uri))
        }
    }

    // --- OpenDeeplinkAction.onAction: the guard actually fires, not just the predicate ---

    private fun parametersOf(uri: String?): ActionParameters =
        if (uri != null) actionParametersOf(DeeplinkUriKey.to(uri)) else actionParametersOf()

    @Test
    fun `onAction launches an ACTION_VIEW intent for an allowed deeplink`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val glanceId = FakeGlanceId

        kotlinx.coroutines.runBlocking {
            OpenDeeplinkAction().onAction(context, glanceId, parametersOf(Deeplink.HOME))
        }

        val started = shadowOf(context as ContextWrapper).nextStartedActivity
        requireNotNull(started)
        assertEquals(android.content.Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse(Deeplink.HOME), started.data)
        // Regression: unpinned, this intent resolved into the OTHER build variant when debug
        // and release are installed side by side — a release widget's chip opened the debug
        // app. The intent must always target the package the widget itself belongs to.
        assertEquals(context.packageName, started.`package`)
    }

    @Test
    fun `onAction does not launch anything for a disallowed uri`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val glanceId = FakeGlanceId

        kotlinx.coroutines.runBlocking {
            OpenDeeplinkAction().onAction(context, glanceId, parametersOf("${Deeplink.SCHEME}://evil.com/home"))
        }

        assertNull(shadowOf(context as ContextWrapper).peekNextStartedActivity())
    }

    @Test
    fun `onAction is a no-op when the deeplink parameter is missing`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val glanceId = FakeGlanceId

        kotlinx.coroutines.runBlocking {
            OpenDeeplinkAction().onAction(context, glanceId, parametersOf(null))
        }

        assertNull(shadowOf(context as ContextWrapper).peekNextStartedActivity())
    }
}

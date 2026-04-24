package us.beary.netlens.feature.whois

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import us.beary.netlens.feature.whois.engine.FakeDomainResolver
import us.beary.netlens.feature.whois.engine.FakeRdnsResolver
import us.beary.netlens.feature.whois.engine.FakeWhoisClient
import us.beary.netlens.feature.whois.model.RdnsResult
import us.beary.netlens.feature.whois.model.WhoisResult
import us.beary.netlens.feature.whois.model.WhoisUiState

@OptIn(ExperimentalCoroutinesApi::class)
class WhoisViewModelTest {

    private lateinit var fakeWhoisClient: FakeWhoisClient
    private lateinit var fakeRdnsResolver: FakeRdnsResolver
    private lateinit var fakeDomainResolver: FakeDomainResolver
    private lateinit var viewModel: WhoisViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeWhoisClient = FakeWhoisClient()
        fakeRdnsResolver = FakeRdnsResolver()
        fakeDomainResolver = FakeDomainResolver()
        viewModel = WhoisViewModel(fakeWhoisClient, fakeRdnsResolver, fakeDomainResolver)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        viewModel.state.test {
            assertEquals(WhoisUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `onQueryChanged updates query`() = runTest {
        viewModel.query.test {
            assertEquals("", awaitItem()) // initial

            viewModel.onQueryChanged("example.com")
            assertEquals("example.com", awaitItem())

            viewModel.onQueryChanged("google.com")
            assertEquals("google.com", awaitItem())
        }
    }

    @Test
    fun `lookup with blank input shows error`() = runTest {
        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("   ")

            val state = awaitItem()
            assertTrue(state is WhoisUiState.Error)
            assertEquals(
                "Please enter a domain or IP address",
                (state as WhoisUiState.Error).message,
            )
        }
    }

    @Test
    fun `lookup domain success transitions to Success`() = runTest {
        val whoisResult = WhoisResult(
            domain = "example.com",
            registrar = "Test Registrar",
            createdDate = "2020-01-01",
            expiryDate = "2025-01-01",
            nameServers = listOf("ns1.example.com", "ns2.example.com"),
            rawResponse = "Domain: example.com\nRegistrar: Test Registrar",
        )
        fakeWhoisClient.result = whoisResult
        fakeDomainResolver.ip = "93.184.216.34"
        fakeRdnsResolver.result = RdnsResult(
            ip = "93.184.216.34",
            hostnames = listOf("example.com"),
        )

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("example.com")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Success)
            val success = finalState as WhoisUiState.Success
            assertNotNull(success.whois)
            assertEquals("example.com", success.whois!!.domain)
            assertEquals("Test Registrar", success.whois.registrar)
            assertNotNull(success.rdns)
            assertEquals("93.184.216.34", success.rdns!!.ip)
        }
    }

    @Test
    fun `lookup domain with dns failure still succeeds with whois only`() = runTest {
        val whoisResult = WhoisResult(
            domain = "example.com",
            registrar = "Test Registrar",
            createdDate = "2020-01-01",
            expiryDate = "2025-01-01",
            nameServers = listOf("ns1.example.com"),
            rawResponse = "Domain: example.com",
        )
        fakeWhoisClient.result = whoisResult
        fakeDomainResolver.ip = null

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("example.com")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Success)
            val success = finalState as WhoisUiState.Success
            assertNotNull(success.whois)
            assertNull(success.rdns)
        }
    }

    @Test
    fun `lookup domain failure transitions to Error`() = runTest {
        fakeWhoisClient.error = RuntimeException("Connection refused")
        fakeDomainResolver.ip = null

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("example.com")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Error)
            assertEquals("Connection refused", (finalState as WhoisUiState.Error).message)
        }
    }

    @Test
    fun `lookup IP address performs rdns only`() = runTest {
        val rdnsResult = RdnsResult(
            ip = "8.8.8.8",
            hostnames = listOf("dns.google"),
        )
        fakeRdnsResolver.result = rdnsResult

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("8.8.8.8")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Success)
            val success = finalState as WhoisUiState.Success
            assertNull(success.whois)
            assertNotNull(success.rdns)
            assertEquals("8.8.8.8", success.rdns!!.ip)
            assertEquals(listOf("dns.google"), success.rdns.hostnames)
        }
    }

    @Test
    fun `lookup IP address with rdns failure transitions to Error`() = runTest {
        fakeRdnsResolver.error = RuntimeException("Reverse DNS failed")

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("8.8.8.8")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Error)
            assertEquals(
                "Reverse DNS failed",
                (finalState as WhoisUiState.Error).message,
            )
        }
    }

    @Test
    fun `lookup uses query value when no argument provided`() = runTest {
        val rdnsResult = RdnsResult(
            ip = "1.1.1.1",
            hostnames = listOf("one.one.one.one"),
        )
        fakeRdnsResolver.result = rdnsResult

        viewModel.onQueryChanged("1.1.1.1")

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup() // uses _query.value

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Success)
            val success = finalState as WhoisUiState.Success
            assertNotNull(success.rdns)
            assertEquals("1.1.1.1", success.rdns!!.ip)
        }
    }

    @Test
    fun `lookup trims whitespace from input`() = runTest {
        val rdnsResult = RdnsResult(
            ip = "8.8.4.4",
            hostnames = listOf("dns.google"),
        )
        fakeRdnsResolver.result = rdnsResult

        viewModel.state.test {
            awaitItem() // Idle

            viewModel.lookup("  8.8.4.4  ")

            val finalState = expectMostRecentItem()
            assertTrue(finalState is WhoisUiState.Success)
        }
    }
}

package com.ventoux.netlens.feature.tls

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.core.data.dao.TlsHistoryDao
import com.ventoux.netlens.core.data.model.TlsHistoryEntry
import com.ventoux.netlens.feature.tls.engine.FakeTlsInspector
import com.ventoux.netlens.feature.tls.model.TlsCertInfo
import com.ventoux.netlens.feature.tls.model.TlsInspectResult
import com.ventoux.netlens.feature.tls.model.TlsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class TlsViewModelTest {

    private lateinit var fakeInspector: FakeTlsInspector
    private lateinit var viewModel: TlsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeInspector = FakeTlsInspector()
        viewModel = TlsViewModel(fakeInspector, FakeTlsHistoryDao())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `inspect success transitions to Success`() = runTest {
        val cert = TlsCertInfo(
            subjectCN = "example.com",
            issuerCN = "Let's Encrypt",
            serialNumber = "ABC123",
            notBefore = "2025-01-01",
            notAfter = "2026-01-01",
            signatureAlgorithm = "SHA256withRSA",
            isExpired = false,
            daysUntilExpiry = 365,
        )
        val expectedResult = TlsInspectResult(
            host = "example.com",
            port = 443,
            protocol = "TLSv1.3",
            cipherSuite = "TLS_AES_256_GCM_SHA384",
            certificates = listOf(cert),
        )
        fakeInspector.result = expectedResult

        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
            viewModel.inspect("example.com")
            assertEquals(TlsUiState.Success(expectedResult), awaitItem())
        }
    }

    @Test
    fun `inspect emits Loading before Success`() = runTest {
        val expectedResult = TlsInspectResult(
            host = "example.com",
            port = 443,
            protocol = "TLSv1.3",
            cipherSuite = "TLS_AES_256_GCM_SHA384",
            certificates = emptyList(),
        )
        fakeInspector.result = expectedResult
        fakeInspector.enableSuspend()

        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
            viewModel.inspect("example.com")
            assertEquals(TlsUiState.Loading, awaitItem())
            fakeInspector.resume()
            assertEquals(TlsUiState.Success(expectedResult), awaitItem())
        }
    }

    @Test
    fun `inspect failure transitions to Error`() = runTest {
        fakeInspector.error = RuntimeException("Connection refused")

        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
            viewModel.inspect("example.com")
            assertEquals(TlsUiState.Error("Connection refused"), awaitItem())
        }
    }

    @Test
    fun `inspect failure with null message shows default error`() = runTest {
        fakeInspector.error = RuntimeException()

        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
            viewModel.inspect("example.com")
            assertEquals(TlsUiState.Error("TLS inspection failed"), awaitItem())
        }
    }

    @Test
    fun `inspect uses custom port`() = runTest {
        val expectedResult = TlsInspectResult(
            host = "example.com",
            port = 8443,
            protocol = "TLSv1.3",
            cipherSuite = "TLS_AES_256_GCM_SHA384",
            certificates = emptyList(),
        )
        fakeInspector.result = expectedResult

        viewModel.uiState.test {
            assertEquals(TlsUiState.Idle, awaitItem())
            viewModel.inspect("example.com", port = 8443)
            assertEquals(TlsUiState.Success(expectedResult), awaitItem())
        }
    }
    @Test
    fun `buildExportText formats certificate details`() = runTest {
        val cert = TlsCertInfo(
            subjectCN = "example.com",
            issuerCN = "Let's Encrypt",
            serialNumber = "ABC123",
            notBefore = "2025-01-01",
            notAfter = "2026-01-01",
            signatureAlgorithm = "SHA256withRSA",
            isExpired = false,
            daysUntilExpiry = 365,
        )
        val result = TlsInspectResult(
            host = "example.com",
            port = 443,
            protocol = "TLSv1.3",
            cipherSuite = "TLS_AES_256_GCM_SHA384",
            certificates = listOf(cert),
        )
        fakeInspector.result = result
        viewModel.inspect("example.com")

        val text = viewModel.buildExportText()
        assertTrue(text.contains("TLS Inspector for example.com:443:"))
        assertTrue(text.contains("Protocol: TLSv1.3"))
        assertTrue(text.contains("Cipher: TLS_AES_256_GCM_SHA384"))
        assertTrue(text.contains("--- Certificate 1 ---"))
        assertTrue(text.contains("Subject: example.com"))
        assertTrue(text.contains("Issuer: Let's Encrypt"))
        assertTrue(text.contains("Valid: 2025-01-01 to 2026-01-01"))
        assertTrue(text.contains("Algorithm: SHA256withRSA"))
    }

    @Test
    fun `buildExportText returns empty when not Success`() = runTest {
        val text = viewModel.buildExportText()
        assertEquals("", text)
    }
}

private class FakeTlsHistoryDao : TlsHistoryDao {
    override fun getRecent(limit: Int): Flow<List<TlsHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<TlsHistoryEntry>> = flowOf(emptyList())
    override suspend fun getById(id: Long): TlsHistoryEntry? = null
    override suspend fun insert(entry: TlsHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}

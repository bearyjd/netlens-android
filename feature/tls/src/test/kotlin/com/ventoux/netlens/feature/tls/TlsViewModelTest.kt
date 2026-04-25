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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.feature.tls.engine.FakeTlsInspector
import com.ventoux.netlens.feature.tls.model.TlsCertInfo
import com.ventoux.netlens.feature.tls.model.TlsInspectResult
import com.ventoux.netlens.feature.tls.model.TlsUiState

@OptIn(ExperimentalCoroutinesApi::class)
class TlsViewModelTest {

    private lateinit var fakeInspector: FakeTlsInspector
    private lateinit var viewModel: TlsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeInspector = FakeTlsInspector()
        viewModel = TlsViewModel(fakeInspector)
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
}

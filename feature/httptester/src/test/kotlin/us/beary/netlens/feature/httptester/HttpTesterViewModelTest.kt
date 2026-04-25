package us.beary.netlens.feature.httptester

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
import us.beary.netlens.feature.httptester.engine.FakeHttpRequester
import us.beary.netlens.feature.httptester.model.HttpMethod
import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpResponseResult
import us.beary.netlens.feature.httptester.model.HttpTesterUiState

@OptIn(ExperimentalCoroutinesApi::class)
class HttpTesterViewModelTest {

    private lateinit var fakeRequester: FakeHttpRequester
    private lateinit var viewModel: HttpTesterViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRequester = FakeHttpRequester()
        viewModel = HttpTesterViewModel(fakeRequester)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testConfig = HttpRequestConfig(
        url = "https://example.com",
        method = HttpMethod.GET,
        headers = emptyMap(),
        body = null,
    )

    private val testResponse = HttpResponseResult(
        statusCode = 200,
        statusDescription = "OK",
        headers = mapOf("Content-Type" to listOf("text/html")),
        body = "<html>Hello</html>",
        latencyMs = 150,
        contentLength = 18,
    )

    @Test
    fun `initial state is Idle`() = runTest {
        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `sendRequest success transitions to Success`() = runTest {
        fakeRequester.result = testResponse

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(testConfig)
            // Loading is set synchronously before the coroutine launch
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Success(testResponse), awaitItem())
        }
    }

    @Test
    fun `sendRequest failure transitions to Error`() = runTest {
        fakeRequester.error = RuntimeException("Connection timeout")

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(testConfig)
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Error("Connection timeout"), awaitItem())
        }
    }

    @Test
    fun `sendRequest failure with null message shows default error`() = runTest {
        fakeRequester.error = RuntimeException()

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(testConfig)
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Error("Request failed"), awaitItem())
        }
    }

    @Test
    fun `sendRequest with IllegalArgumentException shows invalid request error`() = runTest {
        fakeRequester.error = IllegalArgumentException("Invalid URL")

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(testConfig)
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Error("Invalid URL"), awaitItem())
        }
    }

    @Test
    fun `sendRequest with IllegalArgumentException and null message shows default`() = runTest {
        fakeRequester.error = IllegalArgumentException()

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(testConfig)
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Error("Invalid request"), awaitItem())
        }
    }

    @Test
    fun `sendRequest with POST method succeeds`() = runTest {
        val postConfig = HttpRequestConfig(
            url = "https://example.com/api",
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"key":"value"}""",
        )
        val postResponse = testResponse.copy(statusCode = 201, statusDescription = "Created")
        fakeRequester.result = postResponse

        viewModel.state.test {
            assertEquals(HttpTesterUiState.Idle, awaitItem())
            viewModel.sendRequest(postConfig)
            assertEquals(HttpTesterUiState.Loading, awaitItem())
            assertEquals(HttpTesterUiState.Success(postResponse), awaitItem())
        }
    }
}

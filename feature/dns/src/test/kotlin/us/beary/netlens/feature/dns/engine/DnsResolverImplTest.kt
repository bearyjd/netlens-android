package us.beary.netlens.feature.dns.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import us.beary.netlens.feature.dns.model.DnsRecordType

class DnsResolverImplTest {

    private val resolver = DnsResolverImpl()

    @Test
    fun `empty domain throws exception`() = runTest {
        assertThrows<Exception> {
            resolver.lookup("", setOf(DnsRecordType.A))
        }
    }

    @Test
    fun `valid nonexistent domain returns success with empty list`() = runTest {
        val result = resolver.lookup(
            "this.domain.does.not.exist.invalid",
            setOf(DnsRecordType.A),
        )
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `empty record types returns success with empty list`() = runTest {
        val result = resolver.lookup("example.com", emptySet())
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `root domain is valid and returns success`() = runTest {
        val result = resolver.lookup(".", setOf(DnsRecordType.A))
        assertTrue(result.isSuccess)
    }
}

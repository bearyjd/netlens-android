package com.ventouxlabs.netlens.feature.whois.engine

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class WhoisClientImplTest {

    private val client = WhoisClientImpl()

    @Test
    fun `blank domain throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.query("   ") }
        }
    }

    @Test
    fun `domain starting with dash throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.query("-example.com") }
        }
    }

    @Test
    fun `domain with invalid chars throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.query("exam ple.com") }
        }
    }

    @Test
    fun `single label without TLD throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.query("localhost") }
        }
    }

    @Test
    fun `valid domain format does not throw IllegalArgumentException`() {
        assertDoesNotThrow {
            runBlocking {
                try {
                    client.query("example.com")
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (_: Exception) {
                    // network I/O failure is expected in unit test context
                }
            }
        }
    }
}

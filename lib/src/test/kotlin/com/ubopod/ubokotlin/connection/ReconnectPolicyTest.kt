package com.ubopod.ubokotlin.connection

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Mirrors `Tests/UboSwiftTests/ReconnectPolicyTests.swift`. Asserts the same
 * backoff schedule the Swift port and the Python GUI client both rely on.
 */
class ReconnectPolicyTest {

    private val tolerance = 0.0001

    @Test
    fun `fast phase returns initial delay for the first eight attempts`() {
        val policy = ReconnectPolicy.Default
        for (attempt in 1..8) {
            assertThat(policy.delaySeconds(attempt)).isWithin(tolerance).of(0.2)
        }
    }

    @Test
    fun `exponential phase starts at base delay and doubles each attempt`() {
        val policy = ReconnectPolicy.Default
        assertThat(policy.delaySeconds(9)).isWithin(tolerance).of(1.0)
        assertThat(policy.delaySeconds(10)).isWithin(tolerance).of(2.0)
        assertThat(policy.delaySeconds(11)).isWithin(tolerance).of(4.0)
        assertThat(policy.delaySeconds(12)).isWithin(tolerance).of(8.0)
        assertThat(policy.delaySeconds(13)).isWithin(tolerance).of(16.0)
    }

    @Test
    fun `caps at max delay`() {
        val policy = ReconnectPolicy.Default
        assertThat(policy.delaySeconds(14)).isWithin(tolerance).of(30.0)
        assertThat(policy.delaySeconds(50)).isWithin(tolerance).of(30.0)
    }

    @Test
    fun `none policy always returns zero`() {
        val policy = ReconnectPolicy.None
        for (attempt in 1..10) {
            assertThat(policy.delaySeconds(attempt)).isEqualTo(0.0)
        }
    }

    @Test
    fun `invalid attempt zero or negative returns zero`() {
        val policy = ReconnectPolicy.Default
        assertThat(policy.delaySeconds(0)).isEqualTo(0.0)
        assertThat(policy.delaySeconds(-1)).isEqualTo(0.0)
    }
}

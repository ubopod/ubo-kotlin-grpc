package com.ubopod.ubokotlin.connection

import kotlin.math.min
import kotlin.math.pow

/**
 * Backoff schedule used by long-lived subscriptions when the underlying
 * stream errors out. Mirrors `Sources/UboSwift/Connection/ReconnectPolicy.swift`
 * which itself mirrors the schedule the Python GUI client uses
 * (`ubo_app/gui/ubo_gui_client/client.py`):
 *
 * - The first [initialFastAttempts] retries are spaced [initialDelaySeconds]
 *   apart so transient drops feel instant.
 * - Subsequent retries grow exponentially from [baseDelaySeconds], capped at
 *   [maxDelaySeconds].
 * - The loop gives up after [maxRetries].
 */
public data class ReconnectPolicy(
    val initialDelaySeconds: Double = 0.2,
    val initialFastAttempts: Int = 8,
    val baseDelaySeconds: Double = 1.0,
    val maxDelaySeconds: Double = 30.0,
    val maxRetries: Int = 50,
) {
    /**
     * Delay before retry [attempt] (1-indexed). Returns 0 for invalid input.
     */
    public fun delaySeconds(attempt: Int): Double {
        if (attempt < 1) return 0.0
        if (attempt <= initialFastAttempts) return initialDelaySeconds
        val normalAttempt = attempt - initialFastAttempts
        val exponent = (normalAttempt - 1).coerceAtLeast(0)
        val computed = baseDelaySeconds * 2.0.pow(exponent.toDouble())
        return min(computed, maxDelaySeconds)
    }

    public companion object {
        public val Default: ReconnectPolicy = ReconnectPolicy()

        /** Disable retries entirely. The first error finishes the stream. */
        public val None: ReconnectPolicy = ReconnectPolicy(
            initialDelaySeconds = 0.0,
            initialFastAttempts = 0,
            baseDelaySeconds = 0.0,
            maxDelaySeconds = 0.0,
            maxRetries = 0,
        )
    }
}

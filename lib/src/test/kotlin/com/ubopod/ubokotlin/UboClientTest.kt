package com.ubopod.ubokotlin

import com.google.common.truth.Truth.assertThat
import com.ubopod.ubokotlin.connection.ConnectionState
import com.ubopod.ubokotlin.connection.ReconnectPolicy
import com.ubopod.ubokotlin.models.Key
import com.ubopod.ubokotlin.models.UboAction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Pure-StateFlow smoke tests for [UboClient]. These don't talk to a real
 * gRPC server — they cover the surface that fails fast when no
 * connection is established (sentinel errors, initial state, reconnect
 * policy plumbing).
 *
 * End-to-end tests against a mock server land alongside the gRPC mock
 * fixture in a follow-up.
 */
class UboClientTest {

    @Test
    fun `initial connection state is disconnected`() {
        val client = UboClient()
        assertThat(client.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(client.isConnected).isFalse()
        assertThat(client.lastError.value).isNull()
        assertThat(client.currentView.value).isNull()
        assertThat(client.statusBar.value).isNull()
        assertThat(client.activeInputs.value).isEmpty()
        assertThat(client.systemStats.value).isNull()
        client.close()
    }

    @Test
    fun `dispatch before connect throws NotConnected`() {
        val client = UboClient()
        try {
            val thrown = runCatching {
                runBlocking { client.dispatch(UboAction.MenuGoBack) }
            }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(UboError.NotConnected::class.java)
        } finally {
            client.close()
        }
    }

    @Test
    fun `pressKey before connect throws NotConnected`() {
        val client = UboClient()
        try {
            val thrown = runCatching {
                runBlocking { client.pressKey(Key.UP) }
            }.exceptionOrNull()
            assertThat(thrown).isInstanceOf(UboError.NotConnected::class.java)
        } finally {
            client.close()
        }
    }

    @Test
    fun `reconnect policy is mutable through the public surface`() {
        val client = UboClient()
        assertThat(client.reconnectPolicy).isEqualTo(ReconnectPolicy.Default)
        client.reconnectPolicy = ReconnectPolicy.None
        assertThat(client.reconnectPolicy).isEqualTo(ReconnectPolicy.None)
        client.close()
    }
}

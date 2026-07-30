package com.pdtoscillo.core.model

/** Transport の接続状態。 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data class Connecting(val config: ConnectionConfig) : ConnectionState

    data class Connected(
        val config: ConnectionConfig,
        val localAddress: String?,
        val remoteAddress: String?,
        val connectedAtEpochMillis: Long,
    ) : ConnectionState

    data class Reconnecting(val config: ConnectionConfig, val attempt: Int, val maxAttempts: Int, val cause: ScopeError?) : ConnectionState

    data class Failed(val config: ConnectionConfig?, val error: ScopeError) : ConnectionState

    val isConnected: Boolean get() = this is Connected

    /** 通信中または通信可能な状態か。UI の進捗表示に使う。 */
    val isBusyState: Boolean get() = this is Connecting || this is Reconnecting
}

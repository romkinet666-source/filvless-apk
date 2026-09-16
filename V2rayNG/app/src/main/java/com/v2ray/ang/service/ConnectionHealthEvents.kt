package com.v2ray.ang.service

internal enum class ConnectionHealthEvent { LOST, RESTORED }

/** Emits user-visible events only after a session has first proved that the tunnel works. */
internal class ConnectionHealthEventState {
    private var wasAvailable = false
    private var outage = false

    fun reset() {
        wasAvailable = false
        outage = false
    }

    fun accept(state: ConnectionHealth): ConnectionHealthEvent? = when (state) {
        ConnectionHealth.AVAILABLE -> {
            val event = if (outage) ConnectionHealthEvent.RESTORED else null
            wasAvailable = true
            outage = false
            event
        }
        ConnectionHealth.WAITING_NETWORK, ConnectionHealth.UNREACHABLE -> {
            if (wasAvailable && !outage) {
                outage = true
                ConnectionHealthEvent.LOST
            } else null
        }
        ConnectionHealth.IDLE -> {
            reset()
            null
        }
        ConnectionHealth.CHECKING, ConnectionHealth.RECOVERING -> null
    }
}

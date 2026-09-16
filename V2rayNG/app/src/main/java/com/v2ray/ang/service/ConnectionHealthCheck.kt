package com.v2ray.ang.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class ConnectionHealth { IDLE, CHECKING, AVAILABLE, UNREACHABLE, WAITING_NETWORK, RECOVERING }

/** A finite check per connection/network change, cancelled when the user stops the VPN. */
internal class ConnectionHealthCheck(
    private val scope: CoroutineScope,
    private val probe: suspend () -> Boolean,
    private val publish: (ConnectionHealth) -> Unit,
) {
    private var job: Job? = null
    private var generation = 0L
    @Synchronized fun start() {
        job?.cancel()
        val token = ++generation
        publish(ConnectionHealth.CHECKING)
        job = scope.launch {
            repeat(3) { attempt ->
                if (attempt > 0) delay(attempt * 2_000L)
                val available = try { probe() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                coroutineContext.ensureActive()
                synchronized(this@ConnectionHealthCheck) {
                    if (token != generation) return@launch
                    if (available) { publish(ConnectionHealth.AVAILABLE); return@launch }
                }
            }
            synchronized(this@ConnectionHealthCheck) {
                if (token == generation) publish(ConnectionHealth.UNREACHABLE)
            }
        }
    }
    @Synchronized fun stop(state: ConnectionHealth = ConnectionHealth.IDLE) {
        generation++
        job?.cancel()
        job = null
        publish(state)
    }
}

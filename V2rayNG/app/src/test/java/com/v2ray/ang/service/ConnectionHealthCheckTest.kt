package com.v2ray.ang.service

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionHealthCheckTest {
    @Test fun successFinishesAndStopCancelsPendingProbe() = runBlocking {
        val events = mutableListOf<ConnectionHealth>()
        val gate = CompletableDeferred<Boolean>()
        val checker = ConnectionHealthCheck(this, { gate.await() }, events::add)
        checker.start()
        yield()
        gate.complete(true)
        yield()
        assertEquals(listOf(ConnectionHealth.CHECKING, ConnectionHealth.AVAILABLE), events)
        checker.stop()
        assertEquals(ConnectionHealth.IDLE, events.last())
    }
    @Test fun networkLossAndUserStopCannotPublishLateSuccess() = runBlocking {
        val events = mutableListOf<ConnectionHealth>()
        val gate = CompletableDeferred<Boolean>()
        val checker = ConnectionHealthCheck(this, { gate.await() }, events::add)
        checker.start(); yield()
        checker.stop(ConnectionHealth.WAITING_NETWORK)
        gate.complete(true); yield()
        assertEquals(listOf(ConnectionHealth.CHECKING, ConnectionHealth.WAITING_NETWORK), events)
        checker.stop()
    }
    @Test fun failuresHaveFiniteRetries() = runBlocking {
        val events = mutableListOf<ConnectionHealth>()
        var attempts = 0
        val done = CompletableDeferred<Unit>()
        val checker = ConnectionHealthCheck(this, { attempts++; false }, {
            events.add(it)
            if (it == ConnectionHealth.UNREACHABLE) done.complete(Unit)
        })
        checker.start()
        withTimeout(10_000) { done.await() }
        assertEquals(3, attempts)
        assertEquals(ConnectionHealth.UNREACHABLE, events.last())
        checker.stop()
    }
}

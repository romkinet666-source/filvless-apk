package com.v2ray.ang.service

/** Ignore late loss/capability events from the old network during make-before-break handover. */
internal class UpstreamTracker<T> {
    @Volatile var current: T? = null
        private set
    private var seenNetwork = false
    @Synchronized fun available(network: T): Boolean {
        val recover = seenNetwork && current != network
        current = network
        seenNetwork = true
        return recover
    }
    @Synchronized fun lost(network: T): Boolean {
        if (current != network) return false
        current = null
        return true
    }
    @Synchronized fun reset() { current = null; seenNetwork = false }
}

package com.v2ray.ang.root

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.TimeUnit

class ProcessCompatTest {
    @Test fun completedProcessReturnsImmediately() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenReturn(0)
        assertTrue(process.waitForCompat(0, TimeUnit.MILLISECONDS))
    }

    @Test fun runningProcessRespectsTimeout() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenThrow(IllegalThreadStateException())
        assertFalse(process.waitForCompat(0, TimeUnit.MILLISECONDS))
        verify(process, never()).waitFor()
    }
}

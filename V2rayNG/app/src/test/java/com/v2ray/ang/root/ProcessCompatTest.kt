package com.v2ray.ang.root

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.TimeUnit

class ProcessCompatTest {
    @Test fun outputIsBoundedAndDrained() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenReturn(0)
        `when`(process.inputStream).thenReturn("x".repeat(200_000).byteInputStream())
        assertEquals(65_536, process.readOutputWithTimeout(2, TimeUnit.SECONDS)?.length)
    }

    @Test fun missingOutputEofCannotBypassDeadline() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenReturn(0)
        `when`(process.inputStream).thenReturn(object : java.io.InputStream() {
            override fun read(): Int { Thread.sleep(10_000); return -1 }
        })
        val start = System.nanoTime()
        assertNull(process.readOutputWithTimeout(40, TimeUnit.MILLISECONDS))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000)
        verify(process).destroy()
    }

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

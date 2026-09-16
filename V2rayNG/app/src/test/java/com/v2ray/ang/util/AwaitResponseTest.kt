package com.v2ray.ang.util

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AwaitResponseTest {
    @Test fun leavingScreenCancelsCallAndIgnoresLateResponse() = runBlocking {
        val call = mock(Call::class.java)
        var callback: Callback? = null
        doAnswer { callback = it.arguments[0] as Callback; null }.`when`(call).enqueue(org.mockito.kotlin.any<Callback>())
        var parsed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse { parsed = true } }
        job.cancelAndJoin()
        verify(call).cancel()
        callback!!.onResponse(call, Response.Builder().request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("{}".toResponseBody()).build())
        assertFalse(parsed)
    }
}

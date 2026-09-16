package com.v2ray.ang.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancel the HTTP request with its screen/worker; always close the response body. */
internal suspend fun <T> Call.awaitResponse(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!continuation.isActive) return
                try { continuation.resume(read(it)) }
                catch (error: Exception) { continuation.resumeWithException(error) }
            }
        }
    })
}

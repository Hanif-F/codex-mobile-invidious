package net.wingress.mobivious.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response

internal data class HttpResponse(val code: Int, val headers: Headers, val body: String) {
    val isSuccessful get() = code in 200..299
    fun header(name: String) = headers[name]
}

/** Cancellation closes the socket even while the body is still being read. */
internal suspend fun Call.awaitBody(maxBytes: Long = Long.MAX_VALUE): HttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    val body = it.body
                    if (body != null && maxBytes < Long.MAX_VALUE) {
                        if (body.contentLength() > maxBytes) throw IOException("Response is too large")
                        body.source().request(maxBytes + 1)
                        if (body.source().buffer.size > maxBytes) throw IOException("Response is too large")
                    }
                    HttpResponse(it.code, it.headers, body?.string().orEmpty())
                }
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    })
}

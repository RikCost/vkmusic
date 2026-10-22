package com.vkmusic.data.api.vk

import com.vkmusic.data.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) =
                continuation.resume(response) { _, _, _ -> response.close() }

            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
        },
    )
}

internal suspend fun Call.awaitBody(requireSuccess: Boolean = true): String = withContext(Dispatchers.IO) {
    await().use { response ->
        if (requireSuccess && !response.isSuccessful) throw HttpStatusException(response.code)
        response.body.string()
    }
}

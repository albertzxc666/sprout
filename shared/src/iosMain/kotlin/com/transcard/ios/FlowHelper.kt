package com.transcard.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Bridge for collecting a Kotlin Flow from Swift.
 * Returns a [Cancellable] — call [Cancellable.cancel] to stop collection.
 */
class Cancellable internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}

fun <T : Any> subscribe(flow: Flow<T>, onEach: (T) -> Unit): Cancellable {
    val scope = CoroutineScope(Dispatchers.Main)
    val job = scope.launch {
        flow.onEach { onEach(it) }.collect()
    }
    return Cancellable(job)
}

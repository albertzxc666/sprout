package com.transcard.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Bridge for collecting a Kotlin Flow from Swift.
 * Returns a [FlowCancellable] — call [FlowCancellable.cancel] to stop collection.
 * Named explicitly to avoid clashing with Swift's `Combine.Cancellable`.
 */
class FlowCancellable internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}

fun <T : Any> subscribe(flow: Flow<T>, onEach: (T) -> Unit): FlowCancellable {
    val scope = CoroutineScope(Dispatchers.Main)
    val job = flow.onEach { onEach(it) }.launchIn(scope)
    return FlowCancellable(job)
}

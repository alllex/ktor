package io.ktor.util.pipeline

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Represents running execution of a pipeline
 */
@ContextDsl
interface PipelineContext<TSubject : Any, TContext : Any> : CoroutineScope {
    /**
     * Object representing context in which pipeline executes
     */
    val context: TContext

    /**
     * Subject of this pipeline execution that goes along the pipeline
     */
    val subject: TSubject

    /**
     * Finishes current pipeline execution
     */
    fun finish()

    /**
     * Continues execution of the pipeline with the given subject
     */
    suspend fun proceedWith(subject: TSubject): TSubject

    /**
     * Continues execution of the pipeline with the same subject
     */
    suspend fun proceed(): TSubject
}

/**
 * Represent an object that launches pipeline execution
 */
@KtorExperimentalAPI
interface PipelineExecutor<R> {
    /**
     * Start pipeline execution or fail if already running and not yet completed.
     */
    suspend fun execute(initial: R): R
}

internal fun <TSubject : Any, TContext : Any> pipelineExecutorFor(
    context: TContext,
    interceptors: List<PipelineInterceptorRaw<TSubject, TContext>>,
    subject: TSubject,
    coroutineContext: CoroutineContext
): PipelineExecutor<TSubject> {
    return SuspendFunctionGun(subject, context, interceptors).apply {
    }
}

private class SuspendFunctionGun<TSubject : Any, TContext : Any>(
    initial: TSubject,
    override val context: TContext,
    private val blocks: List<PipelineInterceptorRaw<TSubject, TContext>>
) : PipelineContext<TSubject, TContext>, PipelineExecutor<TSubject>, CoroutineScope {

    override val coroutineContext: CoroutineContext get() = continuation.context

    // this is impossible to inline because of property name clash
    // between PipelineContext.context and Continuation.context
    private val continuation: Continuation<Unit> = object : Continuation<Unit> {
        @Suppress("UNCHECKED_CAST")
        override val context: CoroutineContext
            get () {
                val cont = rootContinuation
                return when (cont) {
                    null -> throw IllegalStateException("Not started")
                    is Continuation<*> -> cont.context
                    is List<*> -> (cont as List<Continuation<*>>)[0].context
                    else -> throw IllegalStateException("Unexpected rootContinuation value")
                }
            }

        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                suspended = true
                resumeRootWith(Result.failure(result.exceptionOrNull()!!))
                return
            }

            do {
                val index = index  // it is important to read index every time
                if (index == blocks.size) {
                    if (suspended) {
                        resumeRootWith(Result.success(subject))
                    }
                    return
                }

                this@SuspendFunctionGun.index = index + 1  // it is important to increase it before function invocation
                val next = blocks[index]

                try {
                    val me = this@SuspendFunctionGun
                    val rc = next.startCoroutineUninterceptedOrReturn(me, me.continuation)
                    if (rc === COROUTINE_SUSPENDED) {
                        suspended = true
                        return
                    }
                } catch (cause: Throwable) {
                    suspended = true
                    resumeRootWith(Result.failure(cause))
                    return
                }
            } while (true)
        }

    }

    override var subject: TSubject = initial
        private set

    private var rootContinuation: Any? = null
    private var index = 0
    private var suspended = false

    override fun finish() {
        index = blocks.size
    }

    override suspend fun proceed(): TSubject = suspendCoroutineUninterceptedOrReturn { continuation ->
        if (index == blocks.size) return@suspendCoroutineUninterceptedOrReturn subject

        addContinuation(continuation)

        this.continuation.resume(Unit)

        when {
            suspended -> COROUTINE_SUSPENDED
            index == blocks.size -> {
                rootContinuation = null
                subject
            }
            else -> COROUTINE_SUSPENDED
        }
    }

    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        if (index == blocks.size) return initial
        subject = initial

        if (rootContinuation != null) throw IllegalStateException("Already started")
        suspended = false

        return proceed()
    }

    private fun resumeRootWith(result: Result<TSubject>) {
        val rootContinuation = rootContinuation

        @Suppress("UNCHECKED_CAST")
        val next = when (rootContinuation) {
            null -> throw IllegalStateException("No more continuations to resume")
            is Continuation<*> -> {
                this.rootContinuation = null
                rootContinuation
            }
            is ArrayList<*> -> {
                if (rootContinuation.isEmpty()) throw IllegalStateException("No more continuations to resume")
                rootContinuation.removeAt(rootContinuation.lastIndex)
            }
            else -> unexpectedRootContinuationValue(rootContinuation)
        } as Continuation<TSubject>

        next.resumeWith(result)
    }

    private fun addContinuation(continuation: Continuation<TSubject>) {
        val rootContinuation = rootContinuation

        when (rootContinuation) {
            null -> {
                this.rootContinuation = continuation
            }
            is Continuation<*> -> {
                this.rootContinuation = ArrayList<Continuation<*>>().apply {
                    add(rootContinuation)
                    add(continuation)
                }
            }
            is ArrayList<*> -> {
                @Suppress("UNCHECKED_CAST")
                rootContinuation as ArrayList<Continuation<TSubject>>

                rootContinuation.add(continuation)
            }
            else -> unexpectedRootContinuationValue(rootContinuation)
        }
    }

    private fun unexpectedRootContinuationValue(rootContinuation: Any?): Nothing {
        throw IllegalStateException("Unexpected rootContinuation content: $rootContinuation")
    }
}


package io.ktor.util.pipeline

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents running execution of a pipeline
 * @param context object representing context in which pipeline executes
 * @param interceptors list of interceptors to execute
 * @param subject object representing subject that goes along the pipeline
 */
@ContextDsl
class PipelineContext<TSubject : Any, out TContext : Any> constructor(
    val context: TContext,
    private val interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    /**
     * Subject of this pipeline execution
     */
    var subject: TSubject = subject
        private set

    private var index = 0

    /**
     * Finishes current pipeline execution
     */
    fun finish() {
        index = -1
    }

    /**
     * Continues execution of the pipeline with the given subject
     */
    suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    /**
     * Continues execution of the pipeline with the same subject
     */
    suspend fun proceed(): TSubject {
        val index = index
        if (index < 0) return subject

        if (index >= interceptors.size) {
            finish()
            return subject
        }

        return proceedLoop()
    }

    private suspend fun proceedLoop(): TSubject {
        do {
            val index = index
            val interceptors = interceptors
            if (interceptors.size == index) {
                finish()
                break
            }
            val executeInterceptor = interceptors[index]
            this.index = index + 1
            executeInterceptor.invoke(this, subject)
        } while (true)

        return subject
    }
}

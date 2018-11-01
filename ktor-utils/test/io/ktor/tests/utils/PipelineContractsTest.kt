package io.ktor.tests.utils

import io.ktor.util.pipeline.*
import kotlin.coroutines.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
class PipelineContractsTest {
    private var v = 0
    private val phase1 = PipelinePhase("A")
    private val phase2 = PipelinePhase("B")
    private val interceptor1: PipelineInterceptor<Unit, Unit> = { v = 1 }
    private val interceptor2: PipelineInterceptor<Unit, Unit> = { v = 2 }

    @Test
    fun testMergeEmpty() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        second.merge(first)
        assertTrue { first.isEmpty }
        assertTrue { second.isEmpty }

        assertSame(first.interceptors(), second.interceptors())
    }

    @Test
    fun testMergeSingle() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1) {}

        second.merge(first)

        assertSame(first.interceptors(), second.interceptors())
        assertSame(first.interceptors(), first.phaseInterceptors(phase1))
    }

    @Test
    fun testModifyAfterMerge() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1, interceptor1)

        second.merge(first)

        first.intercept(phase1, interceptor2)

        assertNotSame(first.interceptors(), second.interceptors())
        assertTrue { interceptor2 !in second.phaseInterceptors(phase1) }
        assertTrue { interceptor2 !in second.interceptors() }
    }

    private fun Pipeline<Unit, Unit>.createContext() = createContext(Unit, Unit, EmptyCoroutineContext)
}

package org.kalasim.test

import io.kotest.matchers.doubles.*
import io.kotest.matchers.shouldBe
import org.apache.commons.math3.distribution.ExponentialDistribution
import org.junit.Test
import org.kalasim.*
import org.kalasim.analysis.EntityCreatedEvent
import org.kalasim.misc.roundAny
import org.kalasim.monitors.NumericStatisticMonitor
import kotlin.test.assertEquals
import kotlin.test.fail

class GeneratorTest {

    class Customer : Component()

    @Test
    fun testCustomerGenerator() {

        val eventLog = EventLog()

        Environment().apply {
            addEventListener(eventLog)

            ComponentGenerator(iat = ExponentialDistribution(2.0), total = 4) { Customer() }
        }.run(100.0)

        val customers = eventLog()
            .filterIsInstance<EntityCreatedEvent>()
            .map { it.entity }.distinct()
            .filter { it.name.startsWith("Customer") }

        assertEquals(4, customers.size, "incorrect expected customer cont")
    }


    @Test
    fun `it should allow to stop a generator from outside`() = createTestSimulation {
        val cg = ComponentGenerator(iat = constant(1), keepHistory = true) { it.toString() }

        run(10)

        cg.cancel()

        cg.addConsumer { fail() }

        run(10)
    }

    @Test
    fun `it should allow sampling iat from a triangular distribution`() = createTestSimulation(false) {
        val nsm  = NumericStatisticMonitor()

        var lastCreation :TickTime = now
        ComponentGenerator(iat = triangular(4,8,10)) {
            val timeSinceLastArrival = now - lastCreation
            nsm.addValue(timeSinceLastArrival)
            it.toString()
            lastCreation = now
        }

        run(10000)

        nsm.statistics().min shouldBeGreaterThan 4.0
        nsm.statistics().max shouldBeLessThan 10.0
        nsm.values.toList().map{it.roundAny(2)}
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key shouldBe 8.0.plusOrMinus(0.1)
    }

    @Test
    fun `it should allow to stop a generator from inside`() = createTestSimulation {
        val cg = ComponentGenerator(iat = constant(1), keepHistory = true) { it.toString() }

        run(10)

        cg.addConsumer { cg.cancel() }

        run(3)

        cg.addConsumer { fail() }

        run(10)
    }
}
package org.kalasim.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Ignore
import org.junit.Test
import org.kalasim.*
import org.kalasim.ComponentState.DATA
import org.kalasim.ComponentState.SCHEDULED
import org.kalasim.misc.*
import kotlin.test.fail

class ComponentTests {

    @Ignore
    @Test
    fun `it should create components outside of an environment`() {
        // todo should it? E.g. creation time would prevent that
        Component("foo").info.printThis()

        println("done")
    }

    @Test
    fun `components should be in DATA by default unless a process is defines`() = createTestSimulation {

        object : Component() {
            override fun process(): Sequence<Component> {
                return super.process()
            }
        }.componentState shouldBe SCHEDULED


        Component("foo").componentState shouldBe DATA

        Component("foo", process = Component::process).componentState shouldBe DATA
    }


    @Test
    fun `it should allow for an empty generator process`() = createTestSimulation(true) {
        val c = Component()
//        object : Component(){}

        run(20)

        c.componentState shouldBe DATA
    }

    @Test
    fun `it should log to the console`() = captureOutput {

        createTestSimulation(true) {
            object : Component("tester") {
                override fun process() = sequence<Component> {
                    wait(State(true), true)
                    hold(1)
                    request(Resource()) {
                        hold(1)
                    }
                }
            }

            run(5)
        }
    }.stdout shouldBe """time      current               receiver              action                                                           info                               
--------- --------------------- --------------------- ---------------------------------------------------------------- ----------------------------------
.00                             main                  Created
.00                             tester                Created
.00                                                   Activated, scheduled for .00                                     New state: scheduled
.00                             main                  Running +5.00, scheduled for 5.00                                New state: scheduled
.00       tester                State.1               Created                                                          Initial state: true
.00                             tester                Waiting, scheduled for .00                                       New state: scheduled
.00                                                   Hold +1.00, scheduled for 1.00                                   New state: scheduled
1.00                            Resource.1            Created                                                          capacity=1
1.00                            tester                Requesting 1.0 from Resource.1 with priority null and oneof=...
1.00                                                  Claimed 1.0 from 'tester'
1.00                                                  Request honored by Resource.1, scheduled for 1.00                New state: scheduled
1.00                                                  Hold +1.00, scheduled for 2.00                                   New state: scheduled
2.00                                                  Released 1.0 from 'tester'
2.00                                                  ResourceActivityEvent(start=1.00, end=2.00, requester=tester...
2.00                                                  Ended                                                            New state: data""".trimIndent()


    @Test
    fun `it should yield and terminate automagically`() = createTestSimulation {
        val c = object : Component("foo") {
            override fun process() = sequence {
                hold(2)
            }
        }

        run(5)
        c.componentState shouldBe DATA
    }


    @Test
    fun `it should allow to disable interaction logging`() = createTestSimulation {
        trackingPolicyFactory.disableAll()

        val r = Resource()//.apply {  trackingPolicy = ResourceTrackingConfig(logClaimRelease = false ) }
        val s: State<String> = State("foo")

        val c = object : Component("foo") {

            override fun process() = sequence {
                hold(2)

                s.value = "bar"

                request(r) {
//                                    s.value = "bar"
                }

                log("work done")
            }
        }

        val tc = traceCollector()

        run(10)

        tc.traces.apply {
            size shouldBe 2
            last().shouldBeInstanceOf<InteractionEvent>()
            (last() as InteractionEvent).action shouldBe "work done"
        }
    }

    @Test
    fun `it should allow to register and consume custom tracking policies`() = createTestSimulation {
        class CustomConfig(val logSmthg: Boolean = true) : TrackingConfig

        trackingPolicyFactory.register(ResourceTrackingConfig().copy(trackUtilization = false)) {
            it.name.startsWith("Counter")
        }

        trackingPolicyFactory.register(CustomConfig()) {
            it.name.startsWith("Customer")
        }

        var configuredLog = false

        object : Component("Customer1") {
            override fun process() = sequence<Component> {
                if (env.trackingPolicyFactory.getPolicy<CustomConfig>(getThis()).logSmthg) {
                    println("custom configured logging")
                    configuredLog = true
                }
            }
        }

        // do some random stuff to ensure that this does not interfere with custom tracking config
        val r = Resource()//.apply {  trackingPolicy = ResourceTrackingConfig(logClaimRelease = false ) }
        val s: State<String> = State("foo")

        object : Component("foo") {
            override fun process() = sequence {
                hold(2)
                s.value = "bar"
                request(r) {}
                log("work done")
            }
        }

        run(10)

        configuredLog shouldBe true
    }


    @Test
    fun `it should enforce that either hold or until is not null in hold`() = createTestSimulation {
        val c = object : Component("foo") {
            override fun process() = sequence {
                hold(until = null)
                fail("it should not allow calling hold with duration and until being both null ")
            }
        }

        shouldThrow<IllegalArgumentException>() {
            run(5)
        }
    }


    @Test
    fun `it should track status changes`() = createTestSimulation {
        val component = Component("foo")

        run(2)
        component.activate(delay = 1)
        run(2)


        component.statusTimeline.printHistogram()
    }

    @Test
    fun `it should preserve process definition after being data`() = createTestSimulation {
        // note: regression test, because initially broken
        val salabimTwin = """
            import salabim as sim


            class Customer(sim.Component):
                def process(self):
                    print("huhu")


            env = sim.Environment(trace=True)

            c = Customer()

            env.run(till=1)

            c.activate()
        """

        val c = Component()

        run(1)
        c.activate()
        run(1)

    }


    @Test
    fun `it support resume after interrupt`() = createTestSimulation {


        val tool = object : Component("tool") {
            override fun process() = sequence<Component> {
                hold(10)
                log("production finished")
            }
        }

        val mechanic = object : Component("tool") {
            override fun process() = sequence<Component> {
                hold(1)
                tool.interrupt()

                // do maintenance
                hold(2)
                tool.resume()
            }
        }

        run(20)

        tool.isData shouldBe true
        mechanic.isData shouldBe true
    }

    @Test
    // https://github.com/salabim/salabim/issues/24
    fun `all interactions should fail for an interrupted component`() = createTestSimulation {


        val tool = object : Component("tool") {
            override fun process() = sequence<Component> {
                hold(10)
                log("production finished")
            }
        }

        val mechanic = object : Component("tool") {
            override fun process() = sequence<Component> {
                hold(1)
                tool.interrupt()

                hold(1)
                shouldThrow<IllegalArgumentException> {
                    tool.hold(1)
                }

                // do maintenance
                hold(2)
                tool.resume()

                tool.hold(1)

            }
        }

        run(20)

        tool.isData shouldBe true
        mechanic.isData shouldBe true
    }


    @Test
    fun `it should interrupt and resume a passive component`() = createTestSimulation {


        val tool = object : Component("tool") {
            override fun process() = sequence {
                passivate()
            }
        }
        run(1)


        object : Component("interrupter") {
            override fun process() = sequence {
                tool.interrupt()

                // do maintenance
                hold(2)

                tool.resume()
            }
        }

        run(10)

        tool.isPassive shouldBe true
    }

    @Test
    fun `it should  hold on someones elses behalf`() = createTestSimulation(true) {
        val c = object : Component("other") {
            override fun process() = sequence<Component> {
                println("huhu")
                hold(1)
//                yield(getThis())
            }
        }
        val mechanic = object : Component("controller") {
            override fun process() = sequence<Component> {
                with(c) {
                    hold(1)
                }
                println("huhu2")
            }
        }

        val tc = TraceCollector().apply { addEventListener(this) }

        run(20)

        (tc[4] as InteractionEvent).source!!.name shouldBe "other"
    }


    @Test
    fun `it should throw user exceptions`() = createTestSimulation(true) {
        class MyException(msg: String) : IllegalArgumentException(msg)

        object : Component("other") {
            override fun process() = sequence<Component> {

                hold(1)

                throw MyException("something went wrong")
            }
        }

        shouldThrow<MyException> { run(10) }
    }
}


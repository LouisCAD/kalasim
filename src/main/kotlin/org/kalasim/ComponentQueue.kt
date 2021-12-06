package org.kalasim

import com.github.holgerbrandl.jsonbuilder.json
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import org.json.JSONObject
import org.kalasim.misc.*
import org.kalasim.misc.printThis
import org.kalasim.monitors.MetricTimeline
import org.kalasim.monitors.MetricTimelineStats
import org.kalasim.monitors.NumericStatisticMonitor
import org.koin.core.Koin
import java.util.*

data class CQElement<C>(val component: C, val enterTime: TickTime, val priority: Priority? = null)

class ComponentQueue<C>(
    name: String? = null,
//    val q: Queue<CQElement<T>> = LinkedList()
    val q: Queue<CQElement<C>> = PriorityQueue { o1, o2 ->
        compareValuesBy(
            o1,
            o2,
            { it.priority?.value?.times(-1) ?: 0 },
            { it.enterTime })
    },
    koin: Koin = DependencyContext.get()
) : SimulationEntity(name, koin) {

//    constructor(
//        name: String? = null,
//        comparator: Comparator<C>,
//        koin: Koin = DependencyContext.get()
//    ) : this(
//        name,
//        PriorityQueue { o1, o2 ->
//            compareValuesBy(
//                o1,
//                o2,
//                { comparator.compare(o1.component, o2.component) },
//                { it.enterTime })
//        },
//        koin
//    )


    val size: Int
        get() = q.size

    val components
        get() = q.map { it.component }

    //    val ass = AggregateSummaryStatistics()
    val queueLengthMonitor = MetricTimeline("Length of ${this.name}", koin = koin)
    val lengthOfStayMonitor = NumericStatisticMonitor("Length of stay in ${this.name}", koin = koin)


    var trackingPolicy: ComponentCollectionTrackingConfig = ComponentCollectionTrackingConfig()
        set(newPolicy) {
            field = newPolicy

            with(newPolicy) {
                queueLengthMonitor.enabled = trackCollectionStatistics
                lengthOfStayMonitor.enabled = trackCollectionStatistics
            }
        }

    init {
        trackingPolicy = env.trackingPolicyFactory.getPolicy(this)
    }


    fun add(component: C, priority: Priority? = null): Boolean {
//        log(component, "Entering $name")

        val added = q.add(CQElement(component, env.now, priority))

        changeListeners.forEach { it.added(component) }

        queueLengthMonitor.addValue(q.size.toDouble())

        return added
    }

    fun poll(): C {
        val cqe = q.poll()

        changeListeners.forEach { it.polled(cqe.component) }
        updateExitStats(cqe)


        log(trackingPolicy.trackCollectionStatistics) {
            if (cqe.component is Component) {
                InteractionEvent(env.now, env.curComponent, cqe.component as Component, "Left $name", null)
            } else {
                InteractionEvent(env.now, env.curComponent, null, "${cqe.component} left $name", null)
            }
        }

        return cqe.component
    }

    fun remove(component: C): C {
        val cqe = q.first { it.component == component }
        q.remove(cqe)

        changeListeners.forEach { it.removed(cqe.component) }
        updateExitStats(cqe)

//        log(cqe.component, "Removed from $name")

        return cqe.component
    }


    private fun updateExitStats(cqe: CQElement<C>) {
        val (_, enterTime) = cqe

        lengthOfStayMonitor.addValue((env.now - enterTime))
        queueLengthMonitor.addValue(q.size.toDouble())
    }

    fun contains(c: C): Boolean = q.any { it.component == c }


    fun isEmpty() = size == 0

    fun isNotEmpty() = !isEmpty()

    fun printStats() = stats.print()

    fun printHistogram() {
        if (lengthOfStayMonitor.values.size < 2) {
            println("Skipping histogram of '$name' because of to few data")
        } else {
            lengthOfStayMonitor.printHistogram()
            queueLengthMonitor.printHistogram()
        }
    }

    private val changeListeners = mutableListOf<QueueChangeListener<C>>()

    fun addChangeListener(changeListener: QueueChangeListener<C>): QueueChangeListener<C> {
        changeListeners.add(changeListener); return changeListener
    }

    fun removeChangeListener(changeListener: QueueChangeListener<C>) = changeListeners.remove(changeListener)

    /** Update queue position of component after property changes. */
    fun updateOrderOf(c: C) {
        val element = q.find { it.component == c }

        q.remove(element)
        q.add(element)
    }


    val stats: QueueStatistics
        get() = QueueStatistics(this)

    override val info: Jsonable
        get() = QueueInfo(this)
}


class QueueInfo(cq: ComponentQueue<*>) : Jsonable() {

    data class Entry(val component: String, val enterTime: TickTime, val priority: Priority?)

    val name = cq.name
    val timestamp = cq.env.now
    val queue = cq.q.map { Entry(it.component.toString(), it.enterTime, it.priority) }.toList()

    // salabim example
//    Queue 0x2522b637580
//    name=waitingline
//    component(s):
//    customer.4995        enter_time 49978.472 priority=0
//    customer.4996        enter_time 49991.298 priority=0
}

@Suppress("MemberVisibilityCanBePrivate")
class QueueStatistics(cq: ComponentQueue<*>) {

    val name = cq.name
    val timestamp = cq.env.now

    val lengthStats = cq.queueLengthMonitor.statistics(false)
    val lengthStatsExclZeros = MetricTimelineStats(cq.queueLengthMonitor, excludeZeros = true)

    val lengthOfStayStats = cq.lengthOfStayMonitor.statistics()
    val lengthOfStayStatsExclZeros = cq.lengthOfStayMonitor.statistics(excludeZeros = true)

    // Partial support for weighted percentiles was added in https://github.com/apache/commons-math/tree/fe29577cdbcf8d321a0595b3ef7809c8a3ce0166
    // Update once released, use jitpack or publish manually
//    val ninetyfivePercentile = Percentile(0.95).setData()evaluate()


    fun toJson() = json {
        "name" to name
        "timestamp" to timestamp.value
        "type" to this@QueueStatistics.javaClass.simpleName //"queue statistics"

        "length_of_stay" to {
            "all" to lengthOfStayStats.toJson()
            "excl_zeros" to lengthOfStayStatsExclZeros.toJson()
        }

        "queue_length" to {
            "all" to lengthStats.toJson()
            "excl_zeros" to lengthStatsExclZeros.toJson()
        }
    }

    fun print() = toJson().toString(JSON_INDENT).printThis()
}

fun StatisticalSummary.toJson(): JSONObject {
    return json {
        "entries" to n
        "mean" to mean.roundAny().nanAsNull()
        "standard_deviation" to standardDeviation.roundAny().nanAsNull()

        if (this@toJson is DescriptiveStatistics) {
            "median" to standardDeviation.roundAny().nanAsNull()
            "ninety_pct_quantile" to getPercentile(90.0).roundAny().nanAsNull()
            "ninetyfive_pct_quantile" to getPercentile(95.0).roundAny().nanAsNull()
        }
    }
}

internal fun Double?.nanAsNull(): Double? = if (this != null && isNaN()) null else this

//private fun DoubleArray.standardDeviation(): Double = StandardDeviation(false).evaluate(this)


open class QueueChangeListener<C> {
    open fun added(component: C) {}
    open fun removed(component: C) {}
    open fun polled(component: C) {}
}
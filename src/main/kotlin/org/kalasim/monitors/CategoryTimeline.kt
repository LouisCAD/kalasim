package org.kalasim.monitors

import org.kalasim.TickTime
import org.kalasim.misc.*
import org.koin.core.Koin

/**
 * Level monitors tally levels along with the current (simulation) time. e.g. the number of parts a machine is working on.
 *
 * @sample org.kalasim.dokka.freqLevelDemo
 */
class CategoryTimeline<T>(
    val initialValue: T,
    name: String? = null,
    koin: Koin = DependencyContext.get()
) : Monitor<T>(name, koin), ValueTimeline<T> {

    private val timestamps = mutableListOf<TickTime>()
    private val values = ifEnabled { mutableListOf<T>() }

    init {
        reset(initialValue)
    }


    override fun reset(initial: T) {
        require(enabled) { "resetting a disabled timeline is unlikely to have meaningful semantics" }

        values.clear()
        timestamps.clear()

        addValue(initial)
    }

    override fun addValue(value: T) {
        if (!enabled) return

        timestamps.add(env.now)
        values.add(value)
    }

    fun getPct(value: T): Double {
        val durations = xDuration()

        val freqHist = durations
            .zip(values)
            .groupBy { it.second }
            .mapValues { (_, values) ->
                values.sumOf { it.first }
            }

        val total = freqHist.values.sum()

        return (freqHist[value] ?: error("Invalid or non-observed state")) / total
    }

    private fun xDuration(): DoubleArray =
        timestamps.toMutableList()
            .apply { add(env.now) }
            .zipWithNext { first, second -> second - first }
            .toDoubleArray()


    override fun get(time: Number): T? {
        require(timestamps.first() <= time ) {
            "query time must be greater than timeline start (${timestamps.first()})"
        }

        // https://youtrack.jetbrains.com/issue/KT-43776
        return timestamps.zip(values.toList()).reversed().first { it.first <= time.toDouble() }.second
    }

    operator fun get(time: TickTime) = get(time.value)

    override fun total(value: T): Double = statsData().run {
        // https://youtrack.jetbrains.com/issue/KT-43776
        values.zip(durations)
            .filter { it.first == value }
            .sumOf { it.second }
    }

    fun printHistogram(values: List<T>? = null, sortByWeight: Boolean = false) {
        println("Summary of: '${name}'")
        println("Duration: ${env.now - timestamps[0]}")
        println("# Levels: ${this.values.distinct().size}")
        println()

        if (this.values.size <= 1) {
            println("Skipping histogram of '$name' because of to few data")
            return
        }

        //        val ed = EnumeratedDistribution(hist.asCM())
//        repeat(1000){ ed.sample()}.c

        summed().printConsole(sortByWeight = sortByWeight, values = values)
    }

    /** Accumulated retention time of the ComponentState. Only visited states will be included. */
    fun summed(): FrequencyTable<T> = xDuration().zip(this.values)
        .groupBy { (_, value) -> value }
        .map { kv -> kv.key to kv.value.sumOf { (it.first) } }.toMap()


    override fun statisticsSummary() = statsData().statisticalSummary()

    fun statsData(): LevelStatsData<T> {
        require(values.isNotEmpty()) { "data must not be empty when preparing statistics of $name" }

        val valuesLst = values.toList()

        val timepointsExt = timestamps + env.now
        val durations = timepointsExt.toMutableList().zipWithNext { first, second -> second - first }

        return LevelStatsData(valuesLst, timestamps, durations)
    }


    /** Returns the step function of this monitored value along the time axis. */
    override fun stepFun() = statsData().stepFun()


    override fun resetToCurrent() = reset(get(now)!!)

    override fun clearHistory(before: TickTime) {
        val startFromIdx = timestamps.withIndex().firstOrNull { before > it.value }?.index ?: return

        for (i in 0 until startFromIdx) {
            val newTime = timestamps.subList(0, startFromIdx)
            val newValues = values.subList(0, startFromIdx)

            timestamps.apply { clear(); addAll(newTime) }
            values.apply { clear(); addAll(newValues) }
        }
    }
}
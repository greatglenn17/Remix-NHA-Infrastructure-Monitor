package com.example.util

import java.util.Locale

/** The legend of the SDP print: saleable lots by progress band. */
object SdpSummary {
    /** Same bands as the lot colours on the plan, and the choices for a lot's physical progress. */
    val BANDS = listOf("Not yet started", "1–25%", "26–50%", "51–75%", "76–99%", "100% / RFO")
    /** The % a choice of [BANDS] saves: the top of its band. */
    val BAND_VALUES = listOf(0, 25, 50, 75, 99, 100)

    /** Road lots are marked, not tracked by %: none (0%), concreting (1-99%, saved as [ROAD_CONCRETING]) or completed (100%). */
    val ROAD_STATES = listOf("Not completed", "Concreting", "Completed")
    const val ROAD_CONCRETING = 50

    fun roadState(progress: Int): Int = when {
        progress >= 100 -> 2
        progress > 0 -> 1
        else -> 0
    }

    fun band(progress: Int): Int = when {
        progress >= 100 -> 5
        progress >= 76 -> 4
        progress >= 51 -> 3
        progress >= 26 -> 2
        progress > 0 -> 1
        else -> 0
    }

    /** One lot: its use (see LOT_USES), area and progress. */
    data class Item(val use: String, val areaSqM: Double, val progress: Int)

    data class Totals(val count: Int, val areaSqM: Double)

    data class Summary(
        /** Saleable lots per band of [BANDS]. */
        val saleableBands: List<Int>,
        val saleable: Totals,
        /** Road lots per state of [ROAD_STATES]. */
        val roadLots: List<Int> = listOf(0, 0, 0)
    )

    fun of(items: List<Item>): Summary {
        val saleable = items.filter { it.use == "Saleable" }
        return Summary(
            saleableBands = BANDS.indices.map { b -> saleable.count { band(it.progress) == b } },
            saleable = Totals(saleable.size, saleable.sumOf { it.areaSqM }),
            roadLots = ROAD_STATES.indices.map { r -> items.count { it.use == "Road Lot" && roadState(it.progress) == r } }
        )
    }

    /** The box around all [polygons] (points 0..1) plus [margin], kept on the page; null with no points. */
    fun frameAround(polygons: List<List<Pair<Float, Float>>>, margin: Float = 0.03f): List<Float>? {
        val pts = polygons.flatten().ifEmpty { return null }
        return listOf(
            (pts.minOf { it.first } - margin).coerceAtLeast(0f),
            (pts.minOf { it.second } - margin).coerceAtLeast(0f),
            (pts.maxOf { it.first } + margin).coerceAtMost(1f),
            (pts.maxOf { it.second } + margin).coerceAtMost(1f)
        )
    }

    /** A saved frame ("minX,minY,maxX,maxY"), or null when blank or not a box on the page. */
    fun parseFrame(text: String): List<Float>? {
        val v = text.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (v.size != 4 || v.any { it !in 0f..1f } || v[2] <= v[0] || v[3] <= v[1]) return null
        return v
    }

    fun formatFrame(frame: List<Float>): String = frame.joinToString(",") { "%.4f".format(Locale.US, it) }
}

package com.example.util

import java.util.Locale

/** The side table of the SDP print: saleable lots by progress band, road lots, other non-saleable areas. */
object SdpSummary {
    /** Same bands as the lot colours on the plan. */
    val BANDS = listOf("0–25%", "26–50%", "51–75%", "76–99%", "100% / RFO")

    fun band(progress: Int): Int = when {
        progress >= 100 -> 4
        progress >= 76 -> 3
        progress >= 51 -> 2
        progress >= 26 -> 1
        else -> 0
    }

    /** One lot: its use (see LOT_USES), area and progress; [drainage] counts on road lots only. */
    data class Item(val use: String, val areaSqM: Double, val progress: Int, val drainage: Int = 0)

    data class Totals(val count: Int, val areaSqM: Double)

    data class Summary(
        /** Saleable lots per band of [BANDS]. */
        val saleableBands: List<Int>,
        val saleable: Totals,
        val roads: Totals,
        val roadProgress: Double,
        val drainageProgress: Double,
        /** Each non-saleable use that has lots (road lots too), in the order given. */
        val nonSaleable: List<Pair<String, Totals>>
    )

    fun of(items: List<Item>, uses: List<String>): Summary {
        val saleable = items.filter { it.use == "Saleable" }
        val roads = items.filter { it.use == "Road Lot" }
        val nonSaleable = uses.filter { it != "Saleable" }
            .map { use -> use to totals(items.filter { it.use == use }) }
            .filter { it.second.count > 0 }
        return Summary(
            saleableBands = BANDS.indices.map { b -> saleable.count { band(it.progress) == b } },
            saleable = totals(saleable),
            roads = totals(roads),
            roadProgress = weighted(roads) { it.progress },
            drainageProgress = weighted(roads) { it.drainage },
            nonSaleable = nonSaleable
        )
    }

    private fun totals(items: List<Item>) = Totals(items.size, items.sumOf { it.areaSqM })

    /** Weighted by area, so a long road counts more than a short one; a plain average while areas are unknown. */
    private fun weighted(items: List<Item>, value: (Item) -> Int): Double {
        if (items.isEmpty()) return 0.0
        val area = items.sumOf { it.areaSqM }
        return if (items.all { it.areaSqM > 0 }) items.sumOf { value(it) * it.areaSqM } / area
        else items.sumOf { value(it) }.toDouble() / items.size
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

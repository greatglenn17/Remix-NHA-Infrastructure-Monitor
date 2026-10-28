package com.example.util

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds the lots of a subdivision plan in a picture of its lines (a CAD-exported PDF page
 * rendered black on white). Every closed white area is a candidate; the most common size
 * among the near-rectangular ones is a lot. Lots far from the main group (title block,
 * legend, vicinity map) are dropped, and larger clean rectangles inside the site are kept
 * as open space or facility areas. Roads are the long open strips and are left out.
 *
 * Lots are numbered block by block: lots that touch each other form a block, blocks are
 * counted top to bottom, and lots within a block row by row along the block's direction.
 */
object LotDetector {

    /** One detected area; [polygon] is normalized to the picture (x, y in 0..1). */
    data class Area(val block: Int, val lot: Int, val polygon: List<Pair<Float, Float>>, val isLot: Boolean)

    private class Region(val id: Int) {
        var area = 0
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        var touchesEdge = false
        val w get() = maxX - minX + 1
        val h get() = maxY - minY + 1
        val fill get() = area.toFloat() / (w * h)
        val aspect get() = max(w, h).toFloat() / min(w, h)
        val cx get() = (minX + maxX) / 2f
        val cy get() = (minY + maxY) / 2f
    }

    /**
     * [dark] marks line pixels (row-major, [width] x [height]). Returns the lots first, then
     * the larger areas; empty when the picture has no clear lot pattern (e.g. a scanned plan).
     */
    fun detect(dark: BooleanArray, width: Int, height: Int): List<Area> {
        require(dark.size == width * height)
        val wall = thicken(dark, width, height)
        val labels = IntArray(width * height)
        val regions = label(wall, labels, width, height)

        val candidates = regions.filter { !it.touchesEdge && it.area > 50 }
        val rectish = candidates.filter { it.fill > 0.55f && it.aspect < 6f }
        if (rectish.size < 5) return emptyList()
        val typical = typicalArea(rectish)

        val sized = rectish.filter { it.area > typical * 0.35 && it.area < typical * 3 }
        val lots = mainGroups(sized, gap = sqrt(typical) * 2.5)
        if (lots.isEmpty()) return emptyList()

        val siteMinX = lots.minOf { it.minX }; val siteMaxX = lots.maxOf { it.maxX }
        val siteMinY = lots.minOf { it.minY }; val siteMaxY = lots.maxOf { it.maxY }
        val large = candidates.filter {
            it.area >= typical * 3 && it.area < typical * 40 && it.fill > 0.6f && it.aspect < 4f &&
                it.cx in siteMinX.toFloat()..siteMaxX.toFloat() && it.cy in siteMinY.toFloat()..siteMaxY.toFloat()
        }.sortedWith(compareBy({ it.cy }, { it.cx }))

        val outline = { r: Region -> normalize(simplify(outlineOf(r, labels, width), 1.5), width, height) }
        val numbered = numberLots(lots, touchGap = (sqrt(typical) * 0.15 + 4).toInt())
        return numbered.map { (r, block, lot) -> Area(block, lot, outline(r), isLot = true) } +
            large.mapIndexed { i, r -> Area(0, i + 1, outline(r), isLot = false) }
    }

    /** Thin anti-aliased lines can leave one-pixel gaps; growing walls by a pixel closes them. */
    private fun thicken(dark: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = dark.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (!dark[y * w + x]) continue
            if (x + 1 < w) out[y * w + x + 1] = true
            if (y + 1 < h) out[(y + 1) * w + x] = true
        }
        return out
    }

    /**
     * The outline of the closed area around ([x], [y]), both 0..1, for a lot the user points
     * at; null when the point is on a line, or the area is open or too big to be one lot.
     */
    fun traceAt(dark: BooleanArray, width: Int, height: Int, x: Float, y: Float): List<Pair<Float, Float>>? {
        require(dark.size == width * height)
        val wall = thicken(dark, width, height)
        val px = (x * width).toInt().coerceIn(0, width - 1)
        val py = (y * height).toInt().coerceIn(0, height - 1)
        // A tap can land on the lot line itself; take the nearest open pixel within a few pixels.
        val start = (0..6).asSequence().flatMap { d -> (-d..d).asSequence().flatMap { dy -> (-d..d).asSequence().map { dx -> px + dx to py + dy } } }
            .firstOrNull { (cx, cy) -> cx in 0 until width && cy in 0 until height && !wall[cy * width + cx] }
            ?: return null
        val labels = IntArray(width * height)
        val r = fill(start.second * width + start.first, Region(1), wall, labels, width, height, IntArray(4096))
        if (r.touchesEdge || r.area < 50 || r.area > width.toLong() * height / 50) return null
        return normalize(simplify(outlineOf(r, labels, width), 1.5), width, height)
    }

    private fun label(wall: BooleanArray, labels: IntArray, w: Int, h: Int): List<Region> {
        val regions = mutableListOf<Region>()
        var stack = IntArray(4096)
        for (start in wall.indices) {
            if (wall[start] || labels[start] != 0) continue
            regions += fill(start, Region(regions.size + 1), wall, labels, w, h, stack)
        }
        return regions
    }

    private fun fill(start: Int, r: Region, wall: BooleanArray, labels: IntArray, w: Int, h: Int, initial: IntArray): Region {
        var stack = initial
        var sp = 0
        stack[sp++] = start
        labels[start] = r.id
        while (sp > 0) {
            val p = stack[--sp]
            val x = p % w; val y = p / w
            r.area++
            if (x < r.minX) r.minX = x; if (x > r.maxX) r.maxX = x
            if (y < r.minY) r.minY = y; if (y > r.maxY) r.maxY = y
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) r.touchesEdge = true
            if (sp + 4 > stack.size) stack = stack.copyOf(stack.size * 2)
            if (x > 0 && !wall[p - 1] && labels[p - 1] == 0) { labels[p - 1] = r.id; stack[sp++] = p - 1 }
            if (x < w - 1 && !wall[p + 1] && labels[p + 1] == 0) { labels[p + 1] = r.id; stack[sp++] = p + 1 }
            if (y > 0 && !wall[p - w] && labels[p - w] == 0) { labels[p - w] = r.id; stack[sp++] = p - w }
            if (y < h - 1 && !wall[p + w] && labels[p + w] == 0) { labels[p + w] = r.id; stack[sp++] = p + w }
        }
        return r
    }

    /** The most common size, counted on a log scale so lots of slightly different sizes fall together. */
    private fun typicalArea(regions: List<Region>): Double {
        val bins = regions.groupingBy { (ln(it.area.toDouble()) * 8).roundToInt() }.eachCount()
        return exp(bins.maxBy { it.value }.key / 8.0)
    }

    /** Lots near each other, in groups at least a tenth the size of the biggest group. */
    private fun mainGroups(lots: List<Region>, gap: Double): List<Region> {
        val groups = groupsOf(lots, gap)
        val biggest = groups.maxOfOrNull { it.size } ?: return emptyList()
        return groups.filter { it.size >= max(3, biggest / 10) }.flatten()
    }

    private fun groupsOf(regions: List<Region>, gap: Double): List<List<Region>> {
        val parent = IntArray(regions.size) { it }
        fun find(i: Int): Int { var j = i; while (parent[j] != j) { parent[j] = parent[parent[j]]; j = parent[j] }; return j }
        val order = regions.indices.sortedBy { regions[it].minX }
        for (a in order.indices) {
            val ra = regions[order[a]]
            for (b in a + 1 until order.size) {
                val rb = regions[order[b]]
                if (rb.minX - ra.maxX > gap) break
                val gx = max(0, max(rb.minX - ra.maxX, ra.minX - rb.maxX))
                val gy = max(0, max(rb.minY - ra.maxY, ra.minY - rb.maxY))
                if (gx < gap && gy < gap) parent[find(order[a])] = find(order[b])
            }
        }
        return regions.indices.groupBy { find(it) }.values.map { ids -> ids.map { regions[it] } }
    }

    /** (region, block, lot): blocks top to bottom, lots in rows along each block's long direction. */
    private fun numberLots(lots: List<Region>, touchGap: Int): List<Triple<Region, Int, Int>> {
        val blocks = groupsOf(lots, touchGap.toDouble())
            .sortedWith(compareBy({ b -> b.minOf { it.minY } }, { b -> b.minOf { it.minX } }))
        return blocks.flatMapIndexed { bi, block ->
            val mx = block.map { it.cx }.average(); val my = block.map { it.cy }.average()
            var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            for (r in block) { val dx = r.cx - mx; val dy = r.cy - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
            // Long axis of the block (principal direction of the lot centres).
            val angle = 0.5 * kotlin.math.atan2(2 * sxy, sxx - syy)
            val ux = kotlin.math.cos(angle); val uy = kotlin.math.sin(angle)
            val rowBand = block.map { min(it.w, it.h) }.sorted()[block.size / 2] * 0.8
            block.sortedWith(compareBy(
                { r -> (((r.cx - mx) * -uy + (r.cy - my) * ux) / rowBand).roundToInt() },
                { r -> (r.cx - mx) * ux + (r.cy - my) * uy }
            )).mapIndexed { li, r -> Triple(r, bi + 1, li + 1) }
        }
    }

    /** Left edge top to bottom, then right edge bottom to top: exact for convex lots, close for the rest. */
    private fun outlineOf(r: Region, labels: IntArray, w: Int): List<Pair<Double, Double>> {
        val left = mutableListOf<Pair<Double, Double>>()
        val right = mutableListOf<Pair<Double, Double>>()
        for (y in r.minY..r.maxY) {
            var lo = -1; var hi = -1
            for (x in r.minX..r.maxX) if (labels[y * w + x] == r.id) { if (lo < 0) lo = x; hi = x }
            if (lo < 0) continue
            left += lo.toDouble() to y.toDouble()
            right += (hi + 1).toDouble() to (y + 1).toDouble()
        }
        return left + right.reversed()
    }

    /** Douglas-Peucker on the closed outline, keeping corners further than [eps] pixels from a straight run. */
    internal fun simplify(points: List<Pair<Double, Double>>, eps: Double): List<Pair<Double, Double>> {
        if (points.size < 4) return points
        val keep = BooleanArray(points.size)
        keep[0] = true; keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.size - 1) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var best = -1; var bestD = eps
            for (i in a + 1 until b) {
                val d = distance(points[i], points[a], points[b])
                if (d > bestD) { bestD = d; best = i }
            }
            if (best >= 0) { keep[best] = true; stack.add(a to best); stack.add(best to b) }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun distance(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len == 0.0) return hypot(p.first - a.first, p.second - a.second)
        return abs(dy * p.first - dx * p.second + b.first * a.second - b.second * a.first) / len
    }

    private fun normalize(points: List<Pair<Double, Double>>, w: Int, h: Int) =
        points.map { (x, y) -> (x / w).toFloat() to (y / h).toFloat() }
}

package com.example.ui.detail.tabs

import com.example.ui.theme.NHAColors
import com.example.ui.theme.LocalNHAColors
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.util.BlockNumbering
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.SdpLotInspection
import com.example.data.model.SdpLotProgress
import com.example.data.model.SdpPlan
import com.example.data.model.SdpRoad
import com.example.data.model.UserRole
import com.example.data.model.LOT_USES
import com.example.data.model.anyBilled
import com.example.data.model.billingLabel
import com.example.data.model.isUnassigned
import com.example.data.model.label
import com.example.data.model.m1Billed
import com.example.data.model.m2Billed
import com.example.data.model.isRoadLot
import com.example.data.model.isSaleable
import com.example.data.model.use
import com.example.data.model.hasPermission
import com.example.ui.components.LongForm
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private data class NormalizedRect(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    fun intersects(other: NormalizedRect): Boolean {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
    }
    fun contains(pt: Offset): Boolean {
        return pt.x >= minX && pt.x <= maxX && pt.y >= minY && pt.y <= maxY
    }
}

private data class ParsedSdpLot(
    val lot: SdpLot,
    val polygon: List<Offset>,
    val bounds: NormalizedRect,
    val centroid: Offset
)

private data class ParsedSdpRoad(
    val road: SdpRoad,
    val polyline: List<Offset>,
    val bounds: NormalizedRect
)

enum class SdpInteractionMode {
    MONITORING,
    DIGITIZING_LOT,
    DIGITIZING_ROAD,
    // Tap inside a lot the detector missed; the app traces its outline from the PDF.
    TAP_ADD_LOT,
    EDITING_LOT,
    EDITING_ROAD
}
/** Same bands as [lotProgressFill]. */
val LOT_PROGRESS_FILTERS = listOf("ALL", "0-25%", "26-50%", "51-75%", "76-99%", "100%")

fun isLotMatchingFilter(
    lot: SdpLot,
    query: String,
    progressFilter: String,
    billingFilter: String,
    completionFilter: String,
    progressMap: Map<Long, SdpLotProgress>
): Boolean {
    val progress = progressMap[lot.id]
    
    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        val key = "b${lot.blockNumber}-l${lot.lotNumber}".lowercase()
        val keyNoDash = "b${lot.blockNumber}l${lot.lotNumber}".lowercase()
        val block = "block ${lot.blockNumber}".lowercase()
        val lotStr = "lot ${lot.lotNumber}".lowercase()
        val unit = lot.housingUnitNumber.lowercase()
        val matchesQuery = if (lot.isUnassigned) lot.label.lowercase().contains(q) || unit.contains(q)
            else key.contains(q) || keyNoDash.contains(q) || block.contains(q) || lotStr.contains(q) || unit.contains(q)
        if (!matchesQuery) return false
    }

    when (billingFilter) {
        "M1 BILLED" -> if (!progress.m1Billed) return false
        "M2 BILLED" -> if (!progress.m2Billed) return false
        "NOT BILLED" -> if (progress.anyBilled) return false
    }

    if (completionFilter != "ALL") {
        val isComp = (progress?.physicalProgress ?: 0) >= 100
        if (completionFilter == "COMPLETED" && !isComp) return false
        if (completionFilter == "INCOMPLETE" && isComp) return false
    }

    if (progressFilter != "ALL") {
        val progVal = progress?.physicalProgress ?: 0
        val matchesProg = when (progressFilter) {
            "0-25%" -> progVal <= 25
            "26-50%" -> progVal in 26..50
            "51-75%" -> progVal in 51..75
            "76-99%" -> progVal in 76..99
            "100%" -> progVal >= 100
            else -> true
        }
        if (!matchesProg) return false
    }

    return true
}

/** 0-25% red, 26-50% orange, 51-75% yellow, 76-99% blue, 100% (RFO) green. */
fun lotProgressFill(progress: Int, nha: NHAColors): Color = when {
    progress >= 100 -> nha.onTrack
    progress >= 76 -> nha.progressQuarter4
    progress >= 51 -> nha.progressQuarter3
    progress >= 26 -> nha.progressQuarter2
    else -> nha.behind
}

@Composable
fun getLotProgressFillColor(progress: Int): Color = lotProgressFill(progress, LocalNHAColors.current)

// --- GEOMETRY & MATH UTILITIES ---
fun normalizedToJson(points: List<Offset>): String {
    val array = JSONArray()
    for (pt in points) {
        val ptArray = JSONArray()
        ptArray.put(pt.x.toDouble())
        ptArray.put(pt.y.toDouble())
        array.put(ptArray)
    }
    return array.toString()
}

fun jsonToNormalized(json: String): List<Offset> {
    val result = mutableListOf<Offset>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val ptArray = array.getJSONArray(i)
            val x = ptArray.getDouble(0).toFloat()
            val y = ptArray.getDouble(1).toFloat()
            result.add(Offset(x, y))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result
}

fun distToSegment(p: Offset, v: Offset, w: Offset): Float {
    val l2 = (v.x - w.x) * (v.x - w.x) + (v.y - w.y) * (v.y - w.y)
    if (l2 == 0f) return kotlin.math.sqrt((p.x - v.x) * (p.x - v.x) + (p.y - v.y) * (p.y - v.y))
    var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
    t = t.coerceIn(0f, 1f)
    val projX = v.x + t * (w.x - v.x)
    val projY = v.y + t * (w.y - v.y)
    return kotlin.math.sqrt((p.x - projX) * (p.x - projX) + (p.y - projY) * (p.y - projY))
}

fun pointInPolygon(pt: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y

        val intersect = ((yi > pt.y) != (yj > pt.y)) && (pt.x < (xj - xi) * (pt.y - yi) / (yj - yi) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

fun nearPolyline(pt: Offset, polyline: List<Offset>, threshold: Float): Boolean {
    if (polyline.size < 2) return false
    for (i in 0 until polyline.size - 1) {
        val p1 = polyline[i]
        val p2 = polyline[i + 1]
        val dist = distToSegment(pt, p1, p2)
        if (dist <= threshold) return true
    }
    return false
}

fun calculateCentroid(polygon: List<Offset>): Offset {
    if (polygon.isEmpty()) return Offset.Zero
    var sumX = 0f
    var sumY = 0f
    for (pt in polygon) {
        sumX += pt.x
        sumY += pt.y
    }
    return Offset(sumX / polygon.size, sumY / polygon.size)
}

@Composable
fun SubdivisionPlanTab(
    project: Project,
    sdpPlans: List<SdpPlan>,
    activeSdpPlan: SdpPlan?,
    activeLots: List<SdpLot>,
    activeRoads: List<SdpRoad>,
    lotProgressMap: Map<Long, SdpLotProgress>,
    currentUserRole: UserRole,
    onUploadSdpPlan: (planName: String, pdfUrl: String, description: String) -> Unit,
    onSelectActiveVersion: (sdpPlanId: Long) -> Unit,
    onAddLot: (block: String, lot: String, unit: String, area: Double, json: String, desc: String) -> Unit,
    onUpdateLot: (SdpLot) -> Unit,
    onDeactivateLot: (SdpLot) -> Unit,
    onAddRoad: (name: String, type: String, json: String) -> Unit,
    onUpdateRoad: (SdpRoad) -> Unit,
    onDeactivateRoad: (SdpRoad) -> Unit,
    onUpdateLotProgress: (
        sdpLotId: Long,
        physicalProgress: Int,
        status: String,
        activity: String,
        startDate: String,
        targetDate: String,
        contractor: String,
        remarks: String,
        // Null keeps the lot's photos.
        photos: List<String>?,
        // Road lots only; null keeps the saved value.
        drainage: Int?
    ) -> Unit,
    onUpdateLotBillingStatus: (
        sdpLotId: Long,
        // 1: M1 (80%), 2: M2 (20%).
        milestone: Int,
        isBilled: Boolean,
        reference: String,
        remarks: String
    ) -> Unit,
    onRecordInspection: (
        sdpLotId: Long,
        physicalProgress: Int,
        status: String,
        activity: String,
        contractor: String,
        remarks: String,
        billingStatus: String,
        billingRef: String
    ) -> Unit,
    onGetInspectionsForLot: (sdpLotId: Long) -> Flow<List<SdpLotInspection>>,
    auditLogs: List<com.example.data.model.AuditLog> = emptyList(),
    onDetectLots: (SdpPlan, (List<com.example.util.LotDetector.Area>?) -> Unit) -> Unit = { _, done -> done(null) },
    onAddDetectedLots: (SdpPlan, List<com.example.util.LotDetector.Area>) -> Unit = { _, _ -> },
    onTraceLot: (SdpPlan, Float, Float, (List<Pair<Float, Float>>?) -> Unit) -> Unit = { _, _, _, done -> done(null) },
    onRemoveDetectedLots: (SdpPlan) -> Unit = {},
    onRenumberLots: (renumbered: List<SdpLot>, oldBlock: String) -> Unit = { _, _ -> },
    onSetPlanScale: (SdpPlan, Int) -> Unit = { _, _ -> },
    onLotArea: (SdpPlan, List<Pair<Float, Float>>, (Double?) -> Unit) -> Unit = { _, _, done -> done(null) },
    onSetLotUse: (List<SdpLot>, String) -> Unit = { _, _ -> },
    onRenameLot: (SdpLot, block: String, lot: String) -> Unit = { _, _, _ -> },
    // Null: the whole page.
    onSetPlanFrame: (SdpPlan, List<Float>?) -> Unit = { _, _ -> },
    onPrintPlan: () -> Unit = {}
) {
    var showScaleDialog by remember { mutableStateOf(false) }
    // Area worked out from the plan scale for the lot being added; null until known.
    var draftArea by remember { mutableStateOf<Double?>(null) }
    val context = LocalContext.current

    // Lot detection: runs by itself right after a PDF upload, or from "Find lots".
    var detectAfterUpload by remember { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectedAreas by remember { mutableStateOf<List<com.example.util.LotDetector.Area>?>(null) }
    var detectFailed by remember { mutableStateOf(false) }
    var isTracing by remember { mutableStateOf(false) }
    var tapAddMessage by remember { mutableStateOf<String?>(null) }
    var confirmRemoveDetected by remember { mutableStateOf(false) }
    val runDetection = { plan: SdpPlan ->
        isDetecting = true
        onDetectLots(plan) { areas ->
            isDetecting = false
            if (areas.isNullOrEmpty()) detectFailed = true else detectedAreas = areas
        }
    }
    LaunchedEffect(activeSdpPlan?.id) {
        val plan = activeSdpPlan ?: return@LaunchedEffect
        if (detectAfterUpload && com.example.data.files.Attachments.isRef(plan.pdfFileUrl)) {
            detectAfterUpload = false
            runDetection(plan)
        }
    }

    // PDF Renderer States
    var renderedBitmap by remember(activeSdpPlan?.id) { mutableStateOf<Bitmap?>(null) }
    var selectedPage by remember(activeSdpPlan?.id) { mutableIntStateOf(0) }
    var pageCount by remember(activeSdpPlan?.id) { mutableIntStateOf(0) }
    var isLoadingPdf by remember(activeSdpPlan?.id, selectedPage) { mutableStateOf(false) }
    var pdfError by remember(activeSdpPlan?.id) { mutableStateOf<String?>(null) }

    // Canvas Pan & Zoom State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Interaction & Selection State
    var interactionMode by remember { mutableStateOf(SdpInteractionMode.MONITORING) }
    val isDrawMode = interactionMode != SdpInteractionMode.MONITORING && interactionMode != SdpInteractionMode.TAP_ADD_LOT
    var selectedLot by remember { mutableStateOf<SdpLot?>(null) }
    // Keeps the selected lot's name and use current after a rename or use change.
    LaunchedEffect(activeLots) { selectedLot = selectedLot?.let { s -> activeLots.find { it.id == s.id } ?: s } }
    var selectedRoad by remember { mutableStateOf<SdpRoad?>(null) }
    // Group select: lots that get the same progress in one save (null: off).
    var groupIds by remember { mutableStateOf<Set<Long>?>(null) }
    var showGroupProgress by remember { mutableStateOf(false) }
    var showGroupUse by remember { mutableStateOf(false) }
    // Setting the plan frame: two taps mark opposite corners of the plan area.
    var framing by remember { mutableStateOf(false) }
    var frameCorner by remember { mutableStateOf<Offset?>(null) }

    // Renumbering one block: a panel replaces the lot card and the plan shows the new numbers.
    // renumberBlock is the block the lots came from (null: panel closed); renumberIds the lots
    // being numbered, which taps add to or take out of (empty: pick the next block).
    var renumberBlock by remember { mutableStateOf<String?>(null) }
    var renumberIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var renumberTo by remember { mutableStateOf("") }
    var renumberFirst by remember { mutableStateOf("1") }
    var renumberPattern by remember { mutableStateOf(BlockNumbering.Pattern.ODD_EVEN) }
    var renumberOtherEnd by remember { mutableStateOf(false) }
    var renumberOtherRow by remember { mutableStateOf(false) }
    val startRenumber = { lot: SdpLot ->
        renumberBlock = lot.blockNumber
        // Unnamed lots aren't grouped into blocks yet, so the engineer taps the rest of this block.
        renumberIds = if (lot.isUnassigned) setOf(lot.id) else activeLots.filter { it.blockNumber == lot.blockNumber }.map { it.id }.toSet()
        renumberTo = lot.blockNumber
        renumberFirst = "1"
        renumberPattern = BlockNumbering.Pattern.ODD_EVEN
        renumberOtherEnd = false
        renumberOtherRow = false
        selectedLot = null
        selectedRoad = null
    }

    // Draft Geometry Points (Normalized 0.0 to 1.0)
    val draftPoints = remember { mutableStateListOf<Offset>() }

    // SEARCH & FILTER STATES
    var searchQuery by remember { mutableStateOf("") }
    var selectedProgressFilter by remember { mutableStateOf("ALL") }
    var selectedBillingFilter by remember { mutableStateOf("ALL") }
    var selectedCompletionFilter by remember { mutableStateOf("ALL") }
    var showLegendDialog by remember { mutableStateOf(false) }
    var showFilterPanel by remember { mutableStateOf(false) }

    // Modal Sheet / Dialog States
    var showUploadDialog by remember { mutableStateOf(false) }
    var showAddLotDialog by remember { mutableStateOf(false) }
    var showAddRoadDialog by remember { mutableStateOf(false) }
    var showProgressDialogLot by remember { mutableStateOf<SdpLot?>(null) }
    var showLotInfoDialog by remember { mutableStateOf<SdpLot?>(null) }
    var showRecordInspectionDialogLot by remember { mutableStateOf<SdpLot?>(null) }
    var showLotAuditTrailDialogLot by remember { mutableStateOf<SdpLot?>(null) }
    var lotToDeactivate by remember { mutableStateOf<SdpLot?>(null) }
    var roadToDeactivate by remember { mutableStateOf<SdpRoad?>(null) }
    // Lot, milestone (1: M1, 2: M2), mark as billed.
    var lotToToggleBilling by remember { mutableStateOf<Triple<SdpLot, Int, Boolean>?>(null) }

    val canManageSdp = currentUserRole.hasPermission(com.example.data.model.Permission.MANAGE_SDP_PLANS)
    val canDigitize = currentUserRole.hasPermission(com.example.data.model.Permission.EDIT_PROJECT)

    val textMeasurer = rememberTextMeasurer()

    // --- REACTIVE KPI COMPUTATIONS ---
    // Counted over saleable lots: open spaces, road lots and the like are not housing units.
    val saleableLots = activeLots.filter { it.isSaleable }
    val totalLots = saleableLots.size
    val m1BilledCount = saleableLots.count { lotProgressMap[it.id].m1Billed }
    val m2BilledCount = saleableLots.count { lotProgressMap[it.id].m2Billed }
    val notBilledLotsCount = saleableLots.count { !lotProgressMap[it.id].anyBilled }
    val completedLotsCount = saleableLots.count { (lotProgressMap[it.id]?.physicalProgress ?: 0) >= 100 }
    val avgPhysicalProgress = if (totalLots > 0) {
        saleableLots.sumOf { lotProgressMap[it.id]?.physicalProgress ?: 0 }.toDouble() / totalLots
    } else 0.0
    val planFrame = remember(activeSdpPlan?.frame, activeLots) {
        com.example.util.SdpSummary.parseFrame(activeSdpPlan?.frame ?: "")
            ?: com.example.util.SdpSummary.frameAround(activeLots.map { l -> jsonToNormalized(l.polygonNormalizedJson).map { it.x to it.y } })
    }

    // Filter active check & matching lot calculation
    val isFilterActive = searchQuery.isNotBlank() ||
            selectedProgressFilter != "ALL" ||
            selectedBillingFilter != "ALL" ||
            selectedCompletionFilter != "ALL"

    val matchingLots = remember(activeLots, lotProgressMap, searchQuery, selectedProgressFilter, selectedBillingFilter, selectedCompletionFilter) {
        activeLots.filter { lot ->
            isLotMatchingFilter(lot, searchQuery, selectedProgressFilter, selectedBillingFilter, selectedCompletionFilter, lotProgressMap)
        }
    }

    // PRE-PARSE GEOMETRY & CALCULATE BOUNDING BOXES FOR FAST VIEWPORT CULLING AND SPATIAL HIT-TESTING
    val parsedLots = remember(activeLots) {
        activeLots.mapNotNull { lot ->
            val polygon = jsonToNormalized(lot.polygonNormalizedJson)
            if (polygon.size >= 3) {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                polygon.forEach { pt ->
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
                val bounds = NormalizedRect(minX, minY, maxX, maxY)
                val centroid = calculateCentroid(polygon)
                ParsedSdpLot(lot, polygon, bounds, centroid)
            } else null
        }
    }

    // New lot numbers of the block being renumbered, by lot id.
    val renumberPreview = remember(renumberIds, renumberFirst, renumberPattern, renumberOtherEnd, renumberOtherRow, parsedLots, renderedBitmap) {
        val lots = parsedLots.filter { it.lot.id in renumberIds }
        if (lots.isEmpty()) return@remember emptyMap<Long, Int>()
        val w = (renderedBitmap?.width ?: 1600).toDouble(); val h = (renderedBitmap?.height ?: 1200).toDouble()
        val numbers = BlockNumbering.number(
            lots.map { it.centroid.x * w to it.centroid.y * h },
            renumberFirst.toIntOrNull() ?: 1, renumberPattern, renumberOtherEnd, renumberOtherRow
        )
        lots.zip(numbers).associate { (p, n) -> p.lot.id to n }
    }

    val parsedRoads = remember(activeRoads) {
        activeRoads.mapNotNull { road ->
            val polyline = jsonToNormalized(road.polylineNormalizedJson)
            if (polyline.size >= 2) {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                polyline.forEach { pt ->
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
                val bounds = NormalizedRect(minX, minY, maxX, maxY)
                ParsedSdpRoad(road, polyline, bounds)
            } else null
        }
    }

    // Render PDF page to Bitmap asynchronously with memory recycling
    LaunchedEffect(activeSdpPlan, selectedPage) {
        val currentPlan = activeSdpPlan ?: return@LaunchedEffect
        if (currentPlan.pdfFileUrl.isBlank()) {
            renderedBitmap?.recycle()
            renderedBitmap = null
            pageCount = 0
            return@LaunchedEffect
        }

        isLoadingPdf = true
        pdfError = null

        withContext(Dispatchers.IO) {
            try {
                val pdfUri = Uri.parse(currentPlan.pdfFileUrl)
                var pfd: ParcelFileDescriptor? = null

                if (com.example.data.files.Attachments.isRef(currentPlan.pdfFileUrl)) {
                    // Downloaded the first time when another phone uploaded the plan.
                    val local = com.example.data.files.Attachments.ensureLocal(context, currentPlan.pdfFileUrl)
                    if (local != null) pfd = ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY)
                } else if (pdfUri.scheme == "content" || pdfUri.scheme == "file") {
                    pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                } else {
                    val file = File(pdfUri.path ?: "")
                    if (file.exists()) {
                        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }

                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount

                    val safePage = selectedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    if (renderer.pageCount > safePage) {
                        val page = renderer.openPage(safePage)
                        // One scale for both sides, so the page keeps its shape.
                        val pageScale = minOf(2f, 2400f / page.width, 2400f / page.height)
                        val renderWidth = (page.width * pageScale).toInt().coerceAtLeast(1)
                        val renderHeight = (page.height * pageScale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()

                        val oldBitmap = renderedBitmap
                        renderedBitmap = bitmap
                        oldBitmap?.recycle()
                    } else {
                        renderer.close()
                        pfd.close()
                    }
                } else {
                    pdfError = if (com.example.data.files.Attachments.isRef(currentPlan.pdfFileUrl)) {
                        "The plan PDF is not on this phone yet. It may still be uploading from the other phone; check the internet and open Lots again."
                    } else {
                        "This plan was saved by an older version and its PDF can no longer be opened. Upload the plan again."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                pdfError = "Error loading PDF: ${e.localizedMessage}"
            } finally {
                isLoadingPdf = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        // --- TOP TOOLBAR & HEADER ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SUBDIVISION PLAN",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (activeSdpPlan != null) {
                                "${project.name} • v${activeSdpPlan.version}"
                            } else {
                                "No Approved Site Development Plan Uploaded"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                        )
                        if (activeSdpPlan != null && (canDigitize || activeSdpPlan.scaleDenominator > 0)) {
                            Text(
                                text = if (activeSdpPlan.scaleDenominator > 0) "Scale 1:${activeSdpPlan.scaleDenominator}" + if (canDigitize) " (change)" else ""
                                    else "Set the plan scale to work out lot areas",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (activeSdpPlan.scaleDenominator > 0) MaterialTheme.colorScheme.onSurfaceVariant else LocalNHAColors.current.watch,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .then(if (canDigitize) Modifier.clickable { showScaleDialog = true } else Modifier)
                                    .testTag("plan_scale")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showLegendDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "Legend", tint = LocalNHAColors.current.watch, modifier = Modifier.size(18.dp))
                        }

                        if (activeSdpPlan != null && renderedBitmap != null && canDigitize) {
                            IconButton(
                                onClick = { framing = true; frameCorner = null; selectedLot = null; selectedRoad = null; groupIds = null },
                                modifier = Modifier.size(32.dp).testTag("plan_frame_button")
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = "Plan area for printing", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (activeSdpPlan != null && renderedBitmap != null && activeLots.isNotEmpty()) {
                            IconButton(
                                onClick = onPrintPlan,
                                modifier = Modifier.size(32.dp).testTag("print_plan_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print plan with progress", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (canManageSdp && activeSdpPlan != null && com.example.data.files.Attachments.isRef(activeSdpPlan.pdfFileUrl)) {
                            OutlinedButton(
                                onClick = { runDetection(activeSdpPlan) },
                                enabled = !isDetecting,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("find_lots_button")
                            ) {
                                if (isDetecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isDetecting) "Finding…" else "Find lots", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (canManageSdp) {
                            Button(
                                onClick = { showUploadDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Upload", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sdpPlans.isEmpty()) "Upload SDP" else "Upload Revised",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // COMPACT KPI MONITORING ROW
                if (activeSdpPlan != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KpiChip(label = "SALEABLE LOTS", value = "$totalLots", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        KpiChip(label = "M1 BILLED", value = "$m1BilledCount", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        KpiChip(label = "M2 BILLED", value = "$m2BilledCount", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        KpiChip(label = "NOT BILLED", value = "$notBilledLotsCount", color = LocalNHAColors.current.neutral, modifier = Modifier.weight(1f))
                        KpiChip(label = "COMPLETED", value = "$completedLotsCount", color = LocalNHAColors.current.onTrack, modifier = Modifier.weight(1f))
                        KpiChip(label = "AVG PROGRESS", value = String.format("%.1f%%", avgPhysicalProgress), color = LocalNHAColors.current.watch, modifier = Modifier.weight(1.2f))
                    }
                }

                // Version Dropdown & Digitization Mode Toggle
                if (activeSdpPlan != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Version Selector
                        if (sdpPlans.size > 1) {
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = "Version", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "v${activeSdpPlan.version}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    sdpPlans.forEach { plan ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "v${plan.version} - ${plan.planName} ${if (plan.isActive) "(Active)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (plan.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            },
                                            onClick = {
                                                onSelectActiveVersion(plan.id)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Mode Segmented Buttons
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Button(
                                    onClick = {
                                        interactionMode = SdpInteractionMode.MONITORING
                                        draftPoints.clear()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (interactionMode == SdpInteractionMode.MONITORING) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        contentColor = if (interactionMode == SdpInteractionMode.MONITORING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Monitor", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MONITORING", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                if (canDigitize) {
                                    Button(
                                        onClick = {
                                            interactionMode = SdpInteractionMode.DIGITIZING_LOT
                                            draftPoints.clear()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDrawMode) LocalNHAColors.current.watch else Color.Transparent,
                                            contentColor = if (isDrawMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Digitize", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("EDIT SDP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (com.example.data.files.Attachments.isRef(activeSdpPlan.pdfFileUrl)) {
                                        val tapMode = interactionMode == SdpInteractionMode.TAP_ADD_LOT
                                        Button(
                                            onClick = {
                                                interactionMode = SdpInteractionMode.TAP_ADD_LOT
                                                draftPoints.clear()
                                                tapAddMessage = null
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (tapMode) LocalNHAColors.current.watch else Color.Transparent,
                                                contentColor = if (tapMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp).testTag("tap_add_lot_mode")
                                        ) {
                                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("TAP TO ADD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Lot tools: what a tap does now, and removing the lots the app found.
                    if (canDigitize && interactionMode != SdpInteractionMode.MONITORING) {
                        val removableCount = activeLots.count { it.description.startsWith("Auto-detected") && !lotProgressMap.containsKey(it.id) }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MinHeight)) {  // fixed height: buttons appearing must not shift the plan under the finger
                            Text(
                                text = tapAddMessage ?: if (interactionMode == SdpInteractionMode.TAP_ADD_LOT) {
                                    if (isTracing) "Tracing the lot…" else "Tap inside a lot the app missed to add it."
                                } else "Tap each corner of the lot, then save it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tapAddMessage != null) LocalNHAColors.current.behind else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (removableCount > 0) {
                                TextButton(onClick = { confirmRemoveDetected = true }, modifier = Modifier.testTag("remove_found_lots")) {
                                    Text("Remove found lots ($removableCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNHAColors.current.behind)
                                }
                            }
                            if (interactionMode == SdpInteractionMode.DIGITIZING_LOT && draftPoints.isNotEmpty()) {
                                TextButton(onClick = { draftPoints.clear() }) { Text("Clear", fontSize = 11.sp) }
                                if (draftPoints.size >= 3) {
                                    Button(onClick = { showAddLotDialog = true }, modifier = Modifier.testTag("save_drawn_lot")) {
                                        Text("Save lot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // SEARCH & FILTER BAR
                if (activeSdpPlan != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search Block / Lot / Unit...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Filter Toggle Button
                        OutlinedButton(
                            onClick = { showFilterPanel = !showFilterPanel },
                            border = BorderStroke(1.dp, if (isFilterActive) LocalNHAColors.current.watch else MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFilterActive) LocalNHAColors.current.watch.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = if (isFilterActive) LocalNHAColors.current.watch else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FILTER", fontSize = 11.sp, color = if (isFilterActive) LocalNHAColors.current.watch else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                    }

                    // FILTER CHIPS & DROPDOWN PANEL
                    if (showFilterPanel) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("FILTER SUBDIVISION LOTS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Progress Filter Dropdown
                                    var progExpanded by remember { mutableStateOf(false) }
                                    FilterDropdownChip(
                                        label = "Progress: $selectedProgressFilter",
                                        isSelected = selectedProgressFilter != "ALL",
                                        onClick = { progExpanded = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DropdownMenu(expanded = progExpanded, onDismissRequest = { progExpanded = false }) {
                                        LOT_PROGRESS_FILTERS.forEach { opt ->
                                            DropdownMenuItem(text = { Text(opt) }, onClick = { selectedProgressFilter = opt; progExpanded = false })
                                        }
                                    }

                                    // Billing Filter Dropdown
                                    var billExpanded by remember { mutableStateOf(false) }
                                    FilterDropdownChip(
                                        label = "Billing: $selectedBillingFilter",
                                        isSelected = selectedBillingFilter != "ALL",
                                        onClick = { billExpanded = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DropdownMenu(expanded = billExpanded, onDismissRequest = { billExpanded = false }) {
                                        listOf("ALL", "M1 BILLED", "M2 BILLED", "NOT BILLED").forEach { opt ->
                                            DropdownMenuItem(text = { Text(opt) }, onClick = { selectedBillingFilter = opt; billExpanded = false })
                                        }
                                    }

                                    // Completion Filter Dropdown
                                    var compExpanded by remember { mutableStateOf(false) }
                                    FilterDropdownChip(
                                        label = "Status: $selectedCompletionFilter",
                                        isSelected = selectedCompletionFilter != "ALL",
                                        onClick = { compExpanded = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DropdownMenu(expanded = compExpanded, onDismissRequest = { compExpanded = false }) {
                                        listOf("ALL", "COMPLETED", "INCOMPLETE").forEach { opt ->
                                            DropdownMenuItem(text = { Text(opt) }, onClick = { selectedCompletionFilter = opt; compExpanded = false })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTIVE FILTER READOUT CHIP & RESET BUTTON
                    if (isFilterActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = LocalNHAColors.current.watch.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, LocalNHAColors.current.watch)
                            ) {
                                Text(
                                    text = "● FILTER ACTIVE: ${matchingLots.size} OF $totalLots LOTS MATCHING",
                                    style = MaterialTheme.typography.labelSmall.copy(color = LocalNHAColors.current.watch, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedProgressFilter = "ALL"
                                    selectedBillingFilter = "ALL"
                                    selectedCompletionFilter = "ALL"
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("CLEAR FILTERS", fontSize = 10.sp, color = LocalNHAColors.current.behind, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- PDF & VECTOR OVERLAY CANVAS ---
        if (activeSdpPlan == null) {
            // EMPTY STATE WITH INSTANT INTERACTIVE DEMO INITIALIZATION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Plan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Approved Subdivision Plan Loaded", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Upload the official Approved Subdivision Plan (SDP) PDF or launch the interactive CAD blueprint demo workspace.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            onUploadSdpPlan(
                                "Approved Subdivision Layout Plan (Ph. 3)",
                                "",
                                "Interactive CAD Blueprint Demo Layout initialized for testing vector lot monitoring"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch Interactive Blueprint Demo Workspace", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // INTERACTIVE VIEWPORT WITH BOX CONSTRAINTS FOR PRECISE CENTERING
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clipToBounds()
            ) {
                val viewportWidth = constraints.maxWidth.toFloat()
                val viewportHeight = constraints.maxHeight.toFloat()
                val nhaMap = LocalNHAColors.current
                val primaryMap = MaterialTheme.colorScheme.primary
                // Labels sit on the PDF page (light paper in both themes) or on the themed backdrop.
                val onSurfaceMap = if (renderedBitmap != null) Color.Black else MaterialTheme.colorScheme.onSurface
                val surfaceColor = MaterialTheme.colorScheme.surface
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

                if (isLoadingPdf) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (pdfError != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(pdfError!!, color = LocalNHAColors.current.behind)
                    }
                } else {
                    // The page fitted to the viewer is the frame the image, lots, roads and taps all share.
                    val pageFit = renderedBitmap?.let { minOf(viewportWidth / it.width, viewportHeight / it.height) } ?: 1f
                    val bitmapWidth = renderedBitmap?.let { it.width * pageFit } ?: 1600f
                    val bitmapHeight = renderedBitmap?.let { it.height * pageFit } ?: 1200f
                    val density = androidx.compose.ui.platform.LocalDensity.current

                    // Auto-center viewport when a single lot matches search query
                    LaunchedEffect(matchingLots, searchQuery) {
                        if (searchQuery.isNotBlank() && matchingLots.size == 1) {
                            val targetLot = matchingLots[0]
                            val polygon = jsonToNormalized(targetLot.polygonNormalizedJson)
                            if (polygon.isNotEmpty()) {
                                val centroid = calculateCentroid(polygon)
                                val targetPx = centroid.x * bitmapWidth
                                val targetPy = centroid.y * bitmapHeight
                                scale = 2.0f
                                offset = Offset(
                                    x = -targetPx * scale + (viewportWidth / 2f),
                                    y = -targetPy * scale + (viewportHeight / 2f)
                                )
                                selectedLot = targetLot
                            }
                        }
                    }

                    // TRANSFORMABLE CONTAINER (SHARED PDF + VECTOR LAYER)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(interactionMode, parsedLots, bitmapWidth, bitmapHeight, activeSdpPlan) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = if (scale > 1.2f) 1f else 2.5f
                                        if (scale == 1f) offset = Offset.Zero
                                    },
                                    onTap = { tapOffset ->
                                        val normX = ((tapOffset.x - offset.x) / (bitmapWidth * scale)).coerceIn(0f, 1f)
                                        val normY = ((tapOffset.y - offset.y) / (bitmapHeight * scale)).coerceIn(0f, 1f)
                                        val tappedNorm = Offset(normX, normY)

                                        if (framing && activeSdpPlan != null) {
                                            val a = frameCorner
                                            if (a == null) frameCorner = tappedNorm
                                            else {
                                                onSetPlanFrame(activeSdpPlan, listOf(minOf(a.x, normX), minOf(a.y, normY), maxOf(a.x, normX), maxOf(a.y, normY)))
                                                frameCorner = null
                                                framing = false
                                            }
                                        } else if (renumberBlock != null) {
                                            // While renumbering, a tap adds a lot to the block or takes it out;
                                            // after a save, it picks the next block.
                                            parsedLots.firstOrNull { it.bounds.contains(tappedNorm) && pointInPolygon(tappedNorm, it.polygon) }?.let { hit ->
                                                val id = hit.lot.id
                                                if (renumberIds.isEmpty()) startRenumber(hit.lot)
                                                else if (id !in renumberIds) renumberIds = renumberIds + id
                                                else if (renumberIds.size > 1) renumberIds = renumberIds - id
                                            }
                                        } else if (interactionMode == SdpInteractionMode.DIGITIZING_LOT ||
                                            interactionMode == SdpInteractionMode.DIGITIZING_ROAD) {
                                            draftPoints.add(tappedNorm)
                                        } else if (interactionMode == SdpInteractionMode.TAP_ADD_LOT && activeSdpPlan != null && !isTracing) {
                                            val existing = parsedLots.firstOrNull { it.bounds.contains(tappedNorm) && pointInPolygon(tappedNorm, it.polygon) }
                                            if (existing != null) {
                                                tapAddMessage = "${existing.lot.label} is already a lot. Tap a lot that is not shaded."
                                            } else {
                                                isTracing = true
                                                tapAddMessage = null
                                                onTraceLot(activeSdpPlan, normX, normY) { outline ->
                                                    isTracing = false
                                                    if (outline == null) {
                                                        tapAddMessage = "No closed lot outline there. Zoom in and tap inside the lot, or use Edit SDP to tap its corners."
                                                    } else {
                                                        draftPoints.clear()
                                                        draftPoints.addAll(outline.map { (x, y) -> Offset(x, y) })
                                                        showAddLotDialog = true
                                                    }
                                                }
                                            }
                                        } else if (interactionMode == SdpInteractionMode.MONITORING) {
                                            // FAST SPATIAL HIT-TESTING FOR LOTS & ROADS
                                            var hitLot: SdpLot? = null
                                            for (parsedLot in parsedLots) {
                                                if (parsedLot.bounds.contains(tappedNorm)) {
                                                    if (pointInPolygon(tappedNorm, parsedLot.polygon)) {
                                                        hitLot = parsedLot.lot
                                                        break
                                                    }
                                                }
                                            }

                                            if (hitLot != null) {
                                                val group = groupIds
                                                if (group != null) {
                                                    val id = hitLot.id
                                                    groupIds = if (id in group) group - id else group + id
                                                } else {
                                                    selectedLot = hitLot
                                                    selectedRoad = null
                                                    // NORMAL TAP -> OPEN CONSTRUCTION PROGRESS MODAL (viewers only see the lot card)
                                                    if (canDigitize) showProgressDialogLot = hitLot
                                                }
                                            } else {
                                                // Test roads
                                                var hitRoad: SdpRoad? = null
                                                for (parsedRoad in parsedRoads) {
                                                    if (parsedRoad.bounds.contains(tappedNorm)) {
                                                        if (nearPolyline(tappedNorm, parsedRoad.polyline, threshold = 0.03f)) {
                                                            hitRoad = parsedRoad.road
                                                            break
                                                        }
                                                    }
                                                }
                                                selectedRoad = hitRoad
                                                selectedLot = null
                                            }
                                        }
                                    },
                                    onLongPress = { tapOffset ->
                                        if (interactionMode == SdpInteractionMode.MONITORING && renumberBlock == null) {
                                            val normX = ((tapOffset.x - offset.x) / (bitmapWidth * scale)).coerceIn(0f, 1f)
                                            val normY = ((tapOffset.y - offset.y) / (bitmapHeight * scale)).coerceIn(0f, 1f)
                                            val tappedNorm = Offset(normX, normY)

                                            for (parsedLot in parsedLots) {
                                                if (parsedLot.bounds.contains(tappedNorm)) {
                                                    if (pointInPolygon(tappedNorm, parsedLot.polygon)) {
                                                        selectedLot = parsedLot.lot
                                                        selectedRoad = null
                                                        // LONG PRESS -> OPEN DETAILED LOT INFO MODAL
                                                        showLotInfoDialog = parsedLot.lot
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.2f, 5.0f)
                                    offset += pan
                                }
                            },
                        contentAlignment = Alignment.TopStart
                    ) {
                        // Calculate normalized viewport bounds for spatial culling
                        val visMinX = ((-offset.x) / (bitmapWidth * scale)).coerceIn(0f, 1f)
                        val visMinY = ((-offset.y) / (bitmapHeight * scale)).coerceIn(0f, 1f)
                        val visMaxX = ((-offset.x + viewportWidth) / (bitmapWidth * scale)).coerceIn(0f, 1f)
                        val visMaxY = ((-offset.y + viewportHeight) / (bitmapHeight * scale)).coerceIn(0f, 1f)
                        val viewportBounds = NormalizedRect(visMinX, visMinY, visMaxX, visMaxY)

                        // 1. PDF IMAGE BACKGROUND OR CAD BLUEPRINT GRID BACKDROP
                        if (renderedBitmap != null) {
                            Image(
                                bitmap = renderedBitmap!!.asImageBitmap(),
                                contentDescription = "SDP PDF",
                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                                modifier = Modifier
                                    .requiredSize(with(density) { bitmapWidth.toDp() }, with(density) { bitmapHeight.toDp() })
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    )
                            )
                        } else {
                            // CAD BLUEPRINT BACKDROP CANVAS
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    )
                            ) {
                                drawRect(color = surfaceColor)

                                val gridStep = 80f
                                var x = 0f
                                while (x < bitmapWidth) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, bitmapHeight),
                                        strokeWidth = 1f
                                    )
                                    x += gridStep
                                }
                                var y = 0f
                                while (y < bitmapHeight) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, y),
                                        end = Offset(bitmapWidth, y),
                                        strokeWidth = 1f
                                    )
                                    y += gridStep
                                }

                                drawRect(
                                    color = primaryMap.copy(alpha = 0.35f),
                                    topLeft = Offset(16f, 16f),
                                    size = androidx.compose.ui.geometry.Size(bitmapWidth - 32f, bitmapHeight - 32f),
                                    style = Stroke(width = 2.5f)
                                )
                            }
                        }

                        // 2. INTERACTIVE VECTOR LAYER (ROADS, LOTS WITH SPATIAL VIEWPORT CULLING)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                    transformOrigin = TransformOrigin(0f, 0f)
                                )
                        ) {
                            // A. RENDER ROADS
                            for (parsedRoad in parsedRoads) {
                                if (!viewportBounds.intersects(parsedRoad.bounds)) continue

                                val road = parsedRoad.road
                                val polyline = parsedRoad.polyline
                                val path = Path().apply {
                                    moveTo(polyline[0].x * bitmapWidth, polyline[0].y * bitmapHeight)
                                    for (i in 1 until polyline.size) {
                                        lineTo(polyline[i].x * bitmapWidth, polyline[i].y * bitmapHeight)
                                    }
                                }
                                val isSelected = selectedRoad?.id == road.id
                                drawPath(
                                    path = path,
                                    color = if (isSelected) nhaMap.watch else nhaMap.onTrack,
                                    style = Stroke(width = if (isSelected) 8f else 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f))
                                )
                            }

                            // B. RENDER LOTS WITH SPATIAL VIEWPORT CULLING
                            // This layer is zoomed, so outline widths are divided by the zoom to stay thin on screen.
                            val px = 1f / scale
                            for (parsedLot in parsedLots) {
                                // Spatial Viewport Culling: Skip lots completely outside visible viewport
                                if (!viewportBounds.intersects(parsedLot.bounds)) continue

                                val lot = parsedLot.lot
                                val polygon = parsedLot.polygon
                                val isMatching = !isFilterActive || matchingLots.any { it.id == lot.id }
                                val path = Path().apply {
                                    moveTo(polygon[0].x * bitmapWidth, polygon[0].y * bitmapHeight)
                                    for (i in 1 until polygon.size) {
                                        lineTo(polygon[i].x * bitmapWidth, polygon[i].y * bitmapHeight)
                                    }
                                    close()
                                }
                                val previewNumber = renumberPreview[lot.id]
                                val isSelected = selectedLot?.id == lot.id || previewNumber != null || groupIds?.contains(lot.id) == true
                                val progressRecord = lotProgressMap[lot.id]
                                val progressValue = progressRecord?.physicalProgress ?: 0
                                val isBilled = progressRecord.anyBilled

                                // DERIVED FILL COLOR FROM PROGRESS % (0-100%)
                                val baseProgressColor = if (lot.isSaleable || lot.isRoadLot) lotProgressFill(progressValue, nhaMap) else nhaMap.neutral

                                val fillAlpha = when {
                                    isSelected -> 0.65f
                                    isMatching -> 0.40f
                                    else -> 0.08f // Dimmed non-matching lot fill
                                }

                                    val strokeColor = when {
                                        isSelected -> nhaMap.watch
                                        isMatching -> baseProgressColor
                                        else -> nhaMap.neutral.copy(alpha = 0.25f)
                                    }

                                    // Fill Layer
                                    drawPath(
                                        path = path,
                                        color = if (isSelected) nhaMap.watch.copy(alpha = fillAlpha) else baseProgressColor.copy(alpha = fillAlpha)
                                    )

                                    // Normal Lot Border Stroke Layer
                                    drawPath(
                                        path = path,
                                        color = strokeColor,
                                        style = Stroke(width = (if (isSelected) 3f else (if (isMatching) 1.5f else 0.75f)) * px)
                                    )

                                    // While renumbering, a dashed outline marks lots that still have the app's numbers.
                                    if (renumberBlock != null && previewNumber == null && (lot.isUnassigned && lot.isSaleable || lot.description.startsWith("Auto-detected") && lot.blockNumber != "OS")) {
                                        drawPath(
                                            path = path,
                                            color = nhaMap.watch,
                                            style = Stroke(width = 2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * px, 6f * px), 0f))
                                        )
                                    }

                                    // DISTINCT THICK BILLING BORDER (IF BILLED)
                                    if (isBilled && isMatching) {
                                        drawPath(
                                            path = path,
                                            color = primaryMap,
                                            style = Stroke(
                                                width = 3.5f * px,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * px, 7f * px), 0f)
                                            )
                                        )
                                    }

                                    // The fill colour already shows progress, so only the renumber preview labels lots (with the new number).
                                    if (previewNumber != null) {
                                        val centroid = parsedLot.centroid
                                        val textLayoutResult = textMeasurer.measure(
                                            text = "$previewNumber",
                                            style = TextStyle(
                                                color = if (isMatching) onSurfaceMap else nhaMap.neutral.copy(alpha = 0.5f),
                                                // Same size on screen at any zoom; the layer scales everything else.
                                                fontSize = 9.sp / scale,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        // A plan of hundreds of lots: label only the lots wide enough to hold one.
                                        val lotWidth = (parsedLot.bounds.maxX - parsedLot.bounds.minX) * bitmapWidth
                                        if (textLayoutResult.size.width <= lotWidth * 1.2f || isSelected) drawText(
                                            textLayoutResult = textLayoutResult,
                                            topLeft = Offset(
                                                x = centroid.x * bitmapWidth - (textLayoutResult.size.width / 2f),
                                                y = centroid.y * bitmapHeight - (textLayoutResult.size.height / 2f)
                                            )
                                        )
                                    }
                                }

                            // The plan area that prints, and the first corner once tapped.
                            if (framing) {
                                planFrame?.let { f ->
                                    drawRect(
                                        color = nhaMap.watch,
                                        topLeft = Offset(f[0] * bitmapWidth, f[1] * bitmapHeight),
                                        size = androidx.compose.ui.geometry.Size((f[2] - f[0]) * bitmapWidth, (f[3] - f[1]) * bitmapHeight),
                                        style = Stroke(width = 2f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * px, 6f * px), 0f))
                                    )
                                }
                                frameCorner?.let { drawCircle(color = nhaMap.watch, radius = 6f * px, center = Offset(it.x * bitmapWidth, it.y * bitmapHeight)) }
                            }

                            // C. RENDER DRAFT DIGITIZATION POINTS & LINES
                            if (draftPoints.isNotEmpty()) {
                                val draftPath = Path().apply {
                                    moveTo(draftPoints[0].x * bitmapWidth, draftPoints[0].y * bitmapHeight)
                                    for (i in 1 until draftPoints.size) {
                                        lineTo(draftPoints[i].x * bitmapWidth, draftPoints[i].y * bitmapHeight)
                                    }
                                    if ((interactionMode == SdpInteractionMode.DIGITIZING_LOT || interactionMode == SdpInteractionMode.TAP_ADD_LOT) && draftPoints.size >= 3) {
                                        close()
                                    }
                                }

                                drawPath(
                                    path = draftPath,
                                    color = nhaMap.watch,
                                    style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f))
                                )

                                for (pt in draftPoints) {
                                    drawCircle(
                                        color = nhaMap.watch,
                                        radius = 6f,
                                        center = Offset(pt.x * bitmapWidth, pt.y * bitmapHeight)
                                    )
                                }
                            }
                        }

                        // FLOATING INTERACTIVE ZOOM CONTROLS TOOLBAR (TOP-RIGHT OVERLAY)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                // Zoom Presets: 25%, 50%, 100%, 250%
                                listOf(
                                    0.25f to "25%",
                                    0.50f to "50%",
                                    1.00f to "100%",
                                    2.50f to "250%"
                                ).forEach { (targetScale, label) ->
                                    val isSelected = kotlin.math.abs(scale - targetScale) < 0.08f
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable {
                                            scale = targetScale
                                            if (targetScale == 1.00f) {
                                                offset = Offset.Zero
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                // Zoom Out (-) Button
                                IconButton(
                                    onClick = { scale = (scale / 1.25f).coerceIn(0.2f, 5.0f) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }

                                // Live Percentage Readout
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                // Zoom In (+) Button
                                IconButton(
                                    onClick = { scale = (scale * 1.25f).coerceIn(0.2f, 5.0f) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }

                                // Recenter View / Reset Target Button
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.clickable {
                                        scale = 1.00f
                                        offset = Offset.Zero
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CenterFocusWeak, contentDescription = "Recenter View", tint = LocalNHAColors.current.watch, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET", fontSize = 10.sp, color = LocalNHAColors.current.watch, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                // RENUMBER BLOCK PANEL: the plan above previews the numbers as they are set.
                if (renumberBlock != null) {
                    val block = renumberBlock!!
                    val blockLots = activeLots.filter { it.id in renumberIds }
                    val target = renumberTo.trim()
                    val first = renumberFirst.trim().toIntOrNull()
                    val othersInTarget = activeLots.count { it.blockNumber == target && it.id !in renumberIds }
                    // Blocks with lots nobody has renumbered yet, and numbers two lots share.
                    val blocksToDo = activeLots.filter { !it.isUnassigned && it.description.startsWith("Auto-detected") && it.blockNumber != "OS" }.map { it.blockNumber }.distinct().size
                    val unnamed = activeLots.count { it.isUnassigned && it.isSaleable }
                    val duplicates = activeLots.filter { !it.isUnassigned }.groupBy { it.blockNumber to it.lotNumber }.filter { it.value.size > 1 }.keys.map { (b, l) -> "B$b-L$l" }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (blockLots.isEmpty()) "Renumber blocks" else if (block.isBlank()) "Number lots (${blockLots.size} lots)" else "Renumber Block $block (${blockLots.size} lots)",
                                style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                if (blockLots.isEmpty()) "Saved. Tap a lot to renumber its block."
                                else if (block.isBlank()) "Tap the other lots of this block to add them, then type the block on the plan and its first lot."
                                else "Check the numbers on the shaded lots against the plan. Tap a lot to add it to this block or take it out.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                if (unnamed > 0) "$unnamed ${if (unnamed == 1) "lot has" else "lots have"} no block and lot yet (dashed outline)."
                                else if (blocksToDo == 0) "Every found block is renumbered."
                                else "$blocksToDo ${if (blocksToDo == 1) "block still has" else "blocks still have"} the app's numbers (dashed outline).",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("renumber_blocks_to_do")
                            )
                            if (duplicates.isNotEmpty()) {
                                Text(
                                    "Used twice: " + duplicates.take(4).joinToString(", ") + if (duplicates.size > 4) " and ${duplicates.size - 4} more" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(color = LocalNHAColors.current.behind),
                                    modifier = Modifier.testTag("renumber_duplicates")
                                )
                            }
                            if (blockLots.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = renumberTo,
                                        onValueChange = { renumberTo = it.take(10) },
                                        label = { Text("Block on plan") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("renumber_block_field")
                                    )
                                    OutlinedTextField(
                                        value = renumberFirst,
                                        onValueChange = { v -> renumberFirst = v.filter { it.isDigit() }.take(4) },
                                        label = { Text("First lot") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("renumber_first_field")
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    FilterChip(
                                        selected = renumberPattern == BlockNumbering.Pattern.ODD_EVEN,
                                        onClick = { renumberPattern = BlockNumbering.Pattern.ODD_EVEN },
                                        label = { Text("Odd / even rows") }
                                    )
                                    FilterChip(
                                        selected = renumberPattern == BlockNumbering.Pattern.CONSECUTIVE,
                                        onClick = { renumberPattern = BlockNumbering.Pattern.CONSECUTIVE },
                                        label = { Text("Consecutive") }
                                    )
                                    FilterChip(selected = renumberOtherEnd, onClick = { renumberOtherEnd = !renumberOtherEnd }, label = { Text("Start from other end") })
                                    FilterChip(selected = renumberOtherRow, onClick = { renumberOtherRow = !renumberOtherRow }, label = { Text("Start on other row") })
                                }
                                if (othersInTarget > 0) {
                                    Text(
                                        "Block $target already has $othersInTarget other ${if (othersInTarget == 1) "lot" else "lots"}. They keep their numbers until you renumber them.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = LocalNHAColors.current.behind)
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { renumberBlock = null; renumberIds = emptySet() }) {
                                    Text(if (blockLots.isEmpty()) "Done" else "Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (blockLots.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        enabled = target.isNotEmpty() && first != null,
                                        onClick = {
                                            onRenumberLots(
                                                blockLots.mapNotNull { lot -> renumberPreview[lot.id]?.let { lot.copy(blockNumber = target, lotNumber = it.toString()) } },
                                                block
                                            )
                                            // Stay open so the next tap picks the next block.
                                            renumberIds = emptySet()
                                        },
                                        modifier = Modifier.testTag("renumber_save_button")
                                    ) { Text("Save numbers") }
                                }
                            }
                        }
                    }
                }

                if (framing && activeSdpPlan != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Plan area for printing", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold))
                            Text(
                                if (frameCorner == null) "The dashed box is what prints. Tap one corner of the plan area, then the opposite corner."
                                else "Now tap the opposite corner.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { framing = false; frameCorner = null }) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                TextButton(onClick = { onSetPlanFrame(activeSdpPlan, listOf(0f, 0f, 1f, 1f)); framing = false; frameCorner = null }) { Text("Whole page") }
                                TextButton(
                                    onClick = { onSetPlanFrame(activeSdpPlan, null); framing = false; frameCorner = null },
                                    modifier = Modifier.testTag("frame_fit_lots")
                                ) { Text("Fit to lots") }
                            }
                        }
                    }
                }

                groupIds?.let { group ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "${group.size} lot${if (group.size == 1) "" else "s"} selected",
                                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            )
                            Text("Tap lots on the plan to add or remove them.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { groupIds = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Box {
                                    TextButton(enabled = group.isNotEmpty(), onClick = { showGroupUse = true }, modifier = Modifier.testTag("group_use_button")) { Text("Set use") }
                                    DropdownMenu(expanded = showGroupUse, onDismissRequest = { showGroupUse = false }) {
                                        LOT_USES.forEach { use ->
                                            DropdownMenuItem(text = { Text(use) }, onClick = {
                                                onSetLotUse(activeLots.filter { it.id in group }, use)
                                                showGroupUse = false
                                                groupIds = null
                                            })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    enabled = group.isNotEmpty(),
                                    onClick = { showGroupProgress = true },
                                    modifier = Modifier.testTag("group_update_button")
                                ) { Text("Update progress") }
                            }
                        }
                    }
                }

                // BOTTOM SELECTION INFO CARD (WITH PHASE 7 RECORD INSPECTION & HISTORY ACTIONS)
                if (renumberBlock == null && groupIds == null && (selectedLot != null || selectedRoad != null)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedLot != null) {
                                    val progressRecord = lotProgressMap[selectedLot!!.id]
                                    val progressVal = progressRecord?.physicalProgress ?: 0
                                    val isBilled = progressRecord.anyBilled

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HomeWork, contentDescription = "Lot", tint = getLotProgressFillColor(progressVal), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedLot!!.label,
                                                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (isBilled) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, if (isBilled) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Text(
                                                    text = if (isBilled) "● ${progressRecord.billingLabel}" else "NOT BILLED",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (isBilled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = (if (selectedLot!!.isRoadLot) "Road: ${if (progressVal >= 100) "Completed" else "Not completed"} • Drainage: ${if ((progressRecord?.drainageProgress ?: 0) >= 100) "Completed" else "Not completed"}"
                                                else "Progress: ${progressVal}% (${progressRecord?.constructionStatus ?: "Not Started"})") + " • Area: ${selectedLot!!.lotAreaSqM} sq.m • Unit: ${selectedLot!!.housingUnitNumber.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                        if (progressRecord?.lastModifiedBy?.isNotBlank() == true) {
                                            Text(
                                                text = "Last Updated: ${progressRecord.lastModifiedDate} by ${progressRecord.lastModifiedBy}",
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                } else if (selectedRoad != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timeline, contentDescription = "Road", tint = LocalNHAColors.current.onTrack, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedRoad!!.roadName,
                                                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Text(
                                            text = "Type: ${selectedRoad!!.roadType} • Internal ID: ${selectedRoad!!.id}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    selectedLot = null
                                    selectedRoad = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Action Bar inside info sheet
                            if (selectedLot != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (canDigitize) Button(
                                        onClick = { showProgressDialogLot = selectedLot },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text("UPDATE PROGRESS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (canDigitize) Button(
                                        onClick = { showRecordInspectionDialogLot = selectedLot },
                                        colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.onTrack, contentColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1.1f).height(32.dp)
                                    ) {
                                        Icon(Icons.Default.FactCheck, contentDescription = "Record", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("RECORD INSPECTION", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { showLotInfoDialog = selectedLot },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text("HISTORY & DETAILS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (canDigitize) {
                                        Button(
                                            onClick = { lotToDeactivate = selectedLot },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = LocalNHAColors.current.behind),
                                            border = BorderStroke(1.dp, LocalNHAColors.current.behind),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(0.7f).height(32.dp).testTag("remove_lot_button")
                                        ) {
                                            Text("REMOVE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (canDigitize && selectedLot != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { startRenumber(selectedLot!!) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp).testTag("renumber_block_button")
                                    ) {
                                        Text(if (selectedLot!!.isUnassigned) "NUMBER LOTS" else "RENUMBER BLOCK ${selectedLot!!.blockNumber}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { groupIds = setOf(selectedLot!!.id); selectedLot = null },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp).testTag("group_select_button")
                                    ) {
                                        Text("SELECT MORE LOTS (SAME PROGRESS)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // --- MODAL DIALOGS ---

    // 1. PROGRESS LEGEND MODAL
    if (showLegendDialog) {
        SdpLegendDialog(onDismiss = { showLegendDialog = false })
    }

    // 2. RECORD FORMAL INSPECTION SNAPSHOT MODAL (PHASE 7)
    if (showRecordInspectionDialogLot != null && activeSdpPlan != null) {
        val targetLot = showRecordInspectionDialogLot!!
        val currentProgress = lotProgressMap[targetLot.id]
        RecordInspectionSnapshotDialog(
            lot = targetLot,
            currentProgress = currentProgress,
            defaultContractor = project.contractor,
            onDismiss = { showRecordInspectionDialogLot = null },
            onSubmitInspection = { progressVal: Int, status: String, activity: String, contractor: String, remarks: String, billingStatus: String, billingRef: String ->
                onRecordInspection(
                    targetLot.id,
                    progressVal,
                    status,
                    activity,
                    contractor,
                    remarks,
                    billingStatus,
                    billingRef
                )
                showRecordInspectionDialogLot = null
            }
        )
    }

    // 3. UPDATE LOT CONSTRUCTION PROGRESS & BILLING MODAL (NORMAL TAP)
    if (showProgressDialogLot != null && activeSdpPlan != null) {
        val targetLot = showProgressDialogLot!!
        val currentProgress = lotProgressMap[targetLot.id]
        var pendingRename: Pair<String, String>? = null
        UpdateLotProgressDialog(
            lot = targetLot,
            currentProgress = currentProgress,
            defaultContractor = project.contractor,
            canManageBilling = canDigitize,
            onDismiss = { showProgressDialogLot = null },
            onSubmitProgress = { progressVal: Int, status: String, activity: String, startDate: String, targetDate: String, contractor: String, remarks: String, photos: List<String>, use: String, drainage: Int? ->
                // Both writes carry the new name and use, so neither undoes the other.
                val name = pendingRename
                if (use != targetLot.use) onSetLotUse(listOf(name?.let { targetLot.copy(blockNumber = it.first, lotNumber = it.second) } ?: targetLot), use)
                if (name != null) onRenameLot(targetLot.copy(lotUse = use), name.first, name.second)
                onUpdateLotProgress(
                    targetLot.id,
                    progressVal,
                    status,
                    activity,
                    startDate,
                    targetDate,
                    contractor,
                    remarks,
                    photos,
                    drainage
                )
                showProgressDialogLot = null
            },
            onToggleBillingRequested = { milestone: Int, shouldBeBilled: Boolean ->
                lotToToggleBilling = Triple(targetLot, milestone, shouldBeBilled)
            },
            takenNames = activeLots.filter { it.id != targetLot.id && !it.isUnassigned }.map { it.blockNumber to it.lotNumber }.toSet(),
            // Called right before onSubmitProgress on Save.
            onRename = { block, lotNo -> pendingRename = block to lotNo }
        )
    }

    if (showGroupProgress) {
        val group = groupIds.orEmpty()
        GroupProgressDialog(
            count = group.size,
            onDismiss = { showGroupProgress = false },
            onSubmit = { progressVal, status, activity, remarks ->
                // Each lot keeps its own dates, contractor and photos.
                group.forEach { id ->
                    val existing = lotProgressMap[id]
                    onUpdateLotProgress(
                        id, progressVal, status, activity,
                        existing?.startDate ?: "", existing?.targetCompletionDate ?: "",
                        existing?.contractor?.ifBlank { project.contractor } ?: project.contractor,
                        remarks, null, null
                    )
                }
                showGroupProgress = false
                groupIds = null
            }
        )
    }

    // 4. DEVELOPER BILLING CONFIRMATION DIALOG
    if (lotToToggleBilling != null) {
        val (targetLot, milestone, markAsBilled) = lotToToggleBilling!!
        val share = if (milestone == 1) "M1 (80%)" else "M2 (20%)"
        var billingRef by remember { mutableStateOf("") }
        var billingRemarks by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { lotToToggleBilling = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Billing", tint = if (markAsBilled) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (markAsBilled) "Mark $share of ${targetLot.label} as BILLED?" else "Mark $share of ${targetLot.label} as NOT BILLED?",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (markAsBilled)
                            "This lot will be highlighted with a distinct cyan billing border on the Interactive SDP."
                        else
                            "The $share billing will be removed from this lot.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    if (markAsBilled) {
                        OutlinedTextField(
                            value = billingRef,
                            onValueChange = { billingRef = it },
                            label = { Text("Billing Reference / Voucher No. (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = billingRemarks,
                            onValueChange = { billingRemarks = it },
                            label = { Text("Billing Remarks (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateLotBillingStatus(targetLot.id, milestone, markAsBilled, billingRef.trim(), billingRemarks.trim())
                        lotToToggleBilling = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (markAsBilled) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(if (markAsBilled) "CONFIRM M$milestone BILLED" else "CONFIRM M$milestone UNBILL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { lotToToggleBilling = null }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // 5. DETAILED LOT INFORMATION & HISTORICAL TIMELINE MODAL (PHASE 7)
    if (showLotInfoDialog != null) {
        val targetLot = showLotInfoDialog!!
        val currentProgress = lotProgressMap[targetLot.id]
        LotInfoDialog(
            lot = targetLot,
            currentProgress = currentProgress,
            sdpVersion = activeSdpPlan?.version ?: 1,
            canEdit = canDigitize,
            onGetInspections = { onGetInspectionsForLot(targetLot.id) },
            onDismiss = { showLotInfoDialog = null },
            onRecordInspectionRequested = {
                showRecordInspectionDialogLot = targetLot
                showLotInfoDialog = null
            },
            onViewAuditTrailRequested = {
                showLotAuditTrailDialogLot = targetLot
                showLotInfoDialog = null
            },
            onEditGeometry = {
                interactionMode = SdpInteractionMode.DIGITIZING_LOT
                showLotInfoDialog = null
            },
            onDeactivate = {
                lotToDeactivate = targetLot
                showLotInfoDialog = null
            }
        )
    }

    // 5b. LOT AUDIT HISTORY MODAL (PHASE 8)
    if (showLotAuditTrailDialogLot != null) {
        val targetLot = showLotAuditTrailDialogLot!!
        LotAuditHistoryDialog(
            lot = targetLot,
            auditLogs = auditLogs,
            onDismiss = { showLotAuditTrailDialogLot = null }
        )
    }

    // 6. ADD LOT DIALOG
    if (showAddLotDialog && activeSdpPlan != null) {
        LaunchedEffect(Unit) {
            draftArea = null
            onLotArea(activeSdpPlan, draftPoints.map { it.x to it.y }) { draftArea = it }
        }
        AddLotDialog(
            suggestedArea = draftArea,
            onDismiss = {
                showAddLotDialog = false
                // A traced outline is thrown away on cancel; tapped corners stay for more tapping.
                if (interactionMode == SdpInteractionMode.TAP_ADD_LOT) draftPoints.clear()
            },
            onSubmit = { block: String, lot: String, unit: String, area: Double, desc: String ->
                val json = normalizedToJson(draftPoints)
                onAddLot(block, lot, unit, area, json, desc)
                draftPoints.clear()
                showAddLotDialog = false
            }
        )
    }

    // 6b. PLAN SCALE
    if (showScaleDialog && activeSdpPlan != null) {
        var scaleText by remember { mutableStateOf(activeSdpPlan.scaleDenominator.takeIf { it > 0 }?.toString() ?: "") }
        val scale = scaleText.toIntOrNull()?.takeIf { it in 10..100000 }
        AlertDialog(
            onDismissRequest = { showScaleDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Plan scale", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Type the scale printed on the plan, e.g. SCALE 1:1000. Lot areas are worked out from it; areas someone typed in stay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1 : ", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = scaleText,
                            onValueChange = { v -> scaleText = v.filter { it.isDigit() }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("plan_scale_field")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = scale != null,
                    onClick = {
                        onSetPlanScale(activeSdpPlan, scale!!)
                        showScaleDialog = false
                    },
                    modifier = Modifier.testTag("plan_scale_save")
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showScaleDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // 7. ADD ROAD DIALOG
    if (showAddRoadDialog && activeSdpPlan != null) {
        AddRoadDialog(
            onDismiss = { showAddRoadDialog = false },
            onSubmit = { name: String, type: String ->
                val json = normalizedToJson(draftPoints)
                onAddRoad(name, type, json)
                draftPoints.clear()
                showAddRoadDialog = false
            }
        )
    }

    // 8. DEACTIVATE LOT CONFIRMATION
    if (lotToDeactivate != null) {
        AlertDialog(
            onDismissRequest = { lotToDeactivate = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Remove ${lotToDeactivate!!.label}?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("The lot is taken off the plan on every phone. Its progress and billing history stay in the audit log.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivateLot(lotToDeactivate!!)
                        selectedLot = null
                        lotToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.behind, contentColor = MaterialTheme.colorScheme.surface)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { lotToDeactivate = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // 8b. REMOVE ALL FOUND LOTS CONFIRMATION
    if (confirmRemoveDetected && activeSdpPlan != null) {
        val removable = activeLots.count { it.description.startsWith("Auto-detected") && !lotProgressMap.containsKey(it.id) }
        val kept = activeLots.count { it.description.startsWith("Auto-detected") && lotProgressMap.containsKey(it.id) }
        AlertDialog(
            onDismissRequest = { confirmRemoveDetected = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove $removable found ${if (removable == 1) "lot" else "lots"}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Lots the app found that have no progress yet are taken off the plan on every phone. " +
                        "Lots you added yourself or renumbered stay" + (if (kept > 0) ", and so do the $kept found ${if (kept == 1) "lot that already has" else "lots that already have"} progress." else ".") +
                        " Find lots can add them again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveDetectedLots(activeSdpPlan)
                        selectedLot = null
                        confirmRemoveDetected = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.behind, contentColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("confirm_remove_found_lots")
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveDetected = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // 9. DEACTIVATE ROAD CONFIRMATION
    if (roadToDeactivate != null) {
        AlertDialog(
            onDismissRequest = { roadToDeactivate = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Deactivate Road '${roadToDeactivate!!.roadName}'?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Deactivated roads will be hidden from monitoring mode but preserved in history.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivateRoad(roadToDeactivate!!)
                        selectedRoad = null
                        roadToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.behind, contentColor = MaterialTheme.colorScheme.surface)
                ) { Text("Deactivate") }
            },
            dismissButton = { TextButton(onClick = { roadToDeactivate = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // 10. UPLOAD SDP DIALOG
    if (showUploadDialog) {
        AddSdpPlanDialog(
            currentVersion = sdpPlans.size + 1,
            onDismiss = { showUploadDialog = false },
            onSubmit = { planName: String, pdfUriString: String, desc: String ->
                detectAfterUpload = pdfUriString.isNotBlank()
                onUploadSdpPlan(planName, pdfUriString, desc)
                showUploadDialog = false
            }
        )
    }

    // 11. DETECTED LOTS REVIEW
    detectedAreas?.let { areas ->
        val plan = activeSdpPlan
        val lotCount = areas.count { it.isLot }
        val blockCount = areas.filter { it.isLot }.map { it.block }.distinct().size
        val largeCount = areas.count { !it.isLot }
        val updatedDetected = activeLots.count { it.description.startsWith("Auto-detected") && lotProgressMap.containsKey(it.id) }
        AlertDialog(
            onDismissRequest = { detectedAreas = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Lots found in the plan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Found $lotCount lots in $blockCount blocks" + if (largeCount > 0) ", and $largeCount larger areas (open spaces or facilities)." else ".")
                    Text(
                        "The lots come in without block and lot numbers. Tap a lot and set its Block and Lot in Update Progress, or use Number lots to number a block at once. " +
                            "Earlier found lots with no progress yet are replaced; renumbered lots stay" +
                            if (updatedDetected > 0) "; the $updatedDetected with progress stay." else ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (plan != null) onAddDetectedLots(plan, areas.filter { it.isLot }); detectedAreas = null },
                    modifier = Modifier.testTag("add_detected_lots")
                ) { Text("Add lots") }
            },
            dismissButton = {
                Row {
                    if (largeCount > 0) {
                        TextButton(onClick = { if (plan != null) onAddDetectedLots(plan, areas); detectedAreas = null }) {
                            Text("Add lots and larger areas")
                        }
                    }
                    TextButton(onClick = { detectedAreas = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        )
    }
    if (detectFailed) {
        AlertDialog(
            onDismissRequest = { detectFailed = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("No lots found", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The app finds lots in a plan PDF exported from CAD, where each lot is a closed outline. " +
                        "A scanned plan or a photo has no clear outlines; add its lots by hand in Digitize mode.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { detectFailed = false }) { Text("OK") } }
        )
    }
}
}

// --- PHASE 7 RECORD INSPECTION SNAPSHOT DIALOG ---
@Composable
fun RecordInspectionSnapshotDialog(
    lot: SdpLot,
    currentProgress: SdpLotProgress?,
    defaultContractor: String,
    onDismiss: () -> Unit,
    onSubmitInspection: (
        progress: Int,
        status: String,
        activity: String,
        contractor: String,
        remarks: String,
        billingStatus: String,
        billingRef: String
    ) -> Unit
) {
    var progressSlider by remember { mutableFloatStateOf(currentProgress?.physicalProgress?.toFloat() ?: 0f) }
    var constructionStatus by remember { mutableStateOf(currentProgress?.constructionStatus ?: "Not Started") }
    var currentActivity by remember { mutableStateOf(currentProgress?.currentActivity ?: "") }
    var contractor by remember { mutableStateOf(currentProgress?.contractor?.ifBlank { defaultContractor } ?: defaultContractor) }
    var remarks by remember { mutableStateOf("") }
    // The snapshot keeps both billings, e.g. "M1 BILLED (80%)".
    var billingStatus by remember { mutableStateOf(currentProgress.billingLabel) }
    var billingRef by remember { mutableStateOf(currentProgress?.billingReference ?: "") }

    val intProgress = progressSlider.toInt().coerceIn(0, 100)
    val derivedColor = getLotProgressFillColor(intProgress)

    val cs = MaterialTheme.colorScheme
    fun snapshot() = listOf(progressSlider, constructionStatus, currentActivity, contractor, remarks, billingStatus, billingRef)
    val initial = remember { snapshot() }
    val dirty = snapshot() != initial

    LongForm(
        title = "Record inspection: ${lot.label}",
        dirty = dirty,
        saveEnabled = true,
        saveTag = "submit_lot_inspection",
        onDismiss = onDismiss,
        onSave = {
            onSubmitInspection(
                intProgress,
                constructionStatus,
                currentActivity,
                contractor,
                remarks,
                billingStatus,
                billingRef
            )
        }
    ) {
        Text(
            text = "This will capture an immutable snapshot of the lot's condition for inspection history and velocity analytics.",
            style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant)
        )

        // Physical Progress Slider
        Surface(
            color = cs.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, derivedColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inspected Progress %", style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant))
                    Surface(color = derivedColor, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "$intProgress%",
                            style = MaterialTheme.typography.labelSmall.copy(color = cs.surface, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                ProgressPicker(progressSlider, derivedColor) {
                    progressSlider = it
                    constructionStatus = statusForProgress(it.toInt())
                }
            }
        }

        OutlinedTextField(value = constructionStatus, onValueChange = { constructionStatus = it }, label = { Text("Construction Status") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = currentActivity, onValueChange = { currentActivity = it }, label = { Text("Current Activity") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = contractor, onValueChange = { contractor = it }, label = { Text("Contractor") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Inspection Field Remarks") }, modifier = Modifier.fillMaxWidth())
    }
}

// --- DETAILED LOT INFO & HISTORICAL TIMELINE DIALOG (PHASE 7) ---
@Composable
fun LotInfoDialog(
    lot: SdpLot,
    currentProgress: SdpLotProgress?,
    sdpVersion: Int,
    canEdit: Boolean,
    onGetInspections: () -> Flow<List<SdpLotInspection>>,
    onDismiss: () -> Unit,
    onRecordInspectionRequested: () -> Unit,
    onViewAuditTrailRequested: () -> Unit,
    onEditGeometry: () -> Unit,
    onDeactivate: () -> Unit
) {
    val progressVal = currentProgress?.physicalProgress ?: 0
    val derivedColor = getLotProgressFillColor(progressVal)
    val isBilled = currentProgress.anyBilled

    val inspectionsList by onGetInspections().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${lot.label} Metadata & History",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // CURRENT STATUS CARD
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, derivedColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CURRENT LOT STATUS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Physical Progress", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Text("${progressVal}% (${currentProgress?.constructionStatus ?: "Not Started"})", style = MaterialTheme.typography.bodySmall.copy(color = derivedColor, fontWeight = FontWeight.Bold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Developer Billing", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Text(if (isBilled) "● ${currentProgress.billingLabel}" else "NOT BILLED", style = MaterialTheme.typography.bodySmall.copy(color = if (isBilled) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
                        }
                        Text(if (lot.isUnassigned) "Block and lot not assigned yet" else "Block Number: ${lot.blockNumber} • Lot Number: ${lot.lotNumber}", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text("Housing Unit: ${lot.housingUnitNumber.ifBlank { "N/A" }} • Area: ${lot.lotAreaSqM} sq.m", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
                        Text("SDP Plan Version: v$sdpVersion • Internal ID: ${lot.id}", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }

                // PHASE 7 HISTORICAL TIMELINE SECTION
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INSPECTION HISTORY & TIMELINE", style = MaterialTheme.typography.labelSmall.copy(color = LocalNHAColors.current.onTrack, fontWeight = FontWeight.Bold))

                            if (canEdit) {
                                Button(
                                    onClick = onRecordInspectionRequested,
                                    colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.onTrack, contentColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Record", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("RECORD SNAPSHOT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (inspectionsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.History, contentDescription = "No History", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("NO INSPECTION HISTORY", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
                                    Text(if (canEdit) "Tap RECORD SNAPSHOT above to log an official inspection point." else "No inspection snapshots recorded yet.", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 10.sp)
                                }
                            }
                        } else {
                            // Timeline progression string
                            val chronological = inspectionsList.reversed()
                            val timelineStr = chronological.joinToString(" → ") { "${it.physicalProgress}%" }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Timeline: $timelineStr",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Reverse Chronological List
                            inspectionsList.forEachIndexed { index, inspection ->
                                val color = getLotProgressFillColor(inspection.physicalProgress)

                                // Velocity / Change calculation relative to previous chronological inspection
                                var changeText = ""
                                if (index < inspectionsList.size - 1) {
                                    val prevInspection = inspectionsList[index + 1]
                                    val diff = inspection.physicalProgress - prevInspection.physicalProgress
                                    val sign = if (diff >= 0) "+" else ""

                                    // Calculate days elapsed
                                    val daysElapsed = try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                        val d1 = sdf.parse(inspection.inspectionDate)
                                        val d2 = sdf.parse(prevInspection.inspectionDate)
                                        if (d1 != null && d2 != null) {
                                            val diffMs = d1.time - d2.time
                                            (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                                        } else 0
                                    } catch (e: Exception) { 0 }

                                    changeText = " ($sign$diff percentage points / $daysElapsed days)"
                                } else if (inspectionsList.size == 1) {
                                    changeText = " (First inspection recorded)"
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, color),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${inspection.physicalProgress}% • ${inspection.constructionStatus}",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = inspection.billingStatus,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (inspection.billingStatus != "NOT BILLED") LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${inspection.inspectionDate} by ${inspection.inspectedBy}$changeText",
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            )
                                            if (inspection.remarks.isNotBlank()) {
                                                Text(
                                                    text = "Remarks: ${inspection.remarks}",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewAuditTrailRequested,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "Audit Trail", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AUDIT TRAIL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                if (canEdit) {
                    Button(
                        onClick = onEditGeometry,
                        colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.watch, contentColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Geometry", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EDIT GEOMETRY", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// --- READ-ONLY LOT AUDIT HISTORY DIALOG (PHASE 8) ---
@Composable
fun LotAuditHistoryDialog(
    lot: SdpLot,
    auditLogs: List<com.example.data.model.AuditLog>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = "Audit", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Audit History — ${lot.label}",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Read-only system audit log entries for ${lot.label}.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                val lotAuditLogs = remember(auditLogs, lot.id) {
                    auditLogs.filter { log ->
                        log.details.contains("Lot ID: ${lot.id}") ||
                        log.details.contains("Lot ID ${lot.id}") ||
                        (!lot.isUnassigned && log.details.contains("Lot B${lot.blockNumber}-L${lot.lotNumber}"))
                    }.sortedByDescending { it.timestamp }
                }

                if (lotAuditLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No audit log records found for this lot.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                } else {
                    lotAuditLogs.forEach { log ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.actionType, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(log.timestamp))
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp))
                                }
                                Text("By: ${log.user} (${log.device})", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp))
                                Text(log.details, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
                                if (log.oldValue.isNotBlank() || log.newValue.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (log.oldValue.isNotBlank()) {
                                        Text("Previous: ${log.oldValue}", style = MaterialTheme.typography.labelSmall.copy(color = LocalNHAColors.current.behind, fontSize = 10.sp))
                                    }
                                    if (log.newValue.isNotBlank()) {
                                        Text("New: ${log.newValue}", style = MaterialTheme.typography.labelSmall.copy(color = LocalNHAColors.current.onTrack, fontSize = 10.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// --- HELPER DIALOGS ---
@Composable
fun AddLotDialog(
    onDismiss: () -> Unit,
    onSubmit: (block: String, lot: String, unit: String, area: Double, desc: String) -> Unit,
    suggestedArea: Double? = null
) {
    var blockNumber by remember { mutableStateOf("") }
    var lotNumber by remember { mutableStateOf("") }
    var unitNumber by remember { mutableStateOf("") }
    var areaSqM by remember { mutableStateOf("50.0") }
    // The worked-out area arrives a moment after the dialog opens; it replaces the default only.
    LaunchedEffect(suggestedArea) { if (suggestedArea != null && areaSqM == "50.0") areaSqM = suggestedArea.toString() }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Save Digitized Lot Geometry", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = blockNumber, onValueChange = { blockNumber = it }, label = { Text("Block Number (e.g., 3)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lotNumber, onValueChange = { lotNumber = it }, label = { Text("Lot Number (e.g., 25)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unitNumber, onValueChange = { unitNumber = it }, label = { Text("Housing Unit (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = areaSqM, onValueChange = { areaSqM = it }, label = { Text("Lot Area (sq.m)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description / Remarks") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val area = areaSqM.toDoubleOrNull() ?: 50.0
                    if (blockNumber.isNotBlank() && lotNumber.isNotBlank()) {
                        onSubmit(blockNumber.trim(), lotNumber.trim(), unitNumber.trim(), area, description.trim())
                    }
                },
                enabled = blockNumber.isNotBlank() && lotNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("Save Lot", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}

@Composable
fun AddRoadDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, type: String) -> Unit
) {
    var roadName by remember { mutableStateOf("") }
    var roadType by remember { mutableStateOf("Main Road") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Save Digitized Road Geometry", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = roadName, onValueChange = { roadName = it }, label = { Text("Road Name (e.g., Main Avenue)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roadType, onValueChange = { roadType = it }, label = { Text("Road Type (Main Road / Secondary / Alley)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roadName.isNotBlank()) {
                        onSubmit(roadName.trim(), roadType.trim())
                    }
                },
                enabled = roadName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LocalNHAColors.current.onTrack, contentColor = MaterialTheme.colorScheme.surface)
            ) { Text("Save Road", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}

@Composable
fun AddSdpPlanDialog(
    currentVersion: Int,
    onDismiss: () -> Unit,
    onSubmit: (planName: String, pdfUrl: String, description: String) -> Unit
) {
    var planName by remember { mutableStateOf("Approved Subdivision Plan Layout") }
    var description by remember { mutableStateOf("Official Approved SDP PDF Reference Document") }
    // The pick is copied into the app and uploaded like any attachment, so every phone can open it.
    var pdfRef by remember { mutableStateOf<String?>(null) }
    val pdfPicker = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        if (com.example.data.files.Attachments.isPdf(ref)) pdfRef = ref
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "SDP", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload Approved SDP (Version $currentVersion)",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    label = { Text("Plan Document Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Remarks") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Select PDF File Button
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (pdfRef != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pdfPicker.launch("application/pdf") }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pdfRef != null) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = "PDF",
                            tint = if (pdfRef != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pdfRef != null) "PDF Selected" else "Select Approved SDP PDF",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "Lots are found automatically when the plan is a PDF exported from CAD.",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pdfRef?.let { onSubmit(planName, it, description) }
                },
                enabled = pdfRef != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Save SDP Plan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// --- KPI CHIP COMPOSABLE ---
@Composable
fun KpiChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold))
        }
    }
}

// --- FILTER DROPDOWN CHIP ---
@Composable
fun FilterDropdownChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) LocalNHAColors.current.watch.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isSelected) LocalNHAColors.current.watch else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) LocalNHAColors.current.watch else MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
    }
}

// --- PROGRESS & BILLING LEGEND DIALOG ---
@Composable
fun SdpLegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = "Legend", tint = LocalNHAColors.current.watch)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SDP Color & Billing Legend", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONSTRUCTION PHYSICAL PROGRESS (LOT FILL):", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

                LegendRow(color = getLotProgressFillColor(0), label = "0 – 25% Physical Progress")
                LegendRow(color = getLotProgressFillColor(26), label = "26 – 50% Physical Progress")
                LegendRow(color = getLotProgressFillColor(51), label = "51 – 75% Physical Progress")
                LegendRow(color = getLotProgressFillColor(76), label = "76 – 99% Physical Progress")
                LegendRow(color = getLotProgressFillColor(100), label = "100% Completed / RFO")
                LegendRow(color = LocalNHAColors.current.neutral, label = "Non-saleable (open space, breaker, easement, facility)")

                Spacer(modifier = Modifier.height(6.dp))
                Text("DEVELOPER BILLING STATUS (LOT BORDER):", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.Transparent)
                            .border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("BILLED = Thick Cyan Dashed Outer Border", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// --- LOT PROGRESS UPDATE DIALOG (NORMAL TAP WORKFLOW) ---
@Composable
fun UpdateLotProgressDialog(
    lot: SdpLot,
    currentProgress: SdpLotProgress?,
    defaultContractor: String,
    canManageBilling: Boolean,
    onDismiss: () -> Unit,
    onSubmitProgress: (
        progress: Int,
        status: String,
        activity: String,
        startDate: String,
        targetDate: String,
        contractor: String,
        remarks: String,
        photos: List<String>,
        use: String,
        // Road lots only.
        drainage: Int?
    ) -> Unit,
    // milestone 1: M1 (80%), 2: M2 (20%).
    onToggleBillingRequested: (milestone: Int, shouldBeBilled: Boolean) -> Unit,
    // Block and lot names other lots already use; a lot can't take one of them.
    takenNames: Set<Pair<String, String>> = emptySet(),
    onRename: (block: String, lot: String) -> Unit = { _, _ -> }
) {
    var lotUse by remember { mutableStateOf(lot.use) }
    var blockName by remember { mutableStateOf(lot.blockNumber) }
    var lotName by remember { mutableStateOf(lot.lotNumber) }
    // "2-b" and "2-B" are the same lot on the plan.
    val nameTaken = takenNames.any { (b, l) -> b.equals(blockName.trim(), ignoreCase = true) && l.equals(lotName.trim(), ignoreCase = true) }
    val nameError = when {
        // An unnamed lot can still get progress; it stays unassigned until both are filled in.
        lot.isUnassigned && blockName.isBlank() && lotName.isBlank() -> null
        blockName.isBlank() || lotName.isBlank() -> "Block and lot can't be blank"
        nameTaken -> "Block ${blockName.trim()} Lot ${lotName.trim()} already exists"
        else -> null
    }
    var drainageSlider by remember { mutableFloatStateOf(currentProgress?.drainageProgress?.toFloat() ?: 0f) }
    val isRoad = lotUse == "Road Lot"
    var progressSlider by remember { mutableFloatStateOf(currentProgress?.physicalProgress?.toFloat() ?: 0f) }
    var constructionStatus by remember { mutableStateOf(currentProgress?.constructionStatus ?: "Not Started") }
    var currentActivity by remember { mutableStateOf(currentProgress?.currentActivity ?: "") }
    var startDate by remember { mutableStateOf(currentProgress?.startDate ?: "") }
    var targetCompletionDate by remember { mutableStateOf(currentProgress?.targetCompletionDate ?: "") }
    var contractor by remember { mutableStateOf(currentProgress?.contractor?.ifBlank { defaultContractor } ?: defaultContractor) }
    var remarks by remember { mutableStateOf(currentProgress?.remarks ?: "") }
    var photos by remember { mutableStateOf(com.example.data.files.Attachments.parseList(currentProgress?.photosJson ?: "[]")) }

    val intProgress = progressSlider.toInt().coerceIn(0, 100)
    val derivedColor = getLotProgressFillColor(intProgress)
    val isBilled = currentProgress.anyBilled

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HomeWork, contentDescription = "Lot", tint = derivedColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${lot.label} Construction & Billing",
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canManageBilling) {
                    // The lot's name as the team calls it, e.g. Block 12 Lot 5-A.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = blockName, onValueChange = { blockName = it }, label = { Text("Block") }, singleLine = true,
                            isError = nameError != null, modifier = Modifier.weight(1f).testTag("lot_block_name")
                        )
                        OutlinedTextField(
                            value = lotName, onValueChange = { lotName = it }, label = { Text("Lot") }, singleLine = true,
                            isError = nameError != null, modifier = Modifier.weight(1f).testTag("lot_lot_name")
                        )
                    }
                    nameError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text("Lot use", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LOT_USES.forEach { use ->
                            FilterChip(selected = lotUse == use, onClick = { lotUse = use }, label = { Text(use) }, modifier = Modifier.testTag("lot_use_$use"))
                        }
                    }
                }

                if (isRoad) {
                    // Road lots are tagged done instead of tracked by %; done is saved as 100%.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = intProgress == 100,
                            onClick = {
                                progressSlider = if (intProgress == 100) 0f else 100f
                                constructionStatus = statusForProgress(progressSlider.toInt())
                            },
                            label = { Text("Road completed") },
                            modifier = Modifier.testTag("road_done")
                        )
                        FilterChip(
                            selected = drainageSlider >= 100f,
                            onClick = { drainageSlider = if (drainageSlider >= 100f) 0f else 100f },
                            label = { Text("Drainage completed") },
                            modifier = Modifier.testTag("drainage_done")
                        )
                    }
                } else Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, derivedColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Physical Progress %", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Surface(
                                color = derivedColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "$intProgress%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        ProgressPicker(progressSlider, derivedColor) {
                            progressSlider = it
                            constructionStatus = statusForProgress(it.toInt())
                        }
                    }
                }

                OutlinedTextField(
                    value = constructionStatus,
                    onValueChange = { constructionStatus = it },
                    label = { Text("Construction Status") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = currentActivity,
                    onValueChange = { currentActivity = it },
                    label = { Text("Current Activity (e.g., Rebar / Concreting)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contractor,
                    onValueChange = { contractor = it },
                    label = { Text("Contractor") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetCompletionDate,
                        onValueChange = { targetCompletionDate = it },
                        label = { Text("Target Date") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                LotPhotos(lot, photos) { photos = it }

                // DEVELOPER BILLING CONTROL SECTION
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isBilled) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DEVELOPER BILLING STATUS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(
                            text = if (isBilled) "● ${currentProgress.billingLabel}" else "NOT BILLED",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = if (isBilled) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        // A lot is billed twice: M1 for 80%, then M2 for the last 20%.
                        val m1 = currentProgress.m1Billed
                        val m2 = currentProgress.m2Billed
                        listOf(
                            Triple(1, m1, listOf(currentProgress?.billingDate, currentProgress?.billedBy, currentProgress?.billingReference)),
                            Triple(2, m2, listOf(currentProgress?.m2BillingDate, currentProgress?.m2BilledBy, currentProgress?.m2BillingReference))
                        ).forEach { (milestone, billed, info) ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = (if (milestone == 1) "M1 · 80%" else "M2 · 20%") + if (billed) " · BILLED" else " · NOT BILLED",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (billed) LocalNHAColors.current.onTrack else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (billed) {
                                        val (date, by, ref) = info
                                        Text(
                                            text = "${date?.ifBlank { null } ?: "N/A"} • By: ${by?.ifBlank { null } ?: "N/A"}" + (ref?.takeIf { it.isNotBlank() }?.let { " • Ref No: $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }
                                if (canManageBilling) {
                                    // M2 follows M1: it is billed after M1 and unbilled before M1.
                                    val enabled = if (milestone == 1) !(billed && m2) else billed || m1
                                    Button(
                                        onClick = { onToggleBillingRequested(milestone, !billed) },
                                        enabled = enabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (billed) LocalNHAColors.current.behind else LocalNHAColors.current.onTrack,
                                            contentColor = MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("bill_m$milestone")
                                    ) {
                                        Text(
                                            text = if (billed) "MARK M$milestone NOT BILLED" else "MARK M$milestone BILLED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        if (canManageBilling && !m1) {
                            Text("M2 can be marked once M1 is billed.", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !canManageBilling || nameError == null,
                onClick = {
                    if (canManageBilling) onRename(blockName.trim(), lotName.trim())
                    onSubmitProgress(
                        intProgress,
                        constructionStatus,
                        currentActivity,
                        startDate,
                        targetCompletionDate,
                        contractor,
                        remarks,
                        photos,
                        lotUse,
                        if (isRoad) drainageSlider.toInt().coerceIn(0, 100) else null
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = derivedColor, contentColor = MaterialTheme.colorScheme.surface)
            ) {
                Text("SAVE PROGRESS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/** One progress update saved to every lot picked with group select. */
@Composable
private fun GroupProgressDialog(count: Int, onDismiss: () -> Unit, onSubmit: (progress: Int, status: String, activity: String, remarks: String) -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf(statusForProgress(0)) }
    var activity by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    val intProgress = progress.toInt().coerceIn(0, 100)
    val color = getLotProgressFillColor(intProgress)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Update $count lots", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Physical Progress: $intProgress%", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                ProgressPicker(progress, color) { progress = it; status = statusForProgress(it.toInt()) }
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Construction Status") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = activity, onValueChange = { activity = it }, label = { Text("Current Activity") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(intProgress, status, activity, remarks) }, modifier = Modifier.testTag("group_save_button")) {
                Text("SAVE TO $count LOTS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}

/** Construction status that goes with a progress %, as the progress slider sets it. */
fun statusForProgress(progress: Int): String = when (progress) {
    0 -> "Not Started"
    in 1..20 -> "Layout / Excavation Started"
    in 21..80 -> "Under Construction"
    in 81..99 -> "Substantially Completed"
    else -> "Completed"
}

/** Progress slider with a box to type the exact %. */
@Composable
private fun ProgressPicker(value: Float, color: Color, onChange: (Float) -> Unit) {
    var typed by remember { mutableStateOf(value.toInt().toString()) }
    // Keep the box in step when the slider moves.
    LaunchedEffect(value) { if (typed.toIntOrNull() != value.toInt()) typed = value.toInt().toString() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { text ->
                typed = text.substringBefore('.').substringBefore(',').filter(Char::isDigit).take(3)  // lot progress is whole percent
                typed.toIntOrNull()?.coerceIn(0, 100)?.let { onChange(it.toFloat()) }
            },
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(92.dp).testTag("lot_progress_input")
        )
    }
}

/** Photos of a lot. Each new one is stamped "Block X Lot Y" with the date, so the picture says which lot it shows. */
@Composable
private fun LotPhotos(lot: SdpLot, photos: List<String>, onChange: (List<String>) -> Unit) {
    // Built when the photo comes in, so the time is when it was taken.
    val stamp = { listOf(lot.label, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", java.util.Locale.ENGLISH))) }
    val latest by rememberUpdatedState(photos)
    val camera = com.example.ui.components.rememberAttachmentCamera(com.example.data.files.Attachments.Kind.PHOTO, stamp) { onChange(latest + it) }
    val picker = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.PHOTO, stamp) { onChange(latest + it) }
    val open = com.example.ui.components.rememberAttachmentOpener()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Lot photos (${photos.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photos.forEach { ref ->
                    Box {
                        coil.compose.AsyncImage(
                            model = ref, contentDescription = "Photo of ${lot.label}",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)).clickable { open(ref) }
                        )
                        IconButton(onClick = { onChange(photos - ref) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = camera, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Take photo")
            }
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Gallery")
            }
        }
    }
}

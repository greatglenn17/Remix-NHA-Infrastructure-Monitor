package com.example.ui.detail.tabs

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.data.model.hasPermission
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
    EDITING_LOT,
    EDITING_ROAD
}
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
        val matchesQuery = key.contains(q) || keyNoDash.contains(q) || block.contains(q) || lotStr.contains(q) || unit.contains(q)
        if (!matchesQuery) return false
    }

    if (billingFilter != "ALL") {
        val isBilled = progress?.billingStatus == "BILLED"
        if (billingFilter == "BILLED" && !isBilled) return false
        if (billingFilter == "NOT BILLED" && isBilled) return false
    }

    if (completionFilter != "ALL") {
        val isComp = (progress?.physicalProgress ?: 0) >= 100
        if (completionFilter == "COMPLETED" && !isComp) return false
        if (completionFilter == "INCOMPLETE" && isComp) return false
    }

    if (progressFilter != "ALL") {
        val progVal = progress?.physicalProgress ?: 0
        val matchesProg = when (progressFilter) {
            "0%" -> progVal == 0
            "1-20%" -> progVal in 1..20
            "21-50%" -> progVal in 21..50
            "51-80%" -> progVal in 51..80
            "81-99%" -> progVal in 81..99
            "100%" -> progVal >= 100
            else -> true
        }
        if (!matchesProg) return false
    }

    return true
}

fun getLotProgressFillColor(progress: Int): Color {
    return when {
        progress == 0 -> Color(0xFFEF4444)
        progress in 1..20 -> Color(0xFFF97316)
        progress in 21..40 -> Color(0xFFEAB308)
        progress in 41..60 -> Color(0xFF22C55E)
        progress in 61..80 -> Color(0xFFEC4899)
        progress in 81..99 -> Color(0xFF3B82F6)
        progress >= 100 -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }
}

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
        remarks: String
    ) -> Unit,
    onUpdateLotBillingStatus: (
        sdpLotId: Long,
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
    auditLogs: List<com.example.data.model.AuditLog> = emptyList()
) {
    val context = LocalContext.current

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
    var selectedLot by remember { mutableStateOf<SdpLot?>(null) }
    var selectedRoad by remember { mutableStateOf<SdpRoad?>(null) }

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
    var lotToToggleBilling by remember { mutableStateOf<Pair<SdpLot, Boolean>?>(null) }

    val canManageSdp = currentUserRole.hasPermission(com.example.data.model.Permission.MANAGE_SDP_PLANS)
    val canDigitize = currentUserRole.hasPermission(com.example.data.model.Permission.EDIT_PROJECT)

    val textMeasurer = rememberTextMeasurer()

    // --- REACTIVE KPI COMPUTATIONS ---
    val totalLots = activeLots.size
    val lotsWithProgressCount = activeLots.count { lotProgressMap.containsKey(it.id) }
    val billedLotsCount = activeLots.count { lotProgressMap[it.id]?.billingStatus == "BILLED" }
    val notBilledLotsCount = totalLots - billedLotsCount
    val completedLotsCount = activeLots.count { (lotProgressMap[it.id]?.physicalProgress ?: 0) >= 100 }
    val avgPhysicalProgress = if (totalLots > 0) {
        activeLots.sumOf { lotProgressMap[it.id]?.physicalProgress ?: 0 }.toDouble() / totalLots
    } else 0.0

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

                if (pdfUri.scheme == "content" || pdfUri.scheme == "file") {
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
                        val renderWidth = (page.width * 2).coerceAtMost(2400)
                        val renderHeight = (page.height * 2).coerceAtMost(2400)
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
                    pdfError = "Unable to open PDF file descriptor."
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
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        // --- TOP TOOLBAR & HEADER ---
        Surface(
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder),
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
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (activeSdpPlan != null) {
                                "${project.name} • v${activeSdpPlan.version}"
                            } else {
                                "No Approved Site Development Plan Uploaded"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showLegendDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "Legend", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        }

                        if (canManageSdp) {
                            Button(
                                onClick = { showUploadDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF38BDF8),
                                    contentColor = Color.Black
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
                        KpiChip(label = "TOTAL LOTS", value = "$totalLots", color = DarkTextPrimary, modifier = Modifier.weight(1f))
                        KpiChip(label = "BILLED", value = "$billedLotsCount", color = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                        KpiChip(label = "NOT BILLED", value = "$notBilledLotsCount", color = Color(0xFF94A3B8), modifier = Modifier.weight(1f))
                        KpiChip(label = "COMPLETED", value = "$completedLotsCount", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                        KpiChip(label = "AVG PROGRESS", value = String.format("%.1f%%", avgPhysicalProgress), color = Color(0xFFEAB308), modifier = Modifier.weight(1.2f))
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
                                color = DarkSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = "Version", tint = DarkTextSecondary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "v${activeSdpPlan.version}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = DarkTextPrimary)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = DarkTextSecondary)
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.background(DarkSurfaceVariant)
                                ) {
                                    sdpPlans.forEach { plan ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "v${plan.version} - ${plan.planName} ${if (plan.isActive) "(Active)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = if (plan.isActive) Color(0xFF38BDF8) else DarkTextPrimary
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
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Button(
                                    onClick = {
                                        interactionMode = SdpInteractionMode.MONITORING
                                        draftPoints.clear()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (interactionMode == SdpInteractionMode.MONITORING) Color(0xFF0F172A) else Color.Transparent,
                                        contentColor = if (interactionMode == SdpInteractionMode.MONITORING) Color(0xFF38BDF8) else DarkTextSecondary
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
                                            containerColor = if (interactionMode != SdpInteractionMode.MONITORING) Color(0xFFF59E0B) else Color.Transparent,
                                            contentColor = if (interactionMode != SdpInteractionMode.MONITORING) Color.Black else DarkTextSecondary
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Digitize", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("EDIT SDP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) Color(0xFF38BDF8) else DarkBorder),
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
                                    tint = Color(0xFF38BDF8),
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
                                                color = DarkTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
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
                                            tint = DarkTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Filter Toggle Button
                        OutlinedButton(
                            onClick = { showFilterPanel = !showFilterPanel },
                            border = BorderStroke(1.dp, if (isFilterActive) Color(0xFFF59E0B) else DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFilterActive) Color(0xFFF59E0B).copy(alpha = 0.15f) else DarkSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = if (isFilterActive) Color(0xFFF59E0B) else DarkTextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FILTER", fontSize = 11.sp, color = if (isFilterActive) Color(0xFFF59E0B) else DarkTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    // FILTER CHIPS & DROPDOWN PANEL
                    if (showFilterPanel) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("FILTER SUBDIVISION LOTS", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))

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
                                        listOf("ALL", "0%", "1-20%", "21-40%", "41-60%", "61-80%", "81-99%", "100%").forEach { opt ->
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
                                        listOf("ALL", "BILLED", "NOT BILLED").forEach { opt ->
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
                                color = Color(0xFFF59E0B).copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Text(
                                    text = "● FILTER ACTIVE: ${matchingLots.size} OF $totalLots LOTS MATCHING",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold),
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
                                Text("CLEAR FILTERS", fontSize = 10.sp, color = StatusRedText, fontWeight = FontWeight.Bold)
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
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Plan", tint = Color(0xFF38BDF8), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Approved Subdivision Plan Loaded", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Upload the official Approved Subdivision Plan (SDP) PDF or launch the interactive CAD blueprint demo workspace.",
                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary),
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
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
                    .background(Color(0xFF0F172A))
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .clipToBounds()
            ) {
                val viewportWidth = constraints.maxWidth.toFloat()
                val viewportHeight = constraints.maxHeight.toFloat()

                if (isLoadingPdf) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                } else if (pdfError != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(pdfError!!, color = StatusRedText)
                    }
                } else {
                    val bitmapWidth = renderedBitmap?.width?.toFloat() ?: 1600f
                    val bitmapHeight = renderedBitmap?.height?.toFloat() ?: 1200f

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
                            .pointerInput(interactionMode) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = if (scale > 1.2f) 1f else 2.5f
                                        if (scale == 1f) offset = Offset.Zero
                                    },
                                    onTap = { tapOffset ->
                                        val normX = ((tapOffset.x - offset.x) / (bitmapWidth * scale)).coerceIn(0f, 1f)
                                        val normY = ((tapOffset.y - offset.y) / (bitmapHeight * scale)).coerceIn(0f, 1f)
                                        val tappedNorm = Offset(normX, normY)

                                        if (interactionMode == SdpInteractionMode.DIGITIZING_LOT ||
                                            interactionMode == SdpInteractionMode.DIGITIZING_ROAD) {
                                            draftPoints.add(tappedNorm)
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
                                                selectedLot = hitLot
                                                selectedRoad = null
                                                // NORMAL TAP -> OPEN CONSTRUCTION PROGRESS MODAL
                                                showProgressDialogLot = hitLot
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
                                        if (interactionMode == SdpInteractionMode.MONITORING) {
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
                                modifier = Modifier
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
                                drawRect(color = Color(0xFF07111E))

                                val gridStep = 80f
                                var x = 0f
                                while (x < bitmapWidth) {
                                    drawLine(
                                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, bitmapHeight),
                                        strokeWidth = 1f
                                    )
                                    x += gridStep
                                }
                                var y = 0f
                                while (y < bitmapHeight) {
                                    drawLine(
                                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                        start = Offset(0f, y),
                                        end = Offset(bitmapWidth, y),
                                        strokeWidth = 1f
                                    )
                                    y += gridStep
                                }

                                drawRect(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.35f),
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
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    style = Stroke(width = if (isSelected) 8f else 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f))
                                )
                            }

                            // B. RENDER LOTS WITH SPATIAL VIEWPORT CULLING
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
                                val isSelected = selectedLot?.id == lot.id
                                val progressRecord = lotProgressMap[lot.id]
                                val progressValue = progressRecord?.physicalProgress ?: 0
                                val isBilled = progressRecord?.billingStatus == "BILLED"

                                // DERIVED FILL COLOR FROM PROGRESS % (0-100%)
                                val baseProgressColor = getLotProgressFillColor(progressValue)

                                val fillAlpha = when {
                                    isSelected -> 0.65f
                                    isMatching -> 0.40f
                                    else -> 0.08f // Dimmed non-matching lot fill
                                }

                                    val strokeColor = when {
                                        isSelected -> Color(0xFFF59E0B)
                                        isMatching -> baseProgressColor
                                        else -> Color(0xFF94A3B8).copy(alpha = 0.25f)
                                    }

                                    // Fill Layer
                                    drawPath(
                                        path = path,
                                        color = if (isSelected) Color(0xFFF59E0B).copy(alpha = fillAlpha) else baseProgressColor.copy(alpha = fillAlpha)
                                    )

                                    // Normal Lot Border Stroke Layer
                                    drawPath(
                                        path = path,
                                        color = strokeColor,
                                        style = Stroke(width = if (isSelected) 5f else (if (isMatching) 3f else 1.5f))
                                    )

                                    // DISTINCT THICK BILLING BORDER (IF BILLED)
                                    if (isBilled && isMatching) {
                                        drawPath(
                                            path = path,
                                            color = Color(0xFF38BDF8),
                                            style = Stroke(
                                                width = 7.5f,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f), 0f)
                                            )
                                        )
                                    }

                                    // Render Label (Centroid)
                                    if (isMatching || scale > 1.8f) {
                                        val centroid = parsedLot.centroid
                                        val billedTag = if (isBilled) " • BILLED" else ""
                                        val labelText = "B${lot.blockNumber}-L${lot.lotNumber}\n${progressValue}%$billedTag"
                                        val textLayoutResult = textMeasurer.measure(
                                            text = labelText,
                                            style = TextStyle(
                                                color = if (isMatching) Color.White else Color(0xFF94A3B8).copy(alpha = 0.5f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        drawText(
                                            textLayoutResult = textLayoutResult,
                                            topLeft = Offset(
                                                x = centroid.x * bitmapWidth - (textLayoutResult.size.width / 2f),
                                                y = centroid.y * bitmapHeight - (textLayoutResult.size.height / 2f)
                                            )
                                        )
                                    }
                                }

                            // C. RENDER DRAFT DIGITIZATION POINTS & LINES
                            if (draftPoints.isNotEmpty()) {
                                val draftPath = Path().apply {
                                    moveTo(draftPoints[0].x * bitmapWidth, draftPoints[0].y * bitmapHeight)
                                    for (i in 1 until draftPoints.size) {
                                        lineTo(draftPoints[i].x * bitmapWidth, draftPoints[i].y * bitmapHeight)
                                    }
                                    if (interactionMode == SdpInteractionMode.DIGITIZING_LOT && draftPoints.size >= 3) {
                                        close()
                                    }
                                }

                                drawPath(
                                    path = draftPath,
                                    color = Color(0xFFF59E0B),
                                    style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f))
                                )

                                for (pt in draftPoints) {
                                    drawCircle(
                                        color = Color(0xFFF59E0B),
                                        radius = 6f,
                                        center = Offset(pt.x * bitmapWidth, pt.y * bitmapHeight)
                                    )
                                }
                            }
                        }

                        // FLOATING INTERACTIVE ZOOM CONTROLS TOOLBAR (TOP-RIGHT OVERLAY)
                        Surface(
                            color = DarkSurfaceVariant.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkBorder),
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
                                        color = if (isSelected) Color(0xFF0284C7) else DarkSurface,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else DarkBorder),
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
                                                color = if (isSelected) Color.White else DarkTextPrimary,
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
                                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }

                                // Live Percentage Readout
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
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
                                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }

                                // Recenter View / Reset Target Button
                                Surface(
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.clickable {
                                        scale = 1.00f
                                        offset = Offset.Zero
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.CenterFocusWeak, contentDescription = "Recenter View", tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RESET", fontSize = 10.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                // BOTTOM SELECTION INFO CARD (WITH PHASE 7 RECORD INSPECTION & HISTORY ACTIONS)
                if (selectedLot != null || selectedRoad != null) {
                    Surface(
                        color = DarkSurfaceVariant.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        border = BorderStroke(1.dp, DarkBorder),
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
                                    val isBilled = progressRecord?.billingStatus == "BILLED"

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HomeWork, contentDescription = "Lot", tint = getLotProgressFillColor(progressVal), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Block ${selectedLot!!.blockNumber} / Lot ${selectedLot!!.lotNumber}",
                                                style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (isBilled) Color(0xFF10B981) else DarkSurface,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, if (isBilled) Color(0xFF10B981) else DarkBorder)
                                            ) {
                                                Text(
                                                    text = if (isBilled) "● BILLED" else "NOT BILLED",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (isBilled) Color.Black else DarkTextSecondary,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Progress: ${progressVal}% (${progressRecord?.constructionStatus ?: "Not Started"}) • Area: ${selectedLot!!.lotAreaSqM} sq.m • Unit: ${selectedLot!!.housingUnitNumber.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                                        )
                                        if (progressRecord?.lastModifiedBy?.isNotBlank() == true) {
                                            Text(
                                                text = "Last Updated: ${progressRecord.lastModifiedDate} by ${progressRecord.lastModifiedBy}",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                                            )
                                        }
                                    }
                                } else if (selectedRoad != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Timeline, contentDescription = "Road", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = selectedRoad!!.roadName,
                                                style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        Text(
                                            text = "Type: ${selectedRoad!!.roadType} • Internal ID: ${selectedRoad!!.id}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    selectedLot = null
                                    selectedRoad = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                                }
                            }

                            // Action Bar inside info sheet
                            if (selectedLot != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { showProgressDialogLot = selectedLot },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text("UPDATE PROGRESS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { showRecordInspectionDialogLot = selectedLot },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
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
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = DarkTextPrimary),
                                        border = BorderStroke(1.dp, DarkBorder),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text("HISTORY & DETAILS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        UpdateLotProgressDialog(
            lot = targetLot,
            currentProgress = currentProgress,
            defaultContractor = project.contractor,
            canManageBilling = canDigitize,
            onDismiss = { showProgressDialogLot = null },
            onSubmitProgress = { progressVal: Int, status: String, activity: String, startDate: String, targetDate: String, contractor: String, remarks: String ->
                onUpdateLotProgress(
                    targetLot.id,
                    progressVal,
                    status,
                    activity,
                    startDate,
                    targetDate,
                    contractor,
                    remarks
                )
                showProgressDialogLot = null
            },
            onToggleBillingRequested = { shouldBeBilled: Boolean ->
                lotToToggleBilling = Pair(targetLot, shouldBeBilled)
            }
        )
    }

    // 4. DEVELOPER BILLING CONFIRMATION DIALOG
    if (lotToToggleBilling != null) {
        val (targetLot, markAsBilled) = lotToToggleBilling!!
        var billingRef by remember { mutableStateOf("") }
        var billingRemarks by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { lotToToggleBilling = null },
            containerColor = DarkSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Billing", tint = if (markAsBilled) Color(0xFF10B981) else StatusRedText)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (markAsBilled) "Mark Block ${targetLot.blockNumber} Lot ${targetLot.lotNumber} as BILLED?" else "Mark Block ${targetLot.blockNumber} Lot ${targetLot.lotNumber} as NOT BILLED?",
                        style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (markAsBilled)
                            "This lot will be highlighted with a distinct cyan billing border on the Interactive SDP."
                        else
                            "Developer billing border will be removed from this lot.",
                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
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
                        onUpdateLotBillingStatus(targetLot.id, markAsBilled, billingRef.trim(), billingRemarks.trim())
                        lotToToggleBilling = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (markAsBilled) Color(0xFF10B981) else StatusRedText,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (markAsBilled) "CONFIRM BILLED" else "CONFIRM UNBILL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { lotToToggleBilling = null }) {
                    Text("CANCEL", color = DarkTextSecondary)
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
        AddLotDialog(
            onDismiss = { showAddLotDialog = false },
            onSubmit = { block: String, lot: String, unit: String, area: Double, desc: String ->
                val json = normalizedToJson(draftPoints)
                onAddLot(block, lot, unit, area, json, desc)
                draftPoints.clear()
                showAddLotDialog = false
            }
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
            containerColor = DarkSurfaceVariant,
            title = { Text("Deactivate Lot Block ${lotToDeactivate!!.blockNumber} Lot ${lotToDeactivate!!.lotNumber}?", color = DarkTextPrimary) },
            text = { Text("Deactivated lots will be hidden from monitoring mode but preserved in audit logs and history.", color = DarkTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivateLot(lotToDeactivate!!)
                        selectedLot = null
                        lotToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRedText, contentColor = Color.White)
                ) { Text("Deactivate") }
            },
            dismissButton = { TextButton(onClick = { lotToDeactivate = null }) { Text("Cancel", color = DarkTextSecondary) } }
        )
    }

    // 9. DEACTIVATE ROAD CONFIRMATION
    if (roadToDeactivate != null) {
        AlertDialog(
            onDismissRequest = { roadToDeactivate = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Deactivate Road '${roadToDeactivate!!.roadName}'?", color = DarkTextPrimary) },
            text = { Text("Deactivated roads will be hidden from monitoring mode but preserved in history.", color = DarkTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivateRoad(roadToDeactivate!!)
                        selectedRoad = null
                        roadToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRedText, contentColor = Color.White)
                ) { Text("Deactivate") }
            },
            dismissButton = { TextButton(onClick = { roadToDeactivate = null }) { Text("Cancel", color = DarkTextSecondary) } }
        )
    }

    // 10. UPLOAD SDP DIALOG
    if (showUploadDialog) {
        AddSdpPlanDialog(
            currentVersion = sdpPlans.size + 1,
            onDismiss = { showUploadDialog = false },
            onSubmit = { planName: String, pdfUriString: String, desc: String ->
                onUploadSdpPlan(planName, pdfUriString, desc)
                showUploadDialog = false
            }
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
    var billingStatus by remember { mutableStateOf(currentProgress?.billingStatus ?: "NOT BILLED") }
    var billingRef by remember { mutableStateOf(currentProgress?.billingReference ?: "") }

    val intProgress = progressSlider.toInt().coerceIn(0, 100)
    val derivedColor = getLotProgressFillColor(intProgress)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, contentDescription = "Inspection", tint = Color(0xFF10B981))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Record Inspection for Block ${lot.blockNumber} Lot ${lot.lotNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
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
                Text(
                    text = "This will capture an immutable snapshot of the lot's condition for inspection history and velocity analytics.",
                    style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                )

                // Physical Progress Slider
                Surface(
                    color = DarkSurface,
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
                            Text("Inspected Progress %", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                            Surface(color = derivedColor, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "$intProgress%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Slider(
                            value = progressSlider,
                            onValueChange = {
                                progressSlider = it
                                constructionStatus = when (it.toInt()) {
                                    0 -> "Not Started"
                                    in 1..20 -> "Layout / Excavation Started"
                                    in 21..80 -> "Under Construction"
                                    in 81..99 -> "Substantially Completed"
                                    else -> "Completed"
                                }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = derivedColor, activeTrackColor = derivedColor, inactiveTrackColor = DarkBorder)
                        )
                    }
                }

                OutlinedTextField(value = constructionStatus, onValueChange = { constructionStatus = it }, label = { Text("Construction Status") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = currentActivity, onValueChange = { currentActivity = it }, label = { Text("Current Activity") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contractor, onValueChange = { contractor = it }, label = { Text("Contractor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Inspection Field Remarks") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitInspection(
                        intProgress,
                        constructionStatus,
                        currentActivity,
                        contractor,
                        remarks,
                        billingStatus,
                        billingRef
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
            ) {
                Text("SAVE SNAPSHOT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = DarkTextSecondary) }
        }
    )
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
    val isBilled = currentProgress?.billingStatus == "BILLED"

    val inspectionsList by onGetInspections().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lot B${lot.blockNumber}-L${lot.lotNumber} Metadata & History",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
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
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, derivedColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CURRENT LOT STATUS", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Physical Progress", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                            Text("${progressVal}% (${currentProgress?.constructionStatus ?: "Not Started"})", style = MaterialTheme.typography.bodySmall.copy(color = derivedColor, fontWeight = FontWeight.Bold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Developer Billing", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                            Text(if (isBilled) "● BILLED" else "NOT BILLED", style = MaterialTheme.typography.bodySmall.copy(color = if (isBilled) Color(0xFF10B981) else DarkTextSecondary, fontWeight = FontWeight.Bold))
                        }
                        Text("Block Number: ${lot.blockNumber} • Lot Number: ${lot.lotNumber}", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                        Text("Housing Unit: ${lot.housingUnitNumber.ifBlank { "N/A" }} • Area: ${lot.lotAreaSqM} sq.m", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                        Text("SDP Plan Version: v$sdpVersion • Internal ID: ${lot.id}", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                    }
                }

                // PHASE 7 HISTORICAL TIMELINE SECTION
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INSPECTION HISTORY & TIMELINE", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))

                            if (canEdit) {
                                Button(
                                    onClick = onRecordInspectionRequested,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
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
                                    Icon(Icons.Default.History, contentDescription = "No History", tint = DarkTextSecondary, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("NO INSPECTION HISTORY", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                                    Text(if (canEdit) "Tap RECORD SNAPSHOT above to log an official inspection point." else "No inspection snapshots recorded yet.", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary), fontSize = 10.sp)
                                }
                            }
                        } else {
                            // Timeline progression string
                            val chronological = inspectionsList.reversed()
                            val timelineStr = chronological.joinToString(" → ") { "${it.physicalProgress}%" }

                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Timeline: $timelineStr",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
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
                                    color = DarkSurfaceVariant,
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
                                                    style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = inspection.billingStatus,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (inspection.billingStatus == "BILLED") Color(0xFF10B981) else DarkTextSecondary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "${inspection.inspectionDate} by ${inspection.inspectedBy}$changeText",
                                                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp)
                                            )
                                            if (inspection.remarks.isNotBlank()) {
                                                Text(
                                                    text = "Remarks: ${inspection.remarks}",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontSize = 10.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "Audit Trail", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AUDIT TRAIL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                if (canEdit) {
                    Button(
                        onClick = onEditGeometry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black)
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
                Text("CLOSE", color = DarkTextSecondary)
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
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = "Audit", tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Audit History — Lot B${lot.blockNumber}-L${lot.lotNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
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
                    text = "Read-only system audit log entries for Block ${lot.blockNumber} Lot ${lot.lotNumber}.",
                    style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                )

                val lotAuditLogs = remember(auditLogs, lot.id) {
                    auditLogs.filter { log ->
                        log.details.contains("Lot ID: ${lot.id}") ||
                        log.details.contains("Lot ID ${lot.id}") ||
                        log.details.contains("Lot B${lot.blockNumber}-L${lot.lotNumber}")
                    }.sortedByDescending { it.timestamp }
                }

                if (lotAuditLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No audit log records found for this lot.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                    }
                } else {
                    lotAuditLogs.forEach { log ->
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.actionType, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(log.timestamp))
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp))
                                }
                                Text("By: ${log.user} (${log.device})", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp))
                                Text(log.details, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                                if (log.oldValue.isNotBlank() || log.newValue.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (log.oldValue.isNotBlank()) {
                                        Text("Previous: ${log.oldValue}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEF4444), fontSize = 10.sp))
                                    }
                                    if (log.newValue.isNotBlank()) {
                                        Text("New: ${log.newValue}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontSize = 10.sp))
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
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
    onSubmit: (block: String, lot: String, unit: String, area: Double, desc: String) -> Unit
) {
    var blockNumber by remember { mutableStateOf("") }
    var lotNumber by remember { mutableStateOf("") }
    var unitNumber by remember { mutableStateOf("") }
    var areaSqM by remember { mutableStateOf("50.0") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text("Save Digitized Lot Geometry", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)) },
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
            ) { Text("Save Lot", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DarkTextSecondary) } }
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
        containerColor = DarkSurfaceVariant,
        title = { Text("Save Digitized Road Geometry", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)) },
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
            ) { Text("Save Road", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DarkTextSecondary) } }
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
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            pdfFileName = uri.lastPathSegment ?: "Approved_SDP.pdf"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "SDP", tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload Approved SDP (Version $currentVersion)",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
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
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = DarkTextSecondary
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Remarks") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = DarkTextSecondary
                    )
                )

                // Select PDF File Button
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selectedPdfUri != null) Color(0xFF38BDF8) else DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pdfPickerLauncher.launch("application/pdf") }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selectedPdfUri != null) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                            contentDescription = "PDF",
                            tint = if (selectedPdfUri != null) Color(0xFF38BDF8) else DarkTextSecondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedPdfUri != null) "PDF Selected" else "Select Approved SDP PDF",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                            )
                            if (pdfFileName.isNotBlank()) {
                                Text(
                                    text = pdfFileName,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8)),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalUri = selectedPdfUri?.toString() ?: ""
                    if (finalUri.isNotBlank()) {
                        onSubmit(planName, finalUri, description)
                    }
                },
                enabled = selectedPdfUri != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
            ) {
                Text("Save SDP Plan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
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
        color = DarkSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = DarkTextSecondary))
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
        color = if (isSelected) Color(0xFFF59E0B).copy(alpha = 0.2f) else DarkSurfaceVariant,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else DarkBorder),
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
                    color = if (isSelected) Color(0xFFF59E0B) else DarkTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = DarkTextSecondary, modifier = Modifier.size(12.dp))
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
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
    }
}

// --- PROGRESS & BILLING LEGEND DIALOG ---
@Composable
fun SdpLegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = "Legend", tint = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SDP Color & Billing Legend", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONSTRUCTION PHYSICAL PROGRESS (LOT FILL):", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))

                LegendRow(color = Color(0xFFEF4444), label = "0% / Layout / Excavation Started")
                LegendRow(color = Color(0xFFF97316), label = "1 – 20% Physical Progress")
                LegendRow(color = Color(0xFFEAB308), label = "21 – 40% Physical Progress")
                LegendRow(color = Color(0xFF22C55E), label = "41 – 60% Physical Progress")
                LegendRow(color = Color(0xFFEC4899), label = "61 – 80% Physical Progress (Pink)")
                LegendRow(color = Color(0xFF3B82F6), label = "81 – 99% Physical Progress")
                LegendRow(color = Color(0xFF10B981), label = "100% Completed")

                Spacer(modifier = Modifier.height(6.dp))
                Text("DEVELOPER BILLING STATUS (LOT BORDER):", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.Transparent)
                            .border(BorderStroke(3.dp, Color(0xFF38BDF8)), shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("BILLED = Thick Cyan Dashed Outer Border", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)) {
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
        remarks: String
    ) -> Unit,
    onToggleBillingRequested: (shouldBeBilled: Boolean) -> Unit
) {
    var progressSlider by remember { mutableFloatStateOf(currentProgress?.physicalProgress?.toFloat() ?: 0f) }
    var constructionStatus by remember { mutableStateOf(currentProgress?.constructionStatus ?: "Not Started") }
    var currentActivity by remember { mutableStateOf(currentProgress?.currentActivity ?: "") }
    var startDate by remember { mutableStateOf(currentProgress?.startDate ?: "") }
    var targetCompletionDate by remember { mutableStateOf(currentProgress?.targetCompletionDate ?: "") }
    var contractor by remember { mutableStateOf(currentProgress?.contractor?.ifBlank { defaultContractor } ?: defaultContractor) }
    var remarks by remember { mutableStateOf(currentProgress?.remarks ?: "") }

    val intProgress = progressSlider.toInt().coerceIn(0, 100)
    val derivedColor = getLotProgressFillColor(intProgress)
    val isBilled = currentProgress?.billingStatus == "BILLED"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HomeWork, contentDescription = "Lot", tint = derivedColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lot B${lot.blockNumber}-L${lot.lotNumber} Construction & Billing",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
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
                // Physical Progress Slider & Visual Color Preview
                Surface(
                    color = DarkSurface,
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
                            Text("Physical Progress %", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                            Surface(
                                color = derivedColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "$intProgress%",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Black, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Slider(
                            value = progressSlider,
                            onValueChange = {
                                progressSlider = it
                                constructionStatus = when (it.toInt()) {
                                    0 -> "Not Started"
                                    in 1..20 -> "Layout / Excavation Started"
                                    in 21..80 -> "Under Construction"
                                    in 81..99 -> "Substantially Completed"
                                    else -> "Completed"
                                }
                            },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = derivedColor,
                                activeTrackColor = derivedColor,
                                inactiveTrackColor = DarkBorder
                            )
                        )
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

                // DEVELOPER BILLING CONTROL SECTION
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isBilled) Color(0xFF10B981) else DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DEVELOPER BILLING STATUS", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                Text(
                                    text = if (isBilled) "● BILLED" else "NOT BILLED",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isBilled) Color(0xFF10B981) else DarkTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            if (canManageBilling) {
                                Button(
                                    onClick = { onToggleBillingRequested(!isBilled) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isBilled) StatusRedText else Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isBilled) "MARK AS NOT BILLED" else "MARK AS BILLED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isBilled) {
                            Text(
                                text = "Billed Date: ${currentProgress?.billingDate ?: "N/A"} • By: ${currentProgress?.billedBy ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                            )
                            if (currentProgress?.billingReference?.isNotBlank() == true) {
                                Text(
                                    text = "Ref No: ${currentProgress.billingReference}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitProgress(
                        intProgress,
                        constructionStatus,
                        currentActivity,
                        startDate,
                        targetCompletionDate,
                        contractor,
                        remarks
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = derivedColor, contentColor = Color.Black)
            ) {
                Text("SAVE PROGRESS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = DarkTextSecondary)
            }
        }
    )
}

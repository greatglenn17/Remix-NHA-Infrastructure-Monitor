package com.example.data.remote

import com.example.data.model.Project
import org.json.JSONArray
import org.json.JSONObject

/**
 * The project's own fields in its cloud document, without the sync fields (uuid, revision, times, editors) and without
 * the bar chart, which only its editors send (see FirestoreSyncManager.flushOutbox).
 */
fun projectFields(p: Project): Map<String, Any> = mapOf(
    "name" to p.name,
    "location" to p.location,
    "implementingOffice" to p.implementingOffice,
    "contractor" to p.contractor,
    "scopeOfWork" to p.scopeOfWork,
    "projectType" to p.projectType,
    "landArea" to p.landArea,
    "numberOfUnits" to p.numberOfUnits,
    "projectedBeneficiaries" to p.projectedBeneficiaries,
    "boardResolution" to p.boardResolution,
    "noticeOfAward" to p.noticeOfAward,
    "noticeToProceed" to p.noticeToProceed,
    "preparedByName" to p.preparedByName,
    "preparedByPosition" to p.preparedByPosition,
    "checkedByName" to p.checkedByName,
    "checkedByPosition" to p.checkedByPosition,
    "notedByName" to p.notedByName,
    "notedByPosition" to p.notedByPosition,
    "weeklyCheckedByName" to p.weeklyCheckedByName,
    "weeklyCheckedByPosition" to p.weeklyCheckedByPosition,
    "weeklyNotedByName" to p.weeklyNotedByName,
    "weeklyNotedByPosition" to p.weeklyNotedByPosition,
    "preparedByOffice" to p.preparedByOffice,
    "checkedByOffice" to p.checkedByOffice,
    "notedByOffice" to p.notedByOffice,
    "weeklyCheckedByOffice" to p.weeklyCheckedByOffice,
    "weeklyNotedByOffice" to p.weeklyNotedByOffice,
    "billingPreparedByName" to p.billingPreparedByName,
    "billingPreparedByPosition" to p.billingPreparedByPosition,
    "billingPreparedByOffice" to p.billingPreparedByOffice,
    "billingCheckedByName" to p.billingCheckedByName,
    "billingCheckedByPosition" to p.billingCheckedByPosition,
    "billingCheckedByOffice" to p.billingCheckedByOffice,
    "billingReviewedByName" to p.billingReviewedByName,
    "billingReviewedByPosition" to p.billingReviewedByPosition,
    "billingReviewedByOffice" to p.billingReviewedByOffice,
    "billingRecommendedByName" to p.billingRecommendedByName,
    "billingRecommendedByPosition" to p.billingRecommendedByPosition,
    "billingRecommendedByOffice" to p.billingRecommendedByOffice,
    "contractCostOriginal" to p.contractCostOriginal,
    "contractCostRevised" to p.contractCostRevised,
    "contractDurationDays" to p.contractDurationDays,
    "dateStarted" to p.dateStarted,
    "completionDateOriginal" to p.completionDateOriginal,
    "completionDateRevised" to p.completionDateRevised,
    "status" to p.status,
    "targetAccomplishment" to p.targetAccomplishment,
    "actualAccomplishment" to p.actualAccomplishment,
    "assignedStaff" to p.assignedStaff,
    "latestUpdatePhotoUrl" to p.latestUpdatePhotoUrl
)

/** The fields of [projectFields] that [new] changed from [old]. */
fun changedProjectFields(old: Project?, new: Project): List<String> {
    val after = projectFields(new)
    val before = old?.let(::projectFields) ?: return after.keys.toList()
    return after.keys.filter { after[it] != before[it] }
}

/**
 * The outbox payload of a project edit. [fields] names what it changed, so the upload sends only those: a phone's
 * older copy of the rest never replaces another phone's newer edit. A bar chart save also says so (see
 * SyncOutboxDao.hasPendingBarChartFor).
 */
fun projectEditPayload(projectUuid: String, fields: Collection<String>, barChart: Boolean = false): String =
    JSONObject().put("projectUuid", projectUuid).apply { if (barChart) put("barChart", true) }
        .put("fields", JSONArray(fields)).toString()

/** The fields an outbox event sends, or null for the whole document (a new project, or an event from before fields). */
fun fieldsToSend(operation: String, payloadJson: String): Set<String>? {
    if (operation != "UPDATE") return null
    val fields = runCatching { JSONObject(payloadJson).optJSONArray("fields") }.getOrNull() ?: return null
    return (0 until fields.length()).map { fields.getString(it) }.toSet()
}

/** [this] with the fields a newer cloud document has; a field the document lacks keeps the local value. */
fun Project.withRemote(doc: Map<String, Any?>): Project {
    fun s(key: String, local: String) = doc[key] as? String ?: local
    fun d(key: String, local: Double) = (doc[key] as? Number)?.toDouble() ?: local
    return copy(
        name = s("name", name),
        location = s("location", location),
        implementingOffice = s("implementingOffice", implementingOffice),
        contractor = s("contractor", contractor),
        scopeOfWork = s("scopeOfWork", scopeOfWork),
        projectedBeneficiaries = s("projectedBeneficiaries", projectedBeneficiaries),
        boardResolution = s("boardResolution", boardResolution),
        noticeOfAward = s("noticeOfAward", noticeOfAward),
        noticeToProceed = s("noticeToProceed", noticeToProceed),
        preparedByName = s("preparedByName", preparedByName),
        preparedByPosition = s("preparedByPosition", preparedByPosition),
        checkedByName = s("checkedByName", checkedByName),
        checkedByPosition = s("checkedByPosition", checkedByPosition),
        notedByName = s("notedByName", notedByName),
        notedByPosition = s("notedByPosition", notedByPosition),
        weeklyCheckedByName = s("weeklyCheckedByName", weeklyCheckedByName),
        weeklyCheckedByPosition = s("weeklyCheckedByPosition", weeklyCheckedByPosition),
        weeklyNotedByName = s("weeklyNotedByName", weeklyNotedByName),
        weeklyNotedByPosition = s("weeklyNotedByPosition", weeklyNotedByPosition),
        preparedByOffice = s("preparedByOffice", preparedByOffice),
        checkedByOffice = s("checkedByOffice", checkedByOffice),
        notedByOffice = s("notedByOffice", notedByOffice),
        weeklyCheckedByOffice = s("weeklyCheckedByOffice", weeklyCheckedByOffice),
        weeklyNotedByOffice = s("weeklyNotedByOffice", weeklyNotedByOffice),
        billingPreparedByName = s("billingPreparedByName", billingPreparedByName),
        billingPreparedByPosition = s("billingPreparedByPosition", billingPreparedByPosition),
        billingPreparedByOffice = s("billingPreparedByOffice", billingPreparedByOffice),
        billingCheckedByName = s("billingCheckedByName", billingCheckedByName),
        billingCheckedByPosition = s("billingCheckedByPosition", billingCheckedByPosition),
        billingCheckedByOffice = s("billingCheckedByOffice", billingCheckedByOffice),
        billingReviewedByName = s("billingReviewedByName", billingReviewedByName),
        billingReviewedByPosition = s("billingReviewedByPosition", billingReviewedByPosition),
        billingReviewedByOffice = s("billingReviewedByOffice", billingReviewedByOffice),
        billingRecommendedByName = s("billingRecommendedByName", billingRecommendedByName),
        billingRecommendedByPosition = s("billingRecommendedByPosition", billingRecommendedByPosition),
        billingRecommendedByOffice = s("billingRecommendedByOffice", billingRecommendedByOffice),
        barChartJson = s("barChartJson", barChartJson),
        projectType = s("projectType", projectType),
        landArea = s("landArea", landArea),
        numberOfUnits = s("numberOfUnits", numberOfUnits),
        contractCostOriginal = d("contractCostOriginal", contractCostOriginal),
        contractCostRevised = d("contractCostRevised", contractCostRevised),
        contractDurationDays = (doc["contractDurationDays"] as? Number)?.toInt() ?: contractDurationDays,
        dateStarted = s("dateStarted", dateStarted),
        completionDateOriginal = s("completionDateOriginal", completionDateOriginal),
        completionDateRevised = s("completionDateRevised", completionDateRevised),
        status = s("status", status),
        targetAccomplishment = d("targetAccomplishment", targetAccomplishment),
        actualAccomplishment = d("actualAccomplishment", actualAccomplishment),
        assignedStaff = s("assignedStaff", assignedStaff),
        latestUpdatePhotoUrl = s("latestUpdatePhotoUrl", latestUpdatePhotoUrl),
        updatedByUid = s("updatedByUid", updatedByUid),
        isDeleted = false
    )
}

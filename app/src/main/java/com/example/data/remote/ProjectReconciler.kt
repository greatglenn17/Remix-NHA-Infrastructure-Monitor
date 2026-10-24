package com.example.data.remote

/**
 * Decides how local Room projects and Firestore project documents line up.
 *
 * The same real project can carry different projectUuids on different phones (the
 * v21 migration and older Drive restores generated random ones), so matching by uuid
 * alone inserts a second copy. Projects are matched by uuid first, then by
 * normalized name + location, and every phone settles on the same canonical cloud
 * document: the oldest one for that name + location.
 */
object ProjectReconciler {

    data class LocalRef(val id: Long, val uuid: String, val key: String)
    data class RemoteRef(val uuid: String, val key: String, val createdAt: Long, val isDeleted: Boolean)

    data class Plan(
        /** Local row id -> canonical cloud uuid it should take over. */
        val adoptUuid: Map<Long, String>,
        /** Cloud uuids with no local row yet. */
        val insertRemote: Set<String>,
        /** Local uuids that are not in the cloud yet. */
        val uploadLocal: Set<String>,
        /** Local row ids whose cloud document was deleted. */
        val deleteLocal: Set<Long>
    )

    fun key(name: String, location: String): String = "${name.normalized()}|${location.normalized()}"

    fun plan(local: List<LocalRef>, remote: List<RemoteRef>): Plan {
        val live = remote.filter { !it.isDeleted }
        val canonicalByKey = live.groupBy { it.key }
            .mapValues { (_, docs) -> docs.minWith(compareBy<RemoteRef>({ it.createdAt }, { it.uuid })) }
        val liveByUuid = live.associateBy { it.uuid }
        val deletedUuids = remote.filter { it.isDeleted }.map { it.uuid }.toSet() - liveByUuid.keys

        val adopt = mutableMapOf<Long, String>()
        val upload = mutableSetOf<String>()
        val delete = mutableSetOf<Long>()
        val claimed = mutableSetOf<String>()
        val pending = mutableListOf<LocalRef>()

        // Pass 1: exact uuid matches on canonical docs claim them first, so a later
        // adoption can never hand the same uuid to two local rows.
        for (row in local) {
            val doc = liveByUuid[row.uuid]
            when {
                row.uuid in deletedUuids -> delete += row.id
                doc != null && canonicalByKey[doc.key]?.uuid == row.uuid -> claimed += row.uuid
                else -> pending += row
            }
        }

        // Pass 2: match the rest by the cloud doc they belong to, or by name + location.
        for (row in pending) {
            val viaUuid = liveByUuid[row.uuid]?.let { canonicalByKey[it.key] }
            val canonical = viaUuid ?: canonicalByKey[row.key]
            when {
                canonical == null -> upload += row.uuid
                canonical.uuid in claimed -> Unit // another local row owns it; merged by localDuplicates
                else -> {
                    adopt[row.id] = canonical.uuid
                    claimed += canonical.uuid
                }
            }
        }

        val insert = canonicalByKey.values.map { it.uuid }.filterNot { it in claimed }.toSet()
        return Plan(adopt, insert, upload, delete)
    }

    /** Local rows describing the same project: duplicate id -> id of the row to keep (the oldest). */
    fun localDuplicates(local: List<LocalRef>): Map<Long, Long> =
        local.groupBy { it.key }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keep = group.minOf { it.id }
                group.filter { it.id != keep }.map { it.id to keep }
            }
            .toMap()

    private fun String.normalized(): String =
        trim().lowercase().replace(' ', ' ').replace(Regex("\\s+"), " ")
}

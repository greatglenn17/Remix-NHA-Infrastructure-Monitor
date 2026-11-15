package com.example.data.remote

/**
 * Decides how local Room projects and Firestore project documents line up.
 *
 * The same real project can carry different projectUuids on different phones (the
 * v21 migration and older Drive restores generated random ones), so matching by uuid
 * alone inserts a second copy. Projects are matched by uuid first. Only a local project
 * with no cloud document of its own is then matched by normalized name + location, to the
 * oldest cloud document of that name.
 *
 * A project whose own document is live never moves to another one. Whoever may create a
 * project can upload one with any name, location and createdAt, so a name match must not
 * re-home a project: its files and records would be uploaded again under the other document,
 * for that document's assigned accounts to read.
 */
object ProjectReconciler {

    data class LocalRef(val id: Long, val uuid: String, val key: String)
    /**
     * [serverCreatedAt] is the document's creation time on the server (functions: guardProjectDocument), which no
     * client can set; a document the server has not stamped yet is brand new, so it counts as the newest.
     * [createdAt] is the creator's own date and only orders documents the server's time does not.
     */
    data class RemoteRef(
        val uuid: String,
        val key: String,
        val createdAt: Long,
        val isDeleted: Boolean,
        val serverCreatedAt: Long = Long.MAX_VALUE
    )

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

    /** updatedByUid of the server's duplicate guard (functions: duplicateGuard.js); no client can write it. */
    const val GUARD_UID = "duplicate-guard"

    fun key(name: String, location: String): String = "${name.normalized()}|${location.normalized()}"

    fun plan(local: List<LocalRef>, remote: List<RemoteRef>): Plan {
        val live = remote.filter { !it.isDeleted }
        val canonicalByKey = live.groupBy { it.key }
            .mapValues { (_, docs) -> docs.minWith(compareBy<RemoteRef>({ it.serverCreatedAt }, { it.createdAt }, { it.uuid })) }
        val liveByUuid = live.associateBy { it.uuid }
        val deletedUuids = remote.filter { it.isDeleted }.map { it.uuid }.toSet() - liveByUuid.keys

        val adopt = mutableMapOf<Long, String>()
        val upload = mutableSetOf<String>()
        val delete = mutableSetOf<Long>()
        /** Names (name + location) of the cloud documents a local row has: no second row is made for one. */
        val held = mutableSetOf<String>()
        val pending = mutableListOf<LocalRef>()

        // Pass 1: a row whose own document is live keeps it, whatever else carries its name. These
        // go first, so a later adoption can never hand a second row a project this phone already has.
        for (row in local) {
            val doc = liveByUuid[row.uuid]
            when {
                row.uuid in deletedUuids -> delete += row.id
                doc != null -> held += doc.key
                else -> pending += row
            }
        }

        // Pass 2: a row with no cloud document of its own is matched by name + location.
        for (row in pending) {
            val canonical = canonicalByKey[row.key]
            when {
                canonical == null -> upload += row.uuid
                row.key in held -> Unit // another local row has that project; merged by localDuplicates
                else -> {
                    adopt[row.id] = canonical.uuid
                    held += row.key
                }
            }
        }

        val insert = canonicalByKey.filterKeys { it !in held }.values.map { it.uuid }.toSet()
        return Plan(adopt, insert, upload, delete)
    }

    /**
     * Local rows describing the same project: duplicate id -> id of the row to keep. Only a row with no live cloud
     * document of its own ([liveUuids]) is merged away, into the oldest row that has one, else into the oldest row.
     * Two projects that are both in the cloud stay apart even under one name: anyone assigned to a project can rename it.
     */
    fun localDuplicates(local: List<LocalRef>, liveUuids: Set<String>): Map<Long, Long> =
        local.groupBy { it.key }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                val keep = group.filter { it.uuid in liveUuids }.ifEmpty { group }.minOf { it.id }
                group.filter { it.id != keep && it.uuid !in liveUuids }.map { it.id to keep }
            }
            .toMap()

    private fun String.normalized(): String =
        trim().lowercase().replace(' ', ' ').replace(Regex("\\s+"), " ")
}

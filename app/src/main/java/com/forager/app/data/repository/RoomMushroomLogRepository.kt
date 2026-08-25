package com.forager.app.data.repository

import com.forager.app.data.local.LogEntryPhotoCrossRef
import com.forager.app.data.local.LogPhotoEntity
import com.forager.app.data.local.MushroomLogDao
import com.forager.app.data.local.MushroomLogEntryEntity
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.model.AnnulusType
import com.forager.app.domain.model.Association
import com.forager.app.domain.model.CapDecoration
import com.forager.app.domain.model.CapMargin
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.CapShape
import com.forager.app.domain.model.CapSurface
import com.forager.app.domain.model.ContextFleshSection
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.FleshTexture
import com.forager.app.domain.model.ForestType
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.GillAttachment
import com.forager.app.domain.model.GillEdge
import com.forager.app.domain.model.GillSpacing
import com.forager.app.domain.model.HostHealth
import com.forager.app.domain.model.HostSubstrateSection
import com.forager.app.domain.model.HymenophoreDetails
import com.forager.app.domain.model.HymenophoreSection
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.LogSyncState
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.domain.model.SporePrint
import com.forager.app.domain.model.SporePrintColor
import com.forager.app.domain.model.SporePrintSection
import com.forager.app.domain.model.StipeBase
import com.forager.app.domain.model.StipeDetails
import com.forager.app.domain.model.StipeInterior
import com.forager.app.domain.model.StipePosition
import com.forager.app.domain.model.StipeSection
import com.forager.app.domain.model.VeilSection
import com.forager.app.domain.model.VolvaType
import java.time.Instant
import java.time.LocalDate

/**
 * Room-backed [MushroomLogRepository]; the only place [MushroomLogEntryEntity]/[LogPhotoEntity]
 * and [MushroomLogEntry] meet. See [MushroomLogEntryEntity]'s doc comment for how
 * [Observed]/[Feature] fields map onto columns — the `toEntity`/`toDomain` functions below are
 * where that encoding is actually applied and where the "which sub-fields apply to which sealed
 * variant" rule is enforced.
 */
class RoomMushroomLogRepository(
    private val dao: MushroomLogDao,
) : MushroomLogRepository {

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = runCatchingCancellable {
        val photosById = dao.getAllPhotos().associateBy { it.id }
        val photoIdsByEntry = dao.getAllCrossRefs().groupBy({ it.entryId }) { it.photoId }
        dao.getAllEntries().map { entity ->
            val photos = photoIdsByEntry[entity.id].orEmpty().mapNotNull { photosById[it] }
            entity.toDomain(photos)
        }
    }

    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = runCatchingCancellable {
        val entryIdsByPhoto = dao.getAllCrossRefs().groupBy({ it.photoId }) { it.entryId }
        dao.getAllPhotos().map { entity ->
            GalleryPhoto(
                photo = LogPhoto(id = entity.id, relativePath = entity.relativePath, createdAtEpochMillis = entity.createdAtEpochMillis),
                referencingEntryIds = entryIdsByPhoto[entity.id].orEmpty(),
            )
        }
    }

    // Never touches log_photos/log_entry_photos — see MushroomLogRepository.save's own doc comment.
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = runCatchingCancellable {
        dao.upsertEntry(entry.toEntity())
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingCancellable {
        dao.deleteEntryAndCrossRefs(id)
    }

    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = runCatchingCancellable {
        dao.insertPhoto(photo.toEntity())
    }

    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = runCatchingCancellable {
        dao.insertCrossRef(LogEntryPhotoCrossRef(entryId = entryId, photoId = photoId))
    }

    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = runCatchingCancellable {
        dao.deleteCrossRef(entryId, photoId)
    }

    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> = runCatchingCancellable {
        dao.deletePhotoAndCrossRefs(photoId)
    }
}

// --- Feature<T> <-> (state, value) column pair -------------------------------------------------

private fun Feature<*>.stateColumn(): String = when (this) {
    is Feature.Present<*> -> "PRESENT"
    Feature.Absent -> "ABSENT"
    Feature.NotObserved -> "NOT_OBSERVED"
}

private fun <E : Enum<E>> Feature<E>.enumValueColumn(): String? = (this as? Feature.Present<E>)?.value?.name

private inline fun <reified E : Enum<E>> featureEnumOf(state: String, value: String?): Feature<E> = when (state) {
    "PRESENT" -> Feature.Present(enumValueOf<E>(value ?: error("Feature state PRESENT stored with no value")))
    "ABSENT" -> Feature.Absent
    "NOT_OBSERVED" -> Feature.NotObserved
    else -> error("Unknown Feature state '$state'")
}

private fun Feature<String>.textValueColumn(): String? = (this as? Feature.Present<String>)?.value

private fun featureTextOf(state: String, value: String?): Feature<String> = when (state) {
    "PRESENT" -> Feature.Present(value ?: error("Feature state PRESENT stored with no value"))
    "ABSENT" -> Feature.Absent
    "NOT_OBSERVED" -> Feature.NotObserved
    else -> error("Unknown Feature state '$state'")
}

private fun Feature<Set<CapDecoration>>.decorationsValueColumn(): String? =
    (this as? Feature.Present<Set<CapDecoration>>)?.value?.joinToString(",") { it.name }

private fun featureDecorationsOf(state: String, value: String?): Feature<Set<CapDecoration>> = when (state) {
    "PRESENT" -> Feature.Present(
        (value ?: error("Feature state PRESENT stored with no value"))
            .split(",")
            .filter { it.isNotEmpty() }
            .map { CapDecoration.valueOf(it) }
            .toSet(),
    )
    "ABSENT" -> Feature.Absent
    "NOT_OBSERVED" -> Feature.NotObserved
    else -> error("Unknown Feature state '$state'")
}

// --- Observed<Enum> <-> one nullable column -----------------------------------------------------

private fun <E : Enum<E>> Observed<E>.toColumn(): String? = (this as? Observed.Recorded<E>)?.value?.name

private inline fun <reified E : Enum<E>> String?.toObservedEnum(): Observed<E> =
    this?.let { Observed.Recorded(enumValueOf<E>(it)) } ?: Observed.NotObserved

// --- MushroomLogEntry -> MushroomLogEntryEntity --------------------------------------------------

private fun MushroomLogEntry.toEntity(): MushroomLogEntryEntity {
    val hymenophoreDetails = hymenophore.details
    val gills = (hymenophoreDetails as? Observed.Recorded<HymenophoreDetails>)
        ?.value as? HymenophoreDetails.Gills

    val stipeDetails = stipe.details
    val stipePresent = (stipeDetails as? Observed.Recorded<StipeDetails>)?.value as? StipeDetails.Present

    val association = (hostSubstrate.association as? Observed.Recorded<Association>)?.value

    val recordedSporePrint = (sporePrint.details as? Observed.Recorded<SporePrint>)?.value

    return MushroomLogEntryEntity(
        id = id,
        lat = foundAt?.lat,
        lng = foundAt?.lng,
        foundOn = foundOn.toString(),
        entryNotes = notes,
        ownIdentification = ownIdentification,

        syncStateKind = when (syncState) {
            is LogSyncState.Draft -> "DRAFT"
            is LogSyncState.Uploading -> "UPLOADING"
            is LogSyncState.Uploaded -> "UPLOADED"
            is LogSyncState.Failed -> "FAILED"
        },
        syncProgress = (syncState as? LogSyncState.Uploading)?.progress,
        syncRemoteObservationId = when (val state = syncState) {
            is LogSyncState.Uploaded -> state.remoteObservationId
            is LogSyncState.Failed -> state.remoteObservationId
            else -> null
        },
        syncUploadedAtEpochMillis = (syncState as? LogSyncState.Uploaded)?.uploadedAt?.toEpochMilli(),
        syncFailureReason = (syncState as? LogSyncState.Failed)?.reason,

        capShape = cap.shape.toColumn(),
        capSurface = cap.surface.toColumn(),
        capDecorationsState = cap.decorations.stateColumn(),
        capDecorationsValue = cap.decorations.decorationsValueColumn(),
        capMargin = cap.margin.toColumn(),
        capNotes = cap.notes,

        hymenophoreKind = when (hymenophoreDetails) {
            is Observed.Recorded -> when (hymenophoreDetails.value) {
                is HymenophoreDetails.Gills -> "GILLS"
                HymenophoreDetails.Pores -> "PORES"
                HymenophoreDetails.Teeth -> "TEETH"
                HymenophoreDetails.SmoothOrWrinkled -> "SMOOTH_OR_WRINKLED"
            }
            Observed.NotObserved -> null
        },
        gillAttachment = gills?.attachment?.toColumn(),
        gillSpacing = gills?.spacing?.toColumn(),
        gillEdge = gills?.edge?.toColumn(),
        hymenophoreNotes = hymenophore.notes,

        stipeKind = when (stipeDetails) {
            is Observed.Recorded -> when (stipeDetails.value) {
                StipeDetails.Absent -> "ABSENT"
                is StipeDetails.Present -> "PRESENT"
            }
            Observed.NotObserved -> null
        },
        stipePosition = stipePresent?.position?.toColumn(),
        stipeInterior = stipePresent?.interior?.toColumn(),
        stipeBase = stipePresent?.base?.toColumn(),
        stipeNotes = stipe.notes,

        annulusState = veil.annulus.stateColumn(),
        annulusValue = veil.annulus.enumValueColumn(),
        volvaState = veil.volva.stateColumn(),
        volvaValue = veil.volva.enumValueColumn(),
        veilNotes = veil.notes,

        fleshTexture = contextFlesh.texture.toColumn(),
        colorChangeState = contextFlesh.colorChangeOnCutting.stateColumn(),
        colorChangeValue = contextFlesh.colorChangeOnCutting.textValueColumn(),
        exudateState = contextFlesh.exudate.stateColumn(),
        exudateValue = contextFlesh.exudate.textValueColumn(),
        contextFleshNotes = contextFlesh.notes,

        sporePrintColorKind = when (recordedSporePrint?.color) {
            null -> null
            SporePrintColor.White -> "WHITE"
            SporePrintColor.Cream -> "CREAM"
            SporePrintColor.PinkSalmon -> "PINK_SALMON"
            SporePrintColor.Ochre -> "OCHRE"
            SporePrintColor.Rust -> "RUST"
            SporePrintColor.ChocolateBrown -> "CHOCOLATE_BROWN"
            SporePrintColor.PurpleBrown -> "PURPLE_BROWN"
            SporePrintColor.Black -> "BLACK"
            is SporePrintColor.Other -> "OTHER"
        },
        sporePrintOtherText = (recordedSporePrint?.color as? SporePrintColor.Other)?.text,
        sporePrintReadOn = recordedSporePrint?.readOn?.toString(),
        sporePrintNotes = sporePrint.notes,

        associationKind = when (association) {
            is Association.Mycorrhizal -> "MYCORRHIZAL"
            is Association.DeadWood -> "DEAD_WOOD"
            Association.SoilOrLitter -> "SOIL_OR_LITTER"
            Association.Dung -> "DUNG"
            is Association.Other -> "OTHER"
            null -> null
        },
        associationHostSpecies = when (association) {
            is Association.Mycorrhizal -> association.hostSpecies
            is Association.DeadWood -> association.hostSpecies
            else -> null
        },
        associationOtherText = (association as? Association.Other)?.text,
        forestType = hostSubstrate.forestType.toColumn(),
        hostHealth = hostSubstrate.hostHealth.toColumn(),
        hostSubstrateNotes = hostSubstrate.notes,
    )
}

private fun sporePrintColorFrom(kind: String, otherText: String?): SporePrintColor = when (kind) {
    "WHITE" -> SporePrintColor.White
    "CREAM" -> SporePrintColor.Cream
    "PINK_SALMON" -> SporePrintColor.PinkSalmon
    "OCHRE" -> SporePrintColor.Ochre
    "RUST" -> SporePrintColor.Rust
    "CHOCOLATE_BROWN" -> SporePrintColor.ChocolateBrown
    "PURPLE_BROWN" -> SporePrintColor.PurpleBrown
    "BLACK" -> SporePrintColor.Black
    "OTHER" -> SporePrintColor.Other(otherText ?: error("SporePrintColor kind OTHER stored with no text"))
    else -> error("Unknown spore print colour kind '$kind'")
}

private fun LogPhoto.toEntity() = LogPhotoEntity(id = id, relativePath = relativePath, createdAtEpochMillis = createdAtEpochMillis)

// --- MushroomLogEntryEntity -> MushroomLogEntry --------------------------------------------------

private fun MushroomLogEntryEntity.toDomain(photos: List<LogPhotoEntity>): MushroomLogEntry = MushroomLogEntry(
    id = id,
    // lat/lng are stored and read together — see MushroomLogEntryEntity.lat's own doc comment for
    // why null is only ever a paired state, never one set without the other.
    foundAt = if (lat != null && lng != null) LatLng(lat = lat, lng = lng) else null,
    foundOn = LocalDate.parse(foundOn),
    cap = CapSection(
        shape = capShape.toObservedEnum<CapShape>(),
        surface = capSurface.toObservedEnum<CapSurface>(),
        decorations = featureDecorationsOf(capDecorationsState, capDecorationsValue),
        margin = capMargin.toObservedEnum<CapMargin>(),
        notes = capNotes,
    ),
    hymenophore = HymenophoreSection(
        details = when (hymenophoreKind) {
            null -> Observed.NotObserved
            "GILLS" -> Observed.Recorded(
                HymenophoreDetails.Gills(
                    attachment = gillAttachment.toObservedEnum<GillAttachment>(),
                    spacing = gillSpacing.toObservedEnum<GillSpacing>(),
                    edge = gillEdge.toObservedEnum<GillEdge>(),
                ),
            )
            "PORES" -> Observed.Recorded(HymenophoreDetails.Pores)
            "TEETH" -> Observed.Recorded(HymenophoreDetails.Teeth)
            "SMOOTH_OR_WRINKLED" -> Observed.Recorded(HymenophoreDetails.SmoothOrWrinkled)
            else -> error("Unknown hymenophore kind '$hymenophoreKind' on entry '$id'")
        },
        notes = hymenophoreNotes,
    ),
    stipe = StipeSection(
        details = when (stipeKind) {
            null -> Observed.NotObserved
            "ABSENT" -> Observed.Recorded(StipeDetails.Absent)
            "PRESENT" -> Observed.Recorded(
                StipeDetails.Present(
                    position = stipePosition.toObservedEnum<StipePosition>(),
                    interior = stipeInterior.toObservedEnum<StipeInterior>(),
                    base = stipeBase.toObservedEnum<StipeBase>(),
                ),
            )
            else -> error("Unknown stipe kind '$stipeKind' on entry '$id'")
        },
        notes = stipeNotes,
    ),
    veil = VeilSection(
        annulus = featureEnumOf<AnnulusType>(annulusState, annulusValue),
        volva = featureEnumOf<VolvaType>(volvaState, volvaValue),
        notes = veilNotes,
    ),
    contextFlesh = ContextFleshSection(
        texture = fleshTexture.toObservedEnum<FleshTexture>(),
        colorChangeOnCutting = featureTextOf(colorChangeState, colorChangeValue),
        exudate = featureTextOf(exudateState, exudateValue),
        notes = contextFleshNotes,
    ),
    sporePrint = SporePrintSection(
        details = when (sporePrintColorKind) {
            null -> Observed.NotObserved
            else -> Observed.Recorded(
                SporePrint(
                    color = sporePrintColorFrom(sporePrintColorKind, sporePrintOtherText),
                    readOn = LocalDate.parse(
                        sporePrintReadOn ?: error("Spore print recorded with no readOn date on entry '$id'"),
                    ),
                ),
            )
        },
        notes = sporePrintNotes,
    ),
    hostSubstrate = HostSubstrateSection(
        association = when (associationKind) {
            null -> Observed.NotObserved
            "MYCORRHIZAL" -> Observed.Recorded(
                Association.Mycorrhizal(
                    associationHostSpecies ?: error("Mycorrhizal association stored with no host species on entry '$id'"),
                ),
            )
            "DEAD_WOOD" -> Observed.Recorded(
                Association.DeadWood(
                    associationHostSpecies ?: error("DeadWood association stored with no host species on entry '$id'"),
                ),
            )
            "SOIL_OR_LITTER" -> Observed.Recorded(Association.SoilOrLitter)
            "DUNG" -> Observed.Recorded(Association.Dung)
            "OTHER" -> Observed.Recorded(
                Association.Other(associationOtherText ?: error("Association kind OTHER stored with no text on entry '$id'")),
            )
            else -> error("Unknown association kind '$associationKind' on entry '$id'")
        },
        forestType = forestType.toObservedEnum<ForestType>(),
        hostHealth = hostHealth.toObservedEnum<HostHealth>(),
        notes = hostSubstrateNotes,
    ),
    notes = entryNotes,
    ownIdentification = ownIdentification,
    photos = photos.map { LogPhoto(id = it.id, relativePath = it.relativePath, createdAtEpochMillis = it.createdAtEpochMillis) },
    syncState = when (syncStateKind) {
        "DRAFT" -> LogSyncState.Draft
        "UPLOADING" -> LogSyncState.Uploading(syncProgress ?: error("Uploading state stored with no progress on entry '$id'"))
        "UPLOADED" -> LogSyncState.Uploaded(
            remoteObservationId = syncRemoteObservationId ?: error("Uploaded state stored with no remote id on entry '$id'"),
            uploadedAt = Instant.ofEpochMilli(
                syncUploadedAtEpochMillis ?: error("Uploaded state stored with no timestamp on entry '$id'"),
            ),
        )
        "FAILED" -> LogSyncState.Failed(
            reason = syncFailureReason ?: error("Failed state stored with no reason on entry '$id'"),
            remoteObservationId = syncRemoteObservationId,
        )
        else -> error("Unknown sync state kind '$syncStateKind' on entry '$id'")
    },
)

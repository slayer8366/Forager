package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry

/**
 * Which photos have no map icon of their own because a find stands for them (dispatch 2026-10-27-05, RECORDs -1734 and
 * -1735). The owner: "some of the photo glyphs on the map are misplaced and are apart from their find location. Can we
 * remove the photos attached to Finds from the map so it can reduce clutter?" The rule, from the owner's rulings in
 * Amendment 1: a photo is hidden from every map while at least one **saved** find **that has a location** links to it.
 *
 * - "No icon anywhere (Recommended)": a photo linked to such a find and also attached to a journal entry is hidden too,
 *   on the Maps tab and on the entry's own map.
 * - "Show its photos (Recommended)": a find with no location draws no icon, so it hides nothing.
 * - "Hide only after Save (Recommended)": a draft hides nothing until it is saved.
 * - "Fine, they reappear (Recommended)": a deleted find's links are gone with it, so its photos are standalone again.
 *
 * The link is the Room cross-reference [GalleryPhoto.referencingEntryIds] carries, never a matching position. Used by
 * [GetMapRecordsUseCase] (the Maps tab) and [GetCartographyEntryMapDataUseCase] (an entry's map), the two places a map's
 * photo markers are built. Headless, no Android type.
 */
object FindLinkedPhotos {
    /** The ids of the finds that hide their photos: saved (not a draft) and located. */
    fun hidingFindIds(finds: List<MushroomLogEntry>): Set<String> =
        finds.filter { !it.isDraft }.mapTo(HashSet()) { it.id }

    /**
     * Whether [photo] is hidden. [hidingFindIds] `null` means the finds could not be read: then any link to an entry
     * hides it, since which finds are saved and located is unknown and a find's photo drawn apart from it is what the
     * owner removed. The caller has the read failure and logs it.
     */
    fun isHidden(photo: GalleryPhoto, hidingFindIds: Set<String>?): Boolean =
        if (hidingFindIds == null) photo.referencingEntryIds.isNotEmpty() else photo.referencingEntryIds.any { it in hidingFindIds }
}

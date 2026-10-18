package com.zynergylabs.forager.app.ui.credits

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The open-source software list (dispatch 2026-10-13-05, the planner's answer Q1): hand-kept in
 * `assets/credits/software.json`, with each licence's text and every reproduced notice beside it.
 * The same file is what `verifyCreditsCoverEveryDependency` in `app/build.gradle.kts` checks against
 * the modules that actually ship, so the page and the check cannot describe two different lists.
 *
 * A build-time licences plugin was the alternative and was rejected: it would be one more pinned
 * plugin whose compatibility with this AGP is unverified, and it reads only Maven metadata, so
 * MapLibre's compiled-in native code and the two fonts would still be kept by hand, beside it.
 */
@Serializable
internal data class SoftwareCatalog(
    /** What the file is and how it is kept; read by people, not by the page. */
    val about: String,
    val licences: List<LicenceText>,
    val libraries: List<CreditedLibrary>,
)

/** A licence whose full text the page shows once, under every library that uses it. */
@Serializable
internal data class LicenceText(val id: String, val name: String, val file: String)

@Serializable
internal data class CreditedLibrary(
    val name: String,
    /** A [LicenceText.id]. */
    val licence: String,
    /** The copyright lines the licence asks to be passed on, shown above the licence's text. */
    val credit: List<String>,
    /** `group:name` patterns for the build check; empty for what has no module (fonts). */
    val modules: List<String> = emptyList(),
    /** Module to version, where a notice was taken from that version's own source. */
    val versions: Map<String, String> = emptyMap(),
    val note: String? = null,
    val notices: List<CreditedNotice> = emptyList(),
    /**
     * `true` for what only the own Street map uses (its label font and its icons; dispatch 2026-10-18-05 Part 2):
     * listed only while [com.zynergylabs.forager.app.map.STREET_SERVED_BY_OWN_MAP] is on, so the page never credits
     * something the app does not show. Off today.
     */
    val onlyWithOwnStreetMap: Boolean = false,
)

/** A notice reproduced word for word: a NOTICE file from a shipped artifact ([module] and [path]), or a project's own notices file. */
@Serializable
internal data class CreditedNotice(
    val title: String,
    val file: String,
    val module: String? = null,
    val path: String? = null,
)

/** A licence as the page shows it: the libraries under it, then its text once, then the notices of those libraries. */
internal data class LicenceGroup(
    val licence: LicenceText,
    val libraries: List<CreditedLibrary>,
    val text: String,
    val notices: List<Pair<CreditedNotice, String>>,
)

internal object SoftwareCredits {
    const val DIRECTORY = "credits"
    const val CATALOG_ASSET = "$DIRECTORY/software.json"

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(text: String): SoftwareCatalog = json.decodeFromString(SoftwareCatalog.serializer(), text)

    /**
     * The catalog as groups, in the order the file lists its licences. A library naming a licence the
     * file does not have, or a licence or notice file that is missing or blank, fails the whole load
     * rather than showing a partial list as if it were complete.
     */
    fun groups(
        catalog: SoftwareCatalog,
        ownStreet: Boolean = com.zynergylabs.forager.app.map.STREET_SERVED_BY_OWN_MAP,
        readText: (String) -> String,
    ): List<LicenceGroup> {
        val known = catalog.licences.map { it.id }.toSet()
        catalog.libraries.firstOrNull { it.licence !in known }?.let { error("\"${it.name}\" names licence ${it.licence}, which the list does not have") }
        val shown = catalog.libraries
        fun read(path: String): String = readText("$DIRECTORY/$path").also { require(it.isNotBlank()) { "$DIRECTORY/$path is blank" } }
        return catalog.licences.mapNotNull { licence ->
            val libraries = shown.filter { it.licence == licence.id }
            if (libraries.isEmpty()) return@mapNotNull null
            LicenceGroup(
                licence = licence,
                libraries = libraries,
                text = read(licence.file),
                notices = libraries.flatMap { it.notices }.map { it to read(it.file) },
            )
        }
    }

    fun load(assets: AssetManager): Result<List<LicenceGroup>> = runCatching {
        fun readAsset(path: String) = assets.open(path).bufferedReader().use { it.readText() }
        groups(parse(readAsset(CATALOG_ASSET)), readText = ::readAsset)
    }
}

/**
 * [text] in pieces at its blank lines, so a long licence lays out a paragraph at a time in a lazy list
 * instead of as one text node tens of thousands of characters long. Joined back with blank lines it
 * is [text] again, less trailing whitespace.
 */
internal fun licenceParagraphs(text: String): List<String> =
    text.replace("\r\n", "\n").trimEnd().split(Regex("\n(?:[ \t]*\n)+")).filter { it.isNotBlank() }

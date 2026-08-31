package com.forager.app.domain

import java.text.Normalizer

/**
 * Normalizes a taxon name or a user's search query into the same comparable form, so
 * `"lion's-mane mushroom"` (stored) and `"lions mane"` (typed) land on equal footing.
 *
 * Case-insensitive, diacritic-insensitive (`café` -> `cafe`), and insensitive to apostrophes
 * (removed outright: `lion's` -> `lions`, not `lion s`), hyphens (turned into a space:
 * `lion's-mane` -> `lions mane`) and any other surrounding punctuation (turned into a space,
 * then collapsed). Applied identically at index build time (stored as
 * `FungiTaxonNameEntity.nameNormalized`) and at query time, so an indexed-column comparison is
 * enough — no per-row normalization work at query time.
 */
fun normalizeSearchName(raw: String): String {
    val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
    val noDiacritics = DIACRITIC_MARK.replace(decomposed, "")
    val lower = noDiacritics.lowercase()
    val noApostrophes = APOSTROPHE.replace(lower, "")
    val hyphensToSpace = HYPHEN.replace(noApostrophes, " ")
    val cleaned = NON_ALPHANUMERIC.replace(hyphensToSpace, " ")
    return WHITESPACE.replace(cleaned, " ").trim()
}

private val DIACRITIC_MARK = Regex("\\p{Mn}+")
private val APOSTROPHE = Regex("['’]")

/** ASCII hyphen-minus plus the common Unicode dash range (en/em dash and friends). */
private val HYPHEN = Regex("[-‐-―]")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9 ]")
private val WHITESPACE = Regex("\\s+")

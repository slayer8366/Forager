package com.zynergylabs.forager.app.ui.credits

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.theme.Spacing

internal const val SOURCES_AND_CREDITS_TITLE = "Sources & credits"
internal const val OPEN_SOURCE_SOFTWARE_TITLE = "Open-source software"
internal const val SOURCES_PAGE_TAG = "sources-and-credits-page"
internal const val OPEN_SOURCE_SOFTWARE_ROW_TAG = "open-source-software-row"
internal const val SOFTWARE_PAGE_TAG = "open-source-software-page"
internal fun sourceCreditRowTag(key: String) = "source-credit-$key"
internal fun sourceCreditLinkTag(key: String) = "source-credit-link-$key"

/** The first paragraph of a licence's text on the software page, for tests: the body is there and is the file's own. */
internal fun licenceTextTag(licenceId: String) = "licence-text-$licenceId"

/**
 * Settings > Sources & credits (dispatch 2026-10-13-05): the data sources, each with a link to its
 * licence or site, then the way into the open-source software list. Laid out as the dispatch's step
 * path draws it. Each row is one touch target across its whole width, its link label at the end
 * ("licence ›"); a touch anywhere on it opens that link in the browser.
 *
 * Draws no fill: it sits in the Tools drawer over the map, whose own 80% layer is the one fill
 * (MapChromeAlphaTest's rule).
 */
@Composable
internal fun SourcesAndCreditsPage(onOpenSoftware: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .testTag(SOURCES_PAGE_TAG)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(SOURCES_AND_CREDITS_TITLE, style = MaterialTheme.typography.titleLarge)
        DATA_SOURCE_SECTIONS.forEach { section ->
            Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
            section.credits.forEach { credit ->
                SourceCreditRow(credit, onOpen = { openWebLink(context, credit.url) })
                credit.detail?.let { detail ->
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))
        CreditsNavigationRow(OPEN_SOURCE_SOFTWARE_TITLE, tag = OPEN_SOURCE_SOFTWARE_ROW_TAG, onClick = onOpenSoftware)
    }
}

@Composable
private fun SourceCreditRow(credit: SourceCredit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(sourceCreditRowTag(credit.key))
            .clickableWithShapedPress(role = Role.Button, onClickLabel = "Open ${credit.linkLabel}", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(credit.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${credit.linkLabel} ›",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(sourceCreditLinkTag(credit.key)),
        )
    }
}

/** A labelled row that opens a page one step deeper, its "›" at the end; the whole row is the touch target. */
@Composable
internal fun CreditsNavigationRow(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .clickableWithShapedPress(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Settings > Sources & credits > Open-source software: grouped by licence, each licence's full text
 * shown once, with the libraries under it and their copyright lines above it, then any notice those
 * libraries ship (the planner's answer Q2). Read from `assets/credits/` ([SoftwareCredits]).
 *
 * Lazy, a paragraph per item: MapLibre's notices alone run to some 76,000 characters. A list that
 * cannot be read says so, and the log records why, rather than showing an empty or partial page.
 */
@Composable
internal fun OpenSourceSoftwarePage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val groups = remember { SoftwareCredits.load(context.assets) }
    LazyColumn(
        modifier = modifier.testTag(SOFTWARE_PAGE_TAG).navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { Text(OPEN_SOURCE_SOFTWARE_TITLE, style = MaterialTheme.typography.titleLarge) }
        groups.fold(
            onSuccess = { loaded ->
                loaded.forEach { group -> licenceGroupItems(group) }
            },
            onFailure = { error ->
                Log.e("OpenSourceSoftware", "Couldn't read the open-source software list.", error)
                item {
                    Text(
                        "Couldn't read the open-source software list. Reinstalling the app restores it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.licenceGroupItems(group: LicenceGroup) {
    item(key = "licence-${group.licence.id}") {
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HorizontalDivider()
            Text(group.licence.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
    items(group.libraries, key = { "library-${it.name}" }) { library ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(library.name, style = MaterialTheme.typography.titleSmall)
            library.credit.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            library.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    licenceParagraphs(group.text).forEachIndexed { index, paragraph ->
        item(key = "text-${group.licence.id}-$index") {
            LicenceParagraph(paragraph, modifier = if (index == 0) Modifier.testTag(licenceTextTag(group.licence.id)) else Modifier)
        }
    }
    group.notices.forEach { (notice, text) ->
        item(key = "notice-${notice.file}") {
            Text(notice.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
        }
        licenceParagraphs(text).forEachIndexed { index, paragraph ->
            item(key = "notice-${notice.file}-$index") { LicenceParagraph(paragraph) }
        }
    }
}

@Composable
private fun LicenceParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.fillMaxWidth(),
    )
}

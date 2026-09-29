package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.onAllNodesWithTag
import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.CartographyEntryRepository
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.log.CartographyViewModel
import com.zynergylabs.forager.app.ui.log.DRAFTS_CONTINUE_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_DISCARD_TEST_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.RETURN_PROMPT_SAVE_AS_DRAFT_TEST_TAG
import com.zynergylabs.forager.app.ui.log.SAVE_CONFIRM_TEST_TAG
import com.zynergylabs.forager.app.ui.log.SHORT_DRAFTS_CHIP_TAG
import com.zynergylabs.forager.app.ui.log.SHORT_NEW_TAG
import com.zynergylabs.forager.app.ui.log.cartographyEntryDeleteNotice
import com.zynergylabs.forager.app.ui.log.entrySwipeTag
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast

// The seven failures that set CartographyUiState.saveErrorMessage, each with its existing text
// (CartographyViewModel.kt at 8dbfd14: :348, :388, :430, :467, :488, :573, :672). Copied here as
// literals on purpose: a test that read the ViewModel's own strings could not notice them change.
private const val SAVE_REFUSED = "Couldn't save your changes."
private const val FINISH_REFUSED = "Couldn't finish that entry."
private const val SAVE_AS_DRAFT_REFUSED = "Couldn't save that as a draft."
private const val DISCARD_REFUSED = "Couldn't discard those changes."
private const val DELETE_REFUSED = "Couldn't delete that entry."

private const val SAVED_TEXT = "A walk by the creek."
private const val DRAFT_TEXT = "Half written."
private const val TYPED = "An edit the store refuses."
private const val NEW_ENTRY_ID = "entry-new"

private val SAVED: CartographyEntry = CartographyEntry.draft(id = "entry-saved", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1_000L)
    .copy(isDraft = false, text = SAVED_TEXT)
private val DRAFT: CartographyEntry = CartographyEntry.draft(id = "entry-draft", date = LocalDate.of(2026, 9, 5), updatedAtEpochMillis = 1_000L)
    .copy(text = DRAFT_TEXT)

/**
 * Room, with its saves, deletes and reads refused on demand, as a full or failing disk would refuse
 * them. Every refusal is recorded in [refused], so a test can confirm the failure it asserts on came
 * from the call its action makes, and [refusal] is the one exception every refusal carries.
 */
internal class SaveFailureRefusingRepository(private val room: CartographyEntryRepository) : CartographyEntryRepository by room {
    val refusal = IllegalStateException("write refused")
    var refuseSave: (CartographyEntry) -> Boolean = { false }
    var refuseDeletes = false
    var refuseReads = false

    /** When set, a save waits for it before answering: a slow disk, so the user can leave the Journal first. */
    var saveGate: CompletableDeferred<Unit>? = null
    val refused = mutableListOf<String>()

    override suspend fun save(entry: CartographyEntry): Result<Unit> {
        saveGate?.await()
        if (refuseSave(entry)) {
            refused += "save ${entry.id}"
            return Result.failure(refusal)
        }
        return room.save(entry)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (refuseDeletes) {
            refused += "delete $id"
            return Result.failure(refusal)
        }
        return room.delete(id)
    }

    override suspend fun getById(id: String): Result<CartographyEntry?> {
        if (refuseReads) {
            refused += "read $id"
            return Result.failure(refusal)
        }
        return room.getById(id)
    }
}

private object SaveFailureNoOfflineMaps : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

/**
 * Entry save failures shown (intent `2026-09-28-68`, continuation `2026-09-28-76`, the owner's
 * "Option B"): every failure that sets `CartographyUiState.saveErrorMessage` shows its own existing
 * text, verbatim, as a Toast hosted by the Journal (`JournalTab` in the compact tree, `LogPanel` in
 * the wide one), the find editor's pattern. The message clears once it has shown, and one raised while
 * the Journal is not on screen shows when the Journal next opens.
 *
 * Through the real [AvailabilityScreen], driven by the real [CartographyViewModel] over an in-memory
 * [ForagerDatabase] behind [SaveFailureRefusingRepository], with the callbacks `MainActivity` passes
 * for the day entries reproduced, the new `onCartographySaveErrorDismissed` among them. Each action is
 * the user's own: the editor's Save and its confirmation, Finish entry, typing in a draft, the return
 * prompt's Save as draft, the leave prompt's Discard, the report's Delete, and a card's Delete. Each
 * test first confirms its action reached the refused call ([SaveFailureRefusingRepository.refused]),
 * so a missing Toast cannot come from an action that never ran.
 *
 * [EntrySaveFailureShownTests] run in every window. [EntrySaveFailureShownCompactTests] adds a card's
 * pending delete, which only the compact tree's cards offer (`AvailabilityScreen` gives the wide
 * tree's `LogPanel` no `onRequestDeleteCartographyEntry`).
 */
internal abstract class EntrySaveFailureHarness {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private lateinit var database: ForagerDatabase
    protected lateinit var repository: SaveFailureRefusingRepository
    protected lateinit var cartographyViewModel: CartographyViewModel

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    /** Opens the Journal: the compact tree's Journal tab, or the wide tree's Mushroom Log panel. */
    protected abstract fun openJournal()

    /** Leaves the Journal, so neither `JournalTab` nor `LogPanel` is composed. */
    protected abstract fun leaveJournal()

    protected fun setScreen() {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        val room = RoomCartographyEntryRepository(database.cartographyEntryDao())
        runBlocking {
            room.save(SAVED).getOrThrow()
            room.save(DRAFT).getOrThrow()
        }
        repository = SaveFailureRefusingRepository(room)
        cartographyViewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(repository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(repository),
            createEntry = CreateCartographyEntryUseCase(repository, now = { 1_000L }, idGenerator = { NEW_ENTRY_ID }),
            saveEntry = SaveCartographyEntryUseCase(repository, now = { 2_000L }),
            getEntry = GetCartographyEntryUseCase(repository),
            commitEntry = CommitCartographyEntryUseCase(repository, now = { 3_000L }),
            deleteEntry = DeleteCartographyEntryUseCase(repository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao()),
                trackRepository = RoomTrackRepository(database.trackDao()),
                waypointRepository = RoomWaypointRepository(database.waypointDao()),
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(SaveFailureNoOfflineMaps),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(repository),
            now = { 1_000L },
        )
        val availabilityViewModel = mapLayersViewModel()
        val map = LayersRecordingMapSlot()
        composeRule.setContent {
            val uiState by availabilityViewModel.uiState.collectAsState()
            val cartographyUiState by cartographyViewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                logUiState = MushroomLogUiState(),
                onUseCurrentLocation = availabilityViewModel::useCurrentLocation,
                onManualLatChanged = availabilityViewModel::onManualLatChanged,
                onManualLngChanged = availabilityViewModel::onManualLngChanged,
                onSearchManualCoordinates = availabilityViewModel::searchManualCoordinates,
                onRadiusChanged = availabilityViewModel::onRadiusChanged,
                onMonthSelected = availabilityViewModel::onMonthSelected,
                onMapTabSelected = availabilityViewModel::onMapTabSelected,
                onSeasonalTabSelected = availabilityViewModel::onSeasonalTabSelected,
                onTaxonSearchQueryChanged = availabilityViewModel::onTaxonSearchQueryChanged,
                onTaxonSearchResultSelected = availabilityViewModel::onTaxonSearchResultSelected,
                onDismissTaxonSuggestions = availabilityViewModel::onDismissTaxonSuggestions,
                onReopenTaxonSuggestions = availabilityViewModel::onReopenTaxonSuggestions,
                onPlaceTripPin = availabilityViewModel::onPlaceTripPin,
                onDeletePlannedTrip = availabilityViewModel::onDeletePlannedTrip,
                onRecentSearchSelected = availabilityViewModel::onRecentSearchSelected,
                onOfflineMapLatChanged = availabilityViewModel::onOfflineMapLatChanged,
                onOfflineMapLngChanged = availabilityViewModel::onOfflineMapLngChanged,
                onOfflineMapRadiusChanged = availabilityViewModel::onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = availabilityViewModel::onOfflineMapNameChanged,
                onOfflineMapsOpened = availabilityViewModel::onOfflineMapsOpened,
                onDownloadOfflineMaps = availabilityViewModel::onDownloadOfflineMaps,
                onDeleteOfflineRegion = availabilityViewModel::onDeleteOfflineRegion,
                onNightModeMapsChanged = availabilityViewModel::onNightModeMapsChanged,
                onThemeModeChanged = availabilityViewModel::onThemeModeChanged,
                onMapFullscreenChanged = availabilityViewModel::onMapFullscreenChanged,
                mapSlot = map.slot,
                onMapShown = availabilityViewModel::onMapShown,
                onMapLayerVisibilityChanged = availabilityViewModel::onMapLayerVisibilityChanged,
                onMapLayerOpacityChanged = availabilityViewModel::onMapLayerOpacityChanged,
                onColourFieldMoved = availabilityViewModel::onColourFieldMoved,
                // What MainActivity passes for the day entries.
                cartographyUiState = cartographyUiState.hidingPendingDelete(),
                onOpenCartographyEntry = cartographyViewModel::onOpenEntry,
                onStartCartographyEntry = cartographyViewModel::onStartEntry,
                onCloseCartographyEntry = cartographyViewModel::onCloseEntry,
                onCartographyTextChanged = cartographyViewModel::onTextChanged,
                onCartographyTagsChanged = cartographyViewModel::onTagsChanged,
                onSetFindDecision = cartographyViewModel::onSetFindDecision,
                onSetTrackDecision = cartographyViewModel::onSetTrackDecision,
                onSetWaypointDecision = cartographyViewModel::onSetWaypointDecision,
                onSetOfflineRegionDecision = cartographyViewModel::onSetOfflineRegionDecision,
                onToggleKeptPhoto = cartographyViewModel::onToggleKeptPhoto,
                onFinishCartographyEntry = cartographyViewModel::onFinishEntry,
                onSaveCartographyEntry = cartographyViewModel::onSaveEntry,
                onDiscardCartographyEntryChanges = cartographyViewModel::onDiscardEntryChanges,
                onSaveCartographyEntryAsDraft = cartographyViewModel::onSaveEntryAsDraft,
                onCartographySaveErrorDismissed = cartographyViewModel::onSaveErrorDismissed,
                onDeleteCartographyEntry = cartographyViewModel::onDeleteEntry,
                onRequestDeleteCartographyEntry = cartographyViewModel::requestDeleteEntry,
                onSetCartographyEntryShownOnMap = cartographyViewModel::onSetShownOnMap,
                onCartographyShownOnMapErrorDismissed = cartographyViewModel::onShownOnMapErrorDismissed,
                // MainActivity's notice for an entry's pending delete: its Undo snackbar, and the
                // real delete when the snackbar ends.
                pendingDeleteNotices = listOfNotNull(
                    cartographyEntryDeleteNotice(
                        cartographyUiState.pendingDelete,
                        onUndo = cartographyViewModel::undoDeleteEntry,
                        onCommit = cartographyViewModel::commitDeleteEntry,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    protected fun touchCentreOf(node: SemanticsNodeInteraction) {
        node.performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    /** Scrolls [node] into view, then a real touch at its centre. */
    protected fun scrollToAndTouch(node: SemanticsNodeInteraction) {
        node.performScrollTo()
        composeRule.waitForIdle()
        touchCentreOf(node)
    }

    protected fun entryCard(id: String) = composeRule.onNode(hasTestTag("entry-card-$id") or hasTestTag("entry-row-$id"))

    protected fun openSavedEntryReport() {
        openJournal()
        scrollToAndTouch(entryCard(SAVED.id))
        // J6a: on the wide tree the open entry's report is in the right side's detail pane while the Entries
        // list, whose card carries the same text, stays in the left column, so the text is read inside the pane there.
        val inPane = composeRule.onAllNodesWithTag("journal-detail-pane").fetchSemanticsNodes().isNotEmpty()
        (if (inPane) composeRule.onNode(hasText(SAVED_TEXT) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("journal-detail-pane"))) else composeRule.onNodeWithText(SAVED_TEXT)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    /** The saved entry's editor, from its report's menu, with [TYPED] in it and not yet saved. */
    protected fun openSavedEntryEditorWithTypedText() {
        openSavedEntryReport()
        touchCentreOf(composeRule.onNodeWithContentDescription("Entry options"))
        touchCentreOf(composeRule.onNodeWithText("Edit entry"))
        typeIntoTheAccount(TYPED)
        assertTrue("the edit is held unsaved", cartographyViewModel.uiState.value.hasUnsavedChanges)
    }

    /** The one draft, through the drafts banner's Continue (the drafts chip in a short window). */
    protected fun openDraftEditor() {
        openJournal()
        touchCentreOf(composeRule.onNode(hasTestTag(DRAFTS_CONTINUE_TAG) or hasTestTag(SHORT_DRAFTS_CHIP_TAG)))
        assertEquals("the draft is open", DRAFT.id, cartographyViewModel.uiState.value.editingEntry?.id)
        composeRule.onNode(hasText("Your own account (optional)") and hasSetTextAction()).assertExists()
    }

    protected fun typeIntoTheAccount(text: String) {
        composeRule.onNode(hasText("Your own account (optional)") and hasSetTextAction()).performScrollTo().performTextReplacement(text)
        composeRule.waitForIdle()
    }

    /** The editor's own Save, then its confirmation's Save. */
    protected fun touchSaveAndConfirm() {
        scrollToAndTouch(composeRule.onNode(hasText("Save") and hasClickAction()))
        touchCentreOf(composeRule.onNodeWithTag(SAVE_CONFIRM_TEST_TAG))
    }

    /** Real ON_STOP and ON_RESUME through the Activity's lifecycle, as LeavingTheJournalFixesTest's backgroundThenResume does. */
    protected fun backgroundThenResume() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    protected val pendingMessage: String? get() = cartographyViewModel.uiState.value.saveErrorMessage

    /** Exactly one Toast has shown, with exactly [text], and the message it showed is cleared. */
    protected fun assertShownOnceThenCleared(text: String) {
        assertEquals("Toasts shown", 1, ShadowToast.shownToastCount())
        assertEquals(text, ShadowToast.getTextOfLatestToast())
        assertNull("cleared once shown", pendingMessage)
    }
}

/** The tests every window runs: portrait, the short landscape window and the wide window. */
internal abstract class EntrySaveFailureShownTests : EntrySaveFailureHarness() {

    // ── Each action shows its own message, exactly, and the message clears once shown ──

    @Test
    fun `the editor's Save refused shows exactly Couldn't save your changes`() {
        setScreen()
        openSavedEntryEditorWithTypedText()
        repository.refuseSave = { true }

        touchSaveAndConfirm()

        assertEquals("the Save reached the store and was refused", listOf("save ${SAVED.id}"), repository.refused)
        assertShownOnceThenCleared(SAVE_REFUSED)
    }

    @Test
    fun `a draft's autosave refused shows exactly Couldn't save your changes`() {
        setScreen()
        openDraftEditor()
        repository.refuseSave = { true }

        typeIntoTheAccount(TYPED)

        assertEquals("the autosave reached the store and was refused", listOf("save ${DRAFT.id}"), repository.refused)
        assertShownOnceThenCleared(SAVE_REFUSED)
    }

    @Test
    fun `Finish entry refused shows exactly Couldn't finish that entry`() {
        setScreen()
        openDraftEditor()
        repository.refuseSave = { true }

        scrollToAndTouch(composeRule.onNode(hasText("Finish entry") and hasClickAction()))

        assertEquals("the finish reached the store and was refused", listOf("save ${DRAFT.id}"), repository.refused)
        assertShownOnceThenCleared(FINISH_REFUSED)
    }

    @Test
    fun `the return prompt's Save as draft refused shows exactly Couldn't save that as a draft`() {
        setScreen()
        openSavedEntryEditorWithTypedText()
        repository.refuseSave = { true }
        backgroundThenResume()

        touchCentreOf(composeRule.onNodeWithTag(RETURN_PROMPT_SAVE_AS_DRAFT_TEST_TAG))

        assertEquals("the demotion reached the store and was refused", listOf("save ${SAVED.id}"), repository.refused)
        assertShownOnceThenCleared(SAVE_AS_DRAFT_REFUSED)
    }

    @Test
    fun `the leave prompt's Discard, with the stored entry unreadable, shows exactly Couldn't discard those changes`() {
        setScreen()
        openSavedEntryEditorWithTypedText()
        repository.refuseReads = true
        touchCentreOf(composeRule.onNodeWithContentDescription("Back to Cartography"))

        touchCentreOf(composeRule.onNodeWithTag(LEAVE_PROMPT_DISCARD_TEST_TAG))

        assertEquals("the Discard's reload reached the store and was refused", listOf("read ${SAVED.id}"), repository.refused)
        assertShownOnceThenCleared(DISCARD_REFUSED)
    }

    @Test
    fun `the report's Delete refused shows exactly Couldn't delete that entry`() {
        setScreen()
        openSavedEntryReport()
        repository.refuseDeletes = true
        touchCentreOf(composeRule.onNodeWithContentDescription("Entry options"))
        touchCentreOf(composeRule.onNodeWithText("Delete entry"))

        touchCentreOf(composeRule.onNodeWithText("Delete"))

        assertEquals("the Delete reached the store and was refused", listOf("delete ${SAVED.id}"), repository.refused)
        assertShownOnceThenCleared(DELETE_REFUSED)
    }

    // ── Cleared once shown, so the same failure again shows again ──

    @Test
    fun `the same refusal twice shows its message twice`() {
        setScreen()
        openDraftEditor()
        repository.refuseSave = { true }

        typeIntoTheAccount("First try.")
        typeIntoTheAccount("Second try.")

        assertEquals("both autosaves reached the store and were refused", listOf("save ${DRAFT.id}", "save ${DRAFT.id}"), repository.refused)
        assertEquals("Toasts shown", 2, ShadowToast.shownToastCount())
        assertEquals(SAVE_REFUSED, ShadowToast.getTextOfLatestToast())
        assertNull("cleared once shown", pendingMessage)
    }

    // ── A failure raised off screen waits for the Journal ──

    @Test
    fun `a Save refused after leaving the Journal shows when the Journal next opens`() {
        setScreen()
        openSavedEntryEditorWithTypedText()
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        repository.refuseSave = { true }
        touchSaveAndConfirm()
        leaveJournal()

        gate.complete(Unit)
        composeRule.waitForIdle()
        assertEquals("the Save was refused after the Journal closed", listOf("save ${SAVED.id}"), repository.refused)
        assertEquals("nothing shown while the Journal is not on screen", 0, ShadowToast.shownToastCount())
        assertEquals("the message waits", SAVE_REFUSED, pendingMessage)

        openJournal()

        assertShownOnceThenCleared(SAVE_REFUSED)
    }

    // ── onStartEntry's dropped failure is logged, and shows nothing ──

    @Test
    fun `a new entry whose kept candidates cannot be saved is logged and shows nothing`() {
        setScreen()
        openJournal()
        var saves = 0
        // The first save is the new draft's own (CreateCartographyEntryUseCase); the second, refused,
        // is onStartEntry's save of that draft with the day's candidates kept.
        repository.refuseSave = { ++saves >= 2 }

        touchCentreOf(composeRule.onNode(hasTestTag(ENTRIES_FAB_TAG) or hasTestTag(SHORT_NEW_TAG)))

        assertEquals("the second save reached the store and was refused", listOf("save $NEW_ENTRY_ID"), repository.refused)
        val logged = ShadowLog.getLogsForTag("Cartography").filter { it.throwable === repository.refusal }
        assertEquals("one log line for the refused save, with the refusal: $logged", 1, logged.size)
        assertEquals("a warning", Log.WARN, logged.single().type)
        assertTrue("naming the entry: ${logged.single().msg}", logged.single().msg.contains(NEW_ENTRY_ID))
        assertEquals("no Toast", 0, ShadowToast.shownToastCount())
        assertNull("no message", pendingMessage)
        assertEquals("the new entry still opens", NEW_ENTRY_ID, cartographyViewModel.uiState.value.editingEntry?.id)
    }
}

/** The compact tree's own: a card's pending delete, which only the compact tree's cards offer. */
internal abstract class EntrySaveFailureShownCompactTests : EntrySaveFailureShownTests() {

    /** A card's Delete, the way this window offers it: a swipe in portrait, the long-press menu in a short window. */
    protected abstract fun requestCardDelete(id: String)

    override fun openJournal() {
        touchCentreOf(composeRule.onNodeWithText("Journal"))
        composeRule.onNodeWithText("Journal").assertIsSelected()
    }

    override fun leaveJournal() {
        touchCentreOf(composeRule.onNodeWithText("Maps"))
        composeRule.onNodeWithText("Maps").assertIsSelected()
    }

    /** SnackbarDuration.Long is about 10 s; past it and the exit animation. */
    private fun letTheUndoSnackbarEnd() {
        composeRule.mainClock.advanceTimeBy(12_000L)
        composeRule.waitForIdle()
    }

    @Test
    fun `a card's Delete refused when its Undo snackbar ends shows exactly Couldn't delete that entry`() {
        setScreen()
        openJournal()
        repository.refuseDeletes = true
        requestCardDelete(SAVED.id)
        assertEquals("pending", SAVED.id, cartographyViewModel.uiState.value.pendingDelete?.item?.id)

        letTheUndoSnackbarEnd()

        assertEquals("the delete reached the store and was refused", listOf("delete ${SAVED.id}"), repository.refused)
        assertShownOnceThenCleared(DELETE_REFUSED)
    }

    @Test
    fun `a card's Delete refused while the Maps tab is up shows when the Journal next opens`() {
        setScreen()
        openJournal()
        repository.refuseDeletes = true
        requestCardDelete(SAVED.id)
        assertEquals("pending", SAVED.id, cartographyViewModel.uiState.value.pendingDelete?.item?.id)
        leaveJournal()

        letTheUndoSnackbarEnd()
        assertEquals("the delete was refused on the Maps tab", listOf("delete ${SAVED.id}"), repository.refused)
        assertEquals("nothing shown while the Journal is not on screen", 0, ShadowToast.shownToastCount())
        assertEquals("the message waits", DELETE_REFUSED, pendingMessage)

        openJournal()

        assertShownOnceThenCleared(DELETE_REFUSED)
    }
}

/** Portrait, at the S22 Ultra's size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
internal class EntrySaveFailureShownPortraitTest : EntrySaveFailureShownCompactTests() {
    override fun requestCardDelete(id: String) {
        composeRule.onNodeWithTag(entrySwipeTag(id)).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }
}

/** The short landscape window, `w823dp-h384dp`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
internal class EntrySaveFailureShownShortLandscapeTest : EntrySaveFailureShownCompactTests() {
    override fun requestCardDelete(id: String) {
        entryCard(id).performScrollTo().performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        touchCentreOf(composeRule.onNodeWithTag("tile-options-delete"))
    }
}

/**
 * The wide window, where the Journal is the drawer's Mushroom Log panel (`LogPanel`). Leaving it is
 * the panel's own back arrow to the search options, after which `LogPanel` is not composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h900dp-mdpi")
internal class EntrySaveFailureShownWideTest : EntrySaveFailureShownTests() {
    override fun openJournal() {
        touchCentreOf(composeRule.onNodeWithText("Mushroom Log"))
        composeRule.onNodeWithContentDescription("Back to search options").assertIsDisplayed()
    }

    override fun leaveJournal() {
        touchCentreOf(composeRule.onNodeWithContentDescription("Back to search options"))
        composeRule.onNodeWithText("Mushroom Log").assertIsDisplayed()
    }
}

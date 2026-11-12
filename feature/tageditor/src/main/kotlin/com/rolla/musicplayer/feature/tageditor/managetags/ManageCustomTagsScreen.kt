@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.tageditor.managetags

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiDropdownMenu
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiMenuItem
import com.rolla.musicplayer.core.designsystem.component.OneUiSearchField
import com.rolla.musicplayer.core.designsystem.component.OneUiTabRow
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.SheetActionRow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialog
import com.rolla.musicplayer.core.ui.customtags.filterCustomTags
import com.rolla.musicplayer.core.ui.tracksCountLabel
import com.rolla.musicplayer.feature.tageditor.BatchProgress
import com.rolla.musicplayer.feature.tageditor.BatchSaveProgress
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester
import kotlinx.coroutines.flow.filter

/**
 * Stateful entry point for Manage tags (custom tags phase 3). It resolves the delete's write-permission prompts as the
 * tag editor routes do: [ManageCustomTagsUiState.consentRequest] through `MediaWriteRequester.ensureWritable` (one
 * prompt for every file carrying the entry) and API 29's [ManageCustomTagsUiState.recoveryRequest] through
 * `recoverAndRetry`. While a delete runs ([ManageCustomTagsUiState.isBusy]) the screen cannot be left (see
 * [ManageCustomTagsScreen]); the navigate-up callback also checks the live state, so a tap queued as the delete starts
 * cannot slip through either.
 *
 * The ⋮'s Export opens the system save prompt (`CreateDocument("application/json")`, suggesting `rolla-tags.json`) and
 * Import the open prompt (`OpenDocument` for `application/json` or `text/plain`); the chosen document goes to
 * [ManageCustomTagsViewModel.onExportDocumentChosen] or [ManageCustomTagsViewModel.onImportDocumentChosen], and a
 * cancelled prompt does nothing. Neither prompt opens while the screen is busy.
 */
// LongMethod: the two consent effects, the busy guard and the screen's event wiring, one flow.
@Suppress("LongMethod")
@Composable
fun ManageCustomTagsRoute(
    onNavigateUp: () -> Unit,
    viewModel: ManageCustomTagsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requester = rememberMediaWriteRequester()

    LaunchedEffect(uiState.consentRequest) {
        val uris = uiState.consentRequest
        if (uris != null) {
            requester.ensureWritable(
                uris = uris.map(String::toUri),
                onGranted = viewModel::onConsentGranted,
                onDenied = viewModel::onConsentDenied,
            )
        }
    }

    LaunchedEffect(uiState.recoveryRequest) {
        val recovery = uiState.recoveryRequest
        if (recovery != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requester.recoverAndRetry(
                exception = recovery,
                onGranted = viewModel::onRecoveryGranted,
                onDenied = viewModel::onConsentDenied,
            )
        }
    }

    val navigateUpUnlessBusy = remember(viewModel, onNavigateUp) {
        { if (!viewModel.uiState.value.isBusy) onNavigateUp() }
    }

    // The list file's Storage Access Framework prompts; a cancelled prompt returns null and does nothing.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LIST_FILE_MIME_TYPE),
    ) { uri -> uri?.let { viewModel.onExportDocumentChosen(it.toString()) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onImportDocumentChosen(it.toString()) }
    }

    ManageCustomTagsScreen(
        uiState = uiState,
        onNavigateUp = navigateUpUnlessBusy,
        onPageChange = remember(viewModel) { viewModel::onPageChange },
        onQueryChange = remember(viewModel) { viewModel::onQueryChange },
        onCreateClick = remember(viewModel) { viewModel::onCreateClick },
        onRenameClick = remember(viewModel) { viewModel::onRenameClick },
        onNameConfirm = remember(viewModel) { viewModel::onNameConfirm },
        onNameDialogDismiss = remember(viewModel) { viewModel::onNameDialogDismiss },
        onDeleteClick = remember(viewModel) { viewModel::onDeleteClick },
        onDeleteConfirm = remember(viewModel) { viewModel::onDeleteConfirm },
        onDeleteDismiss = remember(viewModel) { viewModel::onDeleteDismiss },
        onDismissMessage = remember(viewModel) { viewModel::dismissMessage },
        onExportClick = { if (!viewModel.uiState.value.isBusy) exportLauncher.launch(LIST_FILE_NAME) },
        onImportClick = { if (!viewModel.uiState.value.isBusy) importLauncher.launch(LIST_FILE_OPEN_TYPES) },
        onImportConfirm = remember(viewModel) { viewModel::onImportConfirm },
        onImportDismiss = remember(viewModel) { viewModel::onImportDismiss },
        modifier = modifier,
    )
}

/**
 * Manage tags: `OneUiDetailTopBar` "Manage tags" with a ⋮ (Export, Import), the "Tags" · "Subtags" tab row over a
 * two-page pager, and on each page, inside a [ContentPanel], its search field, a "New tag"/"New subtag" row and the
 * entries the page's query matches ([filterCustomTags]; the ViewModel keeps only the query). Each entry shows its name
 * (wrapping, never ellipsized) over "N tracks"; tapping it renames it, and its ⋮ offers Rename and Delete. An empty
 * list says "No tags yet"/"No subtags yet", a query that matches nothing "No matches".
 *
 * Dialogs, all driven by [uiState]: the name dialog ([ManageCustomTagsUiState.nameDialog]; "Rename tag"/"Rename
 * subtag" with "Rename" while [ManageCustomTagsUiState.renaming], else phase 2's "New tag"/"New subtag" with "Create"),
 * and the delete confirmation ("Delete "Hard"?", with the body "Remove "Hard" from 3 tracks?" only when tracks carry
 * it), and the import confirmation ([ManageCustomTagsUiState.importConfirm]: "Replace your tags?", the replace line
 * and any clash lines, "Cancel" and "Import"). While the delete's files are written, a progress line sits above the
 * tabs; [ManageCustomTagsUiState.message] ("Updated X of N tracks.", the export and import results) shows as a
 * snackbar, then [onDismissMessage].
 *
 * While [ManageCustomTagsUiState.isBusy] the screen cannot be left, so the delete's per-file loop, an export's write or
 * an import is never cancelled mid-way (files rewritten but never re-indexed, a half-written document): system Back is
 * swallowed by a [BackHandler] and the bar's Navigate up and ⋮ are disabled.
 *
 * The page and its search sync like the picker's: the owner holds [ManageCustomTagsUiState.page], a tab tap or the
 * page the pager rests on reports through [onPageChange], and each later change of the page moves the pager there.
 *
 * Insets (per-screen concerns): the bar owns the status bar; the body takes the keyboard minus the navigation bar, and
 * each list pads its bottom by the navigation bar (no mini-player here), so with the keyboard open the two add up to
 * exactly the keyboard.
 */
// One callback per screen event plus the modifier; LongMethod: the Scaffold, its body and the three dialogs.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun ManageCustomTagsScreen(
    uiState: ManageCustomTagsUiState,
    onNavigateUp: () -> Unit,
    onPageChange: (CustomTagKind) -> Unit,
    onQueryChange: (CustomTagKind, String) -> Unit,
    onCreateClick: (CustomTagKind, String) -> Unit,
    onRenameClick: (CustomTag) -> Unit,
    onNameConfirm: (String) -> Unit,
    onNameDialogDismiss: () -> Unit,
    onDeleteClick: (CustomTag) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDismissMessage: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onImportConfirm: () -> Unit,
    onImportDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }
    BackHandler(enabled = uiState.isBusy) {}

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OneUiDetailTopBar(
                title = "Manage tags",
                onNavigateUp = onNavigateUp,
                navigationEnabled = !uiState.isBusy,
                actions = {
                    ListFileMenu(
                        enabled = !uiState.isBusy,
                        onExportClick = onExportClick,
                        onImportClick = onImportClick,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                // The keyboard's owner: the lists below already pad by the navigation bar.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .fillMaxSize(),
        ) {
            uiState.progress?.let { DeleteProgress(progress = it) }
            ManageTabsAndPager(
                uiState = uiState,
                onPageChange = onPageChange,
                onQueryChange = onQueryChange,
                onCreateClick = onCreateClick,
                onRenameClick = onRenameClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }

    uiState.nameDialog?.let { dialog ->
        val renaming = uiState.renaming
        if (renaming == null) {
            CustomTagNameDialog(state = dialog, onCreate = onNameConfirm, onDismiss = onNameDialogDismiss)
        } else {
            CustomTagNameDialog(
                state = dialog,
                onCreate = onNameConfirm,
                onDismiss = onNameDialogDismiss,
                title = renameTitle(renaming.kind),
                confirmLabel = "Rename",
            )
        }
    }
    uiState.deleteConfirm?.let { confirm ->
        DeleteConfirmDialog(confirm = confirm, onConfirm = onDeleteConfirm, onDismiss = onDeleteDismiss)
    }
    uiState.importConfirm?.let { confirm ->
        ImportConfirmDialog(confirm = confirm, onConfirm = onImportConfirm, onDismiss = onImportDismiss)
    }
}

/** The bar's ⋮: Export and Import of the list file. Disabled while a delete runs. */
@Composable
private fun ListFileMenu(enabled: Boolean, onExportClick: () -> Unit, onImportClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OneUiIconButton(
            icon = RollaIcons.More,
            contentDescription = "More options",
            onClick = { expanded = true },
            enabled = enabled,
        )
        OneUiDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            OneUiMenuItem(
                text = "Export",
                onClick = {
                    expanded = false
                    onExportClick()
                },
            )
            OneUiMenuItem(
                text = "Import",
                onClick = {
                    expanded = false
                    onImportClick()
                },
            )
        }
    }
}

/** The delete's files done of its total, inset like the list rows. */
@Composable
private fun DeleteProgress(progress: BatchProgress) {
    BatchSaveProgress(
        progress = progress,
        modifier = Modifier.padding(horizontal = RollaDimens.listThumbStart, vertical = RollaDimens.editorFieldGap),
    )
}

/** The tab row, then the two pages, each in a [ContentPanel], as on Home. */
// One state plus the five page events.
@Suppress("LongParameterList")
@Composable
private fun ManageTabsAndPager(
    uiState: ManageCustomTagsUiState,
    onPageChange: (CustomTagKind) -> Unit,
    onQueryChange: (CustomTagKind, String) -> Unit,
    onCreateClick: (CustomTagKind, String) -> Unit,
    onRenameClick: (CustomTag) -> Unit,
    onDeleteClick: (CustomTag) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = MANAGE_PAGES.indexOf(uiState.page)) { MANAGE_PAGES.size }
    ManagePagerSync(pagerState = pagerState, page = uiState.page, onPageChange = onPageChange)
    Column(modifier = Modifier.fillMaxSize()) {
        OneUiTabRow(
            titles = MANAGE_TAB_TITLES,
            pagerState = pagerState,
            onTabClick = { index -> onPageChange(MANAGE_PAGES[index]) },
        )
        Spacer(modifier = Modifier.height(RollaDimens.panelTopGap))
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val kind = MANAGE_PAGES[index]
            ContentPanel {
                ManagePage(
                    kind = kind,
                    all = if (kind == CustomTagKind.TAG) uiState.tags else uiState.subTags,
                    query = if (kind == CustomTagKind.TAG) uiState.tagQuery else uiState.subTagQuery,
                    onQueryChange = { onQueryChange(kind, it) },
                    onCreateClick = { onCreateClick(kind, it) },
                    onRenameClick = onRenameClick,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }
}

/**
 * Keeps the pager and [page] in step, as the picker's sync does: the pager opens on [page], each later [page] the owner
 * sets moves it there (a spring, or a snap under reduced motion), and whenever the pager comes to rest on another page,
 * that page is reported through [onPageChange]. A page that already matches does nothing, so the two directions never
 * echo each other, and a tab tap whose animation is dragged back still ends with the owner on the page shown.
 */
@Composable
private fun ManagePagerSync(pagerState: PagerState, page: CustomTagKind, onPageChange: (CustomTagKind) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    var followedPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (page == followedPage) {
            return@LaunchedEffect
        }
        followedPage = page
        val target = MANAGE_PAGES.indexOf(page)
        if (pagerState.targetPage != target) {
            pagerState.animateScrollToPage(target, animationSpec = rollaSpringOrSnap(reducedMotion))
        }
    }
    val currentPage by rememberUpdatedState(page)
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, settled) ->
                val settledKind = MANAGE_PAGES[settled]
                if (settledKind != currentPage) currentOnPageChange(settledKind)
            }
    }
}

/** One page: the search field over the "New" row and the entries [query] matches, or the empty state. */
// Seven inputs of one private page: its slice of the state, its four events and the kind. LongMethod: the search
// field and the one LazyColumn of the "New" row, the empty state and the entries.
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun ManagePage(
    kind: CustomTagKind,
    all: List<ManagedTag>,
    query: String,
    onQueryChange: (String) -> Unit,
    onCreateClick: (String) -> Unit,
    onRenameClick: (CustomTag) -> Unit,
    onDeleteClick: (CustomTag) -> Unit,
) {
    val shown = remember(all, query) {
        val matching = filterCustomTags(all.map(ManagedTag::tag), query).toSet()
        all.filter { it.tag in matching }
    }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier = Modifier.fillMaxSize()) {
        OneUiSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = if (kind == CustomTagKind.TAG) "Search tags" else "Search subtags",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RollaDimens.listThumbStart, vertical = RollaDimens.editorFieldGap),
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomPadding)) {
            item(key = NEW_ENTRY_KEY) {
                Column {
                    SheetActionRow(
                        icon = RollaIcons.Add,
                        label = if (kind == CustomTagKind.TAG) "New tag" else "New subtag",
                        onClick = { onCreateClick(query.trim()) },
                    )
                    // A hairline only above entries; above an empty-state message the row is the list's last.
                    if (shown.isNotEmpty()) {
                        InsetDivider(startInset = RollaDimens.listTextStart)
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = EMPTY_STATE_KEY) { ManageEmptyState(text = emptyStateText(kind, all.isEmpty())) }
            }
            itemsIndexed(items = shown, key = { _, entry -> entry.tag.id }) { index, entry ->
                ManagedTagRow(
                    entry = entry,
                    onRename = { onRenameClick(entry.tag) },
                    onDelete = { onDeleteClick(entry.tag) },
                    showDivider = index < shown.lastIndex,
                )
            }
        }
    }
}

/**
 * One entry: the name at listTextStart (in line with the "New" row's label) over its "N tracks", and a trailing ⋮ with
 * Rename and Delete. The name wraps rather than ellipsizing, so a 40-character name stays whole at a large font. The
 * row is one button for TalkBack ("Techno, 14 tracks"; a tap renames) with "Rename" and "Delete" as its actions; the ⋮
 * is its own button, "More options for Techno".
 */
// LongMethod: the row's click, its two TalkBack actions, its two texts and the divider, one row.
@Suppress("LongMethod")
@Composable
private fun ManagedTagRow(entry: ManagedTag, onRename: () -> Unit, onDelete: () -> Unit, showDivider: Boolean) {
    val name = entry.tag.name
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RollaDimens.listRowHeight)
                .clickable(onClickLabel = "Rename", onClick = onRename)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Rename") {
                            onRename()
                            true
                        },
                        CustomAccessibilityAction("Delete") {
                            onDelete()
                            true
                        },
                    )
                }
                .padding(start = RollaDimens.listTextStart, end = RollaDimens.topBarEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = RollaDimens.editorFieldGap),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.songTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tracksCountLabel(entry.songCount),
                    style = MaterialTheme.typography.artistName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(RollaDimens.listTrailingGap))
            RowMenu(name = name, onRename = onRename, onDelete = onDelete)
        }
        // No hairline under the list's last row.
        if (showDivider) {
            InsetDivider(startInset = RollaDimens.listTextStart)
        }
    }
}

/** A row's ⋮, "More options for <name>": Rename, and a destructive Delete. */
@Composable
private fun RowMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OneUiIconButton(
            icon = RollaIcons.More,
            contentDescription = "More options for $name",
            onClick = { expanded = true },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            iconSize = RollaDimens.overflowGlyph,
        )
        OneUiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OneUiMenuItem(
                text = "Rename",
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            OneUiMenuItem(
                text = "Delete",
                onClick = {
                    expanded = false
                    onDelete()
                },
                destructive = true,
            )
        }
    }
}

/** A page's one-line empty state, in line with the entry names. */
@Composable
private fun ManageEmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.emptyState,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = RollaDimens.listTextStart, vertical = RollaDimens.editorFieldGap),
    )
}

/**
 * "Delete "Hard"?" with "Cancel" and a destructive "Delete"; the body "Remove "Hard" from 3 tracks?" only when tracks
 * carry the entry (with none, the title says it all).
 */
@Composable
private fun DeleteConfirmDialog(confirm: DeleteConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = confirm.tag.name
    val body: (@Composable () -> Unit)? = if (confirm.songCount > 0) {
        { Text(text = "Remove \"$name\" from ${tracksCountLabel(confirm.songCount)}?") }
    } else {
        null
    }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "Delete \"$name\"?",
        confirmButton = { OneUiTextButton(text = "Delete", onClick = onConfirm, destructive = true) },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        text = body,
    )
}

/**
 * "Replace your tags?" with "Cancel" and "Import". The body says "Importing replaces your tags and subtags." and, when
 * the import changes what tracks show, "These tracks will show different tags:" over one line per clash
 * ([warningLine]). The body scrolls, so a long clash list keeps the buttons on screen.
 */
@Composable
private fun ImportConfirmDialog(confirm: ImportConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "Replace your tags?",
        confirmButton = { OneUiTextButton(text = "Import", onClick = onConfirm) },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
            ) {
                Text(text = "Importing replaces your tags and subtags.")
                if (confirm.clashes.isNotEmpty()) {
                    Column {
                        Text(text = "These tracks will show different tags:")
                        confirm.clashes.forEach { clash -> Text(text = clash.warningLine()) }
                    }
                }
            }
        },
    )
}

/** "Rename tag" or "Rename subtag": the name dialog's title while it renames an entry. */
private fun renameTitle(kind: CustomTagKind): String = when (kind) {
    CustomTagKind.TAG -> "Rename tag"
    CustomTagKind.SUBTAG -> "Rename subtag"
}

/** "No tags yet"/"No subtags yet" for an empty list, else "No matches" (the query filtered everything out). */
private fun emptyStateText(kind: CustomTagKind, listIsEmpty: Boolean): String = when {
    !listIsEmpty -> "No matches"
    kind == CustomTagKind.TAG -> "No tags yet"
    else -> "No subtags yet"
}

/** The pager's pages, in tab order. */
private val MANAGE_PAGES = listOf(CustomTagKind.TAG, CustomTagKind.SUBTAG)
private val MANAGE_TAB_TITLES = listOf("Tags", "Subtags")

// The list file (spec §5.2): saved as JSON under the suggested name, opened as JSON or as plain text, since some
// providers mis-type JSON.
private const val LIST_FILE_MIME_TYPE = "application/json"
private const val LIST_FILE_NAME = "rolla-tags.json"
private val LIST_FILE_OPEN_TYPES = arrayOf(LIST_FILE_MIME_TYPE, "text/plain")

private const val NEW_ENTRY_KEY = "new_entry"
private const val EMPTY_STATE_KEY = "empty_state"

private fun previewUiState(): ManageCustomTagsUiState = ManageCustomTagsUiState(
    tags = listOf(
        ManagedTag(CustomTag(CustomTagKind.TAG, id = 6L, name = "Acid"), songCount = 0),
        ManagedTag(CustomTag(CustomTagKind.TAG, id = 3L, name = "Hard"), songCount = 3),
        ManagedTag(CustomTag(CustomTagKind.TAG, id = 9L, name = "Progressive House And Melodic Techno Mix"), 1),
        ManagedTag(CustomTag(CustomTagKind.TAG, id = 2L, name = "Techno"), songCount = 14),
    ),
    subTags = listOf(ManagedTag(CustomTag(CustomTagKind.SUBTAG, id = 1L, name = "Peak time"), songCount = 1)),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Manage Tags - Light")
@Preview(name = "Manage Tags - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewManageCustomTagsScreen() {
    PreviewManageCustomTags(uiState = previewUiState())
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Manage Tags Deleting - Light")
@Preview(name = "Manage Tags Deleting - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewManageCustomTagsScreenDeleting() {
    PreviewManageCustomTags(
        uiState = previewUiState().copy(isBusy = true, progress = BatchProgress(done = 1, total = 3)),
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Manage Tags Empty - Light")
@Preview(name = "Manage Tags Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewManageCustomTagsScreenEmpty() {
    PreviewManageCustomTags(uiState = ManageCustomTagsUiState())
}

/** The screen in the app theme with every callback a no-op. */
@Composable
private fun PreviewManageCustomTags(uiState: ManageCustomTagsUiState) {
    RollaMusicPlayerTheme {
        ManageCustomTagsScreen(
            uiState = uiState,
            onNavigateUp = {},
            onPageChange = {},
            onQueryChange = { _, _ -> },
            onCreateClick = { _, _ -> },
            onRenameClick = {},
            onNameConfirm = {},
            onNameDialogDismiss = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onDismissMessage = {},
            onExportClick = {},
            onImportClick = {},
            onImportConfirm = {},
            onImportDismiss = {},
        )
    }
}

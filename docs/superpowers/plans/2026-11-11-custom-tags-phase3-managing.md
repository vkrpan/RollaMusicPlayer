# Custom Tags, Phase 3 (Managing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Manage tags" screen (from Home's ⋮ and from the tag picker) where the user renames tags and subtags,
deletes them (removing the ID from every file that carries it), and exports or imports the tag lists as a JSON file;
open editors stay correct when the user returns from it.

**Architecture:** The lists, delete, import and ID reservation live in `CustomTagRepository`/`CustomTagDao`; the list
file's codec sits next to the field codec in `:core:tagio`. The screen and its ViewModel live in `:feature:tageditor`
(package `managetags`), because deleting reuses the editors' consent and cache-copy file path. A new route
`ManageCustomTags` is reached from Home's ⋮ and from a "Manage tags" link in the picker; on return, both editors prune
their ticks to the current lists and the single editor writes the user's diff instead of a full set.

**Tech Stack:** Kotlin 2.2, Compose (One UI kit), Room, Hilt, Navigation (type-safe routes), Storage Access Framework,
kotlinx-serialization-json, jaudiotagger via `:core:tagio`, JUnit4/MockK/Turbine/Robolectric.

**Spec:** [[2026-11-09-custom-tags-design]] (§5.2 export/import and the clash warning, §5.4 deleting, §6 calls 7, 8, 13).
Roadmap: [[2026-11-09-custom-tags-roadmap]]. Consumes phases 1–2 (committed `dce76dd`, `eb9ebcb`) and their hand-off
in [[Custom tags]] ("Phase 3 hand-off"). Where this plan and the spec disagree, the spec wins; record a ruling.

## Global Constraints

- **List file (spec §5.2), exact shape:**
  `{"format":"rolla-tags","version":1,"tags":[{"id":2,"name":"Techno"}],"subTags":[{"id":1,"name":"Hard"}]}`;
  export suggests the file name `rolla-tags.json` (SAF `CreateDocument("application/json")`); import opens one file
  (SAF `OpenDocument` of `application/json` and `text/plain`, since some providers mis-type JSON).
- **Import replaces** the whole lists, after a confirmation. A malformed file changes nothing and says why.
- **Clash warning (spec §5.2):** for each ID that tracks carry and whose name changes on import, one line
  `2: Techno → Jazz, on 14 tracks`; for an ID that tracks carry and the file lacks, `5: House → removed, on 3 tracks`
  (Claude's call: the same harm, a silent rename to nothing). Counts use `tracksCountLabel` wording ("1 track").
- **Counters only rise:** import raises each list's counter to the larger of its value and the file's highest ID;
  delete never lowers it; IDs are never reused. All IDs stay within `MAX_CUSTOM_TAG_ID`.
- **Delete (spec §5.4, §6 call 13):** confirm with the count; one consent prompt for all files; MP3/FLAC files lose the
  ID (unknown IDs and the other entries stay); the list entry and its Room links are removed even if some files were
  not updated (M4A, refused consent, IO error); a partial result says "Updated X of N tracks.".
- **Names (spec §6 call 8):** rename uses the phase 1 rules (`CustomTagRepository.rename`), the phase 2 name dialog and
  its refusal copy.
- **Copy, verbatim, British:** "Manage tags" (menu item, picker link, screen title), "Tags", "Subtags", "Search tags",
  "Search subtags", "New tag", "New subtag", "Rename", "Delete", "Cancel", "Export", "Import", "Rename tag",
  "Rename subtag", "Delete \"<name>\"?", "Remove \"<name>\" from <N tracks>?", "Updated X of N tracks.",
  "Tags exported", "Couldn't export the tags.", "Couldn't read that file.", "Replace your tags?",
  "Importing replaces your tags and subtags.", "These tracks will show different tags:", "Tags imported",
  "No tags yet", "No subtags yet", "No matches". Phase 2's refusal copy is reused unchanged.
- **Kit only** ([[Build UI from tokens and the kit]]): `OneUiDetailTopBar`, `OneUiTabRow` + `HorizontalPager`,
  `OneUiSearchField`, `ListRowScaffold`/`InsetDivider`, `OneUiDropdownMenu`/`OneUiMenuItem(destructive = true)`,
  `OneUiDialog` + `OneUiTextButton(destructive = true)`, `OneUiDialogField`, `RollaIcons`; no hardcoded colour, type, dp.
- Writes keep the consent and cache-copy path ([[Tag writes go through a cache copy]]) and run `TagSaveFinalizer` with
  the succeeded songs after the loop.
- Vocabulary `Song`/`CustomTag*` ([[Song in code, Tracks on screen]]); navigation per `.claude/rules/navigation-conventions.md`
  (a `@Serializable data object` route in `Routes.kt`, `launchSingleTop`, `navigateUp()`).
- No `sed -i`, no heredocs for code ([[Edit files with Write and Edit, not the shell]]); "Commit" steps mean "stage and
  report ready to commit" ([[Commits are user-triggered]]); implementers may make mechanical fixes to this plan's code.
- Device ([[Device safety]]): only `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest` and,
  in Task 6, `:app:installBenchmark`; the agent never saves tags, deletes, imports or exports on the phone — the walk is
  the user's hand.

## Review Focus

1. **Deleting a tag that M4A or WAV tracks also carry** (in Room, from the read-only scan). Those files keep the ID,
   which becomes unknown; the entry is gone; the message counts them as not updated. Pinned by Task 2's
   `delete_mixedFormats_updatesMp3sAndReportsPartial`.
2. **Declining the consent prompt on delete.** Nothing is written, the entry is still deleted (spec §6 call 13), the
   message is "Updated 0 of N tracks.". Pinned by Task 2's `delete_consentDenied_deletesEntryAndReportsZero`.
3. **Importing a file with a duplicate ID, a blank or 41-character name, two names equal ignoring case, an ID above the
   ceiling, a wrong `format`, or plain garbage.** Nothing changes; "Couldn't read that file." Pinned by Task 1's
   `decode_*` tests.
4. **Opening Manage tags from an open editor, deleting a ticked tag there, returning and saving.** The saved file does
   not regain the deleted ID; an ID that the import made known is not dropped. Pinned by Task 5's
   `returnFromManage_deletedTickedTag_isNotWrittenBack` and `returnFromManage_importedKnownId_isKept`.
5. **Creating a tag right after install, before the scan has read a file that carries a higher ID.** The new tag gets
   an ID above the file's. Pinned by Task 5's `load_reservesIdsFromTheFile`.

---

### Task 1: Data — delete, import, reservation, and the list-file codec

Owner: data-layer-agent (Room, repository) with tag-editor-agent (codec).

**Files:**
- Modify: `core/database/.../dao/CustomTagDao.kt`, `core/testing/.../FakeCustomTagDao.kt`
- Create: `core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/CustomTagListJson.kt`
- Modify: `core/data/.../repository/CustomTagRepository.kt`, `CustomTagRepositoryImpl.kt`
- Test: `core/database/src/androidTest/.../dao/CustomTagDaoTest.kt`, `core/tagio/src/test/.../CustomTagListJsonTest.kt`,
  `core/data/src/test/.../repository/CustomTagRepositoryImplTest.kt`

**Interfaces:**
- `CustomTagDao` gains:
  ```kotlin
  @Transaction suspend fun deleteCustomTag(kind: CustomTagKind, id: Long)        // the entry and every song link to it; counters untouched
  suspend fun songIdsCarrying(kind: CustomTagKind, id: Long): List<String>
  suspend fun linkCounts(kind: CustomTagKind): Map<Long, Int>                      // id → number of songs carrying it (any ID, known or not)
  @Transaction suspend fun replaceAll(tags: List<CustomTagEntity>, subTags: List<CustomTagEntity>) // entries only; links kept; counters raised, never lowered
  ```
  and `insertNew` returns `Long?` — `null` when the next ID would pass `MAX_CUSTOM_TAG_ID`, inside its transaction
  (replaces the phase 1 `IllegalStateException`; closes the last-ID race, [[Custom tags]] known limits).
- `:core:model` produces (moved there in Task 1's fix round, with `CUSTOM_TAG_NAME_MAX_LENGTH`):
  `data class CustomTagListFile(val tags: List<CustomTag>, val subTags: List<CustomTag>)`. `:core:tagio` produces:
  ```kotlin
  object CustomTagListJson {
      fun encode(file: CustomTagListFile): String      // the exact shape above; entries in ascending ID order; pretty-printed with 2-space indent
      fun decode(text: String): CustomTagListFile?     // null on any rule break (see Review Focus 3)
  }
  ```
- `CustomTagRepository` gains:
  ```kotlin
  suspend fun delete(kind: CustomTagKind, id: Long)
  suspend fun songIdsCarrying(kind: CustomTagKind, id: Long): List<String>
  fun observeLinkCounts(kind: CustomTagKind): Flow<Map<Long, Int>>   // drives row subtitles and the delete count
  suspend fun exportLists(): CustomTagListFile                         // current lists
  suspend fun previewImport(file: CustomTagListFile): List<ImportClash>
  suspend fun applyImport(file: CustomTagListFile)
  suspend fun reserveIds(ids: CustomTagIds)                            // raise counters to the IDs' maximum per kind
  data class ImportClash(val kind: CustomTagKind, val id: Long, val currentName: String, val importedName: String?, val trackCount: Int)
  ```
  `previewImport` returns one clash per ID that has `trackCount > 0` and whose name differs (exact string compare) or is
  absent from the file (`importedName = null`), ordered tags first, then subtags, by ID. `create` maps `insertNew`'s
  `null` to `LimitReached`.
  (`observeLinkCounts` needs a Flow DAO query; add `observeLinkCounts(kind): Flow<List<LinkCount>>` to the DAO with a
  tiny `LinkCount(tagId, count)` row class if that is cleaner than the map query — the implementer picks one.)

- [ ] **Step 1: Write the failing tests**

```kotlin
// CustomTagListJsonTest
@Test fun encode_exactShape()                         // {format, version 1, tags [{id 2, name Techno}], subTags [{id 1, name Hard}]}; parse back with Json and compare fields
@Test fun decode_roundTrips()
@Test fun decode_wrongFormat_isNull()                 // "format":"other"
@Test fun decode_wrongVersion_isNull()                // version 2
@Test fun decode_duplicateIdInAList_isNull()
@Test fun decode_duplicateNameIgnoringCase_isNull()   // "Techno" and "techno" in tags
@Test fun decode_blankOrTooLongName_isNull()          // "  " and 41 code points
@Test fun decode_idAboveCeilingOrNotPositive_isNull() // 1000000000 and 0
@Test fun decode_garbage_isNull()                     // "not json", "[]"
@Test fun decode_sameNameAcrossLists_isFine()         // TAG "Hard" and SUBTAG "Hard"

// CustomTagDaoTest (instrumented)
@Test fun deleteCustomTag_removesEntryAndLinksKeepsCounter() // TAG 2 linked to two songs → entry and links gone; next insertNew(TAG) == 3 when counter was 2
@Test fun insertNew_atCeiling_returnsNull()          // replaces insertNew_atCeiling_throwsAndInsertsNothing
@Test fun replaceAll_keepsLinksAndRaisesCounters()   // links to TAG 7 survive; counter raised to file max; never lowered below the old counter
@Test fun linkCounts_countsKnownAndUnknownIds()

// CustomTagRepositoryImplTest
@Test fun delete_removesEntryAndLinks()
@Test fun previewImport_reportsRenamedAndRemovedCarriedIds() // here TAG 2 "Techno" (14 links), TAG 5 "House" (3 links), TAG 6 "Acid" (0 links); file TAG 2 "Jazz", TAG 6 "Acid2"
                                                     // → [Clash(TAG, 2, "Techno", "Jazz", 14), Clash(TAG, 5, "House", null, 3)]; TAG 6 absent (no tracks)
@Test fun previewImport_sameNames_noClashes()
@Test fun applyImport_replacesListsAndRaisesCounters()
@Test fun reserveIds_raisesCountersSoCreateIssuesAbove() // reserve TAG 40 → create(TAG) issues 41
@Test fun create_atCeiling_isLimitReachedWithoutThrowing()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:tagio:testDebugUnitTest :core:data:testDebugUnitTest --tests "*CustomTag*" :core:database:compileDebugAndroidTestKotlin`; Expected: FAIL (compilation).
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run to verify they pass** — the same plus `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(tags): delete, import and reserve custom tag IDs`)

---

### Task 2: Manage tags ViewModel — rename, create, delete with file clean-up

Owner: viewmodel-architect with tag-editor-agent (file IO, consent).

**Files:**
- Create: `feature/tageditor/src/main/kotlin/com/rolla/musicplayer/feature/tageditor/managetags/ManageCustomTagsViewModel.kt`,
  `ManageCustomTagsUiState.kt`
- Test: `feature/tageditor/src/test/kotlin/com/rolla/musicplayer/feature/tageditor/managetags/ManageCustomTagsViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1; phase 2's `CustomTagEditingHandlers`-style patterns (lists observer with `.catch`, refusal copy via
  `refusalMessage()`); `SongFileResolver`, `RollaTagsFileIo`, `canWriteCustomTags`, `TagSaveFinalizer`,
  `SongRepository.observeSong`.
- `ManageCustomTagsUiState`: `page: CustomTagKind`, `tagQuery`, `subTagQuery`, `tags`/`subTags` (`List<ManagedTag>` with
  `tag: CustomTag` and `trackCount: Int`, library order), `nameDialog: CustomTagNameDialogState?` plus
  `renaming: CustomTag?` (null = create), `deleteConfirm: DeleteConfirm?` (`tag`, `trackCount`), `consentRequest:
  List<String>?`, `recoveryRequest`, `progress: BatchProgress?`, `message: String?`, `isBusy`.
- Handlers: `onPageChange`, `onQueryChange`, `onCreateClick(kind, name)`, `onRenameClick(tag)`, `onNameConfirm(name)`,
  `onNameDialogDismiss`, `onDeleteClick(tag)`, `onDeleteConfirm()`, `onDeleteDismiss()`, `onConsentGranted()`,
  `onConsentDenied()`, `onRecoveryGranted()`, `dismissMessage()`.
- Delete flow: confirm → `songIdsCarrying` → resolve songs → `consentRequest` = their uris (skip the prompt when there
  are none) → loop per song (copy → `canWriteCustomTags(copy.extension)` else not updated → read → remove the ID → write
  and persist only if it changed; a file that no longer carries the ID counts as updated with no write → delete copy; API 29 recovery per file like `SelectionArtworkViewModel`) → then
  `repository.delete(kind, id)` → `TagSaveFinalizer.onSongsSaved(succeeded)` → message "Updated X of N tracks." only
  when X < N. Consent denied → no writes, `repository.delete`, message "Updated 0 of N tracks.". The copy is always
  deleted. Rename and create use the dialog with phase 2's refusal copy; a `Saved` closes it.

- [ ] **Step 1: Write the failing tests** (real `CustomTagRepositoryImpl` over `FakeCustomTagDao`; real MP3/FLAC/M4A
  fixtures in a `TemporaryFolder`, as the phase 2 editor tests do)

```kotlin
@Test fun lists_showTrackCountsInLibraryOrder()
@Test fun rename_savedClosesDialog_duplicateShowsError()
@Test fun create_savedAddsEntry()
@Test fun deleteClick_showsConfirmWithCount()                 // TAG "Hard" carried by 3 songs → DeleteConfirm(tag, 3)
@Test fun delete_noTracks_deletesWithoutConsent()             // consentRequest stays null; entry gone
@Test fun delete_allMp3_removesIdFromEveryFileAndEntry()      // two MP3 copies {"tags":"2,5"} → {"tags":"5"}; entry 2 gone; no message
@Test fun delete_keepsUnknownAndOtherIds()                   // file {"tags":"2,99","subTags":"2"} delete TAG 2 → {"tags":"99","subTags":"2"}
@Test fun delete_mixedFormats_updatesMp3sAndReportsPartial()  // MP3 + M4A carry TAG 2 → MP3 cleaned, M4A bytes unchanged, entry gone, "Updated 1 of 2 tracks."
@Test fun delete_consentDenied_deletesEntryAndReportsZero()   // no writes; entry gone; "Updated 0 of 2 tracks."
@Test fun delete_finalizerGetsOnlySucceededSongs()
@Test fun delete_copyAlwaysDeleted()                          // a write that throws → copy deleted, counted not updated
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:tageditor:testDebugUnitTest --tests "*ManageCustomTagsViewModelTest"`; Expected: FAIL (compilation).
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run to verify they pass** — `./gradlew :feature:tageditor:testDebugUnitTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(tags): manage tags view model — rename, create, delete`)

---

### Task 3: Manage tags screen

Owner: ui-builder.

**Files:**
- Create: `feature/tageditor/.../managetags/ManageCustomTagsScreen.kt` (route + stateless screen)
- Test: `feature/tageditor/src/test/.../managetags/ManageCustomTagsScreenTest.kt`

**Interfaces:**
- Consumes: Task 2's state and handlers; phase 2's `CustomTagNameDialog`; `rememberMediaWriteRequester()`.
- Produces: `@Composable fun ManageCustomTagsRoute(onNavigateUp: () -> Unit, viewModel: ManageCustomTagsViewModel = hiltViewModel(), modifier: Modifier = Modifier)`.
- Layout: `OneUiDetailTopBar("Manage tags")` with a ⋮ (`Export`, `Import`; in Task 3 they call lambdas the route
  passes, which stay no-ops until Task 4 wires them); `OneUiTabRow(["Tags", "Subtags"])` +
  `HorizontalPager(2)` synced like the picker (owner holds `page`); per page `OneUiSearchField` ("Search tags"/"Search
  subtags"), a "New tag"/"New subtag" row, then rows: name, subtitle "N tracks" (`tracksCountLabel`), tap = rename,
  trailing ⋮ menu `Rename` / `Delete` (destructive); empty states as the picker; the delete confirm `OneUiDialog`
  ("Delete \"Hard\"?" and, when N > 0, the body "Remove \"Hard\" from 3 tracks?"; buttons "Cancel" / "Delete"
  destructive); a progress line while deleting; messages as a transient text (the screen's existing message pattern,
  as the batch editor shows "Edited X of N tracks."). The mini-player is hidden on this screen.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun showsTabsSearchAndRowsWithCounts()
@Test fun rowTap_opensRenameDialogPrefilled()
@Test fun rowMenu_delete_opensConfirmWithCount()       // "Remove \"Hard\" from 3 tracks?"
@Test fun deleteConfirm_zeroTracks_hasNoBody()
@Test fun message_isShownAndDismissed()
@Test fun largeFont_rowsWrapWithoutOverflow()          // 360 dp, fontScale 1.5, a 40-character name: no visual overflow on the name text
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:tageditor:testDebugUnitTest --tests "*ManageCustomTagsScreenTest"`; Expected: FAIL.
- [ ] **Step 3: Implement** (previews light/dark).
- [ ] **Step 4: Run to verify they pass** — `./gradlew :feature:tageditor:testDebugUnitTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(tags): manage tags screen`)

---

### Task 4: Export and import

Owner: viewmodel-architect with ui-builder.

**Files:**
- Modify: `ManageCustomTagsViewModel.kt`, `ManageCustomTagsUiState.kt`, `ManageCustomTagsScreen.kt`
- Create: `feature/tageditor/.../managetags/CustomTagListDocuments.kt` (a small `@Inject` seam reading/writing a
  `content://` document's text through `ContentResolver`, so VM tests fake it)
- Test: `ManageCustomTagsViewModelTest.kt`, `ManageCustomTagsScreenTest.kt`

**Interfaces:**
- `interface CustomTagListDocuments { suspend fun write(uri: String, text: String); suspend fun read(uri: String): String }`
  with an Android implementation bound in the feature's Hilt module.
- State gains `importConfirm: ImportConfirm?` (`file: CustomTagListFile`, `clashes: List<ImportClash>`).
- Handlers: `onExportDocumentChosen(uri)`, `onImportDocumentChosen(uri)`, `onImportConfirm()`, `onImportDismiss()`.
- Flow: Export → SAF create (screen) → `encode(exportLists())` → write → "Tags exported" / "Couldn't export the
  tags.". Import → SAF open → read → `decode` (null or a read error → "Couldn't read that file.") → `previewImport` →
  confirm dialog: title "Replace your tags?", body "Importing replaces your tags and subtags." and, when there are
  clashes, "These tracks will show different tags:" followed by one line per clash (`2: Techno → Jazz, on 14 tracks`,
  `5: House → removed, on 3 tracks`) → Import → `applyImport` → "Tags imported". Cancel changes nothing.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun export_writesTheExactJson()                  // fake documents captures text == CustomTagListJson.encode(current lists)
@Test fun export_writeFails_showsCouldNotExport()
@Test fun import_malformed_showsCouldNotReadAndChangesNothing()
@Test fun import_withClashes_showsLinesThenReplacesOnConfirm() // lines "2: Techno → Jazz, on 14 tracks" and "5: House → removed, on 3 tracks"; after confirm lists == file; "Tags imported"
@Test fun import_cancel_changesNothing()
@Test fun importScreen_confirmDialogListsClashes()      // screen test with a state carrying two clashes
```

- [ ] **Step 2: Run to verify they fail**; **Step 3: Implement** (SAF launchers in the route: `CreateDocument("application/json")`
  with `rolla-tags.json`, `OpenDocument(arrayOf("application/json", "text/plain"))`); **Step 4: Run** `./gradlew
  :feature:tageditor:testDebugUnitTest`; **Step 5: Stage** (`feat(tags): export and import the tag lists`).

---

### Task 5: Navigation, the picker link, and editors that stay correct after Manage tags

Owner: navigation-agent (route, Home ⋮) with viewmodel-architect (editors) and ui-builder (picker link).

**Files:**
- Modify: `app/.../navigation/Routes.kt` (`@Serializable data object ManageCustomTags : Route`),
  `navigation/MiniPlayerVisibility.kt` (in `allRoutes` and `routesWithoutMiniPlayer`), `MainActivity.kt` (destination;
  `TagEditorRoute`/`BatchTagEditorRoute` get `onManageTagsClick`), `home/HomeScreen.kt` (⋮ "Manage tags" above
  "Settings", `launchSingleTop`)
- Modify: `core/ui/.../customtags/CustomTagPickerSheet.kt` (`onManageClick: (() -> Unit)? = null` → a "Manage tags"
  text link under the list when non-null)
- Modify: `feature/tageditor/.../CustomTagEditing.kt`, `TagEditorViewModel.kt`, `BatchTagEditorViewModel.kt`,
  `TagEditorScreen.kt`, `BatchTagEditorScreen.kt`
- Test: `app` `MiniPlayerVisibilityTest` (route count), a Home menu test, `CustomTagPickerSheetTest`,
  `CustomTagEditingHandlersTest`, `TagEditorViewModelTest`, `BatchTagEditorViewModelTest`

**Interfaces:**
- **Pruning (both editors, in `CustomTagEditingHandlers`):** whenever the lists emit, `picker.selected` keeps only IDs
  present in the lists (Manage may have deleted one).
- **Single editor write = the user's diff:** `ids = (inFile − (loadedKnown − selected)) ∪ selected`, where `inFile` is
  read from the copy at save time and `loadedKnown` is the known-ID baseline captured at load (never pruned). This keeps
  IDs the user never touched, including IDs an import made known, and drops IDs the user unticked or that Manage deleted
  while the editor was open. The phase 2 rule "write only when the ticks changed" stays (`selected != loadedKnown`).
- **Reservation:** the single editor calls `repository.reserveIds(fileIo.read(copy).customTagIds)` at load (all IDs,
  known or not), so a tag created before the scan reaches that file gets a higher ID.
- **Picker link:** both editor screens pass `onManageClick` to the sheet; the route closes the sheet and navigates to
  `ManageCustomTags`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun homeMenu_manageTags_navigates()                       // ⋮ shows "Manage tags" before "Settings"; click → onManageTagsClick
@Test fun picker_manageLink_shownOnlyWithCallback()
@Test fun lists_change_prunesSelectedToKnownIds()               // CustomTagEditingHandlersTest
@Test fun returnFromManage_deletedTickedTag_isNotWrittenBack()  // copy {"tags":"2,3"} both known, both ticked; delete TAG 2 in the repo; untick nothing; save → file {"tags":"3","subTags":""}
@Test fun returnFromManage_importedKnownId_isKept()             // copy {"tags":"3,99"} (99 unknown at load); import makes 99 known; tick TAG 4; save → {"tags":"3,4,99",...}
@Test fun load_reservesIdsFromTheFile()                         // copy carries TAG 40 → after load, create(TAG) issues 41
@Test fun batch_chosenDeletedTag_isPrunedBeforeSave()
@Test fun miniPlayer_hiddenOnManageTags()                       // routesWithoutMiniPlayer contains ManageCustomTags; allRoutes count updated
```

- [ ] **Step 2: Run to verify they fail**; **Step 3: Implement**; **Step 4: Run** `./gradlew :app:testDebugUnitTest
  :core:ui:testDebugUnitTest :feature:tageditor:testDebugUnitTest`; **Step 5: Stage**
  (`feat(tags): reach Manage tags from Home and the picker; editors stay correct`).

---

### Task 6: Gate, device walk and vault

Owner: the controller.

- [ ] **Step 1:** `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`
  → BUILD SUCCESSFUL; `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest` → all pass.
- [ ] **Step 2:** `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`. The user's walk (their hand):
  1. Home ⋮ → Manage tags: Techno and Hard listed with track counts; rename Hard to "Hardcore" (Edit tags on a tagged
     track shows "Hardcore").
  2. Create a throwaway subtag, tick it on one MP3 in Edit tags, save; in Manage tags delete it ("Remove … from 1
     track?") → consent → reopen that track: the subtag is gone, Techno still ticked.
  3. ⋮ Export → save `rolla-tags.json` in Downloads; open it in a file viewer: the JSON shows the lists.
  4. Rename Techno to "Techno2", then ⋮ Import the exported file → the warning lists "… Techno2 → Techno, on N
     tracks" → Import → Techno is back.
  5. From Edit tags → Tags → "Manage tags", delete a ticked tag, go back, save: the track no longer carries it.
- [ ] **Step 3:** Vault: [[Custom tags]] (progress, walk), [[feature-tageditor]], [[core-data]], [[core-database]],
  [[core-tagio]], [[core-ui]], [[app]], [[Tag editor]], [[Timeline]], [[Backlog]] (close the last-ID race, `reserveIds`,
  the Manage-tags hand-off); ledger rulings archived; wiki ingest of this plan; report ready to commit.

---

## Self-review artefacts

### Test-control table (one mutation per test; the expected value is evaluated on the test's literal input)

| Test | Mutation | Mutated result on the literal input |
|---|---|---|
| encode_exactShape | omit `"format"` | parsed object has no `format` → assertion on format fails |
| decode_roundTrips | decode drops `subTags` | subTags [] ≠ [Hard] |
| decode_wrongFormat_isNull | skip the format check | returns a file ≠ null |
| decode_wrongVersion_isNull | skip the version check | non-null |
| decode_duplicateIdInAList_isNull | skip the duplicate-ID check | non-null |
| decode_duplicateNameIgnoringCase_isNull | compare names case-sensitively | "Techno" ≠ "techno" → non-null |
| decode_blankOrTooLongName_isNull | skip name validation | non-null |
| decode_idAboveCeilingOrNotPositive_isNull | accept any Long | non-null |
| decode_garbage_isNull | let the parse exception escape | throws instead of null |
| decode_sameNameAcrossLists_isFine | check name uniqueness across both lists | null ≠ file |
| deleteCustomTag_removesEntryAndLinksKeepsCounter | delete the entry only | links remain → assertion on links fails |
| insertNew_atCeiling_returnsNull | keep the old `check()` | throws ISE instead of null |
| replaceAll_keepsLinksAndRaisesCounters | set counter = file max | counter lowered below the old value |
| linkCounts_countsKnownAndUnknownIds | inner join to `custom_tags` | unknown 99 missing |
| delete_removesEntryAndLinks | repository skips the DAO delete | entry remains |
| previewImport_reportsRenamedAndRemovedCarriedIds | skip absent IDs | the House clash missing |
| previewImport_sameNames_noClashes | report every carried ID | clashes non-empty |
| applyImport_replacesListsAndRaisesCounters | insert without the counter raise | next create collides / issues below |
| reserveIds_raisesCountersSoCreateIssuesAbove | no-op reserveIds | create issues 1 ≠ 41 |
| create_atCeiling_isLimitReachedWithoutThrowing | ignore `null` from insertNew | Saved(id = null) or crash |
| lists_showTrackCountsInLibraryOrder | counts from the wrong kind | 0 ≠ 3 |
| rename_savedClosesDialog_duplicateShowsError | close on any result | dialog null on Duplicate |
| create_savedAddsEntry | never call create | list unchanged |
| deleteClick_showsConfirmWithCount | count = 0 | 0 ≠ 3 |
| delete_noTracks_deletesWithoutConsent | always request consent | consentRequest == [] not null |
| delete_allMp3_removesIdFromEveryFileAndEntry | skip the write | field still "2,5" |
| delete_keepsUnknownAndOtherIds | write `EMPTY` | field removed |
| delete_mixedFormats_updatesMp3sAndReportsPartial | count M4A as updated | "Updated 2 of 2" / no message |
| delete_consentDenied_deletesEntryAndReportsZero | skip `repository.delete` on denial | entry remains |
| delete_finalizerGetsOnlySucceededSongs | pass all songs | finalizer called with 2 ≠ 1 |
| delete_copyAlwaysDeleted | delete only on success | copy left in cache |
| showsTabsSearchAndRowsWithCounts | drop the subtitle | "3 tracks" not found |
| rowTap_opensRenameDialogPrefilled | open an empty dialog | field text "" |
| rowMenu_delete_opensConfirmWithCount | static body | text mismatch |
| deleteConfirm_zeroTracks_hasNoBody | always show the body | "from 0 tracks" found |
| message_isShownAndDismissed | never dismiss | message still shown |
| largeFont_rowsWrapWithoutOverflow | `maxLines = 1` + ellipsis | `hasVisualOverflow == true` |
| export_writesTheExactJson | write the field codec instead | text mismatch |
| export_writeFails_showsCouldNotExport | swallow silently | message null |
| import_malformed_showsCouldNotReadAndChangesNothing | apply partial files | lists changed |
| import_withClashes_showsLinesThenReplacesOnConfirm | apply before confirm | lists replaced before confirm |
| import_cancel_changesNothing | apply on dismiss | lists replaced |
| importScreen_confirmDialogListsClashes | omit the lines | "2: Techno → Jazz, on 14 tracks" not found |
| homeMenu_manageTags_navigates | menu item absent | not found |
| picker_manageLink_shownOnlyWithCallback | always show | link found with null callback |
| lists_change_prunesSelectedToKnownIds | no pruning | deleted ID stays selected |
| returnFromManage_deletedTickedTag_isNotWrittenBack | write `inFile ∪ selected` (no diff) | file keeps 2 |
| returnFromManage_importedKnownId_isKept | phase 2 formula `selected ∪ (inFile − known-now)` | 99 dropped (now known, not selected) |
| load_reservesIdsFromTheFile | skip reserveIds | create issues 1 ≠ 41 |
| batch_chosenDeletedTag_isPrunedBeforeSave | no pruning | deleted ID added to the file |
| miniPlayer_hiddenOnManageTags | not in `routesWithoutMiniPlayer` | shows pill |

### Per-screen concerns

| Surface | Status bar | Navigation bar | Keyboard | TalkBack | Reduced motion |
|---|---|---|---|---|---|
| Manage tags | `OneUiDetailTopBar` | list bottom padding for the navigation bar (no mini-player) | search field and the dialogs: the column uses IME padding like the tag editor | rows: "Techno, 14 tracks" + actions "Rename", "Delete"; ⋮ menus announce items | pager/tab `rollaSpringOrSnap` |
| Delete confirm / import confirm / rename dialogs | `OneUiDialog` (kit) | kit | `OneUiDialogField` (rename) | title + body read; destructive button labelled | kit |
| Picker "Manage tags" link | sheet (kit) | sheet | — | a button "Manage tags" | — |
| Home ⋮ | existing | existing | — | menu item "Manage tags" | existing |

### Earlier rulings and hand-offs checked

- Phase 2 hand-off (stale copy after Manage; diff-based write; pruning; `reserveIds`): Task 5.
- Phase 1 known limit (last-ID race): Task 1 (`insertNew` → `null`).
- Phase 2 Ruling 7 (write only when ticks changed) and Ruling 8 (unreadable field disables rows): kept; Task 5's diff
  formula only changes what is written when a write happens.
- Phase 2 Ruling 10 (undecodable values): delete reads an undecodable field as EMPTY and, since removing the ID changes
  nothing, does not write — the value survives (Task 2's "write only if it changed").
- Observation 0026: each Review Focus line names its owning task's test, and the deletes' writes were checked against
  the spec anti-goals (only the deleted ID is removed; unknown IDs and other fields kept).
- Observation 0027: the screens reachable from Manage tags hold no file copies; the editors reachable before it are
  handled in Task 5.

### Claims about existing code (cited)

- Home's ⋮ has one item, "Settings" (`HomeScreen.kt` 380–393, `OverflowMenu`).
- Routes: `Routes.kt` lists 15 routes; `MiniPlayerVisibility.kt` `allRoutes` and `routesWithoutMiniPlayer` (lines 8–24)
  must include a new route.
- `MainActivity.kt` 309–321 registers `TagEditorRoute`/`BatchTagEditorRoute` with `navigateUp()`.
- `MediaWriteRequester.ensureWritable(uris, onGranted, onDenied)` takes a list (`MediaWriteRequester.kt` 80);
  `SelectionArtworkViewModel` is the multi-file loop pattern (consent, per-song copy, API 29 recovery, finalizer).
- `RollaIcons` has `Delete`, `Edit`, `More` (`RollaIcons.kt`).
- `CustomTagDao.insertNew` throws `IllegalStateException` at the ceiling today (phase 1 final fix; Task 1 changes it).

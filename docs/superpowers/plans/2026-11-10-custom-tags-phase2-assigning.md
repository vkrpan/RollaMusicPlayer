# Custom Tags, Phase 2 (Assigning) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the single and batch tag editors the user ticks tags and subtags in a searchable picker sheet (and can
create new ones there); Save writes them into the file's `ROLLA_TAGS` field for MP3 and FLAC, and the library reads them
back without a stale read ever overwriting a fresh save.

**Architecture:** A generation counter on each `songs` row (Room v7) makes the background `FileTagPass` apply a read
only if the row has not been reset since it was queried, which closes phase 1's apply-after-reset race. A stateless
picker sheet in `:core:ui` (two pages, Tags and Subtags, under the kit's `OneUiTabRow`, each with a search field and a
checklist) is driven by the editors' ViewModels, which read the file's current IDs from the editable copy and write the
merged IDs with `RollaTagsFileIo.writeCustomTagIds` before the single persist.

**Tech Stack:** Kotlin 2.2, Compose (One UI kit), Room 6 → 7, Hilt, jaudiotagger via `:core:tagio`, JUnit4/MockK/
Turbine/Robolectric.

**Spec:** [[2026-11-09-custom-tags-design]] (§5.3, §5.5, §6 calls 1, 4, 8, 13). Roadmap:
[[2026-11-09-custom-tags-roadmap]]. Phase 1 (consumed): [[2026-11-09-custom-tags-phase1-foundation]], committed
`dce76dd`; its notes for this phase are in [[Custom tags]] "Known limits and notes for phase 2". Where this plan and the
spec disagree, the spec wins; record a ruling.

## Global Constraints

- **Formats (user decision 2026-11-10):** custom tags are written to MP3 and FLAC only (`canWriteCustomTags`). For any
  other format (M4A, WAV, OGG, AAC) the Tags and Subtags rows are disabled and show the note
  "Tags can be saved to MP3 and FLAC files only." The batch editor counts such tracks as not updated.
- **Assigning adds, AND everywhere (spec §5.3):** every ticked item is assigned. The batch editor only **adds** ticked
  items to each selected track; it never removes (spec §5.5).
- **Unknown IDs are kept (spec §6 call 4):** a write is `(ticked known IDs) ∪ (IDs in the file at save time that the
  list does not know)`, read from the editable copy at save time, never from Room.
- Field format and ceiling as phase 1: `RollaTagsJson`, `MAX_CUSTOM_TAG_ID`; empty IDs remove the field.
- **Kit only** ([[Build UI from tokens and the kit]], `.claude/rules/ui-style-guide.md` §6): `OneUiBottomSheet`,
  `OneUiTabRow` with a `HorizontalPager`, `OneUiSearchField`, `OneUiCheckbox`, `OneUiDialog` + `OneUiDialogField` +
  `OneUiTextButton`, `PillChip`, `InsetDivider`, `RollaIcons`; no hardcoded colour, type or dp; a missing size becomes a
  `RollaDimens` token with its spec §5.4 row first.
- **Copy, verbatim, British** ([[UI copy and spelling]]): "Tags", "Subtags", "Search tags", "Search subtags",
  "New tag", "New subtag", "Done", "Create", "Cancel", "Name", "No tags yet", "No subtags yet", "No matches",
  "Enter a name", "Use 40 characters or fewer", "That name is already in the list", "This list is full",
  "Tags can be saved to MP3 and FLAC files only.", "None". Counts use `tracksCountLabel`.
- Model vocabulary: `Song` in code ([[Song in code, Tracks on screen]]); `CustomTag*` names from phase 1.
- Writes keep the existing consent and cache-copy path ([[Tag writes go through a cache copy]]) and run
  `TagSaveFinalizer` after a persist.
- No `sed -i`, no heredocs for Kotlin/Gradle/XML ([[Edit files with Write and Edit, not the shell]]); "Commit" steps
  mean "stage and report ready to commit" ([[Commits are user-triggered]]). Implementers may make mechanical fixes to
  this plan's code; it was never compiled.
- Device ([[Device safety]]): instrumented Room tests only as `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew
  :core:database:connectedDebugAndroidTest`; the agent never saves tags on the phone — the Task 6 walk is the user's
  hand.

## Review Focus

1. **The user saves while the background pass is reading the same file.** The fresh IDs win; the stale read is
   dropped and the row is read again. Pinned by Task 1's `applyFileTags_staleGeneration_isSkipped` and
   `pass_rowResetDuringRead_rereadsIt`.
2. **A file that already carries IDs the list doesn't know** (tagged on another phone). Saving from the editor keeps
   them. Pinned by Task 4's `save_keepsUnknownIdsFromTheFile` and Task 5's `batchSave_addsAndKeepsExisting`.
3. **Large font (1.5×) on a 360 dp screen with long tag names.** Chips wrap to more lines; nothing is cut off or
   ellipsized. Pinned by Task 3's `chipFlow_largeFont_wrapsWithoutClipping`.
4. **Searching** "tech" finds "Techno" and "HiTech" (ignoring case and accents as the library order does); a query with no
   match shows "No matches" and keeps "New tag" available with the query as its name. Pinned by Task 2's
   `filterCustomTags_*` and Task 3's `picker_noMatch_showsNoMatchesAndNewRow`.
5. **An M4A, WAV or unreadable file in the editor.** The rows are disabled with the note; Save still writes the other
   fields; the batch editor reports "Edited X of N tracks." Pinned by Task 4's `load_m4a_disablesCustomTags` and Task 5's
   `batchSave_m4aCountsAsNotUpdated`.

---

### Task 1: Generation guard — a stale read never overwrites a fresh save (Room v7)

Owner: data-layer-agent with media-scanning-agent.

**Files:**
- Modify: `core/database/.../entity/SongEntity.kt` (`fileTagsGeneration: Long = 0`, column `file_tags_generation`
  INTEGER NOT NULL DEFAULT 0), `MusicDatabase.kt` (version 7), `migration/Migrations.kt`
- Create: `core/database/.../migration/Migration6To7.kt`, schema `7.json` (generated)
- Modify: `core/database/.../dao/CustomTagDao.kt`: `applyFileTags` and `markFileTagsRead` gain
  `expectedGeneration: Long` and return `Boolean` (true = applied; false = the row's generation changed or the row is
  gone, nothing written)
- Modify: `core/testing/.../FakeCustomTagDao.kt` (same semantics)
- Modify: `core/data/.../scanner/ScannedSong.kt` (`toEntity`: the generation is `existing.fileTagsGeneration + 1`
  whenever the read flag is reset, else kept; new rows 0), `FileTagPass.kt` (pass the row's generation; a skipped apply
  is neither read nor failed: the row stays unread and the loop reads it again)
- Test: `MigrationTest.migrate6To7_*`, `CustomTagDaoTest` (new tests), `ScannedSongMergeTest`, `FileTagPassTest`

**Interfaces:**
- Produces: `CustomTagDao.applyFileTags(songId: String, ids: CustomTagIds, genre: String?, expectedGeneration: Long): Boolean`,
  `markFileTagsRead(songId: String, expectedGeneration: Long): Boolean`; `SongEntity.fileTagsGeneration`.
- The conditional write runs in the `@Transaction`: read the row's generation; if it differs (or no row), return false
  before touching links, genre or counters.

- [ ] **Step 1: Write the failing tests**

```kotlin
// MigrationTest
@Test fun migrate6To7_addsGenerationColumnKeepingRows() // v6 row with links → v7: links kept, file_tags_generation == 0

// CustomTagDaoTest
@Test fun applyFileTags_matchingGeneration_applies()     // gen 0 → applyFileTags(..., expectedGeneration = 0) == true; links set
@Test fun applyFileTags_staleGeneration_isSkipped()      // row gen 1, expected 0 → false; links, genre, flag and counter unchanged
@Test fun markFileTagsRead_staleGeneration_isSkipped()

// ScannedSongMergeTest
@Test fun toEntity_reread_bumpsGeneration()              // existing gen 4, rereadFileTags = true → 5
@Test fun toEntity_changedFile_bumpsGeneration()         // dateModified differs → existing gen + 1
@Test fun toEntity_unchanged_keepsGeneration()           // same dateModified, no reread → same gen

// FileTagPassTest
@Test fun pass_rowResetDuringRead_rereadsIt()            // the fake bumps the row's generation inside withReadableFile on the first read;
                                                         // the first apply is skipped, the second read applies the new file's IDs; result read == 1, failed == 0
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*ScannedSongMergeTest" --tests "*FileTagPassTest" :core:database:compileDebugAndroidTestKotlin`
Expected: FAIL (compilation: `fileTagsGeneration`, `expectedGeneration` unresolved).

- [ ] **Step 3: Implement the column, the migration, the conditional DAO writes, the merge and the pass change**

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest :core:database:assembleDebug` and
`ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest`
Expected: PASS (device: all instrumented tests, the existing 117 included).

- [ ] **Step 5: Stage and report ready to commit** (`fix(scanner): a stale file read never overwrites a fresh save`)

---

### Task 2: Picker state and filtering (pure, `:core:ui`)

Owner: viewmodel-architect.

**Files:**
- Create: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/customtags/CustomTagPickerState.kt`
- Test: `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/customtags/CustomTagPickerStateTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Immutable data class CustomTagPickerState(
      val tags: List<CustomTag>,            // library order, from CustomTagRepository
      val subTags: List<CustomTag>,
      val selected: CustomTagIds,           // known IDs only; unknown IDs never appear in the picker
      val page: CustomTagKind = CustomTagKind.TAG,
      val tagQuery: String = "",
      val subTagQuery: String = "",
  )
  fun filterCustomTags(all: List<CustomTag>, query: String): List<CustomTag> // contains, ignoring case and accents (the library collator's primary strength)
  fun CustomTagIds.toggled(kind: CustomTagKind, id: Long): CustomTagIds
  fun selectedNames(all: List<CustomTag>, ids: Set<Long>): List<String>     // library order; unknown IDs skipped
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun filterCustomTags_matchesSubstringIgnoringCase()   // "tech" in [Techno, HiTech, House] → [Techno, HiTech] (input order kept)
@Test fun filterCustomTags_ignoresAccents()                 // "emile" matches "Émile"
@Test fun filterCustomTags_blankQuery_returnsAll()          // "  " → all
@Test fun toggled_addsThenRemovesPerKind()                  // TAG 2 toggled twice → back to EMPTY; SUBTAG 2 unaffected
@Test fun selectedNames_libraryOrderSkippingUnknown()       // ids {3, 99, 1} over [1 "Acid", 3 "Techno"] → ["Acid", "Techno"]
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:ui:testDebugUnitTest --tests "*CustomTagPickerStateTest"`; Expected: FAIL (compilation).
- [ ] **Step 3: Implement** (accent folding: reuse `:core:common`'s library collator if it exposes one; else `java.text.Normalizer` NFD + strip marks, lower-cased with `Locale.ROOT`).
- [ ] **Step 4: Run to verify they pass** — same command; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(ui): custom tag picker state`)

---

### Task 3: The picker sheet, the chip rows and the name dialog (`:core:ui`)

Owner: ui-builder (m3-design-system-agent for any new token).

**Files:**
- Create: `core/ui/.../customtags/CustomTagPickerSheet.kt`, `CustomTagRows.kt`, `CustomTagNameDialog.kt`
- Modify: `core/ui/build.gradle.kts` only if a dependency is missing
- Test: `core/ui/src/test/.../customtags/CustomTagPickerSheetTest.kt`, `CustomTagRowsTest.kt` (Robolectric, as the
  module's other UI tests)

**Interfaces:**
- Consumes: Task 2.
- Produces:
  ```kotlin
  @Composable fun CustomTagPickerSheet(
      state: CustomTagPickerState,
      onPageChange: (CustomTagKind) -> Unit,
      onQueryChange: (CustomTagKind, String) -> Unit,
      onToggle: (CustomTagKind, Long) -> Unit,
      onCreateClick: (CustomTagKind, String) -> Unit,   // opens the name dialog prefilled with the current query
      onDone: () -> Unit,
      modifier: Modifier = Modifier,
  )   // OneUiBottomSheet(skipPartiallyExpanded = true, title = null); OneUiTabRow(["Tags", "Subtags"]) + HorizontalPager(2);
      // per page: OneUiSearchField ("Search tags"/"Search subtags"), a "New tag"/"New subtag" row first, then the
      // filtered checklist (row = name + OneUiCheckbox, whole row toggles, merged semantics "Techno, checked"),
      // empty states "No tags yet"/"No subtags yet" and "No matches"; a "Done" OneUiPillButton at the bottom.
  @Composable fun CustomTagRows(
      tagNames: List<String>, subTagNames: List<String>, enabled: Boolean,
      onTagsClick: () -> Unit, onSubTagsClick: () -> Unit, modifier: Modifier = Modifier,
  )   // a TagEditorCard-style card: two rows ("Tags", "Subtags"), each with its chosen names as non-interactive PillChips
      // in a FlowRow under the label, or "None"; disabled → 38 % alpha content plus the note
      // "Tags can be saved to MP3 and FLAC files only." under both rows, rows not clickable.
  @Composable fun CustomTagNameDialog(
      kind: CustomTagKind, initialName: String, error: String?,
      onCreate: (String) -> Unit, onDismiss: () -> Unit,
  )   // OneUiDialog titled "New tag"/"New subtag", OneUiDialogField("Name"), buttons "Cancel" / "Create"; error under the field.
  ```
  Opening the sheet on the tapped row's page is the caller's job (`state.page`).

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun picker_showsTabsAndOpensOnStatePage()        // state.page = SUBTAG → "Search subtags" displayed
@Test fun picker_rowTapTogglesThatId()                 // tap "Techno" row → onToggle(TAG, 2)
@Test fun picker_checkedRowAnnouncesChecked()          // "Techno" row semantics: toggleable state On
@Test fun picker_noMatch_showsNoMatchesAndNewRow()     // query "zzz" → "No matches" and "New tag" displayed; tapping it → onCreateClick(TAG, "zzz")
@Test fun picker_emptyList_showsNoTagsYet()
@Test fun rows_disabled_showNoteAndIgnoreTaps()        // enabled = false → note displayed; click → no callback
@Test fun chipFlow_largeFont_wrapsWithoutClipping()    // 360 dp wide, fontScale 1.5, six 20-char names: every chip's right edge ≤ the card's
                                                       // inner right edge, and the chips span ≥ 2 rows (distinct top values)
@Test fun nameDialog_showsErrorAndCreatesTrimmedText() // error "That name is already in the list" shown; Create → onCreate("Techno") for input "Techno"
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:ui:testDebugUnitTest --tests "*CustomTag*"`; Expected: FAIL (compilation).
- [ ] **Step 3: Implement the three composables** (previews included, light and dark, per compose-conventions).
- [ ] **Step 4: Run to verify they pass** — `./gradlew :core:ui:testDebugUnitTest :core:designsystem:testDebugUnitTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(ui): custom tag picker sheet and rows`)

---

### Task 4: Single tag editor — load, pick, create, save

Owner: tag-editor-agent (IO) with viewmodel-architect and ui-builder.

**Files:**
- Modify: `feature/tageditor/build.gradle.kts` (`implementation(project(":core:tagio"))` already there; nothing else
  expected), `TagEditorViewModel.kt`, `TagEditorScreen.kt`
- Test: `TagEditorViewModelTest.kt`, `TagEditorScreenTest.kt`

**Interfaces:**
- Consumes: Task 2–3; phase 1's `CustomTagRepository` (`observeCustomTags`, `create` → `CustomTagNameResult`),
  `RollaTagsFileIo.read/writeCustomTagIds`, `canWriteCustomTags`, `File.extension`.
- `TagEditorUiState` gains: `customTagsEditable: Boolean`, `picker: CustomTagPickerState`,
  `isPickerOpen: Boolean`, `nameDialog: CustomTagNameDialogState?` (`kind`, `initialName`, `error: String?`).
  The VM keeps `loadedCustomTagIds: CustomTagIds` (from the copy at load) privately.
- New handlers: `onCustomTagRowsClick(kind)`, `onPickerPageChange(kind)`, `onPickerQueryChange(kind, query)`,
  `onCustomTagToggle(kind, id)`, `onPickerDone()`, `onCreateCustomTagClick(kind, name)`, `onCreateCustomTagConfirm(name)`,
  `onNameDialogDismiss()`.
- Behaviour:
  - Load: after `tagReader.read(copy)`, `fileIo.read(copy).customTagIds` → `loadedCustomTagIds`; selection = its
    known IDs (present in the observed lists). `customTagsEditable = canWriteCustomTags(copy.extension)`; a read
    failure of `ROLLA_TAGS` alone never fails the load (treat as EMPTY, editable per format).
  - Dirty: a selection different from the loaded known IDs counts as an edit (same `recomputeSaveState`).
  - Create: `create(kind, name)`; `Saved` → close the dialog and tick the new entry; `Blank` → "Enter a name";
    `TooLong` → "Use 40 characters or fewer"; `Duplicate` → "That name is already in the list"; `LimitReached` →
    "This list is full".
  - Save (inside `writeAndPersist`, after `tagWriter.write` and the artwork step, before persist), only when
    `customTagsEditable`: `ids = selected ∪ (fileIo.read(copy).customTagIds − known IDs)`, re-read **from the copy at
    save time**; `fileIo.writeCustomTagIds(copy, ids)`. A failure there is the existing generic save failure.

- [ ] **Step 1: Write the failing tests**

```kotlin
// TagEditorViewModelTest (existing fakes; RollaTagsFileIo against fixture copies or a fake seam the test file already uses)
@Test fun load_readsTicksFromTheFile()                // copy carries {"tags":"2","subTags":""}, list has TAG 2 → picker.selected == ({2}, {})
@Test fun load_m4a_disablesCustomTags()               // .m4a copy → customTagsEditable == false
@Test fun toggle_makesDirtyAndUntoggleRestoresClean()
@Test fun save_writesTickedIdsToTheCopy()             // tick TAG 3 → after save the copy's ROLLA_TAGS == {"tags":"2,3","subTags":""}
@Test fun save_keepsUnknownIdsFromTheFile()           // copy carries TAG 2 and TAG 99 (unknown) → untick 2 → saved {"tags":"99","subTags":""}
@Test fun save_m4a_writesFieldsButNoCustomTags()      // title edited on an M4A → persist happens; writeCustomTagIds never called
@Test fun create_savedTicksTheNewEntry()
@Test fun create_duplicate_showsErrorAndKeepsDialog()  // error == "That name is already in the list"
@Test fun create_limitReached_showsListFull()

// TagEditorScreenTest
@Test fun rows_openPickerOnTappedPage()               // tap "Subtags" row → picker opens with "Search subtags"
@Test fun rows_disabledForM4a_showNote()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:tageditor:testDebugUnitTest --tests "*TagEditorViewModelTest" --tests "*TagEditorScreenTest"`; Expected: FAIL (compilation).
- [ ] **Step 3: Implement the VM and screen changes** (the rows card sits between the fields card and Save/Cancel; the
  sheet and dialog are hosted by `TagEditorScreen`).
- [ ] **Step 4: Run to verify they pass** — `./gradlew :feature:tageditor:testDebugUnitTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(tageditor): assign custom tags in the tag editor`)

---

### Task 5: Batch tag editor — add tags to every selected track

Owner: tag-editor-agent with viewmodel-architect and ui-builder.

**Files:**
- Modify: `BatchTagEditorViewModel.kt`, `BatchTagEditorUiState.kt`, `BatchTagEditorScreen.kt`
- Test: `BatchTagEditorViewModelTest.kt`, `BatchTagEditorScreenTest.kt`

**Interfaces:**
- Consumes: Tasks 2–3, `CustomTagRepository`, `RollaTagsFileIo`, `canWriteCustomTags`.
- `BatchTagEditorUiState` gains `picker: CustomTagPickerState` (selected = what to add; starts EMPTY), `isPickerOpen`,
  `nameDialog`. Rows are always enabled (formats are judged per file at save). Choosing at least one item makes
  `canSave` true even with no field applied.
- Per song in `attemptWrite`, after `writeFields` (skipped when no field is applied) and before persist: if the copy's
  extension can be written, `ids = file's current IDs ∪ chosen`; write only if that differs from the file's. When at
  least one item is chosen and the song's format cannot be written, the song fails **before any write** (fields
  included), through the existing failure path, so "Edited X of N tracks." reports it and no song is ever half-saved.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun batchSave_addsAndKeepsExisting()            // song A has {TAG 1}, choose TAG 2 → A's file {"tags":"1,2","subTags":""}
@Test fun batchSave_neverRemoves()                    // song B has {TAG 5}, choose TAG 2 → B keeps 5
@Test fun batchSave_tagsOnly_enablesSave()            // no field applied, one tag chosen → canSave true; save writes only ROLLA_TAGS
@Test fun batchSave_m4aCountsAsNotUpdated()           // MP3 + M4A selected, TAG 2 chosen → message "Edited 1 of 2 tracks."; the M4A file's bytes unchanged
@Test fun batchSave_alreadyCarried_skipsTheWrite()    // song already has TAG 2 → no persist for it, counted as edited
@Test fun batchScreen_rowsOpenPicker()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew :feature:tageditor:testDebugUnitTest --tests "*BatchTagEditor*"`; Expected: FAIL.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run to verify they pass** — `./gradlew :feature:tageditor:testDebugUnitTest`; Expected: PASS.
- [ ] **Step 5: Stage and report ready to commit** (`feat(tageditor): add custom tags to many tracks`)

---

### Task 6: Gate, device walk and vault

Owner: the controller.

- [ ] **Step 1:** `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`
  → BUILD SUCCESSFUL; `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest` → all pass.
- [ ] **Step 2:** `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark` (v6 → v7 on the user's database). The
  user's walk (their hand; the agent only reads logcat and, read-only, pulls a file the user names to check its field):
  1. Open a track's Edit tags → tap Tags → the sheet opens on Tags; create "Techno", tick it; Subtags → create "Hard",
     tick it; Done; the rows show the chips; Save; consent prompt; the editor closes.
  2. Reopen the same track's Edit tags: Techno and Hard are ticked.
  3. Select three tracks → Edit tags → Tags → tick Techno → Save → "Edited 3 of 3 tracks." (or the M4A note count).
  4. An M4A or WAV track: the rows are disabled with the note.
  5. 1.5× font (system setting changed by the user, then restored): chips wrap, nothing cut off.
- [ ] **Step 3:** Vault: [[Custom tags]] (progress, walk results), [[core-database]] (v7), [[core-data]], [[core-ui]],
  [[feature-tageditor]], [[Tag editor]], [[Timeline]], [[Backlog]] (the race item closed); ledger rulings archived;
  report ready to commit.

---

## Self-review artefacts

### Test-control table (one mutation per test)

| Test | Stub | Assertion that fails | Mutation | Mutated value (computed on the literal input) |
|---|---|---|---|---|
| migrate6To7_addsGenerationColumnKeepingRows | migration adds nothing | Room validation | drop the ALTER | missing column → validation error |
| applyFileTags_matchingGeneration_applies | always skip | `== true`, links set | compare `!=` instead of `==` | 0 != 0 is false → skipped → false |
| applyFileTags_staleGeneration_isSkipped | always apply | `== false`, links unchanged | drop the generation check | applies → links replaced |
| markFileTagsRead_staleGeneration_isSkipped | always mark | flag unchanged | drop the check | flag set to 1 |
| toEntity_reread_bumpsGeneration | keep | `== 5` | keep `existing.gen` on reread | 4 ≠ 5 |
| toEntity_changedFile_bumpsGeneration | keep | `== gen + 1` | bump only on reread | gen ≠ gen + 1 |
| toEntity_unchanged_keepsGeneration | bump | `== gen` | always bump | gen + 1 ≠ gen |
| pass_rowResetDuringRead_rereadsIt | apply stale | final links == the new file's | ignore the Boolean (count as read, stop) — amended in Task 1: the DAO guard still refuses the stale apply, so this fails on `read` (`expected:<1> but was:<2>`); a second mutation (read the generation at apply time instead of the queried one) fails on the links | read 2 ≠ 1 |
| filterCustomTags_matchesSubstringIgnoringCase | return all | `== [Techno, HiTech]` | `startsWith` instead of `contains` | "HiTech" dropped → [Techno] |
| filterCustomTags_ignoresAccents | return all for blank | "Émile" matched | no accent folding | "emile" ∉ "émile" → [] |
| filterCustomTags_blankQuery_returnsAll | filter by raw query | all returned | no trim | "  " matches nothing → [] |
| toggled_addsThenRemovesPerKind | identity | EMPTY after two toggles | always add | {2} ≠ EMPTY |
| selectedNames_libraryOrderSkippingUnknown | names in ID order | ["Acid", "Techno"] | iterate `ids` not `all` | order 3,1 → ["Techno", "Acid"] |
| picker_showsTabsAndOpensOnStatePage | always page 0 | "Search subtags" displayed | pager initial page 0 | "Search tags" shown |
| picker_rowTapTogglesThatId | no click | `onToggle(TAG, 2)` | toggle only on the checkbox | row tap → no callback |
| picker_checkedRowAnnouncesChecked | no semantics | ToggleableState.On | drop `toggleable` semantics | no state |
| picker_noMatch_showsNoMatchesAndNewRow | hide New row on no match | New row displayed, callback with "zzz" | pass "" not the query | onCreateClick(TAG, "") |
| picker_emptyList_showsNoTagsYet | blank | text displayed | show "No matches" for empty | wrong text |
| rows_disabled_showNoteAndIgnoreTaps | clickable | no callback | keep `clickable` when disabled | callback fires |
| chipFlow_largeFont_wrapsWithoutClipping | Row | right edges ≤ card edge, ≥ 2 tops | `Row` instead of `FlowRow` | one top; last chips past the edge |
| nameDialog_showsErrorAndCreatesTrimmedText | ignore error | error displayed | don't render `error` | not found |
| load_readsTicksFromTheFile | EMPTY | `== ({2}, {})` | skip the ROLLA_TAGS read | EMPTY |
| load_m4a_disablesCustomTags | true | `== false` | use `isCustomTagExtension` | m4a → true |
| toggle_makesDirtyAndUntoggleRestoresClean | never dirty | dirty after toggle, clean after untoggle | leave selection out of dirty | not dirty |
| save_writesTickedIdsToTheCopy | no write | `"tags":"2,3"` | skip writeCustomTagIds | field stays "2" |
| save_keepsUnknownIdsFromTheFile | write selected only | `"tags":"99"` | drop the unknown-ID union | field removed (EMPTY) |
| save_m4a_writesFieldsButNoCustomTags | write anyway | `verify(exactly = 0)` | drop the editable check | write called → throws → save fails |
| create_savedTicksTheNewEntry | don't tick | new ID in selected | close the dialog only | not selected |
| create_duplicate_showsErrorAndKeepsDialog | close | error text, dialog open | map Duplicate to null error | error null |
| create_limitReached_showsListFull | other text | "This list is full" | map to "Enter a name" | wrong text |
| rows_openPickerOnTappedPage | page 0 | "Search subtags" | ignore `kind` | "Search tags" |
| rows_disabledForM4a_showNote | no note | note displayed | `enabled = true` always | note absent |
| batchSave_addsAndKeepsExisting | write chosen | `"tags":"1,2"` | write chosen only | "2" |
| batchSave_neverRemoves | union | 5 kept | write chosen only | 5 lost |
| batchSave_tagsOnly_enablesSave | false | canSave true | canSave needs an applied field | false |
| batchSave_m4aCountsAsNotUpdated | count as edited | "Edited 1 of 2 tracks." and bytes unchanged | skip the M4A silently as success | "Edited 2 of 2 tracks." |
| batchSave_alreadyCarried_skipsTheWrite | always write | persist count 0 for it | drop the difference check | persist called |
| batchScreen_rowsOpenPicker | no-op | sheet shown | rows not wired | sheet absent |

### Per-screen concerns

| Surface | Status bar | Navigation bar | Keyboard | TalkBack | Reduced motion |
|---|---|---|---|---|---|
| Tag editor (card added) | `OneUiDetailTopBar` (existing) | existing scroll column | `TagEditorScrollColumn`'s IME padding (existing) | rows: "Tags, Techno, Peak time, button"; disabled rows announce the note | none new |
| Batch editor (card added) | existing | existing | existing | as above | none new |
| Picker sheet | `OneUiBottomSheet`'s `statusBarsPadding` | sheet's own navigation-bar padding (kit) | the search field: sheet content gets `imePadding()` so the list and "Done" stay above the keyboard | rows merge name + checkbox (toggleable); tabs as `OneUiTabRow` | pager/tab use the kit's `rollaSpringOrSnap` |
| Name dialog | `OneUiDialog` (kit) | kit | `OneUiDialogField` focuses on show (kit) | error read with the field | kit |

### Earlier rulings checked

- Phase 1 Ruling 7 (the pass runs in the background; `syncSongs` returns at once): honoured; Task 1 makes a stale apply
  harmless instead of awaiting the pass.
- Phase 1 rulings 5, 10 (M4A read-only; ID ceiling): the editors use `canWriteCustomTags`; `create`'s `LimitReached`
  gets its copy.
- Per-track artwork P20 (two text pills in a plain Row truncate at 1.5×): chips use `FlowRow` (Task 3 test).
- Per-track artwork P21 (last tap wins) and the editor's save-before-close ordering (KDoc "After a successful save"):
  the ROLLA_TAGS write sits inside `writeAndPersist`, before the single persist; the finalizer order is unchanged.
- Redesign rules: the kit table (`ui-style-guide.md` §6) — no M3 component directly.

### Claims about existing code (cited)

- `TagEditorViewModel.writeAndPersist` writes fields, then artwork, then persists (`TagEditorViewModel.kt` 358–379).
- `BatchTagEditorViewModel.attemptWrite` writes fields then persists per song and reports "Edited X of N tracks."
  (`BatchTagEditorViewModel.kt` 330, 356–368).
- `OneUiTabRow(titles, pagerState, onTabClick)` needs a `PagerState` (`OneUiTabRow.kt` 64–70); `OneUiBottomSheet` has
  `skipPartiallyExpanded` and adds `statusBarsPadding` (`OneUiBottomSheet.kt` 38–50).
- `:core:ui` depends on `:core:model` and `:core:designsystem` only (`core/ui/build.gradle.kts` 17–18), so the picker
  stays stateless and the feature ViewModels hold the repository.

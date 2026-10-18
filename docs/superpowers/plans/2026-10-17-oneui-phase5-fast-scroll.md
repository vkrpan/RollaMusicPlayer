# One UI redesign, Phase 5: A–Z fast-scroll rail — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tracks, Favourites, Playlists (user rows), Artists and Folders get the One UI A–Z rail. Tap or drag a letter to jump, with a haptic tick and a TalkBack "Jump to X" action. Every Home list now sorts in one Unicode-aware library order, so the rail's sections match what is on screen: "Émile" sorts and jumps under E, not after Z.

**Architecture:**
- **One definition of order.** `:core:common` (empty until now) gets `LibraryOrder.kt`. `indexLetter(name)` gives a name's section, `#` or A–Z. `sortedByLibraryName` sorts by that section first, then by root-locale primary collation, which ignores case and accents, then by a caller tie-break.
- **Data.** The five Home list repositories re-sort their Room results in that order, off the main thread, through a new `@CoreDataDefaultDispatcher`. The DAOs keep their `COLLATE NOCASE` order as a deterministic pre-order, and no query changes.
- **Design system.** `:core:designsystem` builds the rail's sections from the same `indexLetter`, which adds a dependency on `:core:common`:
  - `FastScrollIndex.kt` holds the pure index logic: sections, label fitting and the touch-to-section mapping.
  - `FastScrollRail` is the control.
  - `FastScrollContainer` overlays the rail on a `LazyColumn`, shows it only when the list scrolls, and jumps the list's real `LazyListState`.
- **Tabs.** The five tabs wrap their lists in `FastScrollContainer`.

**Tech Stack:**
- Kotlin 2.2.21, Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0).
- `java.text.Collator` and `Normalizer`: the JDK on the JVM, ICU-backed on Android.
- Hilt, and JUnit4 + MockK + Turbine for tests.
- Robolectric 4.13, with NATIVE graphics for geometry.
- One connected test on the Samsung SM-S901B.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md). The relevant sections:
- §2 (touch-target exception) and §5.4 (rail tokens, the 30 dp exception);
- §6 (`FastScrollRail`) and §8.2 (which tabs);
- §9 (the data layer's orders) and §10 (the rail, binding);
- §15 (`FastScrollIndex` tests) and §17 (exit criteria: "Index tests pass. Tap and drag jump correctly. TalkBack actions present. No jank (compose-performance-auditor pass)").

The roadmap is [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md). Carry-overs come from the SDD ledgers of Phases 2, 4a and 4b, listed under Rulings.

## Global Constraints

- **Platform:** minSdk 24, targetSdk 34, compileSdk 36. `java.text.Collator`, `java.text.Normalizer`, `traversalIndex` and `HapticFeedbackType.TextHandleMove` are all available at API 24 with this Compose version.
- **Copy and spelling:** UI copy is British ("Favourites", "Equaliser"). The rail's TalkBack copy is exactly `Fast scroll, letter index` for the node and `Jump to <label>` for each action.
- **Colours:** blue text uses `accentText`, never `primary`. The rail has no blue.
- **Icons and values:** icons come from `RollaIcons` only. Colours, type, shapes and dimensions come from `:core:designsystem` tokens, and new values become tokens first. The rail uses:
  - `fastScrollTrack` (track), `fastScrollIndex` (letters) and `fastScrollLetter` (11 sp style);
  - `fastScrollWidth` 22 dp and `fastScrollEnd` 8 dp;
  - `CircleShape`, and the new `fastScrollLetterSlot` 16 dp.
- **Touch target:** the rail is the documented exception, at `fastScrollWidth + fastScrollEnd` (30 dp). It is accepted below 48 dp because every populated section also has a semantic "Jump to X" action.
- **No dead controls:** every visible letter responds to a tap or a drag.
- **Offline only:** no network APIs or dependencies.
- **Tests:** tests the phase touches are updated, never deleted to go green.
- **Mutation-proof policy.**
  - Every new test names the mutation that turns it RED. The implementer applies it, sees RED, and reverts byte-exact.
  - A mutant counts as equivalent only if *no observable output changes*. If the output changes but the test can't see it, the test is blind: move its probe.
  - A named mutation is a hypothesis about the framework.
    - It must be valid usage: a mutation that throws inside Compose or Room is RED for the wrong reason.
    - If it proves equivalent, show the evidence and substitute the closest fail-able mutation.
    - Each test also needs a control assertion showing the code under test acted in the unmutated run.
  - A RED line quotes the test's own assertion message, not an exception the mutation caused, and comes with the Gradle `BUILD FAILED` line. "No tests found" is an error, not a RED.
  - Fixtures must separate the quantities a test distinguishes. Expected answers must not coincide with insertion order, DAO order or binary order.
- **Agents never** run `git commit`, `git add`, `git stash`, `git checkout` or `git reset`. Never use `sed -i` (Spotless enforces CRLF; use the editor). Never write Kotlin through shell heredocs. Run `./gradlew spotlessApply` after edits.
- **Plan code** below was never compiled. Implementers may make mechanical fixes (imports, a renamed parameter, an `@OptIn`) and must report each one. They may not change behaviour, a test's assertions or its fixture.
- **Instrumented code** (androidTest) must compile here (`compileDebugAndroidTestKotlin`). It runs on the device only in Task 7, by the controller.
- `:core:testing` stays `testImplementation` only.

## Rulings (decided at planning; the spec is amended in Task 6)

1. **The library order (the Phase 4a/4b carry-over).** Every Home list (Tracks, Favourites, Playlists, Albums, Artists, Folders) sorts by its display name in this order:
   - (a) The section from `indexLetter`, `#` first and then A–Z, so each rail section is one contiguous run.
   - (b) `Collator.getInstance(Locale.ROOT)` at `PRIMARY` strength: base letters only, so case and accents neither split nor reorder a section ("Émile" sorts between "Emil" and "Emma").
   - (c) A per-list exact tie-break, for a deterministic order.

   **Why in Kotlin, not SQL:**
   - Android's SQLite `COLLATE UNICODE`/`LOCALIZED` is unknown to Room's compile-time verifier, so it would need `@SkipQueryVerification`.
   - Its order would still not group by the rail's letters: digits and non-Latin scripts land at different ends.
   - It differs between the device and the JVM tests.
   - Sorting in the repository makes the rail and the list share one definition. The DAOs keep NOCASE as a deterministic pre-order, so no query, migration or DAO test changes.

   **Costs:**
   - Names in `#` (digits, punctuation, non-Latin scripts, "Øl") all sit at the top, before A.
   - Locale-specific orders (Swedish Ä after Z) are not followed, because the rail strips diacritics anyway (spec §10).
2. **The order lives in `:core:common`.** This is its first real content: `Placeholder.kt` is deleted. `:core:data` already depends on `:core:common`, and `:core:designsystem` gains `implementation(project(":core:common"))`. This is a new edge, inward to a leaf utility module with no Android or Compose code, and it creates no cycle.
   - Spec §6 puts the index logic in `FastScrollIndex.kt`. The section and fitting logic stays there. Only `indexLetter` comes from `:core:common`, because the repositories need the same function.
3. **Sorting runs off the main thread.** Room's flow emits in the collector's context, which is `viewModelScope`, the main thread. A 5 000-song sort with collation keys costs about 10–15 ms, a whole frame.
   - The five repositories take a new `@CoreDataDefaultDispatcher CoroutineDispatcher`, bound to `Dispatchers.Default` in `CoreDataDispatchersModule`, and end each sorted flow with `.flowOn(...)`.
   - The sort runs after `distinctUntilChanged()`, so Room's content-identical re-emissions (play-count writes) never re-sort.
   - Tests pass a test dispatcher.
4. **Per-list tie-breaks.**
   - Songs (Tracks, Favourites): exact title, then id.
   - Playlists: exact name, then id.
   - Albums: exact title, then id.
   - Artists: exact name, which is unique.
   - Folders: the path in library order, then the exact path. This keeps the meaning of Phase 4b ruling 2, and its four existing `FolderRepositoryImplTest` cases still pass unchanged (checked against this order at planning).
5. **Albums follow the order too**, although the Albums grid has no rail (§8.2). Otherwise it would be the one Home list that still puts "Éclair" after Z. It costs one line.
6. **`FastScrollSection` gains `populated: Boolean`.** Spec §10 asks for TalkBack actions "per populated section" and for empty letters to target a neighbour. The section must say which it is. This refines the signature, not its responsibility.
7. **`FastScrollContainer` is new kit.** It is model-agnostic, in `:core:designsystem`, and owns the parts every tab would otherwise copy:
   - the overlay;
   - "hidden when the list fits", read from the real `LazyListState`'s `canScrollForward || canScrollBackward`;
   - the jump, `scrollToItem(firstIndex + itemOffset)`;
   - the bottom inset;
   - the traversal group.
8. **The rail's vertical extent.** It runs from the top of the list (just below the sort header) to `homeListBottomPadding()` above the bottom, so it ends at the pill. The reference rail ends about 15 dp above the pill. Phase 7 calibrates this, and no token is invented for it now.
9. **Hit priority where the ⋮ box meets the rail** (Phase 2 carry-over M1). A row's 48 dp ⋮ box reaches 4 dp into the 30 dp rail strip (`listOverflowEnd` 26 dp). The rail is drawn above the list, so in those 4 dp the rail wins, and the ⋮ keeps 44 dp. Spec §5.4's "doesn't overlap the rows' ⋮ targets" is corrected in Task 6.
10. **Labels fit to their slot.** Spec §10 gives 16 dp a letter. That is the new token `RollaDimens.fastScrollLetterSlot`, but the slot used is at least the `fastScrollLetter` line height at the current font scale. At fontScale 1.5 (19.5 dp) the rail drops more letters instead of overlapping them. Every label keeps an equal slot of the rail's height, so a shown letter sits exactly where a tap selects it.
11. **The gestures are separate detectors.**
    - `detectTapGestures`: a tap selects on release.
    - `detectVerticalDragGestures`: selects from the drag start onwards.
    - A horizontal swipe that starts on the rail therefore still pages the Home pager (spec §18 risk). The tick fires once per change of label, and each new tap or drag counts as a change.
12. **4b carry-over T6.** Tracks, Artists and Folders gain a "scanning wins over a non-empty list" test, while their tabs are open for this phase. Favourites has no scan state.
13. **Deferred, not in this phase:**
    - the `TabMessage`/empty-state duplication (4a L10, Phase 6);
    - `observeArtistAlbums`'s binary order (4a L5, Phase 6);
    - FolderDetail/AlbumDetail/ArtistDetail rails (spec §10 names only the five Home tabs);
    - article stripping ("The Beatles" under T, as the spec's rule gives).
14. **No Task 0 probe.** No outcome of this phase is drawn by OS or vendor UI. The one platform behaviour it relies on, Android's ICU collation agreeing with the JVM's for the rule's cases, gets a connected parity test in Task 1, which runs on the device in Task 7.

## Review Focus

The inputs and conditions most likely to bite a real user that the spec implies but does not spell out, each pinned by a test in the owning task:

1. **Initials outside plain A–Z.** For a library whose names start with an accent, a lower-case letter, a digit or a non-Latin script, each row appears once, inside the rail section its letter shows, and a jump lands on it. Pinned in Tasks 1, 2 and 5.
2. **A horizontal swipe that starts on the rail** still changes the Home tab and does not jump the list. Pinned in Task 4.
3. **The library changes while the tab is open** (a rescan adds songs). The sections follow, and a jump after the update lands on the new first row. Pinned in Task 5.
4. **A short rail** (landscape, or fontScale 1.5) drops labels, but a drag still reaches every letter down to Z, and TalkBack still lists every populated section. Pinned in Tasks 3 and 4.
5. **Tracks in selection mode, or a tap on the rail over a row's end.** The tap jumps the list and never plays, toggles or opens the row's options sheet. Pinned in Task 5.

## File map

| File | Task | Change |
|---|---|---|
| `core/common/build.gradle.kts` | 1 | test + androidTest deps |
| `core/common/src/main/kotlin/com/rolla/musicplayer/core/common/Placeholder.kt` | 1 | delete |
| `core/common/src/main/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrder.kt` | 1 | new |
| `core/common/src/test/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrderTest.kt` | 1 | new |
| `core/common/src/androidTest/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrderDeviceTest.kt` | 1 | new |
| `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/di/CoreDataIoDispatcher.kt` | 2 | + `CoreDataDefaultDispatcher` |
| `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/{Song,Playlist,Album,Artist,Folder}RepositoryImpl.kt` | 2 | sort + `flowOn` |
| `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/repository/{Song,Playlist,Album,Artist,Folder}RepositoryImplTest.kt` | 2 | constructor arg + new order tests |
| `core/designsystem/build.gradle.kts` | 3 | + `:core:common` |
| `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt` | 3 | + `fastScrollLetterSlot` |
| `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollIndex.kt` | 3 | new |
| `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollIndexTest.kt` | 3 | new |
| `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollRail.kt` | 4 | new (`FastScrollRail`, `FastScrollContainer`, previews) |
| `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollRailTest.kt` | 4 | new |
| `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/{TracksTab,ArtistsTab,FoldersTab}.kt` | 5 | wrap lists |
| `feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/{FavouritesTab,PlaylistsTab}.kt` | 5 | wrap lists |
| `feature/library/src/test/.../{TracksTab,ArtistsTab,FoldersTab}Test.kt`, `feature/playlists/src/test/.../{FavouritesTab,PlaylistsTab}Test.kt` | 5 | new rail tests |
| spec, `ui-style-guide.md`, `CLAUDE.md`, `setup-modularization/SKILL.md`, roadmap | 6 | docs |

---

### Task 1: `:core:common`: the library order (data-layer-agent)

**Files:**
- Modify: `core/common/build.gradle.kts`
- Delete: `core/common/src/main/kotlin/com/rolla/musicplayer/core/common/Placeholder.kt`
- Create: `core/common/src/main/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrder.kt`
- Test: `core/common/src/test/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrderTest.kt`
- Test (device): `core/common/src/androidTest/kotlin/com/rolla/musicplayer/core/common/text/LibraryOrderDeviceTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (package `com.rolla.musicplayer.core.common.text`):
  - `const val NON_LETTER_SECTION: Char = '#'`
  - `fun indexLetter(name: String): Char`, which returns `'#'` or `'A'..'Z'`
  - `fun <T> List<T>.sortedByLibraryName(tieBreak: Comparator<in T>, name: (T) -> String): List<T>`
  - `fun <T> libraryNameComparator(name: (T) -> String): Comparator<T>`: the same section-then-collation order, uncached. It exists for tie-breaks and small lists.

- [ ] **Step 1: Add the test dependencies.** `core/common/build.gradle.kts` becomes:

```kotlin
plugins {
    id("rolla.android.library")
}

android {
    namespace = "com.rolla.musicplayer.core.common"
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

`espresso-core` brings `androidx.test:runner`, which the convention plugin's `AndroidJUnitRunner` needs, as in `:core:database`.

- [ ] **Step 2: Write the failing JVM test.** `LibraryOrderTest.kt`:

```kotlin
package com.rolla.musicplayer.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrderTest {

    // Mutations: drop the uppercase → "abba" gives 'a' → '#' → RED; drop trimStart → "  Zed" → '#' → RED.
    @Test
    fun indexLetter_mapsPlainLettersCaseInsensitivelyAfterLeadingSpace() {
        assertEquals('A', indexLetter("abba"))
        assertEquals('M', indexLetter("Mango"))
        assertEquals('Z', indexLetter("  Zed"))
    }

    // Spec §10: the first character's diacritics are stripped. Mutation: skip the NFD normalisation → 'É' and 'ñ' are
    // outside A–Z → '#' → RED.
    @Test
    fun indexLetter_stripsDiacriticsFromTheFirstCharacter() {
        assertEquals('E', indexLetter("Émile"))
        assertEquals('N', indexLetter("ñu"))
        assertEquals('A', indexLetter("Åsa"))
    }

    // Everything outside A–Z after stripping is '#': digits, punctuation, other scripts, letters with no
    // decomposition (Ø), astral characters, and the empty name. Control: 'A' above shows the mapping does pass letters.
    // Mutation: return the uppercased character itself instead of '#' → '1', 'Ω', … → RED.
    @Test
    fun indexLetter_mapsEverythingElseToHash() {
        listOf("1999", "(Intro)", "Ωmega", "日本", "Øl", "𝄞 Clef", "", "   ").forEach { name ->
            assertEquals("for \"$name\"", '#', indexLetter(name))
        }
    }

    // Ruling 1. The input order is neither the expected order nor binary order. Expected: the '#' bucket first (digits
    // before Greek), then A ("apple" before "Apricot": case ignored), then E (accents ignored: Émile, Emma, Eva), F, Z.
    // Mutations: (a) drop the section rank → "Ωmega" sorts after Latin → RED; (b) replace the collator with
    // String.CASE_INSENSITIVE_ORDER → "Émile" ('é' > 'e') sorts after "Eva" → RED; (c) compare names exactly →
    // "Apricot" before "apple" → RED.
    @Test
    fun sortedByLibraryName_groupsBySectionThenIgnoresCaseAndAccents() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        val expected = listOf("1999", "Ωmega", "apple", "Apricot", "Émile", "Emma", "Eva", "Fox", "Zebra")
        assertEquals(expected, input.sortedByLibraryName(naturalOrder()) { it })
    }

    // Ruling 4: a primary tie goes to the caller's tie-break. Mutation: ignore tieBreak (the stable sort then keeps the
    // input order ["b", "B"]) → RED.
    @Test
    fun sortedByLibraryName_breaksPrimaryTiesWithTheTieBreak() {
        assertEquals(listOf("B", "b"), listOf("b", "B").sortedByLibraryName(naturalOrder()) { it })
    }

    // The uncached comparator gives the same order. Mutation: drop its section rank → "Ωmega" last → RED.
    @Test
    fun libraryNameComparator_matchesSortedByLibraryName() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        assertEquals(
            input.sortedByLibraryName(naturalOrder()) { it },
            input.sortedWith(libraryNameComparator<String> { it }.thenBy { it }),
        )
    }
}
```

- [ ] **Step 3: Run it to verify it fails.**
  - Run: `./gradlew :core:common:testDebugUnitTest --tests "*LibraryOrderTest"`
  - Expected: `BUILD FAILED`, with `Unresolved reference 'indexLetter'` in compilation.

- [ ] **Step 4: Implement.** Delete `Placeholder.kt`. Create `LibraryOrder.kt`:

```kotlin
package com.rolla.musicplayer.core.common.text

import java.text.CollationKey
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The A–Z rail's section for names that do not start with a letter A–Z. */
const val NON_LETTER_SECTION: Char = '#'

/**
 * The rail section a library name belongs to (spec §10). It is the name's first character after leading whitespace,
 * with diacritics stripped (NFD, base character kept) and uppercased. 'A'–'Z' map to themselves, and anything else
 * maps to [NON_LETTER_SECTION]: "Émile" → 'E', "ñu" → 'N', "Øl" → '#' (Ø has no decomposition), "1999" → '#', "" → '#'.
 */
fun indexLetter(name: String): Char {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return NON_LETTER_SECTION
    val firstCharacter = trimmed.substring(0, trimmed.offsetByCodePoints(0, 1))
    val letter = Normalizer.normalize(firstCharacter, Normalizer.Form.NFD).uppercase(Locale.ROOT).first()
    return if (letter in 'A'..'Z') letter else NON_LETTER_SECTION
}

/**
 * This list in the library order every Home list uses (Phase 5 ruling 1). It sorts by [indexLetter]'s section first,
 * '#' and then A–Z, so each rail section is one contiguous run. Then it sorts by root-locale collation at primary
 * strength, which ignores case and accents ("Émile" sorts between "Emil" and "Emma"). Then [tieBreak] decides.
 * Collation keys are computed once a name, so a large library sorts in a few milliseconds. Call it off the main thread.
 */
fun <T> List<T>.sortedByLibraryName(tieBreak: Comparator<in T>, name: (T) -> String): List<T> {
    if (size < 2) return toList()
    val collator = libraryCollator()
    return map { item ->
        val itemName = name(item)
        LibrarySortEntry(sectionRank(indexLetter(itemName)), collator.getCollationKey(itemName), item)
    }
        .sortedWith { a, b ->
            val bySection = a.sectionRank.compareTo(b.sectionRank)
            val byName = if (bySection != 0) bySection else a.key.compareTo(b.key)
            if (byName != 0) byName else tieBreak.compare(a.item, b.item)
        }
        .map { it.item }
}

/**
 * The same order as [sortedByLibraryName], without cached keys. It is for tie-breaks (the Folders path) and short
 * lists. Each call makes its own collator, because a `Collator` must not be shared between threads.
 */
fun <T> libraryNameComparator(name: (T) -> String): Comparator<T> {
    val collator = libraryCollator()
    return Comparator { a, b ->
        val nameA = name(a)
        val nameB = name(b)
        val bySection = sectionRank(indexLetter(nameA)).compareTo(sectionRank(indexLetter(nameB)))
        if (bySection != 0) bySection else collator.compare(nameA, nameB)
    }
}

private fun libraryCollator(): Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

private fun sectionRank(letter: Char): Int = if (letter == NON_LETTER_SECTION) 0 else letter - 'A' + 1

private class LibrarySortEntry<T>(val sectionRank: Int, val key: CollationKey, val item: T)
```

- [ ] **Step 5: Run the test to verify it passes.**
  - Run: `./gradlew :core:common:testDebugUnitTest --tests "*LibraryOrderTest"`. Expected: PASS, 6 tests.
  - Run each named mutation, quote its RED assertion line, and revert byte-exact.

- [ ] **Step 6: Write the device parity test.** Android's `Collator` is ICU, not the JDK. This test runs the same fixture through it, and the controller runs it on the device in Task 7. `LibraryOrderDeviceTest.kt`:

```kotlin
package com.rolla.musicplayer.core.common.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// The JVM suite runs the JDK collator. Android's java.text.Collator is ICU-backed, so the same rule is pinned here on the
// device (Phase 5 ruling 14). The fixture and expectations are the JVM test's, verbatim.
@RunWith(AndroidJUnit4::class)
class LibraryOrderDeviceTest {

    @Test
    fun icuCollationGivesTheSameLibraryOrder() {
        val input = listOf("Zebra", "Eva", "Ωmega", "Apricot", "Émile", "1999", "Fox", "apple", "Emma")
        val expected = listOf("1999", "Ωmega", "apple", "Apricot", "Émile", "Emma", "Eva", "Fox", "Zebra")
        assertEquals(expected, input.sortedByLibraryName(naturalOrder()) { it })
        assertEquals(listOf("B", "b"), listOf("b", "B").sortedByLibraryName(naturalOrder()) { it })
    }

    @Test
    fun icuNormalizerGivesTheSameSections() {
        assertEquals(listOf('E', 'N', 'A', '#', '#'), listOf("Émile", "ñu", "Åsa", "Øl", "Ωmega").map(::indexLetter))
    }
}
```

- [ ] **Step 7: Run the module checks.**
  - Run: `./gradlew :core:common:check :core:common:compileDebugAndroidTestKotlin :core:data:compileDebugKotlin spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report every mutation with its RED line. Do not commit.

---

### Task 2: Repositories sort in the library order (data-layer-agent)

**Files:**
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/di/CoreDataIoDispatcher.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/SongRepositoryImpl.kt`, `PlaylistRepositoryImpl.kt`, `AlbumRepositoryImpl.kt`, `ArtistRepositoryImpl.kt`, `FolderRepositoryImpl.kt`
- Test: the five matching `*RepositoryImplTest.kt` files in `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/repository/`

**Interfaces:**
- Consumes: `sortedByLibraryName` and `libraryNameComparator` from Task 1.
- Produces:
  - `@CoreDataDefaultDispatcher`, a qualifier in `com.rolla.musicplayer.core.data.di`, bound to `Dispatchers.Default`.
  - Each `*RepositoryImpl` constructor gains a last parameter, `@CoreDataDefaultDispatcher sortDispatcher: CoroutineDispatcher`.
  - The public repository interfaces are **unchanged**. These flows now emit in library order: `SongRepository.observeSongs()` and `observeFavourites()`, `PlaylistRepository.observePlaylists()`, `AlbumRepository.observeAlbums()`, `ArtistRepository.observeArtists()` and `FolderRepository.observeFolders()`.

**Consumer audit (done at planning; the implementer re-greps and reports any new caller):**

| Flow | Callers | Effect |
|---|---|---|
| `observeSongs` | `TracksViewModel` (list + play-all/shuffle), `BatchTagEditorViewModel`, `TagSaveFinalizer` | Tracks gets the new order. Play-all plays the list as shown, as before. The tag editors only `associateBy` the result, so order does not matter. |
| `observeFavourites` | `FavouritesTabViewModel`, `PlaylistDetailViewModel` (Favourite tracks), `PlaylistsViewModel` (card count and collage), `PlayerViewModel` (membership) | The tab and the smart-playlist detail get the new order. The card collage takes the first four arts in the new order. The player reads membership only. |
| `observePlaylists` | Playlists tab, and the "Add to playlist" sheet in Tracks, Favourites, FolderDetail, Player and PlaylistDetail | The tab and the sheets list playlists in the same order. |
| `observeAlbums` | `AlbumsTabViewModel` | The grid gets the new order (ruling 5). |
| `observeArtists` | `ArtistsTabViewModel` | The tab gets the new order. |
| `observeFolders` | `FoldersTabViewModel` | Ruling 4, with the same tie-break meaning as Phase 4b. |

- [ ] **Step 1: Add the qualifier.** In `CoreDataIoDispatcher.kt`, add this next to `CoreDataIoDispatcher`:

```kotlin
/**
 * Qualifies the [CoroutineDispatcher] the library repositories sort on (Phase 5 ruling 3). Room's flows emit in the
 * collector's context, the main thread, and a large library's collation sort costs about a frame.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CoreDataDefaultDispatcher
```

Then add this to `CoreDataDispatchersModule`:

```kotlin
    @Provides
    @CoreDataDefaultDispatcher
    fun provideCoreDataDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
```

- [ ] **Step 2: Write the failing tests.** Update every existing construction in the five test files to pass a test dispatcher:
  - `SongRepositoryImplTest`: `SongRepositoryImpl(fakeSongDao, mainDispatcherRule.testDispatcher)`.
  - The others: `UnconfinedTestDispatcher()`. Use the test's `MainDispatcherRule` dispatcher where one exists.

  Then add these tests.

`SongRepositoryImplTest` (uses `FakeSongDao` and its existing `testSongEntity` helper; adapt the insert call to the fake's API):

```kotlin
    // Ruling 1. Inserted in NOCASE order (accented initials after Z, as SQLite gives them).
    // Mutation: drop the sort → ["Zed", "Émile"…] → RED.
    @Test
    fun `observeSongs_sortsInLibraryOrder`() = runTest {
        listOf("1" to "Zed", "2" to "Émile", "3" to "echo", "4" to "Abba").forEach { (id, title) ->
            fakeSongDao.insert(testSongEntity(id = id, mediaStoreId = id.toLong(), title = title))
        }
        assertEquals(listOf("Abba", "echo", "Émile", "Zed"), repository.observeSongs().first().map { it.title })
    }

    // Ruling 4. Identical titles; the DAO hands the higher id first. Mutation: drop .thenBy { it.id } → ["9", "2"] → RED.
    @Test
    fun `observeSongs_breaksTitleTiesById`() = runTest {
        fakeSongDao.insert(testSongEntity(id = "9", mediaStoreId = 9L, title = "Intro"))
        fakeSongDao.insert(testSongEntity(id = "2", mediaStoreId = 2L, title = "Intro"))
        assertEquals(listOf("2", "9"), repository.observeSongs().first().map { it.id })
    }

    // Mutation: leave observeFavourites unsorted → ["Zed", "Émile"] → RED. Control: both are favourites (count 2).
    @Test
    fun `observeFavourites_sortsInLibraryOrder`() = runTest {
        fakeSongDao.insert(testSongEntity(id = "1", mediaStoreId = 1L, title = "Zed", isFavorite = true))
        fakeSongDao.insert(testSongEntity(id = "2", mediaStoreId = 2L, title = "Émile", isFavorite = true))
        assertEquals(listOf("Émile", "Zed"), repository.observeFavourites().first().map { it.title })
    }
```

If `FakeSongDao` already returns a sorted order that makes any of these GREEN with the sort removed, the probe is blind. Change the fixture's insert order so the fake's order differs from the expected one, and report it. If `testSongEntity` has no favourite parameter, set the entity's favourite field with `copy(...)`.

`PlaylistRepositoryImplTest`:

```kotlin
    // Ruling 1. Created in neither the expected nor binary order. Mutation: drop the sort → the fake's order → RED.
    @Test
    fun `observePlaylists_sortsInLibraryOrder`() = runTest {
        listOf("Zebra", "Émile", "apple").forEach { repository.createPlaylist(it) }
        assertEquals(listOf("apple", "Émile", "Zebra"), repository.observePlaylists().first().map { it.name })
    }
```

`AlbumRepositoryImplTest` and `ArtistRepositoryImplTest` (MockK DAO; build rows with each file's existing row helper or constructor):

```kotlin
    // Ruling 5. The DAO order is NOCASE: "Émile" after "Zappa". Mutation: drop the sort → RED.
    @Test
    fun observeAlbums_sortsInLibraryOrder() = runTest {
        every { albumDao.observeAlbums() } returns flowOf(listOf(albumRow(1L, "Abba"), albumRow(2L, "Zappa"), albumRow(3L, "Émile")))
        assertEquals(listOf("Abba", "Émile", "Zappa"), repository.observeAlbums().first().map { it.title })
    }
```

```kotlin
    // Ruling 1. Mutation: drop the sort → ["Abba", "Zappa", "Émile"] → RED.
    @Test
    fun observeArtists_sortsInLibraryOrder() = runTest {
        every { artistDao.observeArtists() } returns flowOf(listOf(artistRow("Abba"), artistRow("Zappa"), artistRow("Émile")))
        assertEquals(listOf("Abba", "Émile", "Zappa"), repository.observeArtists().first().map { it.name })
    }
```

`FolderRepositoryImplTest`: keep the four existing tests unchanged, with only the constructor updated. They are the safety net for ruling 4. Add:

```kotlin
    // Ruling 1 (the 4b carry-over). CASE_INSENSITIVE_ORDER puts "Éclair" after "Zed". Mutation: restore the 4b
    // comparator → ["Abba", "Zed", "Éclair"] → RED.
    @Test
    fun observeFolders_sortsAccentedNamesWithTheirLetter() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(FolderSummary("Music/Abba", 1, "a"), FolderSummary("Music/Zed", 1, "z"), FolderSummary("Éclair", 1, "e")),
        )
        assertEquals(listOf("Abba", "Éclair", "Zed"), repository.observeFolders().first().map { it.name })
    }
```

- [ ] **Step 3: Run them to verify they fail.**
  - Run: `./gradlew :core:data:testDebugUnitTest --tests "*RepositoryImplTest"`.
  - Expected: `BUILD FAILED` at compilation, because the constructors don't take a dispatcher yet.

- [ ] **Step 4: Implement.** In each repository:
  - add the constructor parameter `@CoreDataDefaultDispatcher private val sortDispatcher: CoroutineDispatcher`;
  - sort after the existing `distinctUntilChanged()`;
  - end with `.flowOn(sortDispatcher)`.

`SongRepositoryImpl`:

```kotlin
    override fun observeSongs(): Flow<List<Song>> =
        songDao.observeAllSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { songs -> songs.sortedByLibraryName(SongTieBreak) { it.title } }
            .flowOn(sortDispatcher)

    override fun observeFavourites(): Flow<List<Song>> =
        songDao.observeFavourites()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { songs -> songs.sortedByLibraryName(SongTieBreak) { it.title } }
            .flowOn(sortDispatcher)

    private companion object {
        // Ruling 4: an exact-title tie goes to the id, so equal titles never swap between emissions.
        val SongTieBreak: Comparator<Song> = compareBy<Song> { it.title }.thenBy { it.id }
    }
```

(If `SongRepositoryImpl` already has a companion object, add `SongTieBreak` to it.)

`PlaylistRepositoryImpl.observePlaylists`:

```kotlin
            .map { list -> list.map { it.playlist.toDomain(it.songCount) } }
            .distinctUntilChanged()
            .map { playlists -> playlists.sortedByLibraryName(compareBy<Playlist> { it.name }.thenBy { it.id }) { it.name } }
            .flowOn(sortDispatcher)
```

`AlbumRepositoryImpl.observeAlbums`:

```kotlin
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { albums -> albums.sortedByLibraryName(compareBy<Album> { it.title }.thenBy { it.id }) { it.title } }
            .flowOn(sortDispatcher)
```

`ArtistRepositoryImpl.observeArtists`:

```kotlin
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { artists -> artists.sortedByLibraryName(compareBy { it.name }) { it.name } }
            .flowOn(sortDispatcher)
```

`FolderRepositoryImpl`: replace `folderOrder` and the sort in `observeFolders`:

```kotlin
    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders()
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { folders -> folders.sortedByLibraryName(folderTieBreak()) { it.name } }
            .flowOn(sortDispatcher)

    private companion object {
        // Ruling 2 of Phase 4b, in the library order (Phase 5 ruling 4). The name decides. Same-name folders fall
        // back to the path in library order, then exactly. A fresh comparator per emission, because a Collator must
        // not be shared between threads.
        fun folderTieBreak(): Comparator<Folder> = libraryNameComparator<Folder> { it.path }.thenBy { it.path }
    }
```

Keep the existing `observeFolderSongs` and the other non-list flows unchanged: they are not Home lists.

- [ ] **Step 5: Run the tests to verify they pass.**
  - Run: `./gradlew :core:data:testDebugUnitTest`. Expected: PASS, the whole module, the existing tests included.
  - Run each named mutation, quote its RED line, and revert.
  - Re-grep the callers in the consumer audit with `grep -rn "observeSongs()\|observeFavourites()\|observePlaylists()\|observeAlbums()\|observeArtists()\|observeFolders()" --include=*.kt app core feature`. Report any caller not in the table.

- [ ] **Step 6: Run the checks.**
  - Run: `./gradlew :core:data:check :app:kaptDebugKotlin :app:compileDebugKotlin spotlessApply`. Expected: `BUILD SUCCESSFUL`. Hilt resolves the new qualifier. If the app uses KSP for Hilt, the task is `:app:kspDebugKotlin`; use whichever exists.
  - Report. Do not commit.

---

### Task 3: Design system: the rail's index logic and its token (m3-design-system-agent)

**Files:**
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt`
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollIndex.kt`
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollIndexTest.kt`

**Interfaces:**
- Consumes: `indexLetter` and `NON_LETTER_SECTION` from Task 1.
- Produces (package `com.rolla.musicplayer.core.designsystem.component`):
  - `data class FastScrollSection(val label: String, val firstIndex: Int, val populated: Boolean)`
  - `val FastScrollLabels: List<String>`: `"#"`, then `"A"`…`"Z"` (27 labels).
  - `fun buildFastScrollSections(keys: List<String>): List<FastScrollSection>`, which returns 27 sections, or an empty list for empty keys.
  - `fun fastScrollLabelStep(slots: Int, labelCount: Int = FastScrollLabels.size): Int`
  - `fun fastScrollSectionAt(sections: List<FastScrollSection>, fraction: Float): FastScrollSection`
  - `RollaDimens.fastScrollLetterSlot: Dp = 16.dp`

- [ ] **Step 1: Add the dependency and the token.**
  - In `core/designsystem/build.gradle.kts`'s `dependencies`, add `implementation(project(":core:common"))` (ruling 2).
  - In `Dimens.kt`, next to `fastScrollEnd`, add:

```kotlin
    /** Rail height one A–Z label needs (spec §10: the 11 sp letter plus spacing). The rail drops every k-th label below 27 slots. */
    val fastScrollLetterSlot = 16.dp
```

- [ ] **Step 2: Write the failing test.** `FastScrollIndexTest.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollIndexTest {

    private fun List<FastScrollSection>.of(label: String) = single { it.label == label }

    @Test
    fun labelsAreHashThenAToZ() {
        assertEquals(27, FastScrollLabels.size)
        assertEquals("#", FastScrollLabels.first())
        assertEquals("A", FastScrollLabels[1])
        assertEquals("Z", FastScrollLabels.last())
    }

    // Keys in library order. Populated letters point at their first row. Spec §10: an empty letter targets the next
    // populated section. Mutations: (a) target the previous populated section → B gives 1 → RED; (b) record the last
    // index instead of the first (put, not putIfAbsent) → E gives 3 → RED.
    @Test
    fun populatedSectionsPointAtTheirFirstRowAndEmptyOnesAtTheNext() {
        val sections = buildFastScrollSections(listOf("1999", "Abba", "Echo", "Emma", "Zed"))
        assertEquals(27, sections.size)
        assertEquals(FastScrollSection("#", 0, populated = true), sections.of("#"))
        assertEquals(FastScrollSection("A", 1, populated = true), sections.of("A"))
        assertEquals(FastScrollSection("B", 2, populated = false), sections.of("B"))
        assertEquals(FastScrollSection("E", 2, populated = true), sections.of("E"))
        assertEquals(FastScrollSection("F", 4, populated = false), sections.of("F"))
        assertEquals(FastScrollSection("Z", 4, populated = true), sections.of("Z"))
    }

    // Spec §10: past the last populated section, an empty letter targets the previous one; before the first, the next.
    // Mutation: drop the "previous" fallback → no target for N…Z → crash/RED.
    @Test
    fun emptyLettersAtTheEndTargetThePreviousSection() {
        val sections = buildFastScrollSections(listOf("Abba", "Mud"))
        assertEquals(FastScrollSection("#", 0, populated = false), sections.of("#"))
        assertEquals(FastScrollSection("N", 1, populated = false), sections.of("N"))
        assertEquals(FastScrollSection("Z", 1, populated = false), sections.of("Z"))
    }

    // Review Focus 1: the section comes from indexLetter, so an accented initial counts under its letter. Mutation:
    // key on key.first().uppercaseChar() instead of indexLetter → 'É' → no E section → RED.
    @Test
    fun accentedInitialsPopulateTheirLetter() {
        val sections = buildFastScrollSections(listOf("Abba", "Émile", "Fox"))
        assertEquals(FastScrollSection("E", 1, populated = true), sections.of("E"))
    }

    @Test
    fun noKeysMeansNoSections() {
        assertTrue(buildFastScrollSections(emptyList()).isEmpty())
    }

    // Spec §10: the smallest k for which every k-th label fits. Mutation: ceil → floor in the fit check → 26 slots
    // gives 1 → RED.
    @Test
    fun labelStepIsTheSmallestThatFits() {
        assertEquals(1, fastScrollLabelStep(slots = 27))
        assertEquals(1, fastScrollLabelStep(slots = 40))
        assertEquals(2, fastScrollLabelStep(slots = 26))
        assertEquals(2, fastScrollLabelStep(slots = 14))
        assertEquals(3, fastScrollLabelStep(slots = 13))
        assertEquals(4, fastScrollLabelStep(slots = 8))
        assertEquals(27, fastScrollLabelStep(slots = 1))
        assertEquals(27, fastScrollLabelStep(slots = 0))
    }

    // Every label owns an equal slot of the rail, dropped ones included (spec §10: drag still reaches them).
    // Mutation: divide by the visible-label count instead of sections.size → 0.759 maps elsewhere → RED.
    @Test
    fun sectionAtMapsAFractionOverAllLabels() {
        val sections = buildFastScrollSections(listOf("Abba"))
        assertEquals("#", fastScrollSectionAt(sections, 0f).label)
        assertEquals("M", fastScrollSectionAt(sections, 13.5f / 27f).label)
        assertEquals("T", fastScrollSectionAt(sections, 20.5f / 27f).label)
        assertEquals("Z", fastScrollSectionAt(sections, 1f).label)
        assertEquals("#", fastScrollSectionAt(sections, -0.5f).label)
        assertEquals("Z", fastScrollSectionAt(sections, 1.5f).label)
    }
}
```

- [ ] **Step 3: Run it to verify it fails.**
  - Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*FastScrollIndexTest"`
  - Expected: `BUILD FAILED`, with `Unresolved reference 'buildFastScrollSections'`.

- [ ] **Step 4: Implement.** `FastScrollIndex.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import com.rolla.musicplayer.core.common.text.NON_LETTER_SECTION
import com.rolla.musicplayer.core.common.text.indexLetter

/**
 * One label of the A–Z rail (spec §10). [firstIndex] is the list index a jump scrolls to. For a [populated] section
 * that is its first row. For an empty one it is the next populated section's first row, or the previous one's at the
 * end of the alphabet.
 */
data class FastScrollSection(val label: String, val firstIndex: Int, val populated: Boolean)

/** The rail's labels: "#", then A–Z. Every one is shown when it fits, populated or not. */
val FastScrollLabels: List<String> = listOf(NON_LETTER_SECTION.toString()) + ('A'..'Z').map(Char::toString)

/**
 * The rail's sections for [keys], each item's display name in list order. The list must be in the library order
 * (`sortedByLibraryName`), so that each label's rows are one run. Returns no sections for no keys, which hides the rail.
 */
fun buildFastScrollSections(keys: List<String>): List<FastScrollSection> {
    if (keys.isEmpty()) return emptyList()
    val firstIndexByLabel = HashMap<String, Int>()
    keys.forEachIndexed { index, key -> firstIndexByLabel.putIfAbsent(indexLetter(key).toString(), index) }
    val firstIndices = FastScrollLabels.map { firstIndexByLabel[it] }
    return FastScrollLabels.mapIndexed { position, label ->
        val own = firstIndices[position]
        val target = own
            ?: firstIndices.subList(position + 1, firstIndices.size).firstNotNullOfOrNull { it }
            ?: firstIndices.subList(0, position).filterNotNull().last()
        FastScrollSection(label = label, firstIndex = target, populated = own != null)
    }
}

/**
 * The smallest step k for which every k-th of [labelCount] labels fits in [slots] letter slots (spec §10). With fewer
 * than one slot only the first label shows.
 */
fun fastScrollLabelStep(slots: Int, labelCount: Int = FastScrollLabels.size): Int {
    if (slots < 1) return labelCount
    var step = 1
    while ((labelCount + step - 1) / step > slots) step++
    return step
}

/**
 * The section under a touch at [fraction] of the rail's height (0 is the top, 1 the bottom). Every label owns an equal
 * slot, shown or dropped, so a drag reaches all of them. Out-of-range fractions clamp to the ends.
 */
fun fastScrollSectionAt(sections: List<FastScrollSection>, fraction: Float): FastScrollSection =
    sections[(fraction * sections.size).toInt().coerceIn(0, sections.lastIndex)]
```

- [ ] **Step 5: Run the test to verify it passes.**
  - Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*FastScrollIndexTest"`. Expected: PASS, 7 tests.
  - Run each mutation and revert.
  - If `ThemeTokensTest` or `TokenInvariantsTest` enumerates `RollaDimens` and fails on the new token, add the token to its list. Do not weaken it.

- [ ] **Step 6: Run the checks.**
  - Run: `./gradlew :core:designsystem:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 4: Design system: `FastScrollRail` and `FastScrollContainer` (m3-design-system-agent)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollRail.kt`
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/FastScrollRailTest.kt`

**Interfaces:**
- Consumes: Task 3's `FastScrollSection`, `fastScrollLabelStep`, `fastScrollSectionAt` and `RollaDimens.fastScrollLetterSlot`, plus the existing `homeListBottomPadding()`, `fastScrollTrack`, `fastScrollIndex` and `fastScrollLetter` tokens.
- Produces (package `com.rolla.musicplayer.core.designsystem.component`):
  - `const val FAST_SCROLL_RAIL_TEST_TAG = "fast_scroll_rail"`
  - `@Composable fun FastScrollRail(sections: List<FastScrollSection>, onSectionSelected: (FastScrollSection) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun FastScrollContainer(listState: LazyListState, sections: List<FastScrollSection>, modifier: Modifier = Modifier, itemOffset: Int = 0, railBottomPadding: Dp = homeListBottomPadding(), content: @Composable BoxScope.() -> Unit)`

- [ ] **Step 1: Write the failing test.** Follow `SlidersTest`/`OneUiTabRowTest` for the Robolectric setup: `@RunWith(AndroidJUnit4::class)`, `@Config(qualifiers = "w360dp-h640dp")`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and `createComposeRule()`. `FastScrollRailTest.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

// Imports: follow SlidersTest; the APIs used are androidx.compose.ui.test.*, SemanticsActions/SemanticsProperties,
// LocalHapticFeedback/HapticFeedback/HapticFeedbackType, foundation.lazy.*, foundation.pager.*.

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FastScrollRailTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val haptics = object : HapticFeedback {
        var ticks = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (hapticFeedbackType == HapticFeedbackType.TextHandleMove) ticks++
        }
    }

    // 50 rows: "Alpha 0".."Alpha 24" (index 0–24), "Mango 0".."Mango 24" (25–49). Only A and M are populated.
    private val names = List(25) { "Alpha $it" } + List(25) { "Mango $it" }
    private val sections = buildFastScrollSections(names)

    private fun rail() = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG)

    private fun jumpAction(label: String) =
        rail().fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Jump to $label" }

    private fun setRail(height: Dp = 432.dp, sections: List<FastScrollSection> = this.sections, onSelected: (FastScrollSection) -> Unit) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    FastScrollRail(sections = sections, onSectionSelected = onSelected, modifier = Modifier.height(height))
                }
            }
        }
    }

    // Spec §10: one node with actions only for populated sections. Mutation: build actions for every section →
    // "Jump to B" exists → RED. Control: the node and "Jump to A"/"Jump to M" exist.
    @Test
    fun railIsOneNodeWithAJumpActionPerPopulatedSection() {
        setRail {}
        rail().assertContentDescriptionEquals("Fast scroll, letter index")
        val labels = rail().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Jump to A", "Jump to M"), labels)
    }

    // Mutation: the action calls onSectionSelected with sections.first() → "#" → RED.
    @Test
    fun jumpActionSelectsItsSection() {
        val selected = mutableListOf<String>()
        setRail { selected += it.label }
        composeRule.runOnIdle { jumpAction("M").action() }
        assertEquals(listOf("M"), selected)
    }

    // Slot 20 of 27 is "T", which is empty and targets M's row (index 25). Mutation: map the fraction over 26 slots → 19
    // ("S") → RED. Control: one haptic tick.
    @Test
    fun tapSelectsTheLabelUnderTheFingerAndTicksOnce() {
        val selected = mutableListOf<FastScrollSection>()
        setRail { selected += it }
        rail().performTouchInput { click(Offset(centerX, height * 20.5f / 27f)) }
        assertEquals(listOf(FastScrollSection("T", 25, populated = false)), selected)
        assertEquals(1, haptics.ticks)
    }

    // Mutations: (a) select on every move without the label check → repeats → RED; (b) tick on every move → ticks >
    // selections → RED. Control: the drag ends on Z.
    @Test
    fun dragSelectsEachLabelOnceAndTicksPerChange() {
        val selected = mutableListOf<String>()
        setRail { selected += it.label }
        rail().performTouchInput { swipe(Offset(centerX, 1f), Offset(centerX, height - 1f), durationMillis = 1_000) }
        assertEquals("Z", selected.last())
        assertTrue("consecutive repeats in $selected", selected.zipWithNext().none { (a, b) -> a == b })
        assertEquals(selected.size, haptics.ticks)
    }

    // Review Focus 4. A 200 dp rail fits 12 slots, so it shows every 3rd label, but a drag still reaches Z and TalkBack
    // still lists every populated section. Mutation: map the fraction over the visible labels only → never Z → RED.
    @Test
    fun aShortRailStillReachesEveryLetter() {
        val selected = mutableListOf<String>()
        setRail(height = 200.dp) { selected += it.label }
        rail().performTouchInput { swipe(Offset(centerX, 1f), Offset(centerX, height - 1f), durationMillis = 1_000) }
        assertEquals("Z", selected.last())
        assertEquals(listOf("Jump to A", "Jump to M"), rail().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
    }

    @Test
    fun noSectionsRendersNothing() {
        setRail(sections = emptyList()) {}
        rail().assertDoesNotExist()
    }

    // ── FastScrollContainer, over a real LazyColumn and LazyListState (the framework entry point) ──

    private lateinit var listState: LazyListState

    private fun setList(rows: List<String>, itemOffset: Int = 0, railBottomPadding: Dp = 0.dp) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                listState = rememberLazyListState()
                FastScrollContainer(
                    listState = listState,
                    sections = buildFastScrollSections(rows),
                    itemOffset = itemOffset,
                    railBottomPadding = railBottomPadding,
                    modifier = Modifier.fillMaxSize().testTag("container"),
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (itemOffset == 1) item(key = "header") { Box(Modifier.fillMaxWidth().height(143.dp)) }
                        items(rows, key = { it }) { Text(it, Modifier.fillMaxWidth().height(70.dp)) }
                    }
                }
            }
        }
    }

    // Spec §10: hidden when the list fits. Five 70 dp rows fit in 640 dp. Mutation: drop the canScroll condition → the
    // rail shows → RED. Control: the rows render.
    @Test
    fun railIsHiddenWhenTheListFits() {
        setList(List(5) { "Alpha $it" })
        composeRule.onNodeWithText("Alpha 4").assertExists()
        rail().assertDoesNotExist()
    }

    // The jump goes through the real LazyListState, offset past a header item (the Playlists feature cards). Mutation:
    // drop itemOffset from scrollToItem → firstVisibleItemIndex 25 → RED. Control: the rail exists.
    @Test
    fun aJumpScrollsTheListPastItsHeader() {
        setList(names, itemOffset = 1)
        rail().assertExists()
        composeRule.runOnIdle { jumpAction("M").action() }
        composeRule.runOnIdle { assertEquals(26, listState.firstVisibleItemIndex) }
    }

    // Ruling 8. The rail stops railBottomPadding above the container's bottom, at the pill. Mutation: drop the bottom
    // padding → the rail's bottom equals the container's → RED.
    @Test
    fun railEndsAboveTheBottomPadding() {
        setList(names, railBottomPadding = 60.dp)
        val container = composeRule.onNodeWithTag("container").getUnclippedBoundsInRoot()
        val railBounds = rail().getUnclippedBoundsInRoot()
        assertEquals((container.bottom - 60.dp).value, railBounds.bottom.value, 0.5f)
        assertEquals(container.top.value, railBounds.top.value, 0.5f)
    }

    // Spec §10: TalkBack reaches the rail after the list. Mutation: drop traversalIndex → the property is absent → RED.
    @Test
    fun railIsTraversedAfterTheList() {
        setList(names)
        assertEquals(1f, rail().fetchSemanticsNode().config[SemanticsProperties.TraversalIndex])
    }

    // Review Focus 2 / spec §18. A horizontal swipe that starts on the rail pages the pager and jumps nothing.
    // Mutation: one awaitEachGesture handler that selects on down and consumes every move → the pager stays on 0 (and
    // selected is non-empty) → RED. Control: the rail is on screen before the swipe; that vertical gestures on it do
    // select is pinned by dragSelectsEachLabelOnceAndTicksPerChange.
    @Test
    fun aHorizontalSwipeOnTheRailPagesInsteadOfJumping() {
        val selected = mutableListOf<String>()
        lateinit var pagerState: PagerState
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                pagerState = rememberPagerState { 2 }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        FastScrollRail(sections = sections, onSectionSelected = { selected += it.label }, modifier = Modifier.height(432.dp))
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        rail().assertExists()
        rail().performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX - 600f, centerY), durationMillis = 300) }
        composeRule.runOnIdle { assertEquals(1, pagerState.currentPage) }
        assertTrue("swipe selected $selected", selected.isEmpty())
    }
}
```

The rail sits at the pager page's start, not the screen's end. That is irrelevant to the gesture, and keeps the swipe inside the pager.

- [ ] **Step 2: Run it to verify it fails.**
  - Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*FastScrollRailTest"`
  - Expected: `BUILD FAILED`, with `Unresolved reference 'FastScrollRail'`.

- [ ] **Step 3: Implement.** `FastScrollRail.kt`. Match the neighbouring component files (for example `SortHeader.kt`) for any file-level `@Suppress` and preview conventions.

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/** UI test and baseline-profile handle for the A–Z rail. */
const val FAST_SCROLL_RAIL_TEST_TAG = "fast_scroll_rail"

private const val FAST_SCROLL_RAIL_DESCRIPTION = "Fast scroll, letter index"

/**
 * The One UI A–Z rail (spec §10): a [fastScrollTrack] pill with "#" and A–Z in equal slots, pinned to the end of a
 * list. A tap or a vertical drag calls [onSectionSelected] once each time the label under the finger changes, with a
 * `TextHandleMove` tick. When the rail is too short for 27 slots it shows every k-th label (spec §10, ruling 10), and a
 * drag still reaches the dropped ones. For TalkBack it is one node with a "Jump to X" action per populated section,
 * traversed after the list. It renders nothing for no [sections]. The touch width is `fastScrollWidth + fastScrollEnd`
 * (30 dp), the documented exception to the 48 dp rule (spec §5.4).
 */
@Composable
fun FastScrollRail(
    sections: List<FastScrollSection>,
    onSectionSelected: (FastScrollSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val currentSections by rememberUpdatedState(sections)
    val currentOnSectionSelected by rememberUpdatedState(onSectionSelected)
    val tracker = remember { LabelTracker() }
    fun select(y: Float, height: Int) {
        if (height <= 0) return
        val section = fastScrollSectionAt(currentSections, y / height)
        if (section.label == tracker.lastLabel) return
        tracker.lastLabel = section.label
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSectionSelected(section)
    }
    val letterStyle = MaterialTheme.typography.fastScrollLetter
    val letterSlot = with(LocalDensity.current) { maxOf(RollaDimens.fastScrollLetterSlot, letterStyle.lineHeight.toDp()) }
    BoxWithConstraints(
        modifier = modifier
            .width(RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd)
            // Ruling 11: separate detectors, so a horizontal swipe from the rail still reaches the Home pager.
            .pointerInput(haptics) {
                detectTapGestures(onTap = { offset -> tracker.lastLabel = null; select(offset.y, size.height) })
            }
            .pointerInput(haptics) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> tracker.lastLabel = null; select(offset.y, size.height) },
                    onVerticalDrag = { change, _ -> change.consume(); select(change.position.y, size.height) },
                )
            }
            .clearAndSetSemantics {
                testTag = FAST_SCROLL_RAIL_TEST_TAG
                contentDescription = FAST_SCROLL_RAIL_DESCRIPTION
                traversalIndex = 1f
                customActions = sections.filter { it.populated }.map { section ->
                    CustomAccessibilityAction("Jump to ${section.label}") {
                        currentOnSectionSelected(section)
                        true
                    }
                }
            },
    ) {
        val step = fastScrollLabelStep((maxHeight / letterSlot).toInt(), sections.size)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = RollaDimens.fastScrollEnd)
                .width(RollaDimens.fastScrollWidth)
                .background(MaterialTheme.colorScheme.fastScrollTrack, CircleShape),
        ) {
            sections.forEachIndexed { index, section ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (index % step == 0) {
                        Text(
                            text = section.label,
                            style = letterStyle,
                            color = MaterialTheme.colorScheme.fastScrollIndex,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overlays a [FastScrollRail] on a list (spec §10, ruling 7). [content] is the `LazyColumn` driven by [listState]. The
 * rail shows only while the list can scroll, and only with sections. It runs from the top of the container to
 * [railBottomPadding] above its bottom (the floating pill on Home, ruling 8). A selection calls
 * `listState.scrollToItem(firstIndex + itemOffset)`, where [itemOffset] counts the list's items before the sectioned
 * rows (Playlists' feature cards). The container is a traversal group, so TalkBack visits the rail after the list.
 */
@Composable
fun FastScrollContainer(
    listState: LazyListState,
    sections: List<FastScrollSection>,
    modifier: Modifier = Modifier,
    itemOffset: Int = 0,
    railBottomPadding: Dp = homeListBottomPadding(),
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollable by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    Box(modifier = modifier.semantics { isTraversalGroup = true }) {
        content()
        if (scrollable && sections.isNotEmpty()) {
            FastScrollRail(
                sections = sections,
                onSectionSelected = { section -> scope.launch { listState.scrollToItem(section.firstIndex + itemOffset) } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(bottom = railBottomPadding)
                    .fillMaxHeight(),
            )
        }
    }
}

/** The label the current gesture last selected; reset at each tap and drag start, so each one ticks. */
private class LabelTracker {
    var lastLabel: String? = null
}
```

Add previews of a 432 dp `FastScrollRail` over `buildFastScrollSections(listOf("Abba", "Mango", "Zed"))`, in dark, light and fontScale 1.5. Follow the `KitPreviews.kt` naming (`PreviewFastScrollRail…`).

- [ ] **Step 4: Run the test to verify it passes.**
  - Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*FastScrollRailTest"`. Expected: PASS, 11 tests.
  - Run each named mutation, quote the RED line, and revert.
  - If a Robolectric swipe's event count makes `dragSelectsEachLabelOnceAndTicksPerChange` pass with mutation (b), the probe is blind. Lengthen `durationMillis` until the mutation is RED, and report it.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :core:designsystem:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 5: The five Home tabs get the rail (ui-builder)

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/TracksTab.kt`, `ArtistsTab.kt`, `FoldersTab.kt`
- Modify: `feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/FavouritesTab.kt`, `PlaylistsTab.kt`
- Test: `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/TracksTabTest.kt`, `ArtistsTabTest.kt`, `FoldersTabTest.kt`; `feature/playlists/src/test/kotlin/com/rolla/musicplayer/feature/playlists/FavouritesTabTest.kt`, `PlaylistsTabTest.kt`

**Interfaces:**
- Consumes: Task 3's `buildFastScrollSections`; Task 4's `FastScrollContainer` and `FAST_SCROLL_RAIL_TEST_TAG`. Task 2's repositories already hand each tab its rows in library order, so no tab sorts.
- Produces: `internal const val PLAYLIST_LIST_TEST_TAG = "playlist_list"` in `PlaylistsTab.kt`. Nothing else outside the tabs.

The same change applies to each list composable (`SongList` in Tracks, `ArtistList`, `FolderList`, `FavouritesList`, `PlaylistsList`). It is shown in full for each below.

- [ ] **Step 1: Write the failing tests.** Add this helper to each of the five test files (imports: `SemanticsActions`, `FAST_SCROLL_RAIL_TEST_TAG`, `getUnclippedBoundsInRoot`):

```kotlin
    private fun jumpTo(label: String) {
        val rail = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).fetchSemanticsNode()
        val action = rail.config[SemanticsActions.CustomActions].single { it.label == "Jump to $label" }
        composeRule.runOnIdle { action.action() }
        composeRule.waitForIdle()
    }

    private fun assertAtListTop(listTag: String, rowText: String) {
        val listTop = composeRule.onNodeWithTag(listTag).getUnclippedBoundsInRoot().top
        val rowTop = composeRule.onNodeWithText(rowText).getUnclippedBoundsInRoot().top
        assertEquals(listTop.value, rowTop.value, 0.5f)
    }
```

**`TracksTabTest`** (the songs come from the file's existing song helper; give every song the artist "Zed", so keying on the artist is visible):

```kotlin
    // 25 "Alpha" titles, then 25 "Mango" titles, as the repository orders them; every artist is "Zed".
    // Mutation: key the sections on song.artist → only Z is populated, no "Jump to M" → RED.
    // Control: the rail exists and "Mango 0" lands at the list's top.
    @Test
    fun `the rail jumps to the first track of a letter`() {
        setContent(songs = alphaMango())
        jumpTo("M")
        assertAtListTop(SONG_LIST_TEST_TAG, "Mango 0")
    }

    // Review Focus 3. A rescan inserts 10 "Beta" titles before the Mangos. Mutation: remember the sections without the
    // songs key → M still targets index 25 ("Mango 15" at the top) → RED.
    @Test
    fun `the rail follows the library when it changes`() {
        var songs by mutableStateOf(alphaMango())
        setContent(songsProvider = { songs })
        songs = alphaMango().take(25) + List(10) { song("b$it", "Beta $it") } + alphaMango().drop(25)
        composeRule.waitForIdle()
        jumpTo("M")
        assertAtListTop(SONG_LIST_TEST_TAG, "Mango 0")
    }

    // Review Focus 5. Selection mode is on, and a tap lands on the rail over the rows' end. The list jumps; nothing is
    // played, toggled or opened. Mutation: put the rail before the list in the container's Box (under the rows) → the
    // tap reaches a row → toggles → RED. Control: the list moved off its first row.
    @Test
    fun `a tap on the rail never reaches the row under it`() {
        val played = mutableListOf<Song>()
        val selection = SongSelectionState().apply { toggle("a0") }
        setContent(songs = alphaMango(), selection = selection, onSongClick = { played += it })
        composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).performTouchInput { click(Offset(centerX, height * 13.5f / 27f)) }
        composeRule.waitForIdle()
        assertTrue(played.isEmpty())
        assertEquals(setOf("a0"), selection.selectedIds)
        composeRule.onNodeWithText("Add to playlist").assertDoesNotExist()
        assertAtListTop(SONG_LIST_TEST_TAG, "Mango 0")
    }

    // 4b carry-over T6. Mutation: move the Scanning branch below the list branch → the list renders → RED.
    @Test
    fun `scanning wins over a non-empty list`() {
        setContent(songs = alphaMango(), scanState = ScanState.Scanning)
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithTag(SONG_LIST_TEST_TAG).assertDoesNotExist()
    }

    // Spec §10: no rail when the list fits. Mutation: drop FastScrollContainer's canScroll gate → RED. Control: rows show.
    @Test
    fun `a short library has no rail`() {
        setContent(songs = alphaMango().take(3))
        composeRule.onNodeWithText("Alpha 2").assertExists()
        composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).assertDoesNotExist()
    }
```

- `alphaMango()` builds 50 songs: ids `a0..a24` titled `Alpha 0..24`, then `m0..m24` titled `Mango 0..24`, all with the artist "Zed".
- Extend the file's `setContent` helper with a `songsProvider: () -> List<Song>` overload (the existing `songs` parameter delegates to it), plus `selection` and `onSongClick` parameters, keeping every existing call compiling.
- If `SongSelectionState` has no public constructor or `selectedIds`, use `rememberSongSelectionState()` inside `setContent` and the class's existing read API. Report the adaptation.

**`ArtistsTabTest`:**

```kotlin
    // Ruling 1 / Review Focus 1: the accented "Émile" rows are sectioned under E. Mutation: key the sections on
    // artist.id.toString() → every key is '#' → no "Jump to E" → RED. Control: the rail exists.
    @Test
    fun `the rail jumps to an accented artist under its letter`() {
        val artists = List(20) { artist(it.toLong(), "Abba $it") } + List(20) { artist(100L + it, "Émile $it") }
        setContent(artists = artists)
        jumpTo("E")
        assertAtListTop(ARTIST_LIST_TEST_TAG, "Émile 0")
    }

    // 4b carry-over T6. Mutation: move the Scanning branch below the list branch → RED.
    @Test
    fun `scanning wins over a non-empty list`() {
        setContent(artists = List(3) { artist(it.toLong(), "Artist $it") }, scanState = ScanState.Scanning)
        composeRule.onNodeWithTag(ARTIST_LIST_TEST_TAG).assertDoesNotExist()
    }
```

**`FoldersTabTest`** (folders keyed by path; the rail keys on the display name):

```kotlin
    // The sections come from the display name, not the path: "Music/Mango n" shows as "Mango n". Mutation: key on
    // folder.path → every row is under M ("Music/…") → "Jump to A" missing → RED. Control: "Jump to M" lands on Mango 0.
    @Test
    fun `the rail sections folders by their displayed name`() {
        val folders = List(20) { folder("Download/Alpha $it") } + List(20) { folder("Music/Mango $it") }
        setContent(folders = folders)
        jumpTo("A")
        assertAtListTop(FOLDER_LIST_TEST_TAG, "Alpha 0")
        jumpTo("M")
        assertAtListTop(FOLDER_LIST_TEST_TAG, "Mango 0")
    }

    // 4b carry-over T6. Mutation: move the Scanning branch below the list branch → RED.
    @Test
    fun `scanning wins over a non-empty list`() {
        setContent(folders = List(3) { folder("Music/F$it") }, scanState = ScanState.Scanning)
        composeRule.onNodeWithTag(FOLDER_LIST_TEST_TAG).assertDoesNotExist()
    }
```

`folder(path)` = `Folder(path, folderName(path), 1, "")`. Use the file's helper if it has one.

**`FavouritesTabTest`:**

```kotlin
    // Mutation: leave FavouritesList without the container → no rail → RED.
    @Test
    fun `the rail jumps to the first favourite of a letter`() {
        setContent(songs = alphaMango())
        jumpTo("M")
        assertAtListTop(FAVOURITES_LIST_TEST_TAG, "Mango 0")
    }
```

`alphaMango()` is as in Tracks, built with this file's song helper.

**`PlaylistsTabTest`:**

```kotlin
    // Spec §10: the rail covers the user rows only, so the jump skips the feature-card row. Mutation: itemOffset = 0 →
    // "Alpha 19" at the top and "Mango 0" one row lower → RED. Control: "Jump to M" exists.
    @Test
    fun `the rail jumps over the feature cards to a playlist`() {
        val playlists = List(20) { playlist(it.toLong(), "Alpha $it") } + List(20) { playlist(100L + it, "Mango $it") }
        setContent(userPlaylists = playlists)
        jumpTo("M")
        assertAtListTop(PLAYLIST_LIST_TEST_TAG, "Mango 0")
    }

    // No user playlists: only the cards and the hint, no rail. Mutation: build sections from the smart playlists'
    // labels → a rail appears → RED.
    @Test
    fun `no user playlists means no rail`() {
        setContent(userPlaylists = emptyList())
        composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).assertDoesNotExist()
    }
```

`playlist(id, name)` is the file's helper, or `Playlist(id, name, 0, 0L, 0L)`.

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :feature:library:testDebugUnitTest --tests "*TabTest" :feature:playlists:testDebugUnitTest --tests "*TabTest"`.
  - Expected: `BUILD FAILED`. The library and playlists rail tests fail on `onNodeWithTag("fast_scroll_rail")` finding no node, and Playlists also on the unresolved `PLAYLIST_LIST_TEST_TAG`.
  - The "scanning wins" tests may already pass. They pin current behaviour: run their mutation now to prove they can fail.

- [ ] **Step 3: Implement.** Each list composable gets its own `rememberLazyListState()`, sections remembered on its items, and `FastScrollContainer` around its `LazyColumn`. The caller's `modifier` moves to the container. Imports: `androidx.compose.foundation.lazy.rememberLazyListState`, and `com.rolla.musicplayer.core.designsystem.component.FastScrollContainer` / `buildFastScrollSections`.

`TracksTab.SongList`:

```kotlin
    val selectionActive by remember(selection) { derivedStateOf { selection.isActive } }
    val listState = rememberLazyListState()
    // The repository hands the songs in library order, so each letter's rows are one run (Phase 5 ruling 1).
    val sections = remember(songs) { buildFastScrollSections(songs.map { it.title }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(SONG_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            items(items = songs, key = { song -> song.id }) { song ->
                // … unchanged row body …
            }
        }
    }
```

`ArtistsTab.ArtistList`:

```kotlin
    val listState = rememberLazyListState()
    val sections = remember(artists) { buildFastScrollSections(artists.map { it.name }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(ARTIST_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            // … unchanged items(…) …
        }
    }
```

`FoldersTab.FolderList`:

```kotlin
    val listState = rememberLazyListState()
    // Sections come from the displayed name, the order the repository sorts by (4b ruling 2, Phase 5 ruling 4).
    val sections = remember(folders) { buildFastScrollSections(folders.map { it.name }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(FOLDER_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            // … unchanged items(…) …
        }
    }
```

`FavouritesTab.FavouritesList`:

```kotlin
    val listState = rememberLazyListState()
    val sections = remember(songs) { buildFastScrollSections(songs.map { it.title }) }
    FastScrollContainer(listState = listState, sections = sections, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(FAVOURITES_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            // … unchanged items(…) …
        }
    }
```

`PlaylistsTab`: add `internal const val PLAYLIST_LIST_TEST_TAG = "playlist_list"` next to `FEATURE_CARD_ROW_TEST_TAG`. Then in `PlaylistsList`:

```kotlin
    val listState = rememberLazyListState()
    // Spec §10: the rail covers the user rows only; the feature-card row is item 0, so jumps skip it.
    val sections = remember(userPlaylists) { buildFastScrollSections(userPlaylists.map { it.name }) }
    FastScrollContainer(listState = listState, sections = sections, itemOffset = 1, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(PLAYLIST_LIST_TEST_TAG),
            contentPadding = PaddingValues(bottom = homeListBottomPadding()),
        ) {
            // … unchanged feature-cards item, empty hint and user rows …
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest`. Expected: PASS, both modules in full, the existing tab tests included.
  - Run each named mutation, quote the RED line, and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :feature:library:check :feature:playlists:check :app:testDebugUnitTest spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - `:app`'s Home tests (`HomeScreenRestorationTest` and the others) stay green and unmodified.
  - Report. Do not commit.

---

### Task 6: Docs, the phase gate and reviews (controller)

**Files:** the spec, `.claude/rules/ui-style-guide.md`, `CLAUDE.md`, `.claude/skills/setup-modularization/SKILL.md` and the roadmap.

- [ ] **Step 1: Amend the spec.** Mark each amendment "(amended 2026-10-17, Phase 5 ruling N)".
  - **§5.4:**
    - Add the `fastScrollLetterSlot` 16 dp row.
    - Replace "so it doesn't overlap the rows' ⋮ targets" with ruling 9: the rail wins the 4 dp the ⋮ box reaches into the rail strip.
  - **§6:**
    - `FastScrollSection` gains `populated`.
    - Add the `FastScrollContainer` signature.
    - The index letter comes from `:core:common`'s `indexLetter`.
  - **§9:** add a §9.0 "Library order" covering:
    - ruling 1 (section, then root primary collation, then tie-break);
    - that the repositories sort off the main thread, with the DAOs' NOCASE as a pre-order;
    - the per-list tie-breaks (ruling 4).

    In §9.4, replace the "Like NOCASE… Phase 5 Unicode-collation choice applies here too" sentence with a pointer to §9.0.
  - **§10:**
    - Keys go through `indexLetter` (leading whitespace skipped).
    - The list order is §9.0's library order, `#` first.
    - Add the vertical extent (ruling 8) and the label slot rule at large font scales (ruling 10).
- [ ] **Step 2: Update the other docs.**
  - **`ui-style-guide.md`:** add a "Documented exceptions" note under §5 for the EQ columns (28.7 dp) and the A–Z rail (30 dp), as spec §5.4 asks: accepted by the user on 2026-10-06, each with semantic alternatives. In §6 "Fast-Scroll A–Z Index", point to spec §10 and §9.0.
  - **`CLAUDE.md`:**
    - The `:core:common` structure line: "the library order (`LibraryOrder.kt`: `indexLetter`, `sortedByLibraryName`), shared by `:core:data` and `:core:designsystem`; dispatcher qualifiers are still per module".
    - The status banner gains a **Phase 5** bullet: the rail on the five tabs, `FastScrollContainer`, and the library order.
    - Delete the Phase 4a sentence "SQLite NOCASE folds ASCII only, so accented initials still sort after Z. Choosing a Unicode-aware order is a Phase 5 carry-over (the A–Z rail needs it)."
    - In "What remains (post-1.0 backlog)" and in "Next Steps" item 2, the `:core:common` placeholder note no longer applies: update it.
    - The "Post-1.0 cuts" A–Z bullet stays until the Phase 7 docs pass, which the banner already says.
  - **`setup-modularization/SKILL.md` Dependency Rules:** add `:core:designsystem → :core:common` (ruling 2).
  - **Roadmap:** Phase 5 row: plan file link and status.
- [ ] **Step 3: Grep the instruction files for stale names.** Run `grep -rn "Placeholder.kt\|core:common.*EMPTY\|folderOrder\|CASE_INSENSITIVE_ORDER" CLAUDE.md .claude docs/superpowers/specs`. Fix every hit that describes the old state.
- [ ] **Step 4: Run the phase gate.**
  - Run: `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug :core:common:compileDebugAndroidTestKotlin --continue` (timeout 600000 ms). Expected: `BUILD SUCCESSFUL`.
  - Quote the result line and the unit-test total.
- [ ] **Step 5: Run the reviews.** Each on `model: opus`, given the phase diff from Phase 5's BASE as a review-package file:
  - **`compose-performance-auditor`** (spec §17 exit criterion). Its focus:
    - `FastScrollRail`'s recomposition during a drag; it should not recompose per move, because the selection writes no state.
    - `remember(items)` section building on the main thread.
    - `BoxWithConstraints` cost.
    - `FastScrollContainer`'s `derivedStateOf`.
  - **`code-reviewer`**, against this plan and the spec. A breach of a Global Constraint is at least Important.
  - The phase is accepted only with no Critical or High findings from either review, and with the gate green.

---

### Task 7: Device check on the SM-S901B (controller)

These steps follow the device safety rules: never uninstall or clear data, never change settings, leave silent mode, and send no media keys.

- [ ] **Step 1: Precondition.**
  - Run `adb devices`: `R3CTA03TZTY  device` must be listed. If another device is attached, stop and ask the user.
  - Install over the existing app with `./gradlew :app:installDebug`. Never uninstall.
- [ ] **Step 2: Run the connected parity test.**
  - Run `./gradlew :core:common:connectedDebugAndroidTest`. This installs only `:core:common`'s own test APK, so it touches no app data.
  - Expected: 2/2 `LibraryOrderDeviceTest` passed. Quote the result line.
  - A failure means ICU and the JDK disagree on the rule's cases. Stop and rule on it before acceptance.
- [ ] **Step 3: Check that taps will land in our app.**
  - Launch the app (`adb shell am start -n com.rolla.musicplayer/.MainActivity`; use the launcher activity the manifest names). Open Home > Tracks.
  - Read-only check: `adb shell dumpsys window | grep mCurrentFocus` names `com.rolla.musicplayer`. Every tap and swipe below lands on our app's own window. Re-check focus before each injected input if anything else appeared.
- [ ] **Step 4: Check the rail on each tab.**
  - Take a screenshot (`adb exec-out screencap -p`). Read the rail's coordinates from it, at the 1.17 display scale.
  - Tap "M" on the Tracks rail. In a fresh screenshot, the first visible row's title starts with M, or with the next populated letter.
  - Run `adb shell uiautomator dump` (read-only) and check:
    - one node has content-desc `Fast scroll, letter index`;
    - no separate letter nodes exist.
  - Swipe the pager to Favourites, Playlists, Artists and Folders. On each, the rail shows when the list scrolls and is absent when it fits.
- [ ] **Step 5: Check jank during a drag.**
  - Run `adb shell dumpsys gfxinfo com.rolla.musicplayer reset`.
  - Drag the Tracks rail top to bottom over 2 s: `adb shell input swipe <x> <yTop> <x> <yBottom> 2000`, with x, yTop and yBottom from the screenshot.
  - Run `adb shell dumpsys gfxinfo com.rolla.musicplayer`, and record the janky % and the p90 and p99 frame times.
  - Compare with the Phase 4a ledger figure: 1.04 % janky, p90 15 ms, p99 18 ms. A clear regression is a High finding for Task 6's reviewers.
- [ ] **Step 6: Check accented initials.**
  - Look in the uiautomator dump of Artists and Tracks for any title starting with an accented letter, and check that it sits inside its base letter's run.
  - If the library has none, record "not exercised on the device; covered by Step 2 and the JVM suites".
- [ ] **Step 7: The user's checks.** Ask the user to feel the drag's haptic ticks, and optionally to try "Jump to" with TalkBack. Record what they report. These are not gating unless they report a defect.
- [ ] **Step 8: Report.** Report the results, then say "ready to commit" with a prepared message. The user triggers the commit.

## Self-review

**Spec coverage:**

| Spec requirement | Where it is covered |
|---|---|
| §10 sections, keys, labels and the empty-letter targets | Task 3 |
| §10 fitting | Task 3 (step), Task 4 (slot and short-rail drag) |
| §10 interaction and haptic | Task 4 |
| §10 accessibility (one node, actions, traversal) | Task 4 |
| §10 where it appears and "hidden when it fits" | Tasks 4 and 5 |
| §10 dividers stop at the rail | Already landed: `listDividerEnd` since Phase 2; rows are unchanged |
| §5.4 touch exception | Task 4 width, Task 6 style-guide record |
| §6 kit signature | Task 4 |
| §15 `FastScrollIndex` tests | Task 3 |
| §17 exit: index tests | Task 3 |
| §17 exit: tap and drag | Tasks 4 and 5, device in Task 7 |
| §17 exit: TalkBack actions | Tasks 4, 5 and 7 |
| §17 exit: no jank | Task 6 auditor, Task 7 gfxinfo |
| Phase 4a/4b carry-over: Unicode collation for the rail lists and `FolderRepositoryImpl` | Tasks 1 and 2 |
| Phase 2 carry-over M1: ⋮ versus rail hit priority | Ruling 9, Task 5 tap test |
| Phase 4b T6: scanning wins over a non-empty list | Task 5 |

**Placeholder scan:**
- No TBD or "similar to Task N". Every code step carries its code.
- The `// … unchanged …` markers in Task 5 stand for existing bodies that the step tells the implementer to keep verbatim, not for code to write.

**Type consistency:** these names match between producer and consumer tasks:
- `indexLetter`, `NON_LETTER_SECTION`, `sortedByLibraryName(tieBreak, name)` and `libraryNameComparator` (Tasks 1→2, 1→3);
- `FastScrollSection(label, firstIndex, populated)`, `FastScrollLabels`, `buildFastScrollSections`, `fastScrollLabelStep(slots, labelCount)` and `fastScrollSectionAt` (Tasks 3→4→5);
- `FastScrollContainer(listState, sections, modifier, itemOffset, railBottomPadding, content)` and `FAST_SCROLL_RAIL_TEST_TAG` (Tasks 4→5);
- `@CoreDataDefaultDispatcher` (Task 2 only).

**Companion checks (superpowers-extras, writing-plans):**
- **(1) Dependent phases:** this plan was written after Phase 4b was accepted, from the landed code. It consumes 4a/4b's tabs, repositories and tokens, and nothing it produces is consumed by a plan that does not exist yet.
- **(2a) Value tables:** checked entry by entry against the spec.
  - Tokens: `fastScrollTrack`, `fastScrollIndex`, `fastScrollLetter` (11 sp), `fastScrollWidth` 22, `fastScrollEnd` 8 and `CircleShape` are used as specified.
  - The labels: "#" plus A–Z (27).
  - The copy: "Fast scroll, letter index" and "Jump to X".
  - The 16 dp slot.
  - There is one deviation, ruling 10 (the slot is at least the line height).
- **(2b) Consumers:** the Task 2 table, re-grepped in Task 2 Step 5.
- **(2c) Test level:** no spec-mandated test moves to a cheaper level. Robolectric drives the real `LazyListState`, the real pager and real semantics. The ICU behaviour that Robolectric would not show gets a device test (Task 1 Step 6).
- **(2d) Every test can fail:** each test names its mutation.
  - The one change no test can observe is `flowOn(sortDispatcher)`: removing it changes no output. It is covered by review and the Task 7 gfxinfo check, and is stated here as untested by design.
  - The "scanning wins" tests pass before the change, so Task 5 Step 2 runs their mutations first.
- **(3) Framework-validated output:** the jump goes through the real `LazyListState.scrollToItem` (Tasks 4 and 5). The accessibility actions go through the real semantics tree (`SemanticsActions.CustomActions`), not a mocked callback.
- **(4) Mutations:** every one is valid usage. Task 4 Step 4 and the Global Constraints allow a substitute with evidence, and each test carries a control assertion.
- **(5) Device:**
  - Input lands on our own window, with a read-only focus check first (Task 7 Step 3).
  - No process kills, no media keys.
  - Waits are screenshots or dump content, not field values.
  - No Task 0 probe is needed (ruling 14).

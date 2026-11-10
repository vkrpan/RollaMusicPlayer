# Custom tags and subtags — design

**Date:** 2026-11-09 · **Branch:** `feat/oneui-redesign` (at `e129f3e`) · **Status:** draft for the user's review
**Vault:** [[Custom tags]] (progress, answers, open items). Reverses [[Custom tag system is cut]] once approved.
Builds on [[Tag editor]] and [[Search]]; tracked in [[Backlog]].

## 1. Intent

The user tags music by their own vocabulary, beyond genre: a **tag** such as "Techno" and **subtags** such as "Hard" or
"Industrial". They assign them in the tag editor, see them on Now Playing and in the track lists, and find tracks with a
filter screen that combines the usual fields with any number of tags and subtags. Tags are stored in the audio file
itself, so they survive a reinstall and can move to another device or program. The ID-to-name list travels as its own
JSON file.

## 2. Vocabulary

- **Tag** and **subtag**: two separate, flat lists of user-defined names. A subtag is **not** a child of a tag; a track
  may carry any subtag with or without any tag. Each entry has a numeric ID and a name.
- **The tag list**: both lists together (the user calls them "the enums").
- **`ROLLA_TAGS`**: the field inside an audio file that holds a track's tag and subtag IDs.
- Copy rules apply as everywhere: on screen "Tracks", "Tags", "Subtags"; in code `Song`, never `Track`
  (`.claude/rules/model-vocabulary.md`, [[Song in code, Tracks on screen]], [[UI copy and spelling]]). New model names (`TagDefinition` or similar) get a row there first.

## 3. Goals

1. Create, rename and delete tags and subtags.
2. Assign any number of tags and subtags to one track (single editor) or add them to many tracks (batch editor).
3. Write a track's assignment into the file's `ROLLA_TAGS` field and read it back from files during the library scan.
4. Export the tag list to a JSON file and import one (import replaces the current list).
5. Find tracks by title, artist, album, genre, year, tags and subtags; every active filter must match.
6. Show a track's tags under the title on Now Playing and as small chips in the track lists.
7. Play a search result with the whole result as the queue.

## 4. Non-goals and anti-goals

- **Non-goals:** a hierarchy between tags and subtags; removing tags from many tracks at once (the batch editor only
  adds); merging an imported list with the current one; OR-matching in search; tags on formats other than MP3, FLAC and
  M4A; changing Home's existing quick search; any cloud or network sync.
- **Anti-goals (a failure even if it "works"):**
  - writing to the comment field or any other standard field, or losing an existing tag of any kind on save;
  - a file write without Android's write consent;
  - a silently wrong tag: an ID that means one name today and another tomorrow on the same phone;
  - a library scan that becomes noticeably slower on every launch (only new or changed files may be read);
  - any network permission or dependency ([[Offline and privacy first]]).

## 5. Decided by the user (2026-11-09)

### 5.1 Storage in the file

- Field name `ROLLA_TAGS`, a dedicated field, never the comment:
  - **MP3:** an ID3v2 user text frame (`TXXX`) with description `ROLLA_TAGS`;
  - **FLAC:** a Vorbis comment `ROLLA_TAGS`;
  - **M4A:** a freeform (`----`) atom with mean `com.apple.iTunes` and name `ROLLA_TAGS` (Claude's call: common taggers
    such as Mp3tag and foobar2000 show freeform fields under that mean as ordinary named fields, which serves the
    "readable elsewhere" aim; the round-trip is proven in §9).
- Value: JSON with comma-separated ID strings, exactly the user's format:
  `{"tags":"2,3","subTags":"1,3,4"}`. An empty list is `""`. A track with no tags and no subtags has no `ROLLA_TAGS`
  field (the writer removes it).
- Files hold IDs only; names live in the tag list.

### 5.2 The tag list file (export and import)

- Export writes the whole tag list as one JSON file to a location the user picks (Storage Access Framework,
  `ACTION_CREATE_DOCUMENT`, suggested name `rolla-tags.json`). Shape:

  ```json
  {
    "format": "rolla-tags",
    "version": 1,
    "tags": [{ "id": 2, "name": "Techno" }],
    "subTags": [{ "id": 1, "name": "Hard" }]
  }
  ```

- Import reads such a file (`ACTION_OPEN_DOCUMENT`) and **replaces** the whole current list, after a confirmation that
  says so. A malformed file changes nothing and says why.
- **Clash warning (user, 2026-11-09):** when an ID in the imported file names something different from the same ID here,
  and tracks carry it, the confirmation lists each clash with its track count ("2: Techno → Jazz, on 14 tracks") and
  offers Import or Cancel. Import then goes ahead as asked (those tracks now show the imported name). An import onto an
  empty list, or one with no clashing IDs in use, shows only the plain replace confirmation.

### 5.3 Matching

- Assigning: every ticked item is added.
- Searching: AND across everything. One tag and two subtags ticked finds tracks carrying all three; text filters
  combine with them the same way.

### 5.4 Deleting

- Deleting a tag or subtag removes it from the list **and** removes its ID from every file that carries it. The
  confirmation names the count ("Remove 'Hard' from 37 tracks?"). Android's write consent is asked once for all of those
  files (`MediaStore.createWriteRequest` takes a list on 30+; the existing per-SDK consent path otherwise).

### 5.5 Screens (picked from mockups)

- **Tag editor (single track):** below the usual fields, a card with two rows, "Tags" and "Subtags", each showing the
  chosen ones as chips. Tapping either opens **one picker sheet** with a Tags | Subtags tab switch (opening on the tab
  tapped), one search box over a checklist, and "+ New tag" / "+ New subtag" at the top of the list. "Done" closes it;
  the editor's Save writes the file.
- **Batch tag editor:** the same two rows; ticked items are **added** to every selected track on Save. Nothing is
  removed.
- **Find tracks (mockup B):** the Tracks tab's header gets a Search button. It opens one pushed screen, "Find tracks":
  the active filters as a strip of removable chips (✕ drops one) above a live results list; "Edit filters" unfolds the
  fields (Title, Artist, Album, Genre, Year, then the Tags and Subtags rows, which open the same picker). The results
  panel has the usual `SortHeader` with Shuffle and Play over the whole result. Tapping a track plays it with the whole
  result, in display order, as the queue.
- **Now Playing:** the track's tags and subtags appear under the title.
- **Track lists:** small chips on each track row that has tags.

## 6. Claude's calls (the user did not object; check while reading)

1. FLAC and M4A get `ROLLA_TAGS` too, not only MP3, since the editor already handles those formats. For other formats
   the Tags and Subtags rows are disabled with a one-line note.
2. **IDs are never reused** on one phone: a counter keeps the highest ID ever issued per list. After an import the
   counter becomes the larger of its old value and the imported maximum.
3. **The scan reads `ROLLA_TAGS`** from new or changed files (the existing `dateModified` diff in
   `LibraryIndexer.sync`) and unconditionally in `syncSongs(ids)` after a save. The first scan after this ships reads
   every MP3, FLAC and M4A once; later scans read only what changed.
4. **Unknown IDs** (in a file, not in the list — say, tagged on another phone before its list was imported) are kept in
   the file on every write and simply not shown. Deleting from the list only cleans IDs that the list knew.
5. Text filters match any part of the text, ignoring case; Year matches exactly. Results are in library order (the
   Tracks tab's A–Z, `sortedByLibraryName`).
6. Home's quick search is unchanged.
7. **Manage tags** screen, reached from Home's ⋮ menu and from a "Manage tags" link at the bottom of the picker: the
   same tab switch and search; tap to rename (a `OneUiDialog` with `OneUiDialogField`), a destructive Delete with the
   count, and Export / Import in its ⋮. Renaming writes no files.
8. Names are trimmed, 1–40 characters, unique within their list ignoring case. A tag and a subtag may share a name.
9. **Chips in lists:** tags first, then subtags, one line, as many as fit plus "+N". Subtags take a visually distinct
   chip (tonal vs outline — the design-system owner picks the tokens). Rows without tags keep today's height.
10. **Now Playing:** the same chip line under the title, centred, wrapping to at most two lines.
11. **Genre is not in the library today** (`Song` and `SongEntity` have no genre; the scan never reads it). The Find
    tracks Genre filter is fed by the same file pass that reads `ROLLA_TAGS` (MP3, FLAC, M4A); for other formats the
    MediaStore `GENRE` column is used on API 30+, and below that those tracks have no genre. Genre becomes a column in
    the Room 5→6 migration.
12. **The counter also follows the files:** the scan raises each list's counter to the highest ID found in any file, so
    after a reinstall a new tag never takes a number that files already carry.
13. A write that fails for some files (consent refused, file gone, IO error) reports "Updated X of N tracks", like the
    batch editor. The list change (a delete) still happens; files that were not updated keep the old ID, which no
    longer means anything because IDs are never reused.

## 7. Constraints from the project

- Writes go through the existing cache-copy path and consent flow ([[Tag writes go through a cache copy]]), and `TagSaveFinalizer` runs after every write (re-index, artwork invalidation, now-playing refresh).
- jaudiotagger fork `com.github.Adonai:jaudiotagger:2.3.15` is the only tag library. ID3v1-only MP3s need the ID3v2 tag
  built from the ID3v1 tag before adding a frame ([[jaudiotagger and ID3v1-only MP3s]]); every tag-IO
  contract test has an ID3v1-only MP3 fixture next to the untagged one.
- Module direction `:feature → :core → :core:model`; features never depend on each other
  ([[Module dependency direction]]; the modules involved: [[feature-tageditor]], [[feature-search]], [[feature-library]],
  [[feature-player]], [[core-data]], [[core-database]], [[core-ui]], [[core-model]]). Today only
  `:feature:tageditor` opens audio files; the scan in `:core:data` uses MediaStore only. Reading `ROLLA_TAGS` during the
  scan, the picker shared by the editor and Find tracks, and the chips in `SongListItem` (`:core:ui`) therefore need the
  tag IO and the tag list below the feature layer.
- Room is at version 5 with exported schemas; a schema change is a migration to 6 with a migration test.
- UI is built from the One UI kit and tokens only (`.claude/rules/ui-style-guide.md`,
  [[Build UI from tokens and the kit]]); all copy uses British spelling.
- Device checks run on the Motorola only, under [[Device safety]] and [[Device tests run on the Motorola]]. Saving tags on the device is forbidden
  without the user's explicit go-ahead; JVM and Robolectric tests on fixture files carry the IO proof.

## 8. Left to the builder

Module placement (a new core module for tag IO and the tag list versus extending existing ones), the Room schema (tables
for both lists and the song cross-references, or a column), how list rows get their tags (on `Song` or a separate map),
the Find tracks route and ViewModel shape, the exact chip tokens, and test structure — within the constraints above.

## 9. Risks to settle first

- **Format round-trip:** prove with real fixtures that jaudiotagger writes and reads `ROLLA_TAGS` on MP3 (v2.3/v2.4 and
  ID3v1-only), FLAC and M4A, and that a write leaves every other field and the artwork untouched. M4A freeform atoms are
  the least certain.
- **Scan cost:** measure the first full read of the Motorola's library and a normal re-scan; a cold start must not wait
  for it (it runs in the background sync).
- **Scroll cost:** the Tracks list is already 36 % slower than v1.0 on P90 frame time ([[Backlog]]); chips must not add
  measurable cost to rows without tags.

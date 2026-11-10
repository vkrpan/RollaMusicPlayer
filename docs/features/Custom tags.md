---
type: feature
status: active
date: 2026-11-09
source: "user, 2026-11-09: custom tags and subtags, assigned in the tag editor as a searchable checklist, saved into the file's metadata, used to search and filter"
tags: [tag-editor, search, feature, custom-tags]
related: ["[[Features]]", "[[Custom tag system is cut]]", "[[Tag editor]]", "[[Search]]", "[[Tag writes go through a cache copy]]", "[[Backlog]]"]
---

# Custom tags

**Status:** phase 1 (foundation) complete and device-verified, waiting for the user's commit (2026-11-10); no screen
changes yet. Rulings: [[2026-11-09-custom-tags-phase1-foundation rulings]]. Phases 2–4 not started
([[2026-11-09-custom-tags-roadmap]]); phase 2 waits for the user's M4A decision.

## What the user asked for (2026-11-09)

- Create custom tags and subtags, grouped (tag groups), and save them.
- Assign them to a track in the tag editor: a list with a search bar at the top, as a checklist.
- Store the assigned tags **in the audio file's metadata** (the user said MP3), so another system can read them with the
  same mechanism.
- Search and filter tracks by tag in a track search screen.
- Reverses [[Custom tag system is cut]] (cut at v1.0).

## User's answers (2026-11-09)

- **Example:** a tag "Techno"; subtags "Hard", "Industrial".
- **Subtags are free:** not children of a tag; a track can carry a subtag without any tag.
- **Assigning:** pick a track in the library, open the tag editor, check tags and subtags.
- **Finding:** a "Search" button on the Tracks screen opens a search window with fields for name, artist, album, genre
  and year, plus checkbox lists of every tag and every subtag (multiple choice).
- **Playing a result:** tapping a result plays it with the **whole result list as the queue**.
- **Multiple choice is AND:** assigning adds every checked item; searching with one tag and two subtags finds only
  tracks that carry all three.
- **Storage format (user's request):** the IDs of the tags and subtags, written into a large text field of the file
  (the user suggested something like "Description") as JSON: `{ "tags": "2,3", "subTags": "1,3,4" }`.
- **Deleting** a tag or subtag from the collection removes its ID from every track that carries it.

- **Decided (2026-11-09):** files hold IDs only, in a dedicated `ROLLA_TAGS` field (not the comment). The tag and
  subtag lists (the "enums", ID → name) travel as their own JSON file, exported and imported, so any other system can
  read them.

- **Import replaces** the phone's whole tag and subtag list (2026-11-09). When an imported ID names something different
  and tracks carry it, the confirmation lists each clash ("2: Techno → Jazz, on 14 tracks") with Import or Cancel.
- **Proposed by Claude, not yet objected to:** `ROLLA_TAGS` on FLAC and M4A too; export/import through a file the
  user picks; IDs never reused after a delete; the library scan reads `ROLLA_TAGS` from new or changed files (so tags
  come back after a reinstall or on another phone once the list is imported).

- **Editor layout (picked from mockups, 2026-11-09):** the editor shows two rows, "Tags" and "Subtags", each with the
  chosen ones as chips (mockup A). Tapping either opens one sheet with a Tags | Subtags tab switch, one search box, a
  checklist and "+ New tag" (mockup B's picker), opening on the tab that was tapped (Claude's reading).

- **Track search layout (picked from mockups, 2026-11-09): option B.** The Tracks tab's header gets a Search button
  that opens one "Find tracks" screen. The active filters fold into a strip of removable chips (✕ drops one) above a live
  results list; "Edit filters" unfolds the fields (title, artist, album, genre, year, Tags, Subtags). Shuffle and Play
  cover the whole result, and tapping a track queues the whole result.

- **Batch editing adds only (2026-11-09):** ticking a tag in the multi-track editor adds it to every selected track.
- **Claude's calls the user did not object to (2026-11-09):** text filters match part of the text ignoring case, year
  exactly; results in library order; Home's quick search unchanged; a "Manage tags" screen (Home ⋮ and a link in the
  picker) with rename (no file writes), delete (confirm with the track count, then consent and file updates), and
  Export/Import in its ⋮ (import warns that it replaces).

- **Tags shown outside the editor (2026-11-09):** under the title on Now Playing, and as small chips in the track
  lists.
- **Formats:** MP3, FLAC and M4A only; for other formats the Tags and Subtags rows are disabled with a note.

## Design concerns raised with the user (2026-11-09)

- **IDs need their names to travel too.** A file holding `"tags": "2,3"` means nothing on another device unless the
  ID-to-name list travels with it, and another install may number its own tags differently.
- **Comment field vs a dedicated field.** ID3 has no "Description" field; the large standard one is the comment (COMM),
  which other apps and DJ tools also write. Proposed: a dedicated custom field (`TXXX` in MP3, a Vorbis comment in
  FLAC, a freeform atom in M4A), each unlimited in length and readable in Mp3tag or foobar2000.
- **Deleting rewrites files:** every affected file needs Android's write consent (one batched system prompt on
  Android 11+).

## Facts found before the design (2026-11-09)

- Only `:feature:tageditor` opens audio files (jaudiotagger: `TagReader`, `TagWriter`, `TagAccess`). The library scan
  in `:core:data` reads MediaStore only, and MediaStore does not index custom fields. Tags read back from files (for
  example, files tagged on another device) therefore need a new file read during or after the scan.
- Writes go through the cache-copy path and need write consent per file ([[Tag writes go through a cache copy]]).
- Tag editing covers MP3, FLAC and M4A only.

## Open questions

- **M4A writing (before phase 2, the user's call):** the app's jaudiotagger fork cannot write the `ROLLA_TAGS` freeform
  atom ([[jaudiotagger cannot write unknown M4A freeform atoms]]). Phase 1 reads it from M4A and refuses to write it
  (SDD Ruling 5). Options: our own M4A atom writer, a fork upgrade or patch, or MP3 and FLAC only.

## Progress

| Date | Step |
|---|---|
| 2026-11-09 | Asked; vault read; brainstorming started |
| 2026-11-09 | Editor and Find tracks layouts picked from browser mockups (`.superpowers/brainstorm/`, git-ignored) |
| 2026-11-09 | Spec drafted: [[2026-11-09-custom-tags-design]]; builder check done (genre, M4A field and counter settled as Claude's calls; import clash asked) |
| 2026-11-09 | Import clash: warn with each clash and its track count, then Import or Cancel (user). Spec approved. Decision [[Custom tags live in the file's ROLLA_TAGS field]] |
| 2026-11-09 | Roadmap [[2026-11-09-custom-tags-roadmap]] (4 phases) and phase 1 plan [[2026-11-09-custom-tags-phase1-foundation]] written; waiting for the user's review |
| 2026-11-09 | User approved the plan, subagent-driven. Task 1 (`:core:tagio` module, helpers and fixtures moved) and Task 2 (model names, `ROLLA_TAGS` codec) done and reviewed |
| 2026-11-09 | Task 3: MP3 and FLAC read/write work; M4A write blocked by the jaudiotagger fork; M4A kept read-only (Ruling 5) |
| 2026-11-10 | Task 4: Room v6 (lists, per-kind counters, song links, `genre`, `file_tags_read`); instrumented tests compile, device run owed (Motorola not connected) |
| 2026-11-10 | Task 5: the scan's `FileTagPass` reads tags and genre from files in the background (never blocks the caller, never crashes; rulings 7–8) |
| 2026-11-10 | Task 6: `CustomTagRepository` (create, rename, observe lists and per-song IDs) |
| 2026-11-10 | Full JVM gate green (`spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`). Final review: ready with fixes — ID ceiling (`MAX_CUSTOM_TAG_ID` = 999 999 999, Ruling 10), jaudiotagger logging, vault catch-up; first-pass write cost to be measured on the device (Ruling 11) |
| 2026-11-10 | Final fix wave done (ID ceiling, jaudiotagger logging, review fold-ins; all unit tests green). Paused at the user's request, then resumed |
| 2026-11-10 | Fix wave re-reviewed: all code findings addressed; vault residuals fixed; R8 logger keep rule and the last-ID race recorded below. Run ledger: `.superpowers/sdd/2026-11-09-custom-tags-phase1-foundation/progress.md` (git-ignored, local) |
| 2026-11-10 | **Device (Motorola edge 70, Android 16):** `:core:database:connectedDebugAndroidTest` 117/117 (incl. the 5→6 migration and every custom tag DAO test). Benchmark build installed over the user's app: the v6 upgrade ran on the real library; first pass `read 91 files (0 failed) in 3003 ms` (≈33 ms per file; library 97 MP3 + 1 WAV); no crash, no jaudiotagger log noise, CPU idle afterwards; the user found Home and scrolling smooth. Batching the pass's writes stays a backlog item for large libraries |
| 2026-11-10 | Every planned mutation of the device tests run on the Motorola and caught (one equivalent under `@Transaction`, covered by its combined form); final run 117/117. Phase 1 complete; ready for the user's commit |

## Known limits and notes for phase 2

- **M4A writes** wait for the user's decision (open question above).
- **Apply-after-reset race:** if the tag editor saves a file while the background pass is reading that same file, the
  pass can later mark it read with the pre-save values, so the just-written tags are not read back until the file
  changes again. Harmless in phase 1 (nothing writes). Phase 2 fix: after a successful write, the editor applies the IDs
  it wrote itself (`applyFileTags`), or the pass applies only if the row's generation has not changed.
- **Save-time merge:** phase 2 must merge unknown IDs from the file read at save time, not from Room (may be stale).
- **The re-read after a save is asynchronous** (Ruling 7): screens observe a song's links; they never assume the links
  are fresh when `syncSongs` returns.
- **First-pass cost:** one Room transaction per file, so every `songs` observer re-queries per file during the one-time
  first pass; batch the writes if the device probe shows a cost ([[Backlog]]).
- **FLAC artwork kept on a `ROLLA_TAGS` write** is not yet tested: add it before phase 2 ships writes.
- **Last-ID race (phase 3, when the UI calls `create`):** `create` checks the ceiling, then `insertNew` runs in its own
  transaction, so a concurrent create or a `FileTagPass` raising the counter in between can make `insertNew` throw
  `IllegalStateException` out of `create`. Needs the counter at 999 999 998 (only a hand-edited file). Fix then:
  `insertNew` reports "full" inside its transaction instead of throwing.
- **Release logging:** R8 may remove the write-only static logger field in `RollaTagsFileIo`, leaving the
  `org.jaudiotagger` logger held weakly, so its WARNING level could be lost and per-file INFO lines return to logcat.
  Fix: a `-keepclassmembers` rule (in `:core:tagio`'s consumer rules) ([[Backlog]]).

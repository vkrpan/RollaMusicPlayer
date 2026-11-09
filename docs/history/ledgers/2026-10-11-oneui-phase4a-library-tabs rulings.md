---
type: ledger
status: history
date: 2026-10-11
plan: "docs/superpowers/plans/2026-10-11-oneui-phase4a-library-tabs.md"
rulings: 8
deferred_minors: 26
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 4a — library tabs

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-11-oneui-phase4a-library-tabs/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-11-oneui-phase4a-library-tabs.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling: no agent commits (user rule) — review packages diff working-tree snapshot trees (snapshot.sh / review-pkg.sh, copied from Phase 3) — cost if wrong: none, same as Phase 3.
- | T1 self | DAO tests instrumented: run + mutation proofs deferred to gate (phone) | Ruling below |
- Ruling: Task 1's instrumented DAO tests compile in-task and run (with their mutation proofs) at the Task 9 gate on the phone — the phone is not guaranteed connected mid-phase — cost if wrong: a DAO SQL defect surfaces at the gate instead of Task 1 (one fix round later).
- Ruling: Task 2's "every other AlbumArtworkCache implementation must implement peek" is moot — all consumers use relaxed mockks — cost if wrong: none (compile catches any missed fake).
- Ruling: adopt both fixture fixes (spec + plan policy "fixtures must separate the quantities") — plan text was the defect — cost if wrong: none (fixture-only).
- Task 1: minor (deferred): ArtistDaoTest observeArtists Queen albumCount==songCount==2 (COUNT(DISTINCT) mutant survives; observeArtist tests cover projection)
- Task 1: minor (deferred): tie-break keys albumId ASC / name ASC are equivalent mutants on this engine — gate notes must say untested
- Task 1: minor (deferred): ArtistSearchRow.toDomain KDoc stale (FNV id "safe as list key", "no artist-detail route") — fix in Task 7 or gate sweep
- Task 1: minor (deferred): ArtistDao class KDoc ragged reflow
- Task 2: minor (deferred): rename-failure branch untested (drop temp.delete() survives)
- Task 2: minor (deferred): temp.writeBytes throwing leaves partial .tmp (self-heals via overwrite/evict/clear)
- Task 2: minor (deferred): double-miss race pre-existing — re-check file.exists() inside lock would make KDoc "can't double-write" true
- Task 2: minor (deferred): interceptor test get(2L, any()) doesn't pin sourceUri
- Task 2: minor (deferred): AlbumArtworkInterceptor KDoc awkward wrap
- Ruling: ruling 11 means ONE shared copy — the 3 remaining private pixel-helper copies are in Task 3 scope (convert where behaviour-identical; a helper that differs, e.g. sdk-30 ripple draw, may stay private with a one-line reason) — the brief's grep missed differently-named copies — cost if wrong: small churn in 3 test files.
- Task 3: minor (deferred): one reported RED stack frame ran on an intermediate PixelCapture.kt layout (proof still valid)
- Task 3: minor (deferred): drawRoot KDoc stale ("OneUiTabRowTest precedent, shared by the kit pixel tests")
- Task 3: minor (deferred): QueueSheetTest.findComposeView duplicates private composeViewOrNull (insets, not pixels)
- Task 3: minor (deferred): :core:testing test-only guarantee rests on a build comment — consider a guard task (Phase 7 build-tooling)
- Task 4: minor (deferred): AlbumCardTest "placeholder always sits under the art" proves presence not draw order (rename or fake ImageLoader)
- Task 4: minor (deferred): AlbumCard has no intrinsic width (callers must bound it; KDoc says so)
- Task 4: minor (deferred): isNotBlank (card) vs isNotEmpty (thumbnail) inconsistency
- Task 4: minor (deferred): card ripple is a sharp rectangle — Phase 6 polish (custom indication shape)
- Task 4: minor (deferred): AlbumRowTest lacks click/row-height tests; divider insets & card subtitle maxLines unpinned
- Task 5: minor (deferred): shuffleAll empty path unpinned (only playAll)
- Task 5: minor (deferred): blank-name test comment says "   " (actually "" after trim); F9a swap mutant unrealistic — name wrong-id mutant
- Task 5: minor (deferred): no KDoc on addSongToPlaylist/createPlaylistAndAddSong; no error handling in playlist writes (inherited from Tracks)
- Task 6: minor (deferred): disabled-tap probe taps only Play; never decisive
- Task 6: minor (deferred): no characterization test for New playlist → onCreatePlaylistAndAddSong(name, songId) (String,String swap compiles silently)
- Task 6: minor (deferred): TracksTabTest new test uses backtick name vs camelCase file style
- Task 8: minor (deferred): Artists restoration case not independent evidence (same mutation as Playlists)
- Task 8: minor (deferred): MainActivity.kt:312 "wired below" stale; duplicated comment at 231/233
- Ruling: adopt reviewer's 15-item fix wave (code + tests) in ONE dispatch; controller does the docs items itself (CLAUDE.md banner "(ASCII case-folding…)", CLAUDE.md FNV "list-key-only" rows, spec §9.2 FNV sentence → display-only, lists key by name) — cost if wrong: small.
- Ruling: M2 (non-ASCII collation) owned by Phase 5 — rail section builder must be specified against the list's real order (COLLATE UNICODE w/ @SkipQueryVerification or Kotlin-side normalised sort) — cost if wrong: accented initials sort after Z until Phase 5.

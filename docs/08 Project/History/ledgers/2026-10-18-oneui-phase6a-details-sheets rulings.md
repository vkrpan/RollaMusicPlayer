---
type: ledger
status: history
date: 2026-10-18
plan: "docs/superpowers/plans/2026-10-18-oneui-phase6a-details-sheets.md"
rulings: 11
deferred_minors: 46
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 6a — details, sheets, dialogs

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-18-oneui-phase6a-details-sheets/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-18-oneui-phase6a-details-sheets.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling P1: in Task 7, SongOptionsHost lives in PlaylistDetailContent (the stateless half), as in FolderDetailContent/AlbumDetailContent; the content takes userPlaylists, onAddSongToPlaylist, onCreatePlaylistAndAddSong, onEditTagsClick and onRemoveSong, and the Route passes them — the plan's own tests host the content and open the sheet there — cost if wrong: none (same behaviour, one place).
- Ruling P2: new semantic token destructiveText (dark #FF6060 = 4.64:1 on #2D2D2F; light #BA1A1A = 5.28:1), like accentText; OneUiTextButton(destructive) and SheetActionRow(destructive) use it; error stays the spec's non-text colour (unchanged) — consumers only pass `destructive = true`, so Tasks 2/7 are unaffected; spec §5.1 amended in Task 9 — cost if wrong: one more colour token to calibrate in Phase 7.
- Task 1: minor (deferred): M1 ruling-2 properties unpinned (dialog title/text colours, menu container/shape/songTitle, field label/cursor/shape, sheet square bottom corners); statusBarsPadding covered via QueueSheetTest from Task 4.
- Task 1: minor (deferred): M2 unmessaged assertions OneUiSurfacesTest.kt:218/220.
- Task 1: minor (deferred): M3 test name textButtonsDrawAccentTextAndDestructiveError + menu "songTitle" comment overclaims.
- Task 1: minor (deferred): M4 SheetActionRow label maxLines=1 without ellipsis.
- Task 1: minor (deferred): M5 detailArtworkSize sits in the Now Playing token group.
- Task 1: minor (deferred): M6 KitPreviews.kt:127 Row(padding(16.dp)) in preview code.
- Ruling P4: the hero placeholder must be the large one (artworkPlaceholderLarge + a large glyph, like Now Playing at the same 168 dp) — carried into Task 5: ArtworkThumbnail gains `large: Boolean = false` (+ glyph size from tokens) forwarded to ArtworkPlaceholder, DetailArtworkHeader passes large = true, DetailArtworkHeaderTest's pixel colour moves to artworkPlaceholderLarge — a 168 dp square with a list-sized note in a different grey is a visible defect on every artless album — cost if wrong: a small :core:ui edit inside Task 5's diff.
- Task 2: minor (deferred): M2 test name artworkIsDetailArtworkSizeAndTheSubtitleIsOneLine does not test one line.
- Task 2: minor (deferred): M3 no test that a list change still holding the track keeps the sheet open.
- Task 2: minor (deferred): M4 no test for Remove's dismiss-then-remove order.
- Task 2: minor (deferred): M5 close effect captures onDismiss without rememberUpdatedState.
- Task 2: minor (deferred): M6 ArtworkThumbnail isNotBlank untested.
- Task 2: minor (deferred): M7 JVM CDS warning in feature test logs (environment).
- Ruling: fix I1 as the reviewer proposed (present-then-absent in one test) — the constraints' control rule binds over the plan's test text — cost if wrong: a few lines.
- Task 3: minor (deferred): M1 rename observeArtistAlbums_mapsRowsToDomainInDaoOrder (now misleading).
- Task 3: minor (deferred): M2 ArtistRepository.observeArtistAlbums KDoc should say library order (spec §9.0), ties by id.
- Task 3: minor (deferred): M3 album id tie-break untested.
- Task 3: minor (deferred): M4 pre-existing annotation-target warning at ArtistRepositoryImpl.kt:23 (Phase 5 deferred, project-wide).
- Task 3: minor (deferred): M5 createPlaylist then addSongs can leave an empty playlist if cancelled between (pre-existing; NonCancellable or a transactional DAO method later).
- Ruling P5: test hygiene for Tasks 4–8 (ruling-P5-test-hygiene.md: one setContent per test with state flips — Task 5 not-found test called it twice; every test fails under its trivial stub with absence tests showing presence first; a per-test stub/assertion/mutation table in each report) — 0016 recurred in Task 3; pre-empting saves fix rounds — cost if wrong: a few extra lines per test.
- Task 3: minor (deferred): M6 mutation logs quote messages only via JUnit XML (log shows the function line); keep failing TEST-*.xml next to logs in later tasks.
- Task 4: minor (deferred): M1 Home menu container conversion not pinned at the call site (test probes item style only).
- Task 4: minor (deferred): M2 ThemeModeDialog KDoc still says AlertDialog.
- Task 4: minor (deferred): M3 new test's click check duplicates overflowOpensSettings.
- Task 4: minor (deferred): M4 JVM CDS warning in logs (environment, pre-existing).
- Ruling P6: currentSongs is wired and pinned at every SongOptionsHost caller — Task 6 adds it to ArtistDetail (new) plus FolderDetailScreen and TracksTab, and adds a pinning test at AlbumDetail (M1), ArtistDetail, FolderDetail and Tracks (open the sheet on a track, flip the list to one without it, assert the sheet's "Edit tags" is gone, after showing it present); Task 7 does the same for PlaylistDetail and FavouritesTab — ruling 10's close-on-leave behaviour silently does nothing without the argument — cost if wrong: a few lines and one test per caller.
- Ruling P7: Task 6's circular-hero probe moves from the brief's 4 dp corner to 16 dp (4 dp is outside both a circle and the 26 dp rounded square: Task 2 proved it blind, mutation-T2-8-probe4dp.log) — cost if wrong: none.
- Task 5: minor (deferred): M2 P4 glyph size unpinned.
- Task 5: minor (deferred): M4 AlbumDetailViewModelTest `single {}` has no message under a no-op stub.
- Task 5: minor (deferred): M5 empty-album branch untested.
- Task 5: minor (deferred): M6 SongListItem.kt:52 KDoc names AlbumDetailScreen (say AlbumDetailContent).
- Task 5: minor (deferred): M7 hero subtitle maxLines=1 unpinned (Task 2 M2 overlap).
- Task 5: minor (deferred): M3 sed -i slip (remedied), M8 CDS log noise, M9 gradlew --stop (safe only because implementers are sequential).
- Ruling P8: accept the AlbumCard overlay (clickable with no indication + a matchParentSize overlay clipped to featureCard drawing LocalIndication) over the brief's `clip(featureCard).clickable` — the brief's line contradicts Phase 4a ruling 8 (clipping the column cuts the first glyph of the left-aligned subtitle; the implementer reproduced it in a pixel dump); the overlay meets plan ruling 9's goal (ripple stays inside the card shape) and both are pinned (mutations 19, 20) — cost if wrong: a one-line revert plus dropping the glyph test.
- Task 6: minor (deferred): M1 ArtistDetail header fixture ties Artist counts to list sizes.
- Task 6: minor (deferred): M2 empty-artist branch untested (with Task 5 M5).
- Task 6: minor (deferred): M3 FolderDetailScreenTest.kt:121 dummy songs = null argument.
- Task 6: minor (deferred): M4 AlbumCardTest duplicates setCard body.
- Task 6: minor (deferred): M5 HERO_ART_TAG literal duplicates internal constant.
- Task 6: minor (deferred): M6 CDS log noise; M7 AlbumCard.kt:126 preview 12.dp (pre-existing; featureCardGap).
- Task 7: minor (deferred): M1 smart playlist's absent bar ⋮ unpinned.
- Task 7: minor (deferred): M2 loading-hides-header and empty-disables-buttons untested.
- Task 7: minor (deferred): M3 @LooperMode(LEGACY) on rename test (justified; already in build-gotchas memory).
- Task 7: minor (deferred): M4 PlaylistDetailViewModelTest.kt:33 stale KDoc.
- Task 7: minor (deferred): M5 last-track test message phrased as a statement.
- Task 7: minor (deferred): M6 mutation logs do not record the applied edit.
- Task 7: minor (deferred): M7 SongListItem.moreContentDescription has no callers + stale KDoc; :feature:playlists declares unused material.icons.extended; CDS noise.
- Task 8: minor (deferred): M1 MainActivity.kt:213 stale LongMethod suppression comment.
- Task 8: minor (deferred): M2 onEditTagsClick body x4 (openTagEditor helper, 6b).
- Task 8: minor (deferred): M3 launchSingleTop on TagEditor push unpinned.
- Task 8: minor (deferred): M4 mutations preceded formatting-only rewraps (disclosed).
- Task 8: minor (deferred): M5 CDS noise; exact-60 dp relies on Robolectric's 0 nav-bar inset.
- Ruling P9: one final fix wave = final review items 1–7 + perf L1 (final-fix-wave-brief.md); no new mutations (comments, an identical-behaviour refactor, an ellipsis, contentType); the gate re-runs after — the review's fix-now list is cheap and in scope, and L1 is four lines — cost if wrong: one more gate run.
- Ruling P10 (user, 2026-10-21): device testing moves to the Motorola edge 70 (ZY22MBNVB4, Android 16, 3-button navigation, light theme); installing the app there is allowed. installBenchmark → fresh install 15:42. Created a test playlist "Claude test" (app data I created on a fresh install, not the user's) to exercise the user-playlist paths.
- Ruling P11: fold M1 and M2 into F1 now (one line + assertion + mutation; try/finally) — same bug class ResolvedEdgeToEdgeEffect already fixed for the activity — cost if wrong: a few lines. Queue half-expanded rows under the clear nav bar: M3 standard, product follow-up after the device check.

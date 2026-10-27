# One UI redesign: roadmap

- **Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md)
- **Measurements:** [`docs/design/oneui-measurements.md`](../../design/oneui-measurements.md)

The spec has 7 phases, and each one consumes the real APIs the previous one shipped. Writing all seven detailed plans now
would mean guessing at signatures that don't exist yet. So each phase gets its own plan, written **just in time**:
the orchestrator writes Phase N+1's plan after Phase N is accepted, reading the code as it actually landed.

| Phase | Plan file | Status | Depends on |
|---|---|---|---|
| 1 Tokens, icons, component kit | [`2026-10-06-oneui-phase1-tokens-kit.md`](2026-10-06-oneui-phase1-tokens-kit.md) | **Accepted 2026-10-07** (phase review: 0 Critical/High; Phase 2 carry-overs in the SDD ledger: row end-inset tokens, selection-highlight contrast, lineHeightStyle decision, tab ripple, Robolectric convention plugin) | — |
| 2 Home shell, tab row, panel, mini-player overlay, Tracks and Playlists tabs | [`2026-10-07-oneui-phase2-home-shell.md`](2026-10-07-oneui-phase2-home-shell.md) | **Accepted 2026-10-09** (gate green 2026-10-08: check + assembleDebug, 1283 unit tests; phase review: 0 Critical/High, M1 shuffle fixed). The on-device check passed, and the two device defects it found are fixed: the collage backdrop for missing art, and the horizontal swipe toggling a row while selecting. The user checked the shared-element animation and TalkBack by eye. The tab ripple is now pill-shaped (user decision); its render on an API 31+ device is still unchecked. Phase 3 carry-overs are in the SDD ledger: shared Play/Shuffle helper, Queue seek re-pin, cycleRepeatMode queuing, pill footprint from tokens, MiniPlayerRoute order | 1 |
| 3 Now Playing, queue, mini-player, Equaliser, Settings | [`2026-10-09-oneui-phase3-player-eq-settings.md`](2026-10-09-oneui-phase3-player-eq-settings.md) | Accepted 2026-10-11 (gates green, phase review clean, device check done, artwork transition confirmed by the user) | 1, 2 (`LocalMiniPlayerInset`, overlay) |
| 4a Favourites, Albums, Artists tabs | [`2026-10-11-oneui-phase4a-library-tabs.md`](2026-10-11-oneui-phase4a-library-tabs.md) | **Accepted 2026-10-12** (gates green; phase review 0 Critical/High after one fix wave; device check on the SM-S901B done, with 90/90 connected DAO tests and 12 device mutation proofs). Phase 4b/5 carry-overs are in the SDD ledger: Unicode-aware collation for the rail lists (NOCASE is ASCII-only), FolderDao instead of growing SongDao, extracting the row scaffold when FolderRow lands, and the stale Routes.kt FNV comment | 2, 3 (`PlaybackController` helpers) |
| 4b Folders: migration v4, scanner, FolderDetail | [`2026-10-12-oneui-phase4b-folders.md`](2026-10-12-oneui-phase4b-folders.md) | **Accepted 2026-10-12** (gates green; phase review 0 Critical/High after one fix wave; device check on the SM-S901B done: 98/98 connected `:core:database` tests, migration 3→4 included, 14 device mutation proofs, and an in-place upgrade over 4a with the library intact and Folders backfilled). Phase 5 carry-over: Unicode-aware collation must also cover `FolderRepositoryImpl`'s folder order | 2, 4a |
| 5 A–Z fast-scroll rail | [`2026-10-17-oneui-phase5-fast-scroll.md`](2026-10-17-oneui-phase5-fast-scroll.md) | **Accepted 2026-10-18.** The gate is green, with 1031 debug unit tests. The phase review found 0 Critical/High after one fix wave, and the compose-performance audit's one blocker (main-thread section building) is fixed. The device check on the SM-S901B is done: the ICU parity test passes 2/2 with a device mutation proof; the rail works on Tracks and Artists in portrait and landscape with the pill; TalkBack sees one node; the user confirmed the haptic ticks.

The phase adds the library order (spec §9.0) in `:core:common`, and `FastScrollRail`/`FastScrollContainer` on five tabs.

Phase 7 carry-overs are in the SDD ledger:
- a Perfetto trace of a rail jump (about one dropped frame per jump);
- a baseline profile with a rail journey;
- the drag-start slop point;
- end labels that overflow the rail;
- `BoxWithConstraints` → `Modifier.layout`;
- inset calibration. | 2, 4a, 4b |
| 6a Detail screens, sheets, dialogs, menus | [`2026-10-18-oneui-phase6a-details-sheets.md`](2026-10-18-oneui-phase6a-details-sheets.md) | **Committed 7edb0a7 (2026-10-21).** Device-checked on the Motorola edge 70 (Phase 6 split into 6a/6b/6c by user decision, 2026-10-18) | 1–5 |
| 6b Search, tag editors, About/Licenses/Privacy, permission screen | [`2026-10-21-oneui-phase6b-search-editors.md`](2026-10-21-oneui-phase6b-search-editors.md) | **Committed 3e86d8b (2026-10-22).** Reviewed (final review 0 Critical/High after one fix wave; gate green, 1148 unit tests) and device-checked 2026-10-22 on the Motorola edge 70. Also owns Home's permission-state carry-over and the `HomeTabPage` extraction | 6a (kit) |
| 6c Widget | [`2026-10-22-oneui-phase6c-widget.md`](2026-10-22-oneui-phase6c-widget.md) | Implemented; device-checked 2026-10-23 and 2026-10-27 on the Motorola edge 70; pending commit | 6a |
| 7 Device calibration, docs, baseline profile, final gate | `…-oneui-phase7-calibration-release.md` | Written after Phase 6 | all |

## Execution model (user decision)

- **Orchestrator:** the main session (Opus 5.5). It dispatches every task to a fresh subagent with `model: "opus"`, using the
  owning agent type from CLAUDE.md's ownership map. It reads each result against the plan and the code, and accepts it only when
  the task's tests and gates pass and a fresh `code-reviewer` subagent (also `model: "opus"`) finds no CRITICAL or HIGH issues.
- **Commits:** none by agents. The user triggers commits. At each phase boundary the orchestrator reports "ready to commit"
  with a prepared message.
- **Device:** connected tests (Room, DAO, smoke suite) and calibration need the reference phone over adb. Phases 1–3 run
  entirely on the JVM: unit tests and Robolectric.

## Cross-phase rules every plan inherits

- Spec §2 decisions are binding: "Tracks" UI label, ±15 s widget, custom icons, `#2F6FF0`, the touch-target exception for EQ
  columns and the A–Z rail, and "Equaliser" spelling.
- No dead controls. Every visible button does something real (spec §12).
- No hardcoded colors, type, shapes or dimensions outside `:core:designsystem`.
- **Never `primary` as a text color.** `#2F6FF0` is only 3.97:1 on the panel. Blue text always uses `accentText`. Any
  phase that touches a screen with stock M3 text-in-primary (`TextButton`, a focused `TextField` label, a Snackbar action)
  must pass explicit `accentText` content colors (for example `ButtonDefaults.textButtonColors(contentColor = …accentText)`).
  Until each screen's phase lands, those defaults are a known, ledgered temporary AA gap (Phase 1 ruling).
- Tests the redesign touches are updated, never deleted to go green.
- After Phase 1, migrate each feature's private `isReducedMotion()` copy to `rememberReducedMotion()` in the phase that
  rewrites that file: equalizer and player in Phase 3, playlists detail in Phase 6.

# One UI redesign: roadmap

- **Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md)
- **Measurements:** [`docs/design/oneui-measurements.md`](../../design/oneui-measurements.md)

The spec has 7 phases, and each one consumes the real APIs the previous one shipped. Writing all seven detailed plans now
would mean guessing at signatures that don't exist yet. So each phase gets its own plan, written **just in time**:
the orchestrator writes Phase N+1's plan after Phase N is accepted, reading the code as it actually landed.

| Phase | Plan file | Status | Depends on |
|---|---|---|---|
| 1 Tokens, icons, component kit | [`2026-10-06-oneui-phase1-tokens-kit.md`](2026-10-06-oneui-phase1-tokens-kit.md) | **Accepted 2026-10-07** (phase review: 0 Critical/High; Phase 2 carry-overs in the SDD ledger: row end-inset tokens, selection-highlight contrast, lineHeightStyle decision, tab ripple, Robolectric convention plugin) | — |
| 2 Home shell, tab row, panel, mini-player overlay, Tracks and Playlists tabs | [`2026-10-07-oneui-phase2-home-shell.md`](2026-10-07-oneui-phase2-home-shell.md) | **Gate green 2026-10-08** (check + assembleDebug, 1283 unit tests; phase review: 0 Critical/High, M1 shuffle fixed). Acceptance pending the on-device check and the tab-ripple decision. Phase 3 carry-overs are in the SDD ledger: shared Play/Shuffle helper, Queue seek re-pin, cycleRepeatMode queuing, pill footprint from tokens, MiniPlayerRoute order | 1 |
| 3 Now Playing, queue, mini-player, Equaliser, Settings | `…-oneui-phase3-player-eq-settings.md` | Written after Phase 2 | 1, 2 (`LocalMiniPlayerInset`, overlay) |
| 4a Favourites, Albums, Artists tabs | `…-oneui-phase4a-library-tabs.md` | Written after Phase 3 | 2 |
| 4b Folders: migration v4, scanner, FolderDetail | `…-oneui-phase4b-folders.md` | Written after Phase 4a | 2, 4a |
| 5 A–Z fast-scroll rail | `…-oneui-phase5-fast-scroll.md` | Written after Phase 4b | 2, 4a, 4b |
| 6 Secondary screens and widget | `…-oneui-phase6-secondary-widget.md` | Written after Phase 5 | 1–5 |
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

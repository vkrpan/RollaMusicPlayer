---
type: rule
status: active
date: 2026-11-08
source: "user, 2026-11-08: \"implement the rule for saving the important notes from summary after every compact into vault. So after every compact you have all needed informations from compacted summary saved\""
tags: [vault, memory, compaction, workflow]
related: ["[[Rules]]", "[[Record knowledge in the vault]]", "[[Sessions]]"]
---

# Save compaction summaries to the vault

**Rule:** after every context compaction, the important notes from the compaction summary are saved into the vault
before the task continues, so nothing the summary carries lives only in Claude's context.

**How it is enforced (two hooks in `.claude/settings.local.json` — machine-local by the user's choice, 2026-11-08 —
script `.claude/hooks/compact_to_vault.py`):**
1. **PostCompact → `save`:** writes the summary verbatim to `docs/history/sessions/<date time> compaction.md` with
   `status: to-distill`. This needs no judgement and cannot be skipped.
2. **SessionStart (matcher `compact`) → `remind`:** injects an instruction listing every `to-distill` session note and
   telling Claude to distil it before resuming.

**How to apply (Claude, right after a compaction):**
- Read each `to-distill` note in `docs/history/sessions/`.
- Distil every durable item into its entity note: user preferences and instructions → [[Rules]]; decisions →
  [[Decisions]]; lessons → [[Knowledge]]; feature, module and agent state → [[Features]], [[Modules]], [[Agents]];
  pending tasks and open work → [[Backlog]]; phase progress → [[Timeline]]; execution rulings stay in the run's ledger
  (archived to [[Ledgers]]).
- Update existing notes; don't duplicate.
- Set the session note's `status: distilled` and list the notes updated under "## Distilled into".
- Then resume the task the summary describes.

**If the save hook failed** (no `to-distill` note after a compaction), write the summary's important notes by hand,
following the same steps.

**Hook order (seen 2026-11-08, first real firing):** the SessionStart reminder ran before PostCompact had written the
note, so its listing missed the new note (and matched `Sessions.md`, whose prose names the status). The script now
reads the status from frontmatter only and tells Claude to list `docs/history/sessions/` itself.

**Encoding (fixed 2026-11-11):** on Windows the hook read stdin as cp1252, so every non-ASCII character of a saved
summary became mojibake ("â€¦" for "…"). It now decodes stdin bytes as UTF-8; the two earlier notes were repaired.

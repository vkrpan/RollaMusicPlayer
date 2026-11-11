# Custom tags — roadmap

**Spec:** [[2026-11-09-custom-tags-design]] (approved by the user 2026-11-09). **Vault:** [[Custom tags]].
**Branch:** `feat/oneui-redesign`, from `e129f3e`.

The feature is four dependent phases. Each phase consumes what the previous one produces, so only phase 1 has a full
plan now; each later plan is written after the previous phase is accepted, from the landed code (superpowers-extras,
writing-plans rule 1).

| Phase | Plan file | Delivers | Consumes |
|---|---|---|---|
| 1. Foundation | [[2026-11-09-custom-tags-phase1-foundation]] | `:core:tagio` (the `ROLLA_TAGS` field: read and write on MP3 and FLAC; **read-only on M4A**, SDD Ruling 5), the model names, Room v6 (tag lists, song links, genre), the library scan reading tags and genre from files in the background, `CustomTagRepository` (observe, create, rename, per-song IDs), the ID ceiling `MAX_CUSTOM_TAG_ID` | — |
| 2. Assigning | [[2026-11-10-custom-tags-phase2-assigning]] (M4A: MP3 and FLAC only, user 2026-11-10) | The picker sheet (`:core:ui`), the Tags and Subtags rows in the single and batch editors, writing `ROLLA_TAGS` on save, disabled rows for other formats | phase 1's `RollaTagsFileIo.writeCustomTagIds` (MP3/FLAC; check `canWriteCustomTags`), `CustomTagRepository`, and the post-save re-read, which is **asynchronous** (observe the song's links; fix the apply-after-reset race, see [[Custom tags]] "Known limits"). Gate: the user's M4A decision |
| 3. Managing | `2026-11-…-custom-tags-phase3-managing` | Manage tags (Home ⋮ and the picker link): rename, delete with file clean-up and one consent prompt, "Updated X of N tracks"; export and import with the clash warning | phases 1–2: the write path, the per-tag track count, the counters |
| 4. Finding and showing | `2026-11-…-custom-tags-phase4-finding` | Find tracks (Tracks header button, chips strip, live AND results, whole result as the queue), chips in track rows, chips on Now Playing, a scroll benchmark against the baseline | phase 1's song links and genre; phase 2's picker |

## Cross-phase rules

- The spec wins over every plan; a disagreement becomes a recorded ruling ([[Spec wins over plan]]).
- At each phase gate: check that the APIs the phase produced fit the next phase's **spec** needs, and grep `CLAUDE.md`,
  `.claude/rules/` and the vault for names the phase renamed or deleted.
- Each phase ends with the vault updated ([[Custom tags]] progress table, the module notes it touched, [[Timeline]],
  [[Backlog]]) and a "ready to commit" report; the user commits ([[Commits are user-triggered]]).
- Device work follows [[Device safety]]: the agent never saves tags on the Motorola; tag writes on the device are the
  user's hand in phase 2's walk.

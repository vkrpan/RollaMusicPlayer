---
type: rule
status: active
date: 2026-11-04
source: "user, One UI redesign commits (Phase 6c, Phase 7)"
tags: [git, workflow]
related: ["[[Rules]]", "[[Commits are user-triggered]]", "[[git commit hook needs a long timeout]]"]
---

# Commit staging excludes local artefacts

**Rule:** when the user asks for a commit, stage everything except the user's local reference material:

```bash
git add -A -- . ':!Screen_Recording_20261011_014300.mp4' ':!media player example' ':!docs/design/calibration'
git commit -F <message file>     # with a long timeout: the pre-commit hook runs detekt + Spotless
```

The message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Why:** the screen recording, the `media player example/` screenshots and the calibration captures are the user's
local reference files, not repo content.

**Git-ignored (user, 2026-11-08; moved to the repo root 2026-11-13, [[Numbered vault structure]]):**
`.obsidian/workspace*.json`, `.obsidian/cache`, `.trash/`, `.obsidian/plugins/*/data.json`. The rest of `.obsidian/`
(vault settings) is tracked.

**Open question for the user:** whether `.impeccable/` (local design-checker config) should be excluded too.

---
type: rule
status: active
date: 2026-11-09
source: "user, 2026-11-09: \"Can you use Wikilinks connections that can be read in obsidian, when writing all md files, so they can be connected in obsidian.\""
tags: [vault, docs, process]
related: ["[[Rules]]", "[[Vault conventions]]", "[[Record knowledge in the vault]]"]
---

# Link Markdown notes with wikilinks

**Rule:** every Markdown file Claude writes or edits links to other Markdown notes with Obsidian `[[wikilinks]]`, so
the notes connect in Obsidian's graph and backlinks. This covers vault notes, specs, plans, phase docs and session
notes, not only the typed vault notes.

**Why:** the user reads the project in Obsidian; a path in backticks is invisible to the graph.

**How to apply:**
- Link by file name without `.md`: `[[Custom tags]]`, `[[2026-11-09-custom-tags-design]]`. Use `[[name|label]]` when
  the sentence needs other words, and `[[name#Heading]]` for a section.
- File names are unique across the vault, so a bare name always resolves.
- The vault is the repository root (since 2026-11-13, [[Numbered vault structure]]), so `CLAUDE.md` and `README.md`
  are linkable as [[CLAUDE]] and [[README]]. Obsidian does not index dot-folders, so `.claude/rules/*`,
  `.claude/agents/*`, `.claude/skills/*` and source code stay cited by repo path in backticks; link the vault note about
  them when one exists ([[Ownership map]], the [[Agents]] notes, the [[Guides]] notes).
- Generated wiki pages follow their own schema ([[Wiki pages are generated, never hand-edited]]); they already use
  wikilinks.

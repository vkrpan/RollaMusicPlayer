---
type: decision
status: active
date: 2026-07-23
source: "deferred in v1 Phase 5, formally cut at v1.0; CLAUDE.md Post-1.0 cuts"
tags: [tag-editor, scope]
related: ["[[Decisions]]", "[[Tag editor]]", "[[Backlog]]"]
---

# Custom tag system is cut

**Decision:** the user-defined custom-tag layer (scoped for v1 Phase 5) is not built. The tag editor covers the full
standard field set — title, artist, album, genre, year, track number, album artist, composer, artwork — for MP3, FLAC
and M4A.

**How to apply:** if a prompt flags it as "missing", it is a deliberate cut, not a defect. Building it is a new
feature request, through brainstorming and a spec.

**History:** three other v1.0 cuts (the centre-weighted tab bar, Folders browsing, the A–Z fast-scroll index) were
built by the One UI redesign at the user's request ([[One UI redesign decisions]]).

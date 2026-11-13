---
type: decision
adr: "ADR-014"
status: active
date: 2026-10-12
source: "v1 Phase 8 (Album/Artist detail); One UI Phase 4a ruling 12; `.claude/rules/model-vocabulary.md`"
tags: [library, navigation, model]
related: ["[[Decisions]]", "[[Home and library]]", "[[Model vocabulary]]", "[[Unknown artists show as Unknown]]"]
---

# Artists are identified by name

**Decision:** an artist's identity is its **raw name** (the `ArtistDao` `GROUP BY` key). `ArtistDetail(artistName:
String)` routes by name. Search's synthetic FNV-1a artist id is display-only; lists key artists by name.

**Why:** MediaStore has no stable artist entity the app indexes; the name is the real grouping key, and a hash id would
not survive a re-scan if the name's formatting changed.

**Rules out:** routing or keying artists by the synthetic id.

**Artwork:** an artist has no picture since per-track artwork Task 5 (2026-11-08); the earlier rule that
`Artist.artworkUri` is the cover of its alphabetically first album (Phase 4a ruling 12) is gone
([[Albums, artists and folders show the placeholder]]).

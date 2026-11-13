---
type: knowledge
status: active
date: 2026-10-17
source: "panel-resume device result, 2026-10-17 (spec 2026-10-15-panel-resume-design §10)"
tags: [platform, samsung, system-ui]
related: ["[[Knowledge]]", "[[Panel resume targets stock Android]]", "[[Stock Android is the target platform]]", "[[Samsung SM-S901B]]"]
---

# One UI has no media resume card

**Fact:** Samsung One UI (SM-S901B, Android 14) never adds new apps to its media resume list. After the process dies,
Rolla Music's media card disappears from the panel, whatever the app does. Stock Android's System UI
(`MediaResumeListener`) does offer the card to a `MediaLibraryService`.

**Use:** a Samsung-only gap in a platform-UI feature is a known limitation ([[Stock Android is the target platform]]).

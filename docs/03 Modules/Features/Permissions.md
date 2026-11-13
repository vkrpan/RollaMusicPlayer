---
type: feature
status: active
date: 2026-07-01
source: "v1 Phase 0; One UI Phase 6b (permission prompt restyle)"
tags: [permissions, feature]
related: ["[[Features]]", "[[Offline and privacy first]]", "[[core-permissions]]", "[[permissions-agent]]"]
---

# Permissions

- **What is asked:** a version-aware media read permission: `READ_MEDIA_AUDIO` on 13+, `READ_EXTERNAL_STORAGE`
  below.
- **Flow:** a rationale first, then the system dialog; a permanent denial routes to Settings.
- **Where:** a Compose gate wraps Home's pager. Losing access clears the selection.
- **Write access** for tag edits is asked at the moment of writing, per SDK ([[Tag writes go through a cache copy]]).
- **Never asked:** `INTERNET` or any network permission ([[Offline and privacy first]]).
- **Module:** [[core-permissions]]. **Owner:** [[permissions-agent]].

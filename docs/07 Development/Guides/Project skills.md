---
type: guide
status: active
source: ".claude/skills/"
tags: [guide, skills]
related: ["[[Guides]]", "[[Agents]]"]
---

# Project skills

**Source of truth:** `.claude/skills/` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Project skills (loaded by name through the Skill tool): add-animations-transitions, add-dependency-injection-hilt, add-new-screen, add-room-database, add-ui-testing-compose, add-unit-testing, bootstrap-project, debug-playback-issue, generate-baseline-profile, handle-runtime-permissions, implement-datastore, implement-edge-to-edge-and-insets, implement-equalizer, implement-home-widget, implement-image-loading-coil, implement-media-scanning, implement-navigation-graph, implement-repository-pattern, implement-state-management, implement-tag-editor, implement-use-cases, release-build, setup-modularization, setup-static-analysis.
- Pick the skill by the decision the task is about, not by the file it arrived as (CLAUDE.md).
- Process skills come from plugins: superpowers (brainstorming, writing-plans, subagent-driven-development, TDD, systematic-debugging) plus the user's `superpowers-extras` companion, loaded alongside them.

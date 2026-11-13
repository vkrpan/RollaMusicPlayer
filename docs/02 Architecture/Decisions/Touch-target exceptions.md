---
type: decision
adr: "ADR-012"
status: active
date: 2026-10-06
source: "user, 2026-10-06; `.claude/rules/ui-style-guide.md` §5"
tags: [accessibility, ui]
related: ["[[Decisions]]", "[[One UI redesign decisions]]", "[[UI style guide]]", "[[Equaliser]]", "[[Home and library]]"]
---

# Touch-target exceptions

**Decision:** every touch target is at least `minTouchTarget` (48 dp), with exactly two exceptions the user accepted
on 2026-10-06, both above WCAG 2.2 AA's 24 dp and both with full semantic actions:
- **EQ slider columns:** the touch width is the column pitch (`eqColumnPitch` at minimum); each band has increase and
  decrease actions.
- **The A–Z rail:** the touch width is `fastScrollWidth + fastScrollEnd`; each populated section has a "Jump to X"
  action.

**Why:** the exact One UI look needs narrower columns; v1.0's "≥ 48 dp everywhere" gives way for these two only.

**Rules out:** any other control under 48 dp.

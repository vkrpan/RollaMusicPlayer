---
type: knowledge
status: active
date: 2026-10-22
source: "test work 2026-10-10 to 2026-10-22"
tags: [testing, compose]
related: ["[[Knowledge]]", "[[Robolectric screens and pixel tests]]"]
---

# Compose test geometry traps

- **`getBoundsInRoot()` clamps off-screen nodes** to the viewport inside a scrolling container. `performScrollTo()`
  first.
- **`performScrollTo()` cannot test bottom content padding:** it scrolls minimally, so a "drop the padding" mutation
  survives. Scroll to the end (`performScrollToIndex(last)` or a swipe), on a short screen (`w360dp-h640dp`).
- **A merging control's footprint can't be read from its own tagged node.** It reports the drawn bounds, never the
  48 dp `minimumInteractiveComponentSize` box, and `assertTouch*` is always 48. Wrap it in a bare
  `Box(Modifier.testTag("host"))` and measure the host.
- **Box-scoped modifiers (`matchParentSize`, `align`) leak into nested lambdas** (e.g. inside `AnimatedVisibility`) as
  an implicit receiver. They compile and silently size to 0×0. Put them on the direct Box child.
- **A disabled text field has no SetText action**, so `hasSetTextAction()` misses it. Match
  `SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)`.

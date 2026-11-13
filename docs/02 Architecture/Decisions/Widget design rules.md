---
type: decision
adr: "ADR-020"
status: active
date: 2026-10-27
source: "v1 Phase 7 (widget, 2026-07-10/12); One UI Phase 6c (2026-10-22/27) rulings; spec §13"
tags: [widget]
related: ["[[Decisions]]", "[[Home-screen widget]]", "[[widget-agent]]", "[[One UI redesign decisions]]"]
---

# Widget design rules

**Decision:**
- **Controls:** the widget keeps previous · −15 s · play/pause · +15 s · next. The user confirmed this for the
  redesign (2026-10-06), which changed the look only. A body tap opens the app.
- **One player:** there is no second player. Five stateless `ActionCallback`s drive the one `PlaybackController`. ±15 s
  is `seekTo`, clamped.
- **Push, not poll:** `updatePeriodMillis = 0`. `:core:media`'s `PlaybackUpdateHook` dispatcher pushes updates: on a
  track change, on play/pause, and every 1 s while playing. The widget side coalesces them.
- **Receiver:** `exported = true`. With `false` the widget can't be added; the broadcasts are platform-protected.
- **Palette:** the widget keeps its own palette. Glance can't read Compose tokens. It mirrors
  `feature/widget/src/main/res/values/colors.xml` in `MusicWidgetTheme.kt`, kept equal by `WidgetPaletteTest`, and
  stays dark in the light system theme.
- **Layout (Phase 6c):** one row by default, with a centred mode for taller cards (ruling P14). The artwork hides
  below 339 dp; the minimum width is 260 dp.
- **Artwork:** decoded once and memoised. Re-decoding on every 1 s tick was a review-gate HIGH.

**Why:** a widget that polls or runs its own player wastes battery and can disagree with the app.

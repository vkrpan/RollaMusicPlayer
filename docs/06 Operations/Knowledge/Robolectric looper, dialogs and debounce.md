---
type: knowledge
status: active
date: 2026-11-02
source: "test work 2026-10-08 to 2026-11-02"
tags: [testing, robolectric, compose]
related: ["[[Knowledge]]", "[[Robolectric screens and pixel tests]]", "[[Focused dialog text fields]]"]
---

# Robolectric looper, dialogs and debounce

- **A Compose `Dialog` with `usePlatformDefaultWidth = true` that hosts a text field never idles** under the PAUSED
  looper: the test hangs 60 s, then `AppNotIdleException`. The workaround in use is `@LooperMode(LEGACY)` on that one
  test, with a comment. The long-term fix is androidTest coverage. It bit again in custom tags phases 2 and 3 (the
  picker's name dialog, the Manage tags rename dialog): say "use `@LooperMode(LEGACY)`" up front in any brief whose
  test opens a dialog with a field.
- **A state change from `runOnIdle { … }` followed at once by a non-Compose action** (e.g.
  `onBackPressedDispatcher.onBackPressed()`) races: call `waitForIdle()` between them (custom tags phase 3).
- **A real `debounce` on Main never fires under `composeRule.waitForIdle()`**, because the paused clock doesn't
  advance. Use `shadowOf(Looper.getMainLooper()).idleFor(...)` then `waitForIdle()`
  (`DetailRouteFixtures.settleSearchDebounce()`).
- **Robolectric's dialog window is not in touch mode**, so Compose gives the FIRST focusable default focus. A focus
  test needs a decoy field first.
- **Touch slop is 16 dp, not 8 dp.** A swipe that must cross the slop inside a small target passes as a tap. Read
  `ViewConfiguration.get(activity).scaledTouchSlop` at run time.
- **Library modules run with targetSdk = minSdk (24).** Behaviour gated on targetSdk (e.g. `PhoneWindow` ignoring
  `isNavigationBarContrastEnforced` below 29) silently differs from the app's (34). Set the target SDK in such a test.

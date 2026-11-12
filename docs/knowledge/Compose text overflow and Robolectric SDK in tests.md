---
type: knowledge
status: active
date: 2026-11-12
source: "custom tags phase 4, Task 6 implementer report (Find tracks screen tests)"
tags: [testing, robolectric, compose]
related: ["[[Knowledge]]", "[[Robolectric screens and pixel tests]]", "[[Robolectric looper, dialogs and debounce]]", "[[Custom tags]]"]
---

# Compose text overflow and Robolectric SDK in tests

- **`hasVisualOverflow` misleads on one-line ellipsized text.** It reports `true` even when the label fits, because the
  text is laid out at full width and then sized to the label. To test that a one-line label is not cut, assert on the
  `TextLayoutResult`: `isLineEllipsized(0) == false` and `didExceedMaxLines == false`.
- **A module without `src/test/resources/robolectric.properties` runs on Robolectric's default SDK**, where NATIVE text
  measured about 1 dp per character, so width and wrap tests give nonsense. Every module with Compose tests needs
  `sdk=34` there (`:feature:search` lacked it until custom tags phase 4).

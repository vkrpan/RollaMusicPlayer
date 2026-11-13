---
type: hub
status: active
date: 2026-11-13
tags: [operations, troubleshooting]
related: ["[[00 Home]]", "[[Knowledge]]", "[[Known Issues]]", "[[Local Setup]]"]
---

# Troubleshooting

Symptom → cause → fix, one entry per problem. The full story is in the linked [[Knowledge]] note; open bugs in the
app itself are in [[Known Issues]]. A new knowledge note with a symptom adds its line here.

## Build and tooling
### `spotlessKotlinCheck` fails with a wall of line-ending diffs
**Cause:** the checkout is CRLF; an LF-written file (shell edit, new file) fails. **Fix:** `./gradlew spotlessApply`;
edit with the Edit/Write tools. → [[Spotless and line endings]]

### `git commit` exits 143 and nothing lands
**Cause:** the pre-commit hook (detekt + Spotless) outlives a 120 s tool timeout. **Fix:** a 600 s timeout.
→ [[git commit hook needs a long timeout]]

### Build fails because `classes.jar` is locked
**Cause:** a Gradle or Kotlin compile daemon holds the file. **Fix:** `./gradlew --stop`, then end the Kotlin daemon.
→ [[Windows Gradle daemon locks]]

### detekt `LongMethod` or `TooManyFunctions` after a small change
**Cause:** tight thresholds (30 lines; 12/15/20 functions). **Fix:** split the file or use the in-file suppression idiom.
→ [[Detekt thresholds and BOMs]]

### Lint crashes, or `local.properties` raises `PropertyEscape`
**Cause:** AGP 8.13 lint and Windows paths. **Fix:** see the note. → [[AGP 8.13 lint gotchas]]

### Compose stability warnings after adding a module
**Cause:** a per-module `composeCompiler {}` block. **Fix:** remove it; the convention plugins apply
`config/compose/stability-configuration.conf`. → [[Compose stability config location]]

### A heredoc dies with "unexpected EOF"; a long-pattern `replace_all` edits too much
→ [[Shell heredocs fail on large files]] · [[Edit replace_all matches deeper indentation]]

### An invisible character in Kotlin source became `""`
**Fix:** write it as a code point (`0xFEFF.toChar()`). → [[Unicode escapes can vanish when an agent writes a file]]

## Tests
### A Robolectric test hangs 60 s, then `AppNotIdleException`
**Cause:** a dialog with a text field never idles under the PAUSED looper. **Fix:** `@LooperMode(LEGACY)` on that test.
→ [[Robolectric looper, dialogs and debounce]]

### Widths or text sizes are nonsense in a Robolectric test
**Cause:** default 320 dp screen, LEGACY graphics, or a missing `robolectric.properties` (`sdk=34`).
→ [[Robolectric screens and pixel tests]] · [[Compose text overflow and Robolectric SDK in tests]] ·
[[Compose test geometry traps]]

### A shuffle test passes by luck
**Fix:** seed Media3's shuffle order. → [[Seed Media3 shuffle in tests]]

### A mutation run "fails" with last run's failure
**Cause:** stale `build/test-results` XML. **Fix:** clear it; quote the task's FAILED line.
→ [[Stale test XML fakes a RED]]

## App behaviour
### Two untagged tracks show the same album, or the same cover
**Cause:** MediaStore files untagged tracks under one album per folder. → [[Untagged tracks share a MediaStore album]]

### Removing artwork from an old MP3 throws; saving it loses title and artist
**Cause:** an ID3v1-only tag. → [[jaudiotagger and ID3v1-only MP3s]]

### Saving an M4A erases another app's custom fields; `ROLLA_TAGS` cannot be written to M4A
**Cause:** jaudiotagger writes only freeform atoms it knows. → [[jaudiotagger cannot write unknown M4A freeform atoms]]

### The media card disappears after the app is killed (Samsung)
**Cause:** One UI never adds new apps to its resume list. Not fixable in the app. → [[One UI has no media resume card]]

### A bottom sheet runs under the status bar
**Fix:** `statusBarsPadding()` on the sheet's modifier (`OneUiBottomSheet` has it). → [[ModalBottomSheet under the status bar]]

### A dialog field puts the cursor before the existing name
→ [[Focused dialog text fields]]

### The mini-player flashes grey when leaving Now Playing
→ [[Shared element outside the NavHost]]

## Devices
### `adb` not found, paths mangled, long-press drags ignored
→ [[adb on the Motorola]]

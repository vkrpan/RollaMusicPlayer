---
type: knowledge
status: active
date: 2026-11-02
source: "Phase 7 final review, 2026-11-02 (ruling P37)"
tags: [compose, ui, dialogs]
related: ["[[Knowledge]]", "[[Robolectric looper, dialogs and debounce]]"]
---

# Focused dialog text fields

**Symptom:** in a dialog, a `String` text field that takes focus on show puts the cursor at 0, so the first key lands
before an existing value. Switching to `TextFieldValue` crashed `CoreTextField.notifyFocusedRect` ("offset(8) is out
of bounds [0, 6)") on a whole-text replacement while focused (setText, the TalkBack/autofill path).

**Fix in use (ruling P37):** `OneUiDialogField` auto-focuses only an **empty** field (New playlist, Save as preset).
A field that opens with a name (Rename) waits for the user's tap. Compose BOM 2024.09.03.

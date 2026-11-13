---
type: entity
status: active
date: 2026-11-13
table: equalizer_presets
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/EqualizerPreset.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/EqualizerPresetEntity.kt"
tags: [data, entity, equaliser]
related: ["[[Database Schema]]", "[[Equaliser]]", "[[equalizer-agent]]", "[[Configuration]]"]
---

# EqualizerPreset

A named set of equaliser band gains (Room v3).

## Fields
| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER | no | auto-generated |
| `name` | TEXT | no | |
| `is_custom` | INTEGER | no | user-saved (true) or built in |
| `gains_millibel` | TEXT | no | one gain per band, millibels, stored by `ShortListConverter` |
| `created_at` | INTEGER | no | |

## Invariants
- Only **named** presets live here. The equaliser's active state (enabled, current gains) is DataStore's
  `eq_enabled` / `eq_gains` ([[Configuration]]).

## Read and written by
[[feature-equalizer]] and the effect in [[core-media]] ([[Equaliser]]).

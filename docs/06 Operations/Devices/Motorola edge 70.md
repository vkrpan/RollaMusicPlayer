---
type: device
status: active
date: 2026-10-21
source: "user, 2026-10-21"
tags: [device]
related: ["[[Knowledge]]", "[[Device tests run on the Motorola]]", "[[Device safety]]", "[[adb on the Motorola]]"]
---

# Motorola edge 70

- **Role:** the device for all testing from 2026-10-21 ([[Device tests run on the Motorola]]).
- **adb serial:** `ZY22MBNVB4`.
- **System:** Android 16 (SDK 36), near-stock.
- **Screen:** 1220×2712 px at 480 dpi = **3.0 px/dp** (406.7×904 dp).
- **Install:** `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark` (allowed without asking).
- **Data:**
  - the user's real library, playlists and settings ([[Device safety]]);
  - it once carried the release-signed v1.0, sideloaded; the app was first installed by Claude there on 2026-10-21.
- **Used for:** One UI calibration (Phase 7), widget checks (2026-10-23 and 2026-10-27), and the artwork investigation
  (2026-11-08).

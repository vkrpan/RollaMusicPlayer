---
type: rule
status: active
date: 2026-10-21
source: "user, 2026-10-21: \"From now on I want you to use motorola phone that is connected right now. You can install app on that phone and test it.\""
tags: [device, testing]
related: ["[[Rules]]", "[[Motorola edge 70]]", "[[Samsung SM-S901B]]", "[[Device safety]]", "[[Stock Android is the target platform]]"]
---

# Device tests run on the Motorola

**Rule:** all device testing uses the [[Motorola edge 70]] (adb serial `ZY22MBNVB4`). Installing the app there is
allowed without asking. The [[Samsung SM-S901B]] is no longer used unless the user asks.

**Why:** the user chose it; it is near-stock Android, which matches [[Stock Android is the target platform]].

**How to apply:** `-s ZY22MBNVB4` on every adb call; screen coordinates scale at 3.0 px/dp. [[Device safety]] still
applies in full.

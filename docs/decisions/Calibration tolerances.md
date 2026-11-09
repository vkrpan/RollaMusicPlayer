---
type: decision
status: active
date: 2026-11-01
source: "One UI Phase 7 calibration; `docs/design/oneui-measurements.md` (rulings P20, P22)"
tags: [design, calibration, oneui]
related: ["[[Decisions]]", "[[One UI redesign]]", "[[Motorola edge 70]]", "[[Ledgers]]"]
---

# Calibration tolerances

**Decision:**
- Tokens were calibrated by re-measuring the app on the [[Motorola edge 70]] against the reference screenshots, with a
  pixel-scanning script (`calibrate.py`; captures in `docs/design/calibration/`, kept local).
- A value within **±2 dp** (geometry) or **±3 per colour channel** matches.
- Calibrated tokens carry "(calibrated …)" in the spec.
- Remaining differences are recorded as **accepted** in [[oneui-measurements]], not changed
  (rulings P20, P22).

**Why:** the screenshots come from another phone and app; chasing every sub-dp difference would trade correctness of
the token system for pixel parity.

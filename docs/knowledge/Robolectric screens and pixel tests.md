---
type: knowledge
status: active
date: 2026-10-10
source: "Phase 1–3 test work, 2026-10-07 to 2026-10-10"
tags: [testing, robolectric, compose]
related: ["[[Knowledge]]", "[[Compose test geometry traps]]", "[[Robolectric looper, dialogs and debounce]]"]
---

# Robolectric screens and pixel tests

- **Default screen is 320 dp wide, mdpi.** `Modifier.width(360.dp)` is silently clamped. Use
  `@Config(qualifiers = "w360dp-h640dp")` (add `-xxhdpi` for sub-dp precision) on that test.
- **Text metrics are fake under LEGACY graphics** (~36 dp per line, ~1 px per char). Any geometry involving text
  needs `@GraphicsMode(NATIVE)`, which works offline (Robolectric 4.13, SDK 34). Under NATIVE, compare centres of a
  `graphicsLayer`-scaled node, not edges.
- **Pixel tests:**
  - LEGACY never runs draw lambdas.
  - NATIVE + `captureToImage()` times out on this Windows setup.
  - What works: NATIVE, then `view.draw(Canvas(bitmap))` of the root content view on the UI thread (helpers in
    `:core:designsystem` test `PixelTestSupport.kt`).
  - Ripples render only at `@Config(sdk = [30])`.
- **Screens with a `sharedElement`:**
  - `view.draw` fails there ("Software rendering doesn't support drawRenderNode").
  - Use `PixelCopy.request(activity.window, …)` with `robolectric.pixelCopyRenderMode=hardware` set in `@Before` and
    cleared in `@After` (`:feature:player` `PixelTestSupport.captureWindow`).
- **Fake images:** stub images per test with `Coil.setImageLoader(ImageLoader.Builder(ctx).components { add(Interceptor { … }) })`
  (`MiniPlayerArtPixelTest`).

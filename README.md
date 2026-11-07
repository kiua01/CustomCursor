# Custom Cursor (Mojo-style)

Android virtual mouse cursor you can fully customize — inspired by [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher)’s `GLFWCursorView`.

## Features

| Setting | Range | Description |
|--------|--------|-------------|
| **PNG look** | any image/png | Replace the arrow with your art |
| **Scale** | 0.25 – 4.0 | Visual scale (Mojo `PREF_MOUSESCALE`) |
| **Size** | 16 – 256 px | Base bitmap size before scale |
| **Speed** | 0.1 – 5.0 | Movement multiplier |
| **Opacity** | 10% – 100% | Transparency |
| **Smoothing** | 0 – 95% | Position lerp (less jitter) |
| **Hotspot** | 0 – 1 | Click point on the image (default top-left) |

## How it works

1. **Overlay service** (`TYPE_APPLICATION_OVERLAY`) draws `CursorView` on top of other apps (needs “Display over other apps”).
2. **CursorView** mirrors Mojo’s approach: translate to pointer position → `canvas.scale(mouseScale)` → draw bitmap at hotspot.
3. **Settings** are stored in `SharedPreferences` and reloaded live.

## Build

```bash
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`.

## Permissions

1. **Display over other apps** — required for the overlay cursor.
2. **Accessibility** (optional) — reserved for deeper system pointer hooks.

## Embed in a game / launcher (Mojo-like)

```kotlin
val cursor = CursorView(context)
cursor.loadFromPrefs()
// each frame / motion event:
cursor.setPixelPosition(x, y)
// or normalized 0..1:
cursor.setNormalizedPosition(nx, ny)
```

Hide the system cursor when the host supports it; feed relative mouse deltas through `CursorOverlayService.applyDelta(dx, dy)`.

## License

MIT

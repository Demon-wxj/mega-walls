"""Generate the original Enderman plasma atlas from a deterministic mathematical field.

Requires Python 3, NumPy and Pillow. No input images or third-party textures are
read or edited. This is a procedural game rendering asset, separate from the
AI-generated starfield beneath it.

64 non-looping frames, row-major in an 8 x 8 atlas. Each 512-square cell has a
2-pixel replicated gutter and 508-square image area. Sample normalized portal
coordinates x/y in [-1.15, 1.15] with:
    u = (column * 512 + 2 + (x / 2.30 + 0.5) * 508) / 4096
    v = (row    * 512 + 2 + (y / 2.30 + 0.5) * 508) / 4096
Frame f represents age = 24 * f / 63 Minecraft ticks. Clamp to [0, 63]; do not
wrap frame 63 to frame 0. Blend adjacent frames by scaling additive RGB weights.
The PNG is RGB with a dark additive background. Alpha comes from the renderer.
Minecraft's texture metadata enables bilinear filtering without atlas animation.
"""

from pathlib import Path
import json
import time

import numpy as np
from PIL import Image


FRAME_COUNT = 64
GRID = 8
CELL = 512
GUTTER = 2
INNER = CELL - 2 * GUTTER
EXTENT = 1.15
DURATION_TICKS = 24.0
PHASE = 1.4
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/megawalls/textures/effect"


def ease(value):
    return value * value * (3.0 - 2.0 * value)


def hash_grid(x, y):
    """Exact unsigned-overflow counterpart of EnderRiftRenderer.hash(int,int)."""
    with np.errstate(over="ignore"):
        n = x.astype(np.uint32) * np.uint32(374761393)
        n += y.astype(np.uint32) * np.uint32(668265263)
        n = (n ^ (n >> np.uint32(13))) * np.uint32(1274126177)
    return (n & np.uint32(0x7FFFFFFF)).astype(np.float32) / np.float32(0x7FFFFFFF)


def noise(x, y):
    ix, iy = np.floor(x).astype(np.int32), np.floor(y).astype(np.int32)
    u, v = ease(x - ix), ease(y - iy)
    a, b = hash_grid(ix, iy), hash_grid(ix + 1, iy)
    c, d = hash_grid(ix, iy + 1), hash_grid(ix + 1, iy + 1)
    return ((a + (b - a) * u) * (1.0 - v) + (c + (d - c) * u) * v).astype(np.float32)


def fluid_field(x, y, age):
    """Same domain-warped five-noise field as the original runtime plasma."""
    t = age * 0.12
    wx = x * 2.3 + 0.85 * noise(x * 2 + t * 0.7 + PHASE, y * 2 - t * 0.5)
    wy = y * 2.3 + 0.85 * noise(x * 2 - t * 0.4, y * 2 + t * 0.65 + PHASE)
    return (
        0.56 * noise(wx + t * 0.55, wy - t * 0.4)
        + 0.29 * noise(wx * 2.1 - t * 0.45, wy * 2.1 + PHASE)
        + 0.15 * noise(wx * 4.1, wy * 4.1 + t * 0.5)
    )


def render_frame(x, y, age):
    field = fluid_field(x, y, age)
    # A subtle cloud body joins the veins while retaining the stars underneath.
    cloud = 0.02 + 0.06 * np.power(np.clip(field, 0, 1), 1.15)
    rgb = cloud[..., None] * np.array([0.57, 0.085, 1.0], dtype=np.float32)
    for level in range(6):
        iso = 0.27 + 0.09 * level
        distance = field - iso
        core = np.exp(-0.5 * np.square(distance / 0.004))
        halo = np.exp(-0.5 * np.square(distance / 0.016))
        power = 0.50 + 0.18 * np.sin(age * 0.20 + level * 1.4)
        rgb += (core * power)[..., None] * np.array([0.73, 0.095, 1.0], dtype=np.float32)
        rgb += (halo * power * 0.37)[..., None] * np.array([0.48, 0.026, 0.88], dtype=np.float32)
    # Deliberately saturated violet: no broad white cores or alpha-only glows.
    rgb = np.rint(np.clip(rgb, 0.0, 0.98) * 255.0).astype(np.uint8)
    return np.pad(rgb, ((GUTTER, GUTTER), (GUTTER, GUTTER), (0, 0)), mode="edge")


def main():
    started = time.perf_counter()
    OUT.mkdir(parents=True, exist_ok=True)
    coordinates = np.linspace(-EXTENT, EXTENT, INNER, dtype=np.float32)
    x, y = np.meshgrid(coordinates, coordinates)
    atlas = np.empty((CELL * GRID, CELL * GRID, 3), dtype=np.uint8)
    for frame in range(FRAME_COUNT):
        row, column = divmod(frame, GRID)
        cell = render_frame(x, y, DURATION_TICKS * frame / (FRAME_COUNT - 1))
        atlas[row * CELL:(row + 1) * CELL, column * CELL:(column + 1) * CELL] = cell
    target = OUT / "rift_plasma.png"
    Image.fromarray(atlas, "RGB").save(target, compress_level=9, optimize=True)
    target.with_suffix(".png.mcmeta").write_text(
        json.dumps({"texture": {"blur": True, "clamp": True}}, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({
        "path": str(target), "size": [CELL * GRID, CELL * GRID],
        "frames": FRAME_COUNT, "cell": CELL, "gutter": GUTTER,
        "mode": "RGB", "bytes": target.stat().st_size,
        "elapsed_seconds": round(time.perf_counter() - started, 2),
    }))


if __name__ == "__main__":
    main()

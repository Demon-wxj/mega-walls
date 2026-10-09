"""Deterministic mathematical sampling kernels used by the rift mesh; no third-party images."""
from pathlib import Path
from PIL import Image
import math
import json

out = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/megawalls/textures/effect'
out.mkdir(parents=True, exist_ok=True)
size = 64
glow = Image.new('RGBA', (size, size))
for y in range(size):
    for x in range(size):
        r2 = ((x + .5 - size / 2) / (size / 2)) ** 2 + ((y + .5 - size / 2) / (size / 2)) ** 2
        value = round(255 * max(0, (math.exp(-r2 * 5) - math.exp(-5)) / (1 - math.exp(-5)))) if r2 < 1 else 0
        glow.putpixel((x, y), (value, value, value, 255))
glow.save(out / 'rift_glow.png')
Image.new('RGBA', (2, 2), (255, 255, 255, 255)).save(out / 'rift_white.png')
# A white-hot core inside a soft blue halo, sampled across continuous ribbon strips.
line = Image.new('RGB', (256, 2))
for x in range(256):
    distance = (x + .5 - 128) / 128
    core = math.exp(-distance * distance * 1300)
    halo = math.exp(-distance * distance * 35) * .54 + math.exp(-distance * distance * 6) * .07
    rgb = tuple(round(min(1, core*c + halo*h)*255) for c,h in [(1,.20),(1,.36),(1,1)])
    for y in range(2): line.putpixel((x,y), rgb)
line.save(out / 'rift_filament_glow.png')
(out / 'rift_filament_glow.png.mcmeta').write_text(json.dumps({'texture': {'blur': True, 'clamp': True}}))

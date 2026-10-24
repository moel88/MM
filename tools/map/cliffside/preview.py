"""Top-down block preview of a world (not a game screen): every column's highest block in its block colour, shaded by
height, with a 16-block grid and world coordinates. usage: python3 preview.py WORLD OUT.png [scale] [x0,z0,x1,z1]"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, str(Path(__file__).parent))
from world import World

COLOURS = {
    'air': (0, 0, 0), 'water': (40, 110, 160), 'grass_block': (96, 150, 70), 'dirt': (120, 85, 60), 'dirt_path': (150, 125, 80),
    'stone': (125, 125, 125), 'andesite': (135, 135, 135), 'polished_andesite': (150, 152, 152), 'smooth_stone': (165, 165, 165),
    'black_concrete': (25, 25, 30), 'gray_concrete': (60, 63, 68), 'light_gray_concrete': (125, 125, 117), 'white_concrete': (210, 214, 215),
    'calcite': (222, 223, 219), 'quartz_bricks': (232, 226, 218), 'smooth_quartz': (235, 230, 222), 'smooth_quartz_slab': (228, 223, 215),
    'quartz_pillar': (230, 225, 216), 'terracotta': (152, 94, 67), 'brown_terracotta': (77, 51, 36), 'gray_terracotta': (58, 42, 36),
    'light_blue_terracotta': (114, 109, 138), 'blue_terracotta': (74, 60, 91), 'light_blue_stained_glass': (110, 170, 215),
    'white_stained_glass': (240, 240, 240), 'glass': (200, 225, 230), 'sand': (219, 207, 163), 'moss_block': (90, 110, 45),
    'azalea_leaves': (90, 120, 45), 'flowering_azalea_leaves': (110, 125, 70), 'spruce_leaves': (60, 90, 60), 'cherry_leaves': (230, 170, 200),
    'oak_leaves': (70, 120, 40), 'birch_planks': (192, 175, 121), 'birch_log': (215, 210, 200), 'dark_oak_planks': (66, 43, 20),
    'waxed_oxidized_cut_copper_slab': (82, 162, 132), 'waxed_oxidized_cut_copper': (82, 162, 132), 'sea_lantern': (170, 200, 190),
    'spruce_slab': (110, 80, 50), 'red_concrete': (142, 33, 33), 'light_blue_wool': (58, 175, 217), 'white_wool': (234, 236, 236),
    'polished_deepslate': (72, 72, 73), 'iron_block': (220, 220, 220), 'obsidian': (20, 18, 30), 'amethyst_block': (133, 97, 191),
    'gold_block': (246, 208, 61), 'cyan_concrete': (21, 119, 136), 'light_blue_concrete': (36, 137, 199), 'blue_concrete': (45, 47, 143),
    'yellow_concrete': (240, 175, 21), 'deepslate_bricks': (70, 70, 70), 'deepslate_tiles': (54, 54, 55), 'stone_bricks': (122, 121, 122),
    'bookshelf': (110, 90, 60), 'smooth_sandstone': (223, 214, 170), 'sandstone': (216, 203, 155), 'cut_sandstone': (218, 206, 160),
}


def render(w, scale=3, box=None):
    from iso import EXTRA
    table = dict(COLOURS, **EXTRA)
    top = w.top()
    zz, xx = np.indices(top.shape)
    yi = np.clip(top - w.y0, 0, w.H - 1)
    ids = w.v[yi, zz, xx]
    lut = np.array([table.get(n.split(':')[1], (180, 0, 180)) for n in w.names], float)
    rgb = lut[ids]
    shade = np.clip(0.75 + (top - 80) * 0.012, 0.55, 1.35)[..., None]
    # Edge shading: a column higher than its north-west neighbour is lit, lower is shadowed.
    nw = np.roll(np.roll(top, 1, 0), 1, 1)
    rgb = np.clip(rgb * shade * np.where(top > nw, 1.12, np.where(top < nw, 0.82, 1.0))[..., None], 0, 255).astype(np.uint8)
    img = Image.fromarray(rgb).resize((w.W * scale, w.D * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', max(9, 3 * scale + 2))
    except Exception: font = ImageFont.load_default()
    for x in range(w.x0, w.x0 + w.W + 1, 16):
        X = (x - w.x0) * scale
        d.line([(X, 0), (X, img.height)], fill=(255, 255, 255, 60) if x % 64 else (255, 255, 0), width=1)
        if x % 32 == 0: d.text((X + 2, 2), str(x), fill=(255, 255, 0), font=font)
    for z in range(w.z0, w.z0 + w.D + 1, 16):
        Z = (z - w.z0) * scale
        d.line([(0, Z), (img.width, Z)], fill=(255, 255, 255, 60) if z % 64 else (255, 255, 0), width=1)
        if z % 32 == 0: d.text((2, Z + 2), str(z), fill=(255, 255, 0), font=font)
    if box:
        x0, z0, x1, z1 = box
        img = img.crop(((x0 - w.x0) * scale, (z0 - w.z0) * scale, (x1 - w.x0 + 1) * scale, (z1 - w.z0 + 1) * scale))
    return img


if __name__ == '__main__':
    w = World(sys.argv[1], 60, 200)
    scale = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    box = tuple(int(v) for v in sys.argv[4].split(',')) if len(sys.argv) > 4 else None
    render(w, scale, box).save(sys.argv[2])
    print('saved', sys.argv[2])

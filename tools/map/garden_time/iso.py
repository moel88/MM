"""An isometric block preview of part of a world (not a game screen), for checking shapes while designing.

    render(world, x0, y0, z0, x1, y1, z1, scale=6, view='se') -> PIL image
view names the corner the eye looks from: 'se' (from +x +z), 'sw', 'ne', 'nw'.
"""
import numpy as np
from PIL import Image, ImageDraw
from preview import COLOURS

EXTRA = {
    'glass': (205, 230, 235), 'gold_block': (246, 208, 61), 'raw_gold_block': (221, 169, 46), 'chiseled_quartz_block': (231, 226, 218),
    'quartz_slab': (235, 229, 220), 'smooth_quartz_stairs': (235, 230, 222), 'quartz_stairs': (235, 230, 222),
    'chain': (60, 65, 80), 'end_rod': (240, 235, 225), 'lantern': (230, 170, 80), 'sea_lantern': (190, 220, 210),
    'waxed_cut_copper': (191, 107, 80), 'waxed_exposed_cut_copper': (161, 125, 103), 'waxed_weathered_cut_copper': (108, 153, 110),
    'waxed_oxidized_cut_copper_stairs': (82, 162, 132), 'waxed_copper_block': (192, 107, 79), 'waxed_oxidized_copper': (82, 162, 132),
    'waxed_weathered_copper': (108, 153, 110), 'waxed_exposed_copper': (161, 125, 103), 'yellow_stained_glass': (230, 220, 90),
    'light_blue_stained_glass_pane': (110, 170, 215), 'glass_pane': (205, 230, 235), 'yellow_stained_glass_pane': (230, 220, 90),
    'polished_blackstone': (53, 48, 56), 'black_concrete': (25, 25, 30), 'blue_concrete': (45, 47, 143), 'chiseled_sandstone': (216, 203, 155),
    'smooth_sandstone_slab': (223, 214, 170), 'cut_sandstone': (218, 206, 160), 'mossy_stone_bricks': (115, 121, 105),
    'cracked_stone_bricks': (118, 117, 118), 'mossy_cobblestone': (110, 118, 94), 'moss_carpet': (90, 110, 45), 'prismarine': (99, 156, 151),
    'dark_prismarine': (51, 91, 75), 'pearlescent_froglight': (245, 240, 235), 'ochre_froglight': (250, 225, 150), 'shroomlight': (240, 146, 70),
    'allium': (180, 120, 220), 'pink_tulip': (240, 170, 200), 'white_tulip': (240, 240, 240), 'dandelion': (250, 220, 40), 'poppy': (200, 30, 30),
    'cornflower': (70, 100, 220), 'blue_orchid': (40, 170, 230), 'oxeye_daisy': (235, 235, 220), 'lily_of_the_valley': (240, 240, 240),
    'azure_bluet': (230, 235, 245), 'orange_tulip': (240, 140, 40), 'red_tulip': (220, 40, 40), 'polished_deepslate_slab': (72, 72, 73),
    'deepslate_tile_slab': (54, 54, 55), 'smooth_quartz_slab': (228, 223, 215), 'stone_brick_wall': (122, 121, 122), 'polished_andesite_slab': (150, 152, 152),
    'calcite': (222, 223, 219), 'stone_bricks': (122, 121, 122), 'light_blue_concrete': (36, 137, 199), 'stone': (125, 125, 125),
    'dark_oak_fence': (66, 43, 20), 'flower_pot': (120, 70, 50), 'candle': (230, 220, 190), 'white_candle': (230, 230, 230),
}


def colour(name):
    n = name.split(':')[1]
    return EXTRA.get(n) or COLOURS.get(n) or (180, 0, 180)


def render(w, x0, y0, z0, x1, y1, z1, scale=6, view='se'):
    sub = w.v[y0 - w.y0:y1 - w.y0 + 1, z0 - w.z0:z1 - w.z0 + 1, x0 - w.x0:x1 - w.x0 + 1]
    # turn the box so the eye always looks from +x +z
    if view in ('sw', 'nw'): sub = sub[:, :, ::-1]
    if view in ('ne', 'nw'): sub = sub[:, ::-1, :]
    H, D, Wd = sub.shape
    lut = np.array([colour(n) for n in w.names], float)
    solid = sub != 0
    pad = np.pad(solid, 1)
    top = solid & ~pad[2:, 1:-1, 1:-1]
    south = solid & ~pad[1:-1, 2:, 1:-1]
    east = solid & ~pad[1:-1, 1:-1, 2:]
    s = scale; cx, cy = s * 0.866, s * 0.5
    ox = D * cx + 4; oy = H * s + 4
    img = Image.new('RGB', (int((Wd + D) * cx) + 8, int((Wd + D) * cy + H * s) + 8), (24, 30, 40))
    d = ImageDraw.Draw(img)
    ys, zs, xs = np.nonzero(top | south | east)
    order = np.lexsort((ys, xs + zs))
    see = np.array([('glass' in n) for n in w.names])  # glass is drawn see-through
    for i in order:
        y, z, x = ys[i], zs[i], xs[i]
        c = lut[sub[y, z, x]]
        if see[sub[y, z, x]]:
            px = ox + (x - z) * cx; py = oy + (x + z) * cy - y * s
            ga = Image.new('RGBA', img.size, (0, 0, 0, 0)); gd = ImageDraw.Draw(ga)
            if south[y, z, x]: gd.polygon([(px - cx, py - s + cy), (px, py - s + 2 * cy), (px, py + 2 * cy), (px - cx, py + cy)], fill=tuple(int(v * 0.85) for v in c) + (70,))
            if east[y, z, x]: gd.polygon([(px, py - s + 2 * cy), (px + cx, py - s + cy), (px + cx, py + cy), (px, py + 2 * cy)], fill=tuple(int(v * 0.75) for v in c) + (70,))
            if top[y, z, x]: gd.polygon([(px, py - s), (px + cx, py - s + cy), (px, py - s + 2 * cy), (px - cx, py - s + cy)], fill=tuple(int(v) for v in c) + (70,))
            img = Image.alpha_composite(img.convert('RGBA'), ga).convert('RGB'); d = ImageDraw.Draw(img)
            continue
        px = ox + (x - z) * cx; py = oy + (x + z) * cy - y * s
        # the cube's three visible faces: top, south (+z, lower left), east (+x, lower right)
        p_top = [(px, py - s), (px + cx, py - s + cy), (px, py - s + 2 * cy), (px - cx, py - s + cy)]
        p_s = [(px - cx, py - s + cy), (px, py - s + 2 * cy), (px, py + 2 * cy), (px - cx, py + cy)]
        p_e = [(px, py - s + 2 * cy), (px + cx, py - s + cy), (px + cx, py + cy), (px, py + 2 * cy)]
        if south[y, z, x]: d.polygon(p_s, fill=tuple(int(v * 0.78) for v in c))
        if east[y, z, x]: d.polygon(p_e, fill=tuple(int(v * 0.62) for v in c))
        if top[y, z, x]: d.polygon(p_top, fill=tuple(int(v) for v in c))
    return img

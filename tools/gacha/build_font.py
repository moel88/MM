"""Builds the gacha screen's fonts (2.40.0) from Pretendard (SIL Open Font License 1.1).

usage: python3 tools/gacha/build_font.py PRETENDARD_DIR OUT_DIR

Each weight is cut down to what the screen can show (ASCII, Latin-1, common punctuation and symbols, the Hangul
compatibility jamo and the 2,350 syllables of KS X 1001) and, as the licence asks of a modified font, renamed so it
does not carry the Reserved Font Names: it ships as "Botand Gacha Sans". The licence goes with it
(third_party/pretendard/OFL.txt).
"""
import sys
from fontTools import subset
from fontTools.ttLib import TTFont

WEIGHTS = {'Medium': 'medium', 'Bold': 'bold', 'ExtraBold': 'heavy'}
FAMILY = 'Botand Gacha Sans'


def codepoints():
    cps = set(range(0x20, 0x7F)) | set(range(0xA0, 0x100))
    cps |= set(range(0x2010, 0x2028)) | set(range(0x2030, 0x203C)) | {0x2190, 0x2191, 0x2192, 0x2193, 0x2605, 0x2606}
    cps |= {0x25B6, 0x25C0, 0x25C6, 0x25C7, 0x25CB, 0x25CF, 0x3008, 0x3009, 0x300C, 0x300D, 0x300E, 0x300F}
    cps |= set(range(0x3131, 0x318F))
    cps |= {c for c in range(0xAC00, 0xD7A4) if len(chr(c).encode('euc_kr')) == 2}
    return cps


def main():
    src, out = sys.argv[1], sys.argv[2]
    cps = codepoints()
    for weight, short in WEIGHTS.items():
        font = TTFont(f'{src}/Pretendard-{weight}.ttf')
        options = subset.Options()
        options.layout_features = ['kern']
        options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14, 16, 17]
        options.drop_tables += ['DSIG']
        options.hinting = False
        sub = subset.Subsetter(options)
        sub.populate(unicodes=cps)
        sub.subset(font)
        name = font['name']
        for rec in list(name.names):
            if rec.nameID in (1, 16):
                rec.string = FAMILY
            elif rec.nameID == 4:
                rec.string = f'{FAMILY} {weight}'
            elif rec.nameID in (3, 6):
                rec.string = f'BotandGachaSans-{weight}'
        font.save(f'{out}/gacha_{short}.ttf')
        print(weight, len(font.getGlyphOrder()), 'glyphs')


if __name__ == '__main__':
    main()

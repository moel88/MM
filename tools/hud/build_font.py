"""Builds the HUD's serif face (2.42.0) for the danger banner from Nanum Myeongjo ExtraBold (SIL Open Font License 1.1).

usage: python3 tools/hud/build_font.py NanumMyeongjoExtraBold.ttf OUT_DIR

The banner after the user's screenshot ("IN DANGER / Your life is at risk! / (Stand Storage disabled.)", a bold red
serif) is drawn in Korean in a bold Myeongjo. The face is cut down to printable ASCII and the Hangul of the banner's
lines (TEXT, the same strings as AtomicHud's), and, as the licence asks of a modified font, renamed so it does not carry
the Reserved Font Names: it ships as "Botand Hud Serif". The licence goes with it (third_party/nanum-myeongjo/OFL.txt,
and inside the jar assets/celestialfall/licenses/NanumMyeongjo-OFL.txt). Change TEXT and run this again when the
banner's wording changes. The source font comes from the npm package @kfonts/nanum-myeongjo (src/).
"""
import sys
from fontTools import subset
from fontTools.ttLib import TTFont

TEXT = ['위험', '목숨이 위태롭습니다!', '(스탠드 보관 불가)']
FAMILY = 'Botand Hud Serif'


def main():
    src, out = sys.argv[1], sys.argv[2]
    cps = set(range(0x20, 0x7F))
    for line in TEXT:
        cps |= {ord(c) for c in line}
    font = TTFont(src)
    options = subset.Options()
    options.layout_features = ['kern']
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14, 16, 17]
    options.drop_tables += ['DSIG', 'FFTM']
    options.hinting = False
    sub = subset.Subsetter(options)
    sub.populate(unicodes=cps)
    sub.subset(font)
    name = font['name']
    for rec in list(name.names):
        if rec.nameID in (1, 16):
            rec.string = FAMILY
        elif rec.nameID == 4:
            rec.string = f'{FAMILY} ExtraBold'
        elif rec.nameID in (3, 6):
            rec.string = 'BotandHudSerif-ExtraBold'
        elif rec.nameID == 13:
            rec.string = 'This Font Software is licensed under the SIL Open Font License, Version 1.1.'
        elif rec.nameID == 14:
            rec.string = 'https://openfontlicense.org'
    for nid, text in ((13, 'This Font Software is licensed under the SIL Open Font License, Version 1.1.'), (14, 'https://openfontlicense.org')):
        if name.getName(nid, 3, 1, 0x409) is None:
            name.setName(text, nid, 3, 1, 0x409)
    font.save(f'{out}/hud_serif.ttf')
    missing = [c for c in ''.join(TEXT) if ord(c) not in font.getBestCmap()]
    print(len(font.getGlyphOrder()), 'glyphs', 'missing', missing)


if __name__ == '__main__':
    main()

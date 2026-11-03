"""Lays the game's own sound under a film of Lee So-sang's G (2.83.0): as mux_h.py does H's, its swing IsosangRules.ULTIMATE
and its sound whichever of the two recordings the server chose (isosang_g_1 or isosang_g_2: SkillShots' "gvar=1|2" makes
both runs choose the same one; the two runs must have, or this stops).
usage: python3 -I tools/isosang/shots/mux_g.py AUDIO_DIR FILM_DIR ENTRY "A-B,C-D,..." FILM.mp4 OUT.mp4
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_h import mux  # noqa: E402

ULTIMATE = '9'  # G's swing (IsosangRules.ULTIMATE)

if __name__ == '__main__':
    mux(*sys.argv[1:7], combo=ULTIMATE, sounds=('isosang_g_1', 'isosang_g_2'), name='G')

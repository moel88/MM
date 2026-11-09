"""X Chara's G, the sword dance (2.112.0): its sound, from the user's video of it
(6ceb35fc-Screen_Recording_20261110_025901_YouTube.mp4, a screen recording of a video at its real speed), as it is.

usage: python3 -I tools/xchara/cut_g_sound.py G_VIDEO.mp4 [OUT_DIR]

The video's sound is three pieces with digital silence between them: a short alert (0.80 to 1.2 s), the cutscene's sound
(1.78 to 7.72 s) and the blades' (8.0 s on, dying away by 13.9 s). It comes AV_LAG seconds after its picture: 0.35 s,
as the user's other videos' did: so the alert comes with the press (0.45 s into the picture: the target's mark changes
then and the "!!!" shows 0.15 s after), the cutscene's sound with the strange space (1.35 s into the picture) until the
two vanish from it (7.40 s), and the blades' with the world coming back (7.75 s), on until the one cut up is sent flying
(11.4 s). So two cuts, each played as its moment comes in the game (XCharaRules): xchara_g_warn.ogg from the press
(PRESS + AV_LAG, WARN_LENGTH seconds: the alert), and xchara_g.ogg from the strange space (WARN ticks later: the
cutscene's sound and the blades', to the end of the recording, its last END_FADE faded out). A G blocked by a guard is
heard only to its alert.

Nothing else is changed: mono at 48 kHz, one gain for both (the louder's peak at PEAK_DB), 3 ms faded in.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 48000
FADE_IN = .003

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# the press in the video's picture, and how long after its picture its sound comes
PRESS = .45
AV_LAG = .35
# the strange space comes this long after the press (XCharaRules.WARN, 18 ticks)
WARN = 18 / 20
# the alert's cut (it is over by then: digital silence follows), and the end's fade
WARN_LENGTH = .45
END_FADE = .4
PEAK_DB = -1.0


def decode(path):
    """The video's sound, mono (its two channels averaged) at RATE (as tools/isosang/cut_z_sound.py has it)."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


def write(x, path):
    """Mono Ogg Vorbis at RATE (as tools/stand/cut_rush.py writes)."""
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '7', str(path)], input=x.astype(np.float32).tobytes(), check=True)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else SOUNDS
    x = decode(Path(sys.argv[1]))
    a = int(round((PRESS + AV_LAG) * RATE))
    b = int(round((PRESS + AV_LAG + WARN) * RATE))
    warn = x[a:a + int(round(WARN_LENGTH * RATE))].copy()
    dance = x[b:].copy()
    # to where it dies away into the recording's silence (-70 dB under its peak)
    w = int(.01 * RATE)
    env = 20 * np.log10(np.sqrt((dance[:len(dance) // w * w].reshape(-1, w) ** 2).mean(axis=1)) + 1e-12)
    last = int(np.nonzero(env > env.max() - 70)[0][-1])
    dance = dance[:(last + 1) * w]
    g = 10 ** (PEAK_DB / 20) / max(np.abs(warn).max(), np.abs(dance).max())
    warn, dance = warn * g, dance * g
    n_in = int(FADE_IN * RATE)
    for y in (warn, dance):
        y[:n_in] *= np.linspace(0, 1, n_in)
    n_out = int(END_FADE * RATE)
    dance[-n_out:] *= np.cos(np.linspace(0, np.pi / 2, n_out)) ** 2
    n_warn = int(.03 * RATE)
    warn[-n_warn:] *= np.cos(np.linspace(0, np.pi / 2, n_warn)) ** 2
    out.mkdir(parents=True, exist_ok=True)
    write(warn, out / 'xchara_g_warn.ogg')
    write(dance, out / 'xchara_g.ogg')
    env2 = 20 * np.log10(np.sqrt((dance[:len(dance) // w * w].reshape(-1, w) ** 2).mean(axis=1)) + 1e-12)
    on = np.nonzero(env2 > env2.max() - 40)[0]
    gaps = [(i * .01) for i in range(1, len(env2)) if env2[i] > env2.max() - 40 and env2[i - 1] <= env2.max() - 40]
    print(f'xchara_g_warn.ogg: {len(warn) / RATE:.3f} s from {(PRESS + AV_LAG):.3f} s')
    print(f'xchara_g.ogg: {len(dance) / RATE:.3f} s from {(PRESS + AV_LAG + WARN):.3f} s; sound begins {on[0] * .01:.2f} s in, '
          f'pieces begin at {", ".join(f"{t:.2f}" for t in gaps[:4])} s; gain {20 * np.log10(g):+.1f} dB')


if __name__ == '__main__':
    main()

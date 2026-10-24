"""Re-cuts T held's sound (kageno_rush) to the remade skill's timeline (2.38.0).

usage: python3 tools/kageno/recut_rush.py IN OUT.ogg
IN is the 3.5 s clip separate_audio.py cuts from the supplied video (the old timeline: three beams, a dash, the spin
cut). Its pieces are moved, never stretched or pitched, so each sound lands on the remade cast's events: the three fire
slashes at 0.40, 0.60 and 0.80 s, the dash at 0.90 s, the arrival at 1.20 s (the dash's burst laid over it again), the
burst on the one struck at 1.30 s and the spin cut at 1.55 s. The result starts with the cast (KagenoAudio's lead 0).
Each piece fades in and out over a few milliseconds where it meets the next, so no splice clicks.
"""
import subprocess, sys
import numpy as np

SR = 48000
# (from, to) seconds of the old clip -> where the piece starts in the new one.
PIECES = [((.20, .60), .00),    # the blade catching fire, up to the first beam
          ((.60, .80), .40),    # slash 1
          ((.98, 1.18), .60),   # slash 2
          ((1.19, 1.29), .80),  # slash 3
          ((1.38, 1.70), .90),  # the dash leaving and running
          ((1.96, 3.50), 1.22)] # the burst (old 2.03 s -> 1.29 s), the spin cut (old 2.29 s -> 1.55 s) and the fire after
# Laid over the new clip: (from, to) of the old clip, where, gain.
OVER = [((1.38, 1.53), 1.20, .7)]  # the arrival
FADE = .006


def read(path):
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'])
    return np.frombuffer(raw, dtype='<f4').reshape(-1, 2).copy()


def piece(x, a, b, fade_in=FADE, fade_out=FADE):
    p = x[round(a * SR):round(b * SR)].copy()
    for n, ramp in ((round(fade_in * SR), slice(None)), (round(fade_out * SR), slice(None, None, -1))):
        if n <= 0 or n * 2 > len(p): continue
        w = (.5 - .5 * np.cos(np.pi * np.arange(n) / n))[:, None]
        if ramp.step == -1: p[-n:] *= w[::-1]
        else: p[:n] *= w
    return p


def main():
    src, dst = sys.argv[1], sys.argv[2]
    x = read(src)
    end = max(at + (b - a) for (a, b), at in PIECES)
    out = np.zeros((round(end * SR) + 1, 2), np.float32)
    for i, ((a, b), at) in enumerate(PIECES):
        p = piece(x, a, b, .01 if i == 0 else FADE, 0 if i == len(PIECES) - 1 else FADE)
        s = round(at * SR); out[s:s + len(p)] += p
    for (a, b), at, gain in OVER:
        p = piece(x, a, b, FADE, .03) * gain
        s = round(at * SR); out[s:s + len(p)] += p
    peak = float(np.abs(out).max())
    if peak > .98: out *= .98 / peak
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-i', '-', '-c:a', 'libvorbis', '-q:a', '7', '-y', dst],
                   input=out.astype('<f4').tobytes(), check=True)
    print(f'{dst}: {len(out) / SR:.2f} s, peak {min(peak, .98):.3f}')


if __name__ == '__main__':
    main()

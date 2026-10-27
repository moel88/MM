"""Lines each M1 hit's sound (kageno_m1_1..4) up with its motion's impact (2.38.2).

usage: python3 tools/kageno/recut_m1.py IN.ogg OUT.ogg
IN is a clip separate_audio.py cuts from the supplied video (or the 2.38.1 sound). Every hit of the combo connects
0.30 s after its cast (ComboSkill.HIT, the server's damage tick 6), but each clip's attack sat somewhere else in it
(0.11-0.30 s) and the sound was held back 0.40-0.50 s, so it was heard 0.3-0.45 s after the blade connected. The clip is
moved, never stretched or pitched: silence is added before it or the quiet lead-in before the attack is trimmed, so the
attack (where the sound first comes within 6 dB of its loudest) falls ATTACK seconds in. KagenoAudio plays it one
client tick after the cast arrives, on the tick the cast clock counts from, so the attack lands on the impact frame.
"""
import subprocess, sys
import numpy as np

SR = 48000
# Where the attack sits in the clip: the impact (0.30 s) less KagenoAudio's one-tick lead.
ATTACK = .25
FADE = .004


def read(path):
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'])
    return np.frombuffer(raw, dtype='<f4').reshape(-1, 2).copy()


def attack(x):
    """Seconds from the start to where the 5 ms loudness (smoothed over 15 ms) first comes within 6 dB of its peak."""
    mono = x.mean(1)
    win = round(.005 * SR); loud = np.sqrt(np.convolve(mono * mono, np.ones(win) / win, 'same'))
    win = round(.015 * SR); loud = np.convolve(loud, np.ones(win) / win, 'same')
    return int(np.argmax(loud > loud.max() * 10 ** (-6 / 20))) / SR


def main():
    src, dst = sys.argv[1], sys.argv[2]
    x = read(src)
    at = attack(x)
    shift = round((ATTACK - at) * SR)
    if shift >= 0:
        out = np.concatenate([np.zeros((shift, 2), np.float32), x])
    else:
        # Only the quiet lead-in goes; it fades in over a few milliseconds where it is cut, so no click.
        out = x[-shift:].copy()
        n = round(FADE * SR); out[:n] *= (.5 - .5 * np.cos(np.pi * np.arange(n) / n))[:, None]
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-i', '-', '-c:a', 'libvorbis', '-q:a', '7', '-y', dst],
                   input=out.astype('<f4').tobytes(), check=True)
    print(f'{dst}: attack {at:.3f} s -> {attack(read(dst)):.3f} s, {len(out) / SR:.3f} s long')


if __name__ == '__main__':
    main()

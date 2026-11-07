"""Lays the game's own sound under a film of Lee So-sang's H basic attack, 회풍만검 (2.95.0): as mux_h.py does under H's,
the sound run's recording lined up by H's start (its own sound found by its loudness, a tick after H began), and then
each 회풍만검 lined up by its own start too: the server begins one a tick sooner or later in one run than in the other
(as it hears of the press), so where they differ the recording round that one (from 0.15 s before it to 0.15 s after
its sound ends) is taken that many ticks sooner or later, faded in and out over 25 ms against the rest (under it is
H's flowing sound, which a tick's shift does not break).
usage: python3 -I tools/isosang/shots/mux_volley.py AUDIO_DIR FILM_DIR ENTRY "A-B" FILM.mp4 OUT.mp4

One stretch only (A..B of the entry's clock, seconds). Afterwards each 회풍만검's sound is found in the sound written (by
its loudness against isosang_volley.ogg's, as mux_h.onset) and its place printed against the tick after it began in the
film. MUX_KEEP_WAV=1 keeps the sound written beside OUT (OUT.wav), as mux_h.py does.
"""
import os
import subprocess
import sys

import numpy as np
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '../../stand/shots'))
from mux_h import onset  # noqa: E402
from mux_punches import SR, SOUNDS, decode, pling, find, earliest, rows  # noqa: E402

FORMATION, VOLLEY = '8', '10'  # IsosangRules.FORMATION, IsosangRules.VOLLEY
XFADE = .025


def main():
    audio, film_dir, entry, span, film, out = sys.argv[1:7]
    a, b = (float(x) for x in span.split('-'))
    rate, raw = wavfile.read(os.path.join(audio, 'game.wav'))
    wav = raw.astype(np.float64) / (32768.0 if raw.dtype == np.int16 else 1.0)
    wav = wav.mean(1) if wav.ndim > 1 else wav
    assert rate == SR, rate
    folder = os.path.join(audio, entry)
    ticks = rows(os.path.join(folder, 'ticks.csv'))
    marker = int(ticks[0]['nanos'])
    clock = np.array([float(r['clock']) for r in ticks[1:]])
    nanos = np.array([int(r['nanos']) for r in ticks[1:]], dtype=np.float64)
    order = np.argsort(clock)
    clock, nanos = clock[order], nanos[order]
    ref = pling()
    markers = [int(rows(os.path.join(audio, d, 'ticks.csv'))[0]['nanos']) for d in os.listdir(audio) if os.path.exists(os.path.join(audio, d, 'ticks.csv'))]
    at0, _ = earliest(wav, ref)
    guess = at0 + (marker - min(markers)) * SR / 1e9
    at, s = find(wav, ref, int(guess - .5 * SR), int(guess + .5 * SR + len(ref)))
    print(f'marker of {entry} at {at / SR:.3f} s in the recording (match {s:.2f})')

    def sample(tick):
        return at + (np.interp(tick, clock, nanos) - marker) * SR / 1e9

    def swings(where, combo):
        return [int(r['start']) for r in rows(os.path.join(where, 'isosang.csv')) if r['combo'] == combo]

    heard_h, shown_h = swings(folder, FORMATION), swings(os.path.join(film_dir, entry), FORMATION)
    heard_v, shown_v = swings(folder, VOLLEY), swings(os.path.join(film_dir, entry), VOLLEY)
    if not heard_h or not shown_h or len(heard_v) != len(shown_v):
        sys.exit(f'H in the runs: {heard_h} {shown_h}; 회풍만검: {heard_v} {shown_v}')
    shift = heard_h[0] - shown_h[0]
    # H's own sound a tick after H began (mux_h's lead)
    clip = decode(os.path.join(SOUNDS, 'isosang_h.ogg'))
    began = sample(heard_h[0])
    pos, score = onset(wav, clip, began)
    lead = pos - (began + SR / 20) if score > .8 else 0.0
    print(f'  H began on tick {heard_h[0]} (sound run) / {shown_h[0]} (film run); its sound {(pos - began) / SR * 1000:+.0f} ms after it by the clock '
          f'(match {score:.2f}): every piece moved {lead / SR * 1000:+.0f} ms')
    num, den = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=r_frame_rate', '-of', 'csv=p=0', film],
                              capture_output=True, check=True, text=True).stdout.strip().split('/')
    duration = float(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', film],
                                    capture_output=True, check=True, text=True).stdout.strip())
    frame = float(den) / float(num)
    n = int(round((b - a + frame) * SR))

    def piece(ticks_shift):
        lo = int(round(sample(a * 20 + ticks_shift) + lead))
        p = wav[lo:lo + n].copy()
        return np.concatenate([p, np.zeros(n - len(p))]) if len(p) < n else p

    track = piece(shift)
    vlen = len(decode(os.path.join(SOUNDS, 'isosang_volley.ogg'))) / SR
    t = a + np.arange(n) / SR
    for hv, sv in zip(heard_v, shown_v):
        own = hv - sv
        if own == shift:
            print(f'  회풍만검 on tick {sv} of the film ({hv} of the sound run): lined up with H')
            continue
        lo_t, hi_t = (sv - 3) / 20, (sv + 1) / 20 + vlen + .15
        w = np.clip(np.minimum((t - lo_t) / XFADE, (hi_t - t) / XFADE), 0, 1)
        track = track * (1 - w) + piece(own) * w
        print(f'  회풍만검 on tick {sv} of the film ({hv} of the sound run): its stretch {lo_t:.2f}-{hi_t:.2f} s taken {own - shift:+d} ticks from H\'s line')
    fade = int(.006 * SR)
    track[:fade] *= np.linspace(0, 1, fade)
    track[-fade:] *= np.linspace(1, 0, fade)
    track = np.concatenate([track, np.zeros(max(0, int(round(duration * SR)) - n))])[:int(round(duration * SR))]
    peak = np.abs(track).max()
    if peak > .99:
        track *= .99 / peak
    # each 회풍만검's sound in what was written, against a tick after it began in the film
    vclip = decode(os.path.join(SOUNDS, 'isosang_volley.ogg'))
    for sv in shown_v:
        want = ((sv + 1) / 20 - a) * SR
        got, sc = onset(track, vclip, want, before=.3, after=.3)
        print(f'  회풍만검 on tick {sv}: its sound at {got / SR + a:.3f} s of the entry, {(got - want) / SR * 1000:+.0f} ms from a tick after it began (match {sc:.2f})')
    tmp = out + '.wav'
    wavfile.write(tmp, SR, (track * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', film, '-i', tmp, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', out], check=True)
    if os.environ.get('MUX_KEEP_WAV') != '1':
        os.remove(tmp)
    print(f'{out}: {len(track) / SR:.2f} s of sound')


if __name__ == '__main__':
    main()

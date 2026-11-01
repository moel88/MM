"""Lays the game's own sound under a film of Lee So-sang's H (2.82.0), a film made of stretches of one entry's clock (the
formation is 41 s long: its start and its end are filmed, and a moment of it standing; SkillShots' save=).
usage: python3 -I tools/isosang/shots/mux_h.py AUDIO_DIR FILM_DIR ENTRY "A-B,C-D,..." FILM.mp4 OUT.mp4

AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same shot list in real time (SLOW 1,
a small window): game.wav is everything the game played; ENTRY/ticks.csv ties the entry's clock to System.nanoTime (and
holds when the marker tone before it was played), ENTRY/sounds.csv lists every sound started and ENTRY/isosang.csv every
swing of the player's and the tick it began on. FILM_DIR/ENTRY is the slowed film run's folder of the same entry. The
film shows the stretches A..B, C..D, ... of the entry's clock (seconds) one after another.

The game's sound under each stretch is the recording of the same stretch of the sound run, moved by the ticks H began
later or sooner in one run than in the other (the server starts it a tick sooner or later), so H's own sound, found in
the recording by its waveform, is where H began in the film. The pieces are joined with 6 ms fades; the sound written is
exactly as long as the film. (film.py shows both ends of a stretch: each piece is a frame longer than its stretch.)
"""
import os
import subprocess
import sys
import numpy as np
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '../../stand/shots'))
from mux_punches import SR, SOUNDS, decode, pling, find, earliest, rows  # noqa: E402

FORMATION = '8'


def main():
    audio, film_dir, entry, spans, film, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6]
    spans = [tuple(float(x) for x in s.split('-')) for s in spans.split(',')]
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
    at0, s0 = earliest(wav, ref)
    guess = at0 + (marker - min(markers)) * SR / 1e9
    at, s = find(wav, ref, int(guess - .5 * SR), int(guess + .5 * SR + len(ref)))
    print(f'marker of {entry} at {at / SR:.3f} s in the recording (match {s:.2f})')

    def sample(tick):
        """Where the sound run's entry clock {tick} (ticks, fractional) is in the recording."""
        return at + (np.interp(tick, clock, nanos) - marker) * SR / 1e9

    heard = [r for r in rows(os.path.join(folder, 'isosang.csv')) if r['combo'] == FORMATION]
    shown = [r for r in rows(os.path.join(film_dir, entry, 'isosang.csv')) if r['combo'] == FORMATION]
    if not heard or not shown:
        sys.exit(f'{entry}: no H in the sound run ({len(heard)}) or the film run ({len(shown)})')
    shift = int(heard[0]['start']) - int(shown[0]['start'])
    # H's sound in the sound run, found by its waveform near where the log puts it: the rule checked (it starts as its
    # client hears of H, a tick or so after H began)
    played = [r for r in rows(os.path.join(folder, 'sounds.csv')) if r['sound'] == 'celestialfall:isosang_h']
    if played:
        clip = decode(os.path.join(SOUNDS, 'isosang_h.ogg'))
        onset = int(np.argmax(np.abs(clip) > .02))
        piece = clip[onset:onset + int(.4 * SR)]
        near = at + (int(played[0]['nanos']) - marker) * SR / 1e9 + onset
        pos, score = find(wav, piece, int(near - .2 * SR), int(near + .2 * SR + len(piece)))
        began = sample(int(heard[0]['start']))
        print(f'  sound run: H began on tick {heard[0]["start"]}, its sound at {(pos - onset) / SR:.3f} s (match {score:.2f}), '
              f'{(pos - onset - began) / SR * 1000:+.0f} ms after it')
    print(f'  film run: H began on tick {shown[0]["start"]}; the sound run\'s clock moved by {shift:+d} ticks')
    duration = float(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', film],
                                    capture_output=True, check=True, text=True).stdout.strip())
    num, den = subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=r_frame_rate', '-of', 'csv=p=0', film],
                              capture_output=True, check=True, text=True).stdout.strip().split('/')
    # (film.py shows both ends of its stretch: a stretch is a frame longer than its seconds)
    frame = float(den) / float(num)
    length = int(round(duration * SR))
    track = np.zeros(length + SR)
    place = 0
    fade = int(.006 * SR)
    for a, b in spans:
        n = int(round((b - a + frame) * SR))
        lo = int(round(sample(a * 20 + shift)))
        piece = wav[lo:lo + n].copy()
        if len(piece) < n:
            piece = np.concatenate([piece, np.zeros(n - len(piece))])
        piece[:fade] *= np.linspace(0, 1, fade)
        piece[-fade:] *= np.linspace(1, 0, fade)
        track[place:place + n] += piece[:max(0, min(n, len(track) - place))]
        print(f'  {a:.2f}-{b:.2f} s of the entry: from the recording at {lo / SR:.3f} s, under the film at {place / SR:.3f} s')
        place += n
    track = track[:length]
    peak = np.abs(track).max()
    if peak > .99:
        track *= .99 / peak
    tmp = out + '.wav'
    wavfile.write(tmp, SR, (track * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', film, '-i', tmp, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', out], check=True)
    os.remove(tmp)
    print(f'{out}: {len(track) / SR:.2f} s of sound ({place / SR:.2f} s of stretches)')


if __name__ == '__main__':
    main()

"""Lays the game's own sound under a film of Star Platinum's heavy punch (R, 2.59.0), its sound where the game plays it.
usage: python3 tools/stand/shots/mux_heavy.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_rush.py, for the heavy punch: AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time (SLOW 1, a small window), FILM_DIR/ENTRY the slowed film run's
folder of the same entry; the film shows ENTRY's clock from START to END seconds (as given to film.py).

The heavy punch's two sounds (the user's two recordings, StandAttack) start StandAttack.HEAVY_LEAD (10) ticks into the
punch on each client, and the punch can reach the client a tick sooner or later in one run than in the other. So the
recording is laid at the punch: its voice is found in the recording by its own waveform near where the sound run's log
puts it, and goes where the film run's heavy punch started (+10 ticks); the rest of the entry's sound (the stand
coming out, the cries) comes with it as the sound run played it, a tick either way. The script checks the rule in the
sound run too: the voice against the tick HEAVY_LEAD ticks after its punch began there. The sound written is exactly
as long as the film.
"""
import math
import os
import subprocess
import sys
import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_punches import SR, SOUNDS, decode, pling, find, earliest, rows  # noqa: E402

HEAVY, LEAD = '5', 10
# what is laid: the punch's number in stand.csv, the ticks into it its sound starts, its voice's file and its sound event
# (tools/stand/shots/mux_finger.py lays the Star Finger with these)
SKILL = {'combo': HEAVY, 'lead': LEAD, 'file': 'star_platinum_heavy.ogg', 'sound': 'celestialfall:star_platinum_heavy', 'name': 'heavy punch'}


def start_tick(stand, combo=HEAVY):
    """
    The tick of the shot's clock the heavy punch started on, from the first row that shows it: the row's t is a whole
    tick and its age that many ticks in plus the frame's part of a tick (0 to 1), so t less the age is the start less
    that part, and the start is the whole tick at or just above it.
    """
    for r in stand:
        if r.get('combo') == combo and r.get('punch_age'):
            return math.ceil(float(r['t']) - float(r['punch_age']) * 20 - 1e-3)
    return None


def main(skill=SKILL):
    HEAVY, LEAD, name = skill['combo'], skill['lead'], skill['name']
    audio, film_dir, entry, start, end, film, out = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), sys.argv[6], sys.argv[7]
    rate, raw = wavfile.read(os.path.join(audio, 'game.wav'))
    wav = (raw.astype(np.float64) / (32768.0 if raw.dtype == np.int16 else 1.0))
    wav = wav.mean(1) if wav.ndim > 1 else wav
    assert rate == SR, rate
    folder = os.path.join(audio, entry)
    ticks = rows(os.path.join(folder, 'ticks.csv'))
    marker = int(ticks[0]['nanos'])
    tick_nanos = {round(float(r['clock'])): int(r['nanos']) for r in ticks[1:]}
    ref = pling()
    markers = []
    for d in os.listdir(audio):
        path = os.path.join(audio, d, 'ticks.csv')
        if os.path.exists(path):
            markers.append(int(rows(path)[0]['nanos']))
    at0, s0 = earliest(wav, ref)
    guess = at0 + (marker - min(markers)) * SR / 1e9
    at, s = find(wav, ref, int(guess - .5 * SR), int(guess + .5 * SR + len(ref)))
    print(f'marker of {entry} at {at / SR:.3f} s in the recording (match {s:.2f}; the first at {at0 / SR:.3f}, match {s0:.2f})')

    def sample(nanos):
        return at + (nanos - marker) * SR / 1e9

    # the heavy punch's voice in the sound run, found by its own waveform near where the log puts it
    voice = decode(os.path.join(SOUNDS, skill['file']))
    onset = int(np.argmax(np.abs(voice) > .02))
    clip = voice[onset:onset + int(.3 * SR)]
    played = [r for r in rows(os.path.join(folder, 'sounds.csv')) if r['sound'] == skill['sound']]
    if not played:
        sys.exit(f'{entry}: the sound run played no {name}')
    near = sample(int(played[0]['nanos'])) + onset
    pos, score = find(wav, clip, int(near - .15 * SR), int(near + .15 * SR + len(clip)))
    pos -= onset
    rec_start = start_tick(rows(os.path.join(folder, 'stand.csv')), HEAVY)
    nanos = tick_nanos.get(rec_start + LEAD) if rec_start is not None else None
    if nanos is not None:
        print(f'  sound run: the voice at {pos / SR:.3f} s (match {score:.2f}); its punch started on tick {rec_start}, '
              f'tick {rec_start + LEAD} began {(pos - sample(nanos)) / SR * 1000:+.0f} ms from it')
    film_start = start_tick(rows(os.path.join(film_dir, entry, 'stand.csv')), HEAVY)
    if film_start is None:
        sys.exit(f'{entry}: the film run shows no {name}')
    # film time f is recording time f + offset
    offset = pos / SR - ((film_start + LEAD) / 20 - start)
    duration = float(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', film],
                                    capture_output=True, check=True, text=True).stdout.strip())
    length = int(round(duration * SR))
    track = np.zeros(length)
    a = int(round(offset * SR))
    lo, hi = max(0, a), min(len(wav), a + length)
    if hi > lo:
        track[lo - a:hi - a] = wav[lo:hi]
    fade = int(.008 * SR)
    track[:fade] *= np.linspace(0, 1, fade)
    track[-fade:] *= np.linspace(1, 0, fade)
    print(f'  the {name} started on tick {film_start} in the film run ({rec_start} in the sound run): the recording from '
          f'{offset:.3f} s lies under the film from 0 s, the voice at {((film_start + LEAD) / 20 - start):.3f} s')
    peak = np.abs(track).max()
    if peak > .99:
        track *= .99 / peak
    tmp = out + '.wav'
    wavfile.write(tmp, SR, (track * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', film, '-i', tmp, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', out], check=True)
    os.remove(tmp)
    print(f'{out}: {len(track) / SR:.2f} s of sound')


if __name__ == '__main__':
    main()

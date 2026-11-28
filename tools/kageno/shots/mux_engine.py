"""Lays the game's own sound under a film of the combat engine's two rules (2.157.0): the evasive and the perfect guard.
usage: python3 -I tools/kageno/shots/mux_engine.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4 KIND

AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same shot list in real time (a small
window), FILM_DIR the slowed film run; ENTRY the entry (NAME.k: the k-th entry called NAME), shown in FILM.mp4 from its
clock's START to END seconds (as given to film.py). KIND says which moment holds the two runs together (the dummy's
blows need not fall on the same ticks in the two runs, so the recording is laid by the moment itself):
  evade    the evasive as the player's client began it (its sound, the illusioner's mirror move, plays then: EngineClient)
  perfect  the perfect guard as the player's client heard of it (its ding, the arrow's hit, plays then: DefenseFx)
Each run's log (the film tool's: 'evaded at TICK (client world W, ...)', 'perfect guard at TICK (heard N ticks ...)') gives
the moment on the shot's clock; the sound run's sounds.csv the moment its sound started, mapped into the recording by
the entry's marker (as tools/stand/shots/mux_heavy.py). The sound run also tells how far the sound starts from the
logged tick (its ticks.csv), and the film's moment is moved by as much. The rest of the entry's sound comes with it as
the sound run played it (a tick either way). The sound written is exactly as long as the film.
"""
import os
import re
import subprocess
import sys
import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_punches import SR, pling, find, earliest, rows  # noqa: E402

KINDS = {
    # the moment's log line (its age, and how many ticks before the line it happened) and its sound
    'evade': (r'evaded at (-?\d+) \(client world', 'minecraft:entity.illusioner.mirror_move'),
    'perfect': (r'perfect guard at (-?\d+) \(heard', 'minecraft:entity.arrow.hit_player'),
}


def moment(path, entry, kind):
    """The shot-clock tick of the entry's first such moment, from a run's log (None if there is none)."""
    pattern, _ = KINDS[kind]
    name, _, k = entry.partition('.')
    with open(path, encoding='utf8') as fh:
        parts = re.split(r'(?m)^%s at \d+$' % re.escape(name), fh.read())
    part = parts[int(k) if k else 1]
    m = re.search(pattern, part)
    if m is None:
        return None
    # (the line is written a tick or so after the moment, and gives the moment's own tick)
    return int(m.group(1))


def main():
    if len(sys.argv) < 9 or sys.argv[8] not in KINDS:
        sys.exit(__doc__)
    audio, film_dir, entry, start, end, film, out, kind = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), sys.argv[6], sys.argv[7], sys.argv[8]
    _, sound = KINDS[kind]
    rate, raw = wavfile.read(os.path.join(audio, 'game.wav'))
    wav = raw.astype(np.float64) / (32768.0 if raw.dtype == np.int16 else 1.0)
    wav = wav.mean(1) if wav.ndim > 1 else wav
    assert rate == SR, rate
    folder = os.path.join(audio, entry)
    ticks = rows(os.path.join(folder, 'ticks.csv'))
    marker = int(ticks[0]['nanos'])
    tick_nanos = {round(float(r['clock'])): int(r['nanos']) for r in ticks[1:]}
    ref = pling()
    markers = [int(rows(os.path.join(audio, d, 'ticks.csv'))[0]['nanos']) for d in os.listdir(audio) if os.path.exists(os.path.join(audio, d, 'ticks.csv'))]
    at0, s0 = earliest(wav, ref)
    guess = at0 + (marker - min(markers)) * SR / 1e9
    at, s = find(wav, ref, int(guess - .5 * SR), int(guess + .5 * SR + len(ref)))
    print(f'marker of {entry} at {at / SR:.3f} s in the recording (match {s:.2f}; the first at {at0 / SR:.3f}, match {s0:.2f})')

    def sample(nanos):
        return at + (nanos - marker) * SR / 1e9

    rec = moment(os.path.join(audio, 'log.txt'), entry, kind)
    if rec is None or rec not in tick_nanos:
        sys.exit(f'{entry}: the sound run has no {kind} on its clock')
    # the moment's sound in the sound run: the first that started from two ticks before it on
    around = tick_nanos[rec]
    played = [int(r['nanos']) for r in rows(os.path.join(folder, 'sounds.csv')) if r['sound'] == sound and int(r['nanos']) >= around - 100_000_000]
    if not played:
        sys.exit(f'{entry}: the sound run played no {sound} at its {kind}')
    pos = sample(played[0])
    late = (played[0] - around) / 1e9
    print(f'  sound run: the {kind} on tick {rec}, its sound {late * 1000:+.0f} ms from that tick, at {pos / SR:.3f} s of the recording')
    film_at = moment(os.path.join(film_dir, 'log.txt'), entry, kind)
    if film_at is None:
        sys.exit(f'{entry}: the film run shows no {kind}')
    # film time f is recording time f + offset: the film's moment, as late from its tick as the sound run's was
    when = film_at / 20 + late - start
    offset = pos / SR - when
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
    print(f'  the {kind} on tick {film_at} in the film run ({rec} in the sound run): its sound at {when:.3f} s of the film')
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

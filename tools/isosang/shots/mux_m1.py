"""Lays the game's own sound under a film of Lee So-sang's basic attack (2.77.0), each hit's sound where the game plays it.
usage: python3 -I tools/isosang/shots/mux_m1.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same shot list in real time (SLOW 1,
a small window): game.wav is everything the game played through OpenAL; ENTRY/ticks.csv ties that entry's clock to
System.nanoTime (and holds when the marker tone before it was played), ENTRY/sounds.csv lists every sound the game
started and ENTRY/isosang.csv every hit of the player's and the tick it began on. FILM_DIR/ENTRY is the slowed film
run's folder of the same entry (its own isosang.csv). The film shows ENTRY's clock from START to END seconds (as given
to film.py).

Each hit's sound starts on every client as it hears of the hit, the tick its swing's clock starts there too (its
whooshes cut to land on the blows from then, client/isosang/IsosangClient), and a hit reaches the client a tick sooner or
later in one run than in the other. So the recording is laid hit by hit: each hit's sound is found in the recording by
its own waveform near where the sound run's log puts it, and that hit's stretch of the recording (its sound, the blade
biting whom it struck, up to the next hit's sound) goes where the same hit began in the film run, as far after it as the
sound run's sounds came after theirs (their median). The script checks the rule in the sound run too: each hit's sound
against the tick its hit began on there. The sound written is exactly as long as the film.
"""
import os
import subprocess
import sys
import numpy as np
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '../../stand/shots'))
from mux_punches import SR, SOUNDS, decode, pling, find, earliest, rows  # noqa: E402


def main():
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

    # every hit's sound the game played in the sound run, found by its own waveform near where the log puts it
    played = [r for r in rows(os.path.join(folder, 'sounds.csv')) if r['sound'].startswith('celestialfall:isosang_m1_') and not r['sound'].endswith('_hit')]
    # (2.80.0: E's flash is a swing of its own, number 0, with no sound: left out of both)
    heard = [r for r in rows(os.path.join(folder, 'isosang.csv')) if r['combo'] != '0']
    shown = [r for r in rows(os.path.join(film_dir, entry, 'isosang.csv')) if r['combo'] != '0']
    anchors = []
    for r in played:
        combo = int(r['sound'][-1])
        clip = decode(os.path.join(SOUNDS, f'isosang_m1_{combo}.ogg'))
        onset = int(np.argmax(np.abs(clip) > .02))
        piece = clip[onset:onset + int(.3 * SR)]
        near = sample(int(r['nanos'])) + onset
        pos, score = find(wav, piece, int(near - .15 * SR), int(near + .15 * SR + len(piece)))
        anchors.append((combo, pos - onset, score, len(clip)))
    # each of them against the hit it belongs to in the sound run (in order), and the rule checked: a hit's sound starts
    # on the tick its hit began (the ticks' times are the frames' a tick was shown in, so the delays scatter within a
    # tick; their median is how far into its hit the game started each sound)
    delays = []
    for k, (combo, pos, score, _) in enumerate(anchors):
        hit = heard[k] if k < len(heard) else None
        if hit is None or int(hit['combo']) != combo:
            sys.exit(f'{entry}: hit {k + 1} of the sound run is not hit {combo}: {heard}')
        nanos = tick_nanos.get(int(hit['start']))
        line = f'  sound run: hit {combo}\'s sound at {pos / SR:.3f} s (match {score:.2f}), its hit began on tick {hit["start"]}'
        if nanos is not None:
            delays.append(pos - sample(nanos))
        print(line + (f', {(pos - sample(nanos)) / SR * 1000:+.0f} ms from it' if nanos is not None else ''))
    delay = float(np.median(delays)) if delays else 0.0
    print(f'  each hit\'s sound goes {delay / SR * 1000:+.0f} ms after its hit began in the film run')
    duration = float(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', film],
                                    capture_output=True, check=True, text=True).stdout.strip())
    length = int(round(duration * SR))
    track = np.zeros(length + SR)
    for k, (combo, pos, score, size) in enumerate(anchors):
        if k >= len(shown) or int(shown[k]['combo']) != combo:
            print(f'  hit {combo}: no hit {combo} in the film run at that place, left out')
            continue
        stop = anchors[k + 1][1] if k + 1 < len(anchors) else pos + max(int(1.6 * SR), size + int(.25 * SR))
        piece = wav[int(pos):int(stop)].copy()
        fade = int(.006 * SR)
        piece[:fade] *= np.linspace(0, 1, fade)
        piece[-fade:] *= np.linspace(1, 0, fade)
        place = int(round((int(shown[k]['start']) / 20 - start) * SR + delay))
        if place >= length or place + len(piece) <= 0:
            continue
        a, b = max(0, place), min(len(track), place + len(piece))
        track[a:b] += piece[a - place:b - place]
        print(f'  hit {combo}: from the recording at {pos / SR:.3f} s, under the film at {place / SR:.3f} s (hit on tick {shown[k]["start"]})')
    track = track[:length]
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

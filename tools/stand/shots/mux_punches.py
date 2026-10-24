"""Lays the game's own sound under a film of Star Platinum's punches (2.54.0), each punch's shout where the game plays it.
usage: python3 tools/stand/shots/mux_punches.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same shot list in real time (SLOW 1,
a small window): game.wav is everything the game played through OpenAL; ENTRY/ticks.csv ties that entry's clock to
System.nanoTime (and holds when the marker tone before it was played) and ENTRY/sounds.csv lists every sound the game
started. FILM_DIR/ENTRY is the slowed film run's folder of the same entry: its stand.csv says on which tick of the
shot's clock each punch started on the client (t less punch_ticks). The film shows ENTRY's clock from START to END
seconds (as given to film.py).

The two runs are the same shot, but a punch reaches the client a tick sooner or later in one than in the other (the
server's and the client's ticks are not in step), and in the game each punch's shout runs on that punch's own clock:
it starts StandAttack.LEAD (1) tick after the punch did, its "RA" on the punch's contact. So the recording is laid
punch by punch: each punch's stretch of it starts a tick after the punch did and runs to the next punch's (its shout
and its blow's sound, as the game played them) and goes where that punch started (+1 tick) in the film run. A punch
that shouts (since 2.56.0 only the hook and the finisher, StarPlatinumRules.shouts) is found in the recording by its
shout's own waveform (the clip in the mod's sounds), so its "RA" lands exactly; one that does not (the jab and the
cross) is placed by the time its tick began, less how early the found shouts came against theirs. The script checks
the rule in the sound run too: for every shout, the tick it started on against its punch's start there (+1). The recording is tied to the run's clock by the marker tones: every
entry plays the same one, so the first entry's is the earliest in the recording (not merely the best match). The sound
written is exactly as long as the film, so films joined end to end keep their sound in step.
"""
import csv
import json
import os
import subprocess
import sys
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
LEAD = 1
SOUNDS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../src/main/resources/assets/celestialfall/sounds')


def decode(path, rate=SR):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(rate), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)


def pling():
    """The marker as the game plays it: the note block's pling at pitch 2 (twice as fast and an octave up)."""
    index = json.load(open(os.path.expanduser('~/.gradle/caches/fabric-loom/assets/indexes/1.20.1-5.json')))['objects']
    h = index['minecraft/sounds/note/pling.ogg']['hash']
    x = decode(os.path.expanduser(f'~/.gradle/caches/fabric-loom/assets/objects/{h[:2]}/{h}'), SR // 2)
    return x / (np.abs(x).max() + 1e-9)


def scores(seg, ref):
    """How well ref matches seg starting at each sample: normalised cross-correlation."""
    c = signal.fftconvolve(seg, ref[::-1], 'valid')
    run = np.concatenate([[0.0], np.cumsum(seg ** 2)])
    # (silence is floored at an RMS of 0.001, so a match in it cannot blow up)
    energy = np.sqrt(np.maximum(run[len(ref):] - run[:-len(ref)], len(ref) * 1e-6)) * np.sqrt(np.sum(ref ** 2))
    return c / energy


def find(wav, ref, lo, hi):
    """Where ref starts in wav[lo:hi] (samples), by normalised cross-correlation, and how well it matches."""
    lo, hi = max(0, lo), min(len(wav), hi)
    score = scores(wav[lo:hi], ref)
    k = int(np.argmax(score))
    return lo + k, float(score[k])


def earliest(wav, ref):
    """Where ref first starts in wav: the earliest match nearly as good as the best (every entry plays the same marker,
    so the best one need not be the first)."""
    score = scores(wav, ref)
    k = int(np.argmax(score > .9 * score.max()))
    k += int(np.argmax(score[k:k + SR // 10]))
    return k, float(score[k])


def rows(path):
    with open(path) as fh:
        return list(csv.DictReader(fh))


def starts(stand):
    """Each punch's start on the shot's clock (ticks), in order: t less punch_ticks where a new punch first shows."""
    out, last = [], None
    for r in stand:
        if not r.get('combo') or not r.get('punch_ticks'):
            continue
        start = round(float(r['t'])) - int(r['punch_ticks'])
        key = (r['combo'], start)
        if key != last and (not out or start > out[-1][1]):
            out.append((int(r['combo']), start))
        last = key
    return out


def main():
    audio, film_dir, entry, start, end, film, out = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), sys.argv[6], sys.argv[7]
    rate, raw = wavfile.read(os.path.join(audio, 'game.wav'))
    wav = (raw.astype(np.float64) / (32768.0 if raw.dtype == np.int16 else 1.0))
    wav = wav.mean(1) if wav.ndim > 1 else wav
    assert rate == SR, rate
    folder = os.path.join(audio, entry)
    with open(os.path.join(folder, 'ticks.csv')) as fh:
        lines = list(csv.reader(fh))[1:]
    marker = int(lines[0][1])
    tick_nanos = {round(float(c)): int(n) for c, n in lines[1:]}
    # the marker: the first one in the recording (of the entry run first) ties nanoTime to it, then this entry's own close by
    ref = pling()
    markers = []
    for d in os.listdir(audio):
        path = os.path.join(audio, d, 'ticks.csv')
        if os.path.exists(path):
            with open(path) as fh:
                markers.append(int(list(csv.reader(fh))[1][1]))
    first_marker = min(markers)
    at0, s0 = earliest(wav, ref)
    guess = at0 + (marker - first_marker) * SR / 1e9
    at, s = find(wav, ref, int(guess - .5 * SR), int(guess + .5 * SR + len(ref)))
    print(f'marker of {entry} at {at / SR:.3f} s in the recording (match {s:.2f}; the first at {at0 / SR:.3f}, match {s0:.2f})')

    def sample(nanos):
        return at + (nanos - marker) * SR / 1e9

    # every shout the game played in the sound run, found by its own waveform near where its start time puts it
    shouts = []
    for r in rows(os.path.join(folder, 'sounds.csv')):
        sid = r['sound']
        if not sid.startswith('celestialfall:star_platinum_ora_'):
            continue
        k = int(sid[-1])
        clip = decode(os.path.join(SOUNDS, f'star_platinum_ora_{k}.ogg'))
        first_sound = int(np.argmax(np.abs(clip) > .02))
        ref_clip = clip[first_sound:first_sound + int(.3 * SR)]
        near = sample(int(r['nanos'])) + first_sound
        pos, score = find(wav, ref_clip, int(near - .15 * SR), int(near + .15 * SR + len(ref_clip)))
        shouts.append((k, pos - first_sound, score, float(r['clock'])))
    # each punch of the sound run, where its stretch of the recording begins: its shout where it shouts, the time its
    # tick began otherwise (less how far the shouts came from theirs); and the rule, checked: a shout a tick in
    rec = starts(rows(os.path.join(folder, 'stand.csv')))
    anchors, found, early = [], [], []
    for combo, st in rec:
        nanos = tick_nanos.get(st + LEAD)
        tick = sample(nanos) if nanos is not None else None
        mine = [sh for sh in shouts if sh[0] == combo and (tick is None or abs(sh[1] - tick) < .15 * SR)]
        if mine:
            k, pos, score, clock = min(mine, key=lambda sh: abs(sh[1] - tick) if tick is not None else 0)
            anchors.append(pos)
            found.append(True)
            if tick is not None:
                early.append(pos - tick)
                print(f'  sound run: shout {k} found at {pos / SR:.3f} s (match {score:.2f}); its punch started on tick {st}, '
                      f'tick {st + LEAD} began {(pos - tick) / SR * 1000:+.0f} ms from it')
        else:
            anchors.append(tick)
            found.append(False)
            print(f'  sound run: punch {combo} (tick {st}) shouts nothing' + ('' if tick is not None else ', and its tick has no time'))
    shift = float(np.median(early)) if early else 0.0
    anchors = [a if a is None or f else a + shift for a, f in zip(anchors, found)]
    films = starts(rows(os.path.join(film_dir, entry, 'stand.csv')))
    print('  film run punches start on ticks', films, '; sound run on', rec, f'; unshouted punches moved {shift / SR * 1000:+.0f} ms')
    # the sound runs exactly as long as the film (its last frame shown for its full 1/60 s), so films joined end to end
    # keep their sound in step
    duration = float(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=duration', '-of', 'csv=p=0', film],
                                    capture_output=True, check=True, text=True).stdout.strip())
    length = int(round(duration * SR))
    track = np.zeros(length + SR)
    for i, ((combo, st), pos) in enumerate(zip(rec, anchors)):
        if pos is None:
            continue
        if i >= len(films) or films[i][0] != combo:
            print(f'  punch {combo}: no punch {combo} in the film run at that place, left out')
            continue
        nxt = next((a for a in anchors[i + 1:] if a is not None), None)
        stop = nxt if nxt is not None else pos + int(1.6 * SR)
        piece = wav[int(pos):int(stop)].copy()
        fade = int(.008 * SR)
        piece[:fade] *= np.linspace(0, 1, fade)
        piece[-fade:] *= np.linspace(1, 0, fade)
        place = int(round(((films[i][1] + LEAD) / 20 - start) * SR))
        if place >= length:
            continue
        a, b = max(0, place), min(len(track), place + len(piece))
        track[a:b] += piece[a - place:b - place]
        print(f'  punch {combo}: from the recording at {pos / SR:.3f} s, under the film at {place / SR:.3f} s (punch on tick {films[i][1]})')
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

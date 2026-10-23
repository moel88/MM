"""Lays the game's own sound under a meteor or shop film (2.49.0).
usage: python3 tools/meteor/shots/mux.py AUDIO_DIR ENTRY START END FILM.mp4 OUT.mp4

AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same shot list, in real time
(SLOW 1, a small window so the software renderer keeps up): game.wav is everything the game played through OpenAL,
and for each entry ENTRY/ticks.csv ties its clock to System.nanoTime, with the moment a marker tone (a note block's
pling at double pitch) was played before the entry began. The film shows ENTRY's clock from START to END seconds (as
given to film.py).

The first marker is found in the recording by its sound, which ties System.nanoTime to the recording; each entry's own
marker is then looked for close to where that puts it (so any drift is taken out entry by entry). The stretch of the
recording that ran while the entry's clock went from START to END is cut out, and evened to the film's length if the
real-time run's clock lagged a little (it reports by how much), and laid under the film.
"""
import csv
import glob
import json
import os
import subprocess
import sys
import numpy as np
from scipy import signal
from scipy.io import wavfile

audio, entry, start, end, film, out = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4]), sys.argv[5], sys.argv[6]
SR = 48000


def decode(path, rate=SR):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(rate), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64)


def pling():
    """The marker as the game plays it: the note block's pling at pitch 2 (twice as fast and an octave up)."""
    index = json.load(open(os.path.expanduser('~/.gradle/caches/fabric-loom/assets/indexes/1.20.1-5.json')))['objects']
    h = index['minecraft/sounds/note/pling.ogg']['hash']
    x = decode(os.path.expanduser(f'~/.gradle/caches/fabric-loom/assets/objects/{h[:2]}/{h}'), SR // 2)
    return x / (np.abs(x).max() + 1e-9)


def find(wav, ref, lo, hi):
    """Where {ref} starts in wav[lo:hi] (samples), by normalised cross-correlation; and how strong the match is."""
    lo, hi = max(0, lo), min(len(wav), hi)
    seg = wav[lo:hi]
    c = signal.fftconvolve(seg, ref[::-1], 'valid')
    energy = np.sqrt(np.convolve(seg ** 2, np.ones(len(ref)), 'valid')) * np.sqrt(np.sum(ref ** 2)) + 1e-12
    score = c / energy
    k = int(np.argmax(score))
    return lo + k, float(score[k])


def ticks(folder):
    with open(os.path.join(folder, 'ticks.csv')) as fh:
        rows = list(csv.reader(fh))[1:]
    marker = int(rows[0][1])
    return marker, [(float(c), int(n)) for c, n in rows[1:]]


rate, raw = wavfile.read(os.path.join(audio, 'game.wav'))
pcm = raw.astype(np.float64) / (32768.0 if raw.dtype == np.int16 else 1.0)
mono = pcm.mean(axis=1) if pcm.ndim > 1 else pcm
assert rate == SR, rate
ref = pling()

# the first entry's marker, found anywhere in the recording, ties nanoTime to it
entries = sorted((ticks(f)[0], f) for f in (os.path.dirname(p) for p in glob.glob(os.path.join(audio, '*', 'ticks.csv'))))
first_nanos, _ = entries[0]
at, strength = find(mono, ref, 0, len(mono))
print(f'first marker at {at / SR:.3f} s of the recording (match {strength:.2f})')
marker, rows = ticks(os.path.join(audio, entry))
guess = at + int(round((marker - first_nanos) / 1e9 * SR))
mine, strength2 = find(mono, ref, guess - SR, guess + SR + len(ref))
print(f'{entry} marker at {mine / SR:.3f} s (expected {guess / SR:.3f}, match {strength2:.2f})')


def wav_at(clock):
    """Where in the recording the entry's clock read {clock} ticks."""
    cs = [c for c, _ in rows]
    ns = [n for _, n in rows]
    n = np.interp(clock, cs, ns)
    return mine + (n - marker) / 1e9 * SR


a, b = wav_at(start * 20), wav_at(end * 20)
length = end - start
got = (b - a) / SR
tempo = got / length
print(f'clock {start:.2f}-{end:.2f} s is {a / SR:.3f}-{b / SR:.3f} s of the recording ({got:.3f} s for {length:.3f} s of film, tempo {tempo:.4f})')
if abs(tempo - 1) > .08:
    sys.exit(f'the real-time run kept too far from real time ({tempo:.3f}); record it again with a smaller window')
cut = pcm[int(a):int(b)]
tmp = out + '.wav'
wavfile.write(tmp, SR, (np.clip(cut, -1, 1) * 32767).astype(np.int16))
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', film, '-i', tmp, '-filter_complex', f'[1:a]atempo={tempo:.5f},apad[s]',
                '-map', '0:v', '-map', '[s]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', out], check=True)
os.remove(tmp)
print('wrote', out)

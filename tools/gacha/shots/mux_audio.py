"""Lays the game's own sound under a gacha film (2.40.0).
usage: python3 tools/gacha/shots/mux_audio.py AUDIO_DIR VIDEO_LOG ENTRY START END FILM.mp4 OUT.mp4

AUDIO_DIR is a run of run_gacha_shots.py with GACHA_SHOTS_AUDIO=1: game.wav is everything OpenAL played, ticks.txt
ties each entry's clock (ticks) to System.nanoTime and holds the moment the marker tone was played. VIDEO_LOG is the
film run's log.txt. The film shows ENTRY's clock from START to END seconds (as given to film.py).
The recording is placed roughly by the marker tone, then exactly by the gacha sounds themselves: the sound run logs
each one as it starts (cue ENTRY CUE PITCH CLOCK NANOS), each is found in the recording near where the marker puts it,
and the median of (where it was found - its clock) ties the clock to the recording. The scene's opening sound is left
out: it plays with the server's answer, handled before the frame's ticks, when the clock can read a tick low. A run
without that log falls back to the opening sound (or for MENU the details panel's, played on the click). The films'
clock follows the sound run's from the scene start (or the click) that both runs logged.
"""
import re
import subprocess
import sys
import numpy as np
from scipy import signal
from scipy.io import wavfile


def scene_at(log, entry):
    """The last logged scene start of ENTRY (a retried entry logs once per try)."""
    found = re.findall(rf'(?m)^  scene {re.escape(entry)} ([0-9.]+) ([0-9.]+)$', log)
    return float(found[-1][0]) if found else None


def click_at(log, entry, shape):
    block = re.split(rf'(?m)^{re.escape(entry)} at ', log)[-1]
    m = re.findall(rf'click {shape} at \S+ t=([0-9.]+)', block)
    return float(m[0]) if m else None


def locate(wav, sr, cue, near, pitch=1.0, reach=.6, least=.3):
    """Where the sound {cue} (assets/celestialfall/sounds/gacha/CUE.ogg, played at {pitch}) starts in the recording,
    within {reach} seconds of {near}; None when it is not clearly there (a match under {least})."""
    import os
    path = os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/celestialfall/sounds/gacha', cue + '.ogg')
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(sr), '-'], capture_output=True).stdout
    ref = np.frombuffer(raw, np.float32)
    if pitch != 1.0:
        ref = signal.resample(ref, int(len(ref) / pitch)).astype(np.float32)
    ref = ref[:int(.5 * sr)]
    lo, hi = max(0, int((near - reach) * sr)), min(len(wav), int((near + reach) * sr) + len(ref))
    seg = wav[lo:hi].mean(1)
    if len(seg) <= len(ref):
        return None
    corr = signal.fftconvolve(seg, ref[::-1], 'valid')
    norm = np.sqrt(np.maximum(signal.fftconvolve(seg ** 2, np.ones(len(ref)), 'valid'), 0)) * np.sqrt(np.sum(ref ** 2)) + 1e-9
    score = np.nan_to_num(corr / norm)
    k = int(np.argmax(score))
    return (lo + k) / sr if score[k] > least else None


# the sound files of the cues whose names differ from them
FILES = {'SSR_FANFARE': 'fanfare'}


def cue_offset(wav, sr, ticks, entry, marker_wav, marker_nano):
    """The sound run's clock against the recording from the gacha sounds it logged: (offset s, sounds used, spread s),
    or None without a log."""
    rows = [l.split() for l in ticks if l.startswith(f'cue {entry} ')]
    if not rows:
        return None
    offsets = []
    for _, _, cue, pitch, clock, nanos in rows:
        if cue.endswith('_START'):
            continue
        near = marker_wav + (int(nanos) - marker_nano) / 1e9
        found = locate(wav, sr, FILES.get(cue, cue.lower()), near, float(pitch), reach=.25, least=.5)
        if found is not None:
            offsets.append(found - float(clock) / 20)
    if not offsets:
        return None
    off = float(np.median(offsets))
    return off, len(offsets), float(np.median(np.abs(np.array(offsets) - off)))


def main():
    audio_dir, video_log, entry, start, end, film, out = sys.argv[1:8]
    start, end = float(start), float(end)
    sr, wav = wavfile.read(f'{audio_dir}/game.wav')
    wav = wav.astype(np.float32)
    if wav.ndim == 1:
        wav = np.stack([wav, wav], 1)
    if np.abs(wav).max() > 2:
        wav /= 32768.0
    ticks = open(f'{audio_dir}/ticks.txt').read().split('\n')
    marker_nano = next(int(l.split()[2]) for l in ticks if l.startswith('marker '))
    rows = [(float(l.split()[1]), int(l.split()[2])) for l in ticks if l.startswith(entry + ' ')]
    if not rows:
        sys.exit(f'no ticks for {entry}')
    # the marker tone in the recording: a matched filter on the first channel, from the start to two minutes in
    n = int(.4 * sr)
    t = np.arange(n) / sr
    tone = np.sin(2 * np.pi * 1000 * t) * np.minimum(1, np.minimum(t / .005, (.4 - t) / .005))
    head = wav[:min(len(wav), int(240 * sr)), 0]
    corr = signal.fftconvolve(head, tone[::-1], 'valid')
    env = np.abs(signal.hilbert(corr))
    at = int(np.argmax(env))
    marker_wav = at / sr
    # the entry's clock against the recording: wav time = marker + (nanos - marker nanos); the offset for clock c
    clock = np.array([r[0] for r in rows])
    nanos = np.array([r[1] for r in rows], dtype=np.float64)
    wav_t = marker_wav + (nanos - marker_nano) / 1e9
    inside = (clock >= start * 20 - 20) & (clock <= end * 20 + 20)
    offsets = wav_t[inside] - clock[inside] / 20
    off = float(np.median(offsets))
    drift = float(np.max(np.abs(offsets - off))) if len(offsets) else 0.0
    # where the films' clock meets the sound run's: the scene start, or for MENU the click on the details
    audio_log, film_log = open(f'{audio_dir}/log.txt').read(), open(video_log).read()
    a, v = scene_at(audio_log, entry), scene_at(film_log, entry)
    if a is not None:
        cue, at_audio, at_film = ('ten_start' if entry.startswith('TEN') else 'one_start'), a, v
    else:
        cue = 'panel'
        at_audio, at_film = click_at(audio_log, entry, 'detailButton'), click_at(film_log, entry, 'detailButton')
    shift = (at_audio - at_film) / 20 if at_audio is not None and at_film is not None else 0.0
    # the sound run's clock against the recording: from the sounds it logged, else from one sound at a known moment
    logged = cue_offset(wav, sr, ticks, entry, marker_wav, marker_nano)
    if logged is not None:
        anchored, used, spread = logged
        how = f'{used} logged sounds (spread {spread * 1000:.0f} ms)'
    else:
        found = locate(wav, sr, cue, off + at_audio / 20)
        anchored = found - at_audio / 20 if found is not None else off
        how = f'{cue} found at {found if found is None else round(found, 3)} s'
    begin = start + anchored + shift
    print(f'{entry}: marker at {marker_wav:.3f} s, marker offset {off:.3f} s (spread {drift * 1000:.0f} ms), {how} '
          f'({(anchored - off) * 1000:+.0f} ms from the marker), sound from {begin:.3f} s')
    dur = end - start
    seg = wav[max(0, int(begin * sr)):max(0, int(begin * sr)) + int(dur * sr)]
    if begin < 0:
        seg = np.concatenate([np.zeros((int(-begin * sr), 2), np.float32), seg])
    seg = seg[:int(dur * sr)]
    tmp = out + '.wav'
    wavfile.write(tmp, sr, np.clip(seg, -1, 1).astype(np.float32))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', film, '-i', tmp, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
                    '-shortest', out], check=True)
    import os
    os.remove(tmp)


if __name__ == '__main__':
    main()

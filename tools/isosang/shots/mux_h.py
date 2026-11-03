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
the recording by its loudness, is where H began in the film. The pieces are joined with 6 ms fades; the sound written is
exactly as long as the film. (film.py shows both ends of a stretch: each piece is a frame longer than its stretch.)

(2.82.17: the entry clock's times in ticks.csv put the recording some .25 s late, the sounds in it that much ahead of
their ticks, H's own and its end's alike. H's sound is now found by its loudness envelope against isosang_h.ogg's,
log against log, within .6 s of where the clock puts H's start, its waveform matching too poorly under the other sounds;
every piece is moved by how far that is from a tick after H began (the client plays it as it hears of H), MUX_LEAD=0
leaves them where the clock puts them.)
"""
import os
import subprocess
import sys
import numpy as np
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '../../stand/shots'))
from mux_punches import SR, SOUNDS, decode, pling, find, earliest, rows  # noqa: E402

FORMATION = '8'  # H's swing (IsosangRules.FORMATION)


def envelope(x, w=240):
    """The loudness of {x}, every {w} samples (5 ms at 48 kHz)."""
    return np.sqrt(np.convolve(x * x, np.ones(w) / w, 'same'))[::w]


def onset(wav, clip, near, before=.6, after=.6, w=240):
    """Where {clip} starts in {wav} (samples), near {near}: the lag whose loudness envelope matches the clip's best, the
    logarithms of the two against each other (the game plays it quieter or louder, and other sounds lie over it)."""
    lo = max(0, int(near - before * SR))
    seg = wav[lo:int(near + after * SR) + len(clip)]
    es, ec = envelope(seg, w), envelope(clip[:2 * SR], w)
    lc = np.log(ec + 1e-4)
    best = (0, -2.0)
    for lag in range(0, len(es) - len(ec)):
        a = es[lag:lag + len(ec)]
        if a.std() < 1e-6:
            continue
        r = np.corrcoef(np.log(a + 1e-4), lc)[0, 1]
        if r > best[1]:
            best = (lag, r)
    return lo + best[0] * w, best[1]


def mux(audio, film_dir, entry, spans, film, out, combo=FORMATION, sounds=('isosang_h',), name='H'):
    """Lays the sound run's recording under the film (see the module's doc): {combo} is the swing whose start both runs
    are lined up by, {sounds} the sounds of it that may have played (2.83.0: G plays one of two), found by loudness."""
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

    heard = [r for r in rows(os.path.join(folder, 'isosang.csv')) if r['combo'] == combo]
    shown = [r for r in rows(os.path.join(film_dir, entry, 'isosang.csv')) if r['combo'] == combo]
    if not heard or not shown:
        sys.exit(f'{entry}: no {name} in the sound run ({len(heard)}) or the film run ({len(shown)})')
    shift = int(heard[0]['start']) - int(shown[0]['start'])
    # H's sound in the sound run, found by its waveform near where the log puts it: the rule checked (it starts as its
    # client hears of H, a tick or so after H began)
    played = [r['sound'].split(':')[-1] for r in rows(os.path.join(folder, 'sounds.csv')) if r['sound'].split(':')[-1] in sounds]
    lead = 0.0
    if played:
        # (2.83.0) the film shows the one the film run played: the two runs must have played the same
        filmed = [r['sound'].split(':')[-1] for r in rows(os.path.join(film_dir, entry, 'sounds.csv')) if r['sound'].split(':')[-1] in sounds] \
            if os.path.exists(os.path.join(film_dir, entry, 'sounds.csv')) else []
        if filmed and filmed[0] != played[0]:
            sys.exit(f'{entry}: the film run played {filmed[0]}, the sound run {played[0]}: rig both to the same')
        clip = decode(os.path.join(SOUNDS, played[0] + '.ogg'))
        began = sample(int(heard[0]['start']))
        pos, score = onset(wav, clip, began)
        print(f'  sound run: {name} ({played[0]}) began on tick {heard[0]["start"]}, its sound at {pos / SR:.3f} s (envelope match {score:.2f}), '
              f'{(pos - began) / SR * 1000:+.0f} ms after it by the clock')
        if os.environ.get('MUX_LEAD', '1') != '0' and score > .8:
            lead = pos - (began + SR / 20)
            print(f'  every piece moved {lead / SR * 1000:+.0f} ms in the recording: {name}\'s sound a tick after {name} began')
    print(f'  film run: {name} began on tick {shown[0]["start"]}; the sound run\'s clock moved by {shift:+d} ticks')
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
        lo = int(round(sample(a * 20 + shift) + lead))
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
    # (2.83.0) MUX_KEEP_WAV=1 keeps the sound as written (OUT.wav, exactly as long as the film): films made of parts join
    # their parts' sounds before encoding them once (joining encoded parts moves each later part's sound by the encoder's delay)
    if os.environ.get('MUX_KEEP_WAV') != '1':
        os.remove(tmp)
    print(f'{out}: {len(track) / SR:.2f} s of sound ({place / SR:.2f} s of stretches)')


def main():
    mux(*sys.argv[1:7])


if __name__ == '__main__':
    main()

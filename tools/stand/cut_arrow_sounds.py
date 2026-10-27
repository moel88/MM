"""The stand arrow's sounds (2.71.0, user request: "효과음이 너무 AI같아"): cut from recordings, nothing synthesized.

usage: python3 -I tools/stand/cut_arrow_sounds.py SOURCES_DIR [OUT_DIR]

2.70.0 made its sounds from noise and tones (tools/stand/make_arrow_sounds.py, gone); the user heard them as made up.
These are recordings, all public domain (CC0), each listed in SOURCES with where it came from and who made it
(third_party/stand-arrow-sounds/SOURCES.md says the same): cloth, a whoosh, a heartbeat, feet and a body coming down on
the ground, thunder, a bass drum, struck singing bowls, and (2.71.1) for the blow and the glow, metal and glass that ring:
Tibetan cymbals (tingsha), finger cymbals, small chimes, a glass knock and a soft thump. SOURCES_DIR holds them as
downloaded (each at its path in SOURCES); the script only decodes them (ffmpeg), it never runs anything from there.

2.71.1 (user request: "소리가 너무 게임에 안 어울려 목소리나 한숨은 다 빼고 화살 꽂는 소리만 새로 갈아엎어서 새로 만들어줘",
then "꽂고 빛날때 판타지스러운 소리로"): every voice is gone (the sigh, the breaths, the gasp, the caught breath, the breath
out, the choke), and the blow is made anew as a magic one instead of the film foley of 2.71.0 (a blade into fruit):
the point going in lights up (a soft low thump and a glass knock under a finger cymbal's ting and a tingsha's ring,
chimes sparkling after it), and as the arrow glows and is drawn in, the light is drawn in too (the ring of a tingsha,
a finger cymbal and chimes played backwards: a shimmer that swells and is cut as everything goes still).

Each sound of the stab (client/stand/StandArrowSounds, which lays them on the stab's clock) is a stretch of one
recording, or several laid together (the blow, the glow), changed only as any cut is: mono (the channels averaged) at
48 kHz, faded in and out where it is cut (a few ms; a long fade where a sound is to swell or die away), played
backwards where the layer says so (the glow), the rumble under 30 Hz taken off, set to its own loudness (EBU R 128 /
BS.1770, the 'loud' of each) with only its peaks turned down under -1 dB (the limiter of
tools/stand/make_inhale_sounds.py; never more than SQUASH dB, so the blows keep their edge: the beat, the knees and the
fall are as loud as that lets them be). Each sound's first event (the rustle, the blow, the beat, the thud) is a few
ms into it, so the cue sheet puts it where the body does it; the glow's last (its rings' strikes, backwards) is at its
very end, where the stillness comes.
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_inhale_sounds import SR, high, limit, loudness  # noqa: E402

CEILING_DB = -1.0
SQUASH = 4.0

BSB = 'Joseph SARDIN, BigSoundBank.com'
KENNEY = 'Kenney (kenney.nl), "RPG Audio" 1.0'
KENNEY_IMPACT = 'Kenney (kenney.nl), "Impact Sounds" 1.0'
KENNEY_PAGE = 'https://kenney.nl/assets/rpg-audio'
KENNEY_IMPACT_PAGE = 'https://kenney.nl/assets/impact-sounds'

# key: (path in SOURCES_DIR, title, page, author); all CC0 1.0
SOURCES = {
    'cloth1': ('kenney-rpg-audio/extracted/Audio/cloth1.ogg', 'Audio/cloth1.ogg', KENNEY_PAGE, KENNEY),
    'cloth2': ('kenney-rpg-audio/extracted/Audio/cloth2.ogg', 'Audio/cloth2.ogg', KENNEY_PAGE, KENNEY),
    'cloth3': ('kenney-rpg-audio/extracted/Audio/cloth3.ogg', 'Audio/cloth3.ogg', KENNEY_PAGE, KENNEY),
    'sheet': ('bsb-1799-whoosh-sheet-6/bsb_1799.wav', 'Whoosh #6', 'https://bigsoundbank.com/whoosh-6-s1799.html', BSB),
    'rope': ('bsb-1796-whoosh-rope-4/bsb_1796.wav', 'Whoosh #4', 'https://bigsoundbank.com/whoosh-4-s1796.html', BSB),
    'heart': ('bsb-1929-heartbeat-4-slow/bsb_1929.wav', 'Heart Beat #4', 'https://bigsoundbank.com/heart-beat-4-s1929.html', BSB),
    'grass': ('bsb-1839-jump-grass-1/bsb_1839.wav', 'Jumping on Grass #1', 'https://bigsoundbank.com/jumping-on-grass-1-s1839.html', BSB),
    'dirt': ('bsb-1837-jump-dirt-1/bsb_1837.wav', 'Jumping on dirt road #1', 'https://bigsoundbank.com/jumping-on-dirt-road-1-s1837.html', BSB),
    'roll': ('bsb-3184-thunder-11/bsb_3184.wav', 'Thunder #11', 'https://bigsoundbank.com/thunder-11-s3184.html', 'Joseph SARDIN & Axeline T., BigSoundBank.com'),
    'clap': ('bsb-3179-thunder-6/bsb_3179.wav', 'Thunder #6', 'https://bigsoundbank.com/thunder-6-s3179.html', 'Joseph SARDIN & Axeline T., BigSoundBank.com'),
    'tom': ('bsb-2341-bass-tom-4/bsb_2341.wav', 'Bass tom, #4', 'https://bigsoundbank.com/bass-tom-4-s2341.html', BSB),
    'bowl': ('bsb-2554-bowl-struck-3/bsb_2554.wav', 'Tibetan bowl struck #3', 'https://bigsoundbank.com/tibetan-bowl-struck-3-s2554.html', BSB),
    'bodyfall': ('bsb-2453-bodyfall-2/bsb_2453.wav', 'Body Fall #2', 'https://bigsoundbank.com/body-fall-2-s2453.html', BSB),
    # (2.71.1) the magic blow and the glow
    'soft': ('kenney-impact-sounds/extracted/Audio/impactSoft_heavy_000.ogg', 'Audio/impactSoft_heavy_000.ogg', KENNEY_IMPACT_PAGE, KENNEY_IMPACT),
    'glass': ('kenney-impact-sounds/extracted/Audio/impactGlass_heavy_001.ogg', 'Audio/impactGlass_heavy_001.ogg', KENNEY_IMPACT_PAGE, KENNEY_IMPACT),
    'fingers1': ('bsb-2274-finger-cymbals-1/bsb_2274.wav', 'Finger Cymbals #1', 'https://bigsoundbank.com/finger-cymbals-1-s2274.html', BSB),
    'fingers2': ('bsb-2275-finger-cymbals-2/bsb_2275.wav', 'Finger Cymbals #2', 'https://bigsoundbank.com/finger-cymbals-2-s2275.html', BSB),
    'tingsha1': ('bsb-2366-nepalese-cymbal-1/bsb_2366.wav', 'Nepalese cymbal #1', 'https://bigsoundbank.com/nepalese-cymbal-1-s2366.html', BSB),
    'tingsha2': ('bsb-2367-nepalese-cymbal-2/bsb_2367.wav', 'Nepalese cymbal #2', 'https://bigsoundbank.com/nepalese-cymbal-2-s2367.html', BSB),
    'chimes2': ('bsb-2088-chimes-butterfly-2/bsb_2088.wav', 'Chimes "butterfly" #2', 'https://bigsoundbank.com/chimes-butterfly-2-s2088.html', BSB),
    'chimes3': ('bsb-2089-chimes-butterfly-3/bsb_2089.wav', 'Chimes "butterfly" #3', 'https://bigsoundbank.com/chimes-butterfly-3-s2089.html', BSB),
}

# name: layers [(source key, from s, to s, the layer's peak (dB) as laid in, at s in the sound[, {'rev': played
# backwards, 'fade_in', 'fade_out': its own fades (s), as the sound's}])], its loudness (LUFS), fade in and out (s; a
# long one is a curve: it swells in slowly, or drops away and dies slowly)
SOUNDS = {
    # the arm moving (lifting it; lowering it; taking it up; turning it over)
    'stand_arrow_cloth_1': {'layers': [('cloth1', .0, .66, 0, 0)], 'loud': -24},
    'stand_arrow_cloth_2': {'layers': [('cloth2', .04, .41, 0, 0)], 'loud': -24},
    'stand_arrow_cloth_3': {'layers': [('cloth3', .0, .47, 0, 0)], 'loud': -24},
    # the arm driving it down
    'stand_arrow_swing': {'layers': [('sheet', .1, .45, 0, 0)], 'loud': -22},
    # the blow (2.71.1), a magic one: as the point goes in it lights up. A soft low thump and a glass knock under a
    # finger cymbal's ting and the ring of a tingsha (Tibetan cymbals: it rings on, beating, as the light does), chimes
    # sparkling after it; the strikes a few ms apart (not one peak), the first at the start
    'stand_arrow_stab': {'layers': [('soft', .0, .5, -6, 0), ('glass', .0, .43, -8, .003),
                                    ('fingers1', .046, 1.6, -6, .006, {'fade_out': .6}),
                                    ('tingsha1', .032, 2.6, 0, .010, {'fade_out': 1.2}),
                                    ('chimes3', .27, 1.3, -6, 0, {'fade_out': .3})], 'loud': -22, 'fade_out': .4},
    # one beat of the heart (lub, dub)
    'stand_arrow_heart': {'layers': [('heart', .065, .6, 0, 0)], 'loud': -19, 'fade_out': .1},
    # a knee coming down on the ground: the right (heavy), then the left
    'stand_arrow_knee': {'layers': [('grass', .862, 1.1, 0, 0)], 'loud': -23.5, 'fade_out': .06},
    'stand_arrow_knee_2': {'layers': [('dirt', .533, .8, 0, 0)], 'loud': -22.5, 'fade_out': .06},
    # the glow (2.71.1): as the arrow glows and is drawn in, the light drawn in too, a shimmer swelling to the stillness
    # where it is cut: the ring of a tingsha (the other one), a finger cymbal and chimes, all played backwards, so their
    # strikes come at its very end
    'stand_arrow_glow': {'layers': [('tingsha2', .028, 1.378, 0, 0, {'rev': True, 'fade_in': .5, 'fade_out': .004}),
                                    ('fingers2', .012, 1.362, -8, 0, {'rev': True, 'fade_in': .6, 'fade_out': .004}),
                                    ('chimes2', .85, 2.2, -10, 0, {'rev': True, 'fade_in': .3, 'fade_out': .01})],
                         'loud': -26, 'fade_in': .3, 'fade_out': .006},
    # the low roll of thunder swelling under it all, from when it is raised to the sky
    'stand_arrow_rumble': {'layers': [('roll', 9.5, 14.32, 0, 0)], 'loud': -24, 'fade_in': .8, 'fade_out': .1},
    # chosen: the thunderclap with a bass drum under its crack, as the stand bursts out
    'stand_arrow_awaken': {'layers': [('clap', 1.28, 6.0, 0, 0), ('tom', .035, 1.6, -3, .03)], 'loud': -18, 'fade_out': 1.5},
    # chosen: the air of it bursting out
    'stand_arrow_burst': {'layers': [('rope', .12, .7, 0, 0)], 'loud': -20},
    # chosen: a singing bowl struck, ringing on
    'stand_arrow_bowl': {'layers': [('bowl', .0, 3.5, 0, 0)], 'loud': -25, 'fade_out': 1.5},
    # rejected: the body falling on its face (the torso's blow on)
    'stand_arrow_bodyfall': {'layers': [('bodyfall', .458, 1.3, 0, 0)], 'loud': -19.5, 'fade_out': .15},
}


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def fade(x, fade_in, fade_out):
    fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
    x = x.copy()
    x[:fi] *= np.linspace(0, 1, fi) ** (2 if fade_in > .2 else 1)
    x[-fo:] *= np.linspace(1, 0, fo) ** (2 if fade_out > .2 else 1)
    return x


def make(spec, sources):
    layers = []
    for key, a, b, peak, at, *more in spec['layers']:
        opt = more[0] if more else {}
        x = sources[key][int(a * SR):int(b * SR)]
        if opt.get('rev'):
            x = x[::-1]
        x = fade(x, opt.get('fade_in', .004), opt.get('fade_out', .02))
        x *= 10 ** (peak / 20) / np.abs(x).max()
        layers.append((int(at * SR), x))
    out = np.zeros(max(at + len(x) for at, x in layers))
    for at, x in layers:
        out[at:at + len(x)] += x
    out = fade(high(out, 30), spec.get('fade_in', .004), spec.get('fade_out', .02))
    ceiling = 10 ** (CEILING_DB / 20)
    # (only the peaks are turned down: a sound that would need more than SQUASH dB of it to reach its loudness is set
    # quieter instead, so its blows keep their edge)
    natural = loudness(out * ceiling / np.abs(out).max())
    if spec['loud'] > natural + SQUASH + .05:
        raise ValueError(f"{spec['loud']} LUFS would squash it: at most {natural + SQUASH:.2f}")
    for _ in range(4):
        out = limit(out * 10 ** ((spec['loud'] - loudness(out)) / 20), ceiling)
    return out


def main():
    src = Path(sys.argv[1])
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    sources = {key: decode(src / path) for key, (path, *_rest) in SOURCES.items()}
    os.makedirs(out_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, spec in SOUNDS.items():
            x = make(spec, sources)
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6',
                            str(out_dir / (name + '.ogg'))], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()

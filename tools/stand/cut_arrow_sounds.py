"""The stand arrow's sounds (2.71.0, user request: "효과음이 너무 AI같아"): cut from recordings, nothing synthesized.

usage: python3 -I tools/stand/cut_arrow_sounds.py SOURCES_DIR [OUT_DIR]

2.70.0 made its sounds from noise and tones (tools/stand/make_arrow_sounds.py, gone); the user heard them as made up.
These are recordings, all public domain (CC0), each listed in SOURCES with where it came from and who made it
(third_party/stand-arrow-sounds/SOURCES.md says the same): breaths, a sigh, a gasp, cloth, a heartbeat, a blade going
into fruit and vegetables over a dull wet thump (the film foley for a stab), feet and a body coming down on the ground,
thunder, a bass drum, a struck singing bowl, a choking man. SOURCES_DIR holds them as downloaded (each at its path in
SOURCES); the script only decodes them (ffmpeg), it never runs anything from there.

Each sound of the stab (client/stand/StandArrowSounds, which lays them on the stab's clock) is a stretch of one
recording, or several laid together (the blow), changed only as any cut is: mono (the channels averaged) at 48 kHz,
faded in and out where it is cut (a few ms; a long fade where a sound is to swell or die away), the rumble under 30 Hz
taken off, set to its own loudness (EBU R 128 / BS.1770, the 'loud' of each) with only its peaks turned down under -1 dB
(the limiter of tools/stand/make_inhale_sounds.py; never more than SQUASH dB, so the blows keep their edge: the blows, the
beat, the knees and the fall are as loud as that lets them be). Each sound's first event (the breath, the rustle, the blow, the beat,
the thud) is a few ms into it, so the cue sheet puts it where the body does it.
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
OWL = 'OwlishMedia (Freesound: owlstorm), OpenGameArt.org "Sound Effects Pack"'
KENNEY = 'Kenney (kenney.nl), "RPG Audio" 1.0'
OWL_PAGE = 'https://opengameart.org/content/sound-effects-pack'
KENNEY_PAGE = 'https://kenney.nl/assets/rpg-audio'

# key: (path in SOURCES_DIR, title, page, author); all CC0 1.0
SOURCES = {
    'sigh': ('bsb-1405-sigh-mouth/bsb_1405.wav', 'Sigh by the Mouth', 'https://bigsoundbank.com/sigh-by-the-mouth-s1405.html', BSB),
    'nose': ('bsb-1404-sigh-nose/bsb_1404.wav', 'Sigh by the nose', 'https://bigsoundbank.com/sigh-by-the-nose-s1404.html', BSB),
    'scared': ('oga-owlishmedia-pack/extracted/Human/scared-breathing.wav', 'Human/scared-breathing.wav', OWL_PAGE, OWL),
    'freaked': ('oga-owlishmedia-pack/extracted/Human/freakedbreath.wav', 'Human/freakedbreath.wav', OWL_PAGE, OWL),
    'gasp': ('oga-owlishmedia-pack/extracted/Human/gasp1.wav', 'Human/gasp1.wav', OWL_PAGE, OWL),
    'cloth1': ('kenney-rpg-audio/extracted/Audio/cloth1.ogg', 'Audio/cloth1.ogg', KENNEY_PAGE, KENNEY),
    'cloth2': ('kenney-rpg-audio/extracted/Audio/cloth2.ogg', 'Audio/cloth2.ogg', KENNEY_PAGE, KENNEY),
    'cloth3': ('kenney-rpg-audio/extracted/Audio/cloth3.ogg', 'Audio/cloth3.ogg', KENNEY_PAGE, KENNEY),
    'sheet': ('bsb-1799-whoosh-sheet-6/bsb_1799.wav', 'Whoosh #6', 'https://bigsoundbank.com/whoosh-6-s1799.html', BSB),
    'rope': ('bsb-1796-whoosh-rope-4/bsb_1796.wav', 'Whoosh #4', 'https://bigsoundbank.com/whoosh-4-s1796.html', BSB),
    'grapefruit': ('bsb-2587-gore-grapefruit-1/bsb_2587.wav', 'Gore (grapefruit) #1', 'https://bigsoundbank.com/gore-grapefruit-1-s2587.html', BSB),
    'pepper': ('bsb-2590-gore-pepper-2/bsb_2590.wav', 'Gore (pepper) #2', 'https://bigsoundbank.com/gore-pepper-2-s2590.html', BSB),
    'punch': ('bsb-2459-punch-4/bsb_2459.wav', 'Punch #4', 'https://bigsoundbank.com/punch-4-s2459.html', BSB),
    'fruit': ('oga-owlishmedia-pack/extracted/Impacts/fruit1.wav', 'Impacts/fruit1.wav', OWL_PAGE, OWL),
    'heart': ('bsb-1929-heartbeat-4-slow/bsb_1929.wav', 'Heart Beat #4', 'https://bigsoundbank.com/heart-beat-4-s1929.html', BSB),
    'grass': ('bsb-1839-jump-grass-1/bsb_1839.wav', 'Jumping on Grass #1', 'https://bigsoundbank.com/jumping-on-grass-1-s1839.html', BSB),
    'dirt': ('bsb-1837-jump-dirt-1/bsb_1837.wav', 'Jumping on dirt road #1', 'https://bigsoundbank.com/jumping-on-dirt-road-1-s1837.html', BSB),
    'roll': ('bsb-3184-thunder-11/bsb_3184.wav', 'Thunder #11', 'https://bigsoundbank.com/thunder-11-s3184.html', 'Joseph SARDIN & Axeline T., BigSoundBank.com'),
    'clap': ('bsb-3179-thunder-6/bsb_3179.wav', 'Thunder #6', 'https://bigsoundbank.com/thunder-6-s3179.html', 'Joseph SARDIN & Axeline T., BigSoundBank.com'),
    'tom': ('bsb-2341-bass-tom-4/bsb_2341.wav', 'Bass tom, #4', 'https://bigsoundbank.com/bass-tom-4-s2341.html', BSB),
    'bowl': ('bsb-2554-bowl-struck-3/bsb_2554.wav', 'Tibetan bowl struck #3', 'https://bigsoundbank.com/tibetan-bowl-struck-3-s2554.html', BSB),
    'strangled': ('bsb-1639-strangled-1/bsb_1639.wav', 'Strangled man #1', 'https://bigsoundbank.com/strangled-man-1-s1639.html', BSB),
    'bodyfall': ('bsb-2453-bodyfall-2/bsb_2453.wav', 'Body Fall #2', 'https://bigsoundbank.com/body-fall-2-s2453.html', BSB),
}

# name: layers [(source key, from s, to s, the layer's peak (dB) as laid in, at s in the sound)], its loudness (LUFS),
# fade in and out (s; a long one is a curve: it swells in slowly, or drops away and dies slowly)
SOUNDS = {
    # the arm moving (lifting it; lowering it; taking it up; turning it over)
    'stand_arrow_cloth_1': {'layers': [('cloth1', .0, .66, 0, 0)], 'loud': -24},
    'stand_arrow_cloth_2': {'layers': [('cloth2', .04, .41, 0, 0)], 'loud': -24},
    'stand_arrow_cloth_3': {'layers': [('cloth3', .0, .47, 0, 0)], 'loud': -24},
    # the doubt: one long sigh out through the mouth (from where it breaks out)
    'stand_arrow_sigh': {'layers': [('sigh', 1.925, 2.95, 0, 0)], 'loud': -22, 'fade_out': .4},
    # the deep breath in, both hands on it (the same breather's)
    'stand_arrow_breath': {'layers': [('sigh', .82, 1.74, 0, 0)], 'loud': -23, 'fade_in': .25, 'fade_out': .08},
    # short, shaky breaths as it is aimed and held trembling
    'stand_arrow_nervous': {'layers': [('scared', 2.38, 3.7, 0, 0)], 'loud': -25, 'fade_in': .12, 'fade_out': .25},
    # the breath caught for the blow (the sharp intake only)
    'stand_arrow_catch': {'layers': [('freaked', .2, .405, 0, 0)], 'loud': -20, 'fade_out': .03},
    # the arm driving it down
    'stand_arrow_swing': {'layers': [('sheet', .1, .45, 0, 0)], 'loud': -22},
    # the blow: the point going in (grapefruit; the pepper's crunch a little under it) over a dull wet thump (the fruit
    # dropped) and a dry slap (a punch), every one's first transient a few ms in
    'stand_arrow_stab': {'layers': [('grapefruit', .175, .75, 0, 0), ('pepper', .135, .7, -7, .0), ('fruit', .105, .42, -1, .0),
                                    ('punch', .095, .4, -9, .0)], 'loud': -17.5, 'fade_out': .05},
    # the gasp as the body arches back, and the shaky breaths after it
    'stand_arrow_gasp': {'layers': [('gasp', .1, 2.95, 0, 0)], 'loud': -23, 'fade_out': .1},
    # one beat of the heart (lub, dub)
    'stand_arrow_heart': {'layers': [('heart', .065, .6, 0, 0)], 'loud': -19, 'fade_out': .1},
    # a knee coming down on the ground: the right (heavy), then the left
    'stand_arrow_knee': {'layers': [('grass', .862, 1.1, 0, 0)], 'loud': -23.5, 'fade_out': .06},
    'stand_arrow_knee_2': {'layers': [('dirt', .533, .8, 0, 0)], 'loud': -22.5, 'fade_out': .06},
    # the low roll of thunder swelling under it all, from when it is raised to the sky
    'stand_arrow_rumble': {'layers': [('roll', 9.5, 14.32, 0, 0)], 'loud': -24, 'fade_in': .8, 'fade_out': .1},
    # chosen: the thunderclap with a bass drum under its crack, as the stand bursts out
    'stand_arrow_awaken': {'layers': [('clap', 1.28, 6.0, 0, 0), ('tom', .035, 1.6, -3, .03)], 'loud': -18, 'fade_out': 1.5},
    # chosen: the air of it bursting out
    'stand_arrow_burst': {'layers': [('rope', .12, .7, 0, 0)], 'loud': -20},
    # chosen: a singing bowl struck, ringing on
    'stand_arrow_bowl': {'layers': [('bowl', .0, 3.5, 0, 0)], 'loud': -25, 'fade_out': 1.5},
    # chosen: breathing out, through the nose, standing (another breath than the doubt's)
    'stand_arrow_relief': {'layers': [('nose', 2.9, 4.8, 0, 0)], 'loud': -24, 'fade_in': .05, 'fade_out': .7},
    # rejected: the choked gasp
    'stand_arrow_choke': {'layers': [('strangled', 6.93, 7.36, 0, 0)], 'loud': -18, 'fade_out': .05},
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
    for key, a, b, peak, at in spec['layers']:
        x = fade(sources[key][int(a * SR):int(b * SR)], .004, .02)
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

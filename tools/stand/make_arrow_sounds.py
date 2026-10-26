"""Synthesises the stand arrow's sounds (2.70.0, StandArrowClient): the stab, the awakening and the rejection.

usage: python3 -I tools/stand/make_arrow_sounds.py [OUT_DIR]

stand_arrow_stab: what the game plays a tick into the stab (StandArrowClient.LEAD), timed to its motion
(client/stand/ArrowStabPose, its clock less that tick): a glassy shimmer as the arrow is lifted, a thin trembling tone
while it is held up, a sharp breath drawn in as the left hand comes to it, the rush of the blow speeding up into it,
and, as it goes in (HIT), a sharp piercing hit on a deep thud with a metallic ring; then a low drone rising under a
swelling choir-like pad, the heart beating (ArrowStabPose.BEATS, each a lub-dub, louder and louder) and a rush of air
rising into its choice (REVEAL), where it ends.

stand_arrow_awaken: played as the arrow's choice shows, when it chose: a burst (a deep boom, a bright shimmering D
major chord swelling out of it and ringing on, sparkles, a rush of air thrown out).

stand_arrow_reject: played as its choice shows, when it rejected: the swell collapsing into a dark falling drone, the
heart's last two beats, the knees hitting the ground (ArrowStabPose's DIE_TIMES[2] after the choice) and the body
landing on its face (LANDS_AT), a dull rumble dying away.

Made from noise and tones only (fixed seeds), with tools/stand/make_inhale_sounds.py's filters, loudness (EBU R 128)
and peak limiter: nothing to license, and every run makes the same files. Mono at 48 kHz (the game plays them as
loud as the stab is near), written as OGG Vorbis by ffmpeg (on the PATH). OUT_DIR defaults to the mod's sounds folder.
"""
import os
import subprocess
import sys
import tempfile
import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_inhale_sounds as mk  # noqa: E402
from make_inhale_sounds import SR, t, noise, brown, band, low, high, swept, norm, smooth, tone, loudness  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
mk.LOUDNESS.update({'stand_arrow_stab': -16.5, 'stand_arrow_awaken': -15.5, 'stand_arrow_reject': -17.0})

# the stab's clock (ArrowStabPose) less the tick its sound starts after it
LEAD = 1 / 20
HIT = 22 / 20 - LEAD
REVEAL = 44 / 20 - LEAD
JOIN = .76 - LEAD
LOAD = .96 - LEAD
BEATS = [b - LEAD for b in (1.40, 1.68, 1.90, 2.06, 2.16)]
# after the choice shows (ArrowStabPose.DIE_TIMES): on the knees, on the face
KNEES, LANDS = .42, 1.05


def at(x, when):
    """Seconds since {when} (0 before it)."""
    return np.clip(x - when, 0, None)


def env(x, start, attack, decay):
    """Rises over {attack} seconds from {start}, then dies away with time constant {decay}."""
    k = x - start
    return smooth(k / attack) * np.exp(-np.clip(k - attack, 0, None) / decay) * (k >= 0)


def chime(x, start, freq, decay=.6, level=1.0):
    """A glassy struck note: a sine with a few inharmonic partials, soft attack."""
    k = at(x, start)
    e = smooth(k / .006) * np.exp(-k / decay) * (x >= start)
    s = np.sin(2 * np.pi * freq * k) + .35 * np.sin(2 * np.pi * freq * 2.76 * k) * np.exp(-k / (decay * .5)) \
        + .15 * np.sin(2 * np.pi * freq * 5.4 * k) * np.exp(-k / (decay * .3))
    return s * e * level


def thump(x, start, deep=1.0):
    """One beat of the heart: a low thump."""
    k = at(x, start)
    body = tone(42 + 26 * np.exp(-k / .03)) * np.exp(-k / .09) * smooth(k / .004) * (x >= start)
    return body * deep


def heartbeat(x, start, level):
    return (thump(x, start) + .62 * thump(x, start + .17)) * level


def stab():
    d = REVEAL + .03
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    # the arrow lifted: a glassy shimmer climbing, air rising with it
    for i, (f, w) in enumerate([(1174.7, .02), (1479.98, .09), (1760.0, .16), (2349.3, .24)]):
        out += chime(x, w, f, decay=.55, level=.22)
    lift = env(x, 0, .25, .25)
    out += norm(swept(noise(n, 41), 700 + 2200 * smooth(x / .3), 3)) * lift * .22
    # held up: a thin trembling tone, the hesitation
    hold = smooth((x - .28) / .12) * (1 - smooth((x - .7) / .1))
    trem = 1 + .45 * np.sin(2 * np.pi * 9.3 * x) * np.sin(2 * np.pi * 1.7 * x + .4)
    out += (tone(np.full(n, 1975.5)) + .8 * tone(np.full(n, 1987.0), 1.1)) * hold * trem * .05
    out += tone(np.full(n, 55.0)) * hold * .05
    # the breath drawn in as the left hand comes to it
    breath = swept(noise(n, 42), 1100 + 600 * smooth((x - JOIN + .1) / .16), 2.4) + .5 * swept(noise(n, 43), np.full(n, 2600.0), 3.2)
    out += norm(breath) * smooth((x - JOIN + .1) / .14) * (1 - smooth((x - JOIN - .05) / .04)) * .32
    # the blow: a rush of air speeding up into it
    u = np.clip((x - LOAD) / (HIT - LOAD), 0, 1)
    rush = swept(noise(n, 44), 380 + 3400 * u ** 1.6, 2.2) + .6 * band(noise(n, 45), 2500, 7000)
    out += norm(rush) * (u ** 2) * (x < HIT + .01) * .6
    # it goes in: a sharp piercing hit, a deep thud, a metallic ring
    k = at(x, HIT)
    hit = x >= HIT
    out += norm(band(noise(n, 46), 2200, 9000)) * np.exp(-k / .025) * smooth(k / .0015) * hit * .9
    out += tone(36 + 70 * np.exp(-k / .05)) * np.exp(-k / .32) * smooth(k / .004) * hit * .95
    out += norm(low(noise(n, 47), 380, 3)) * np.exp(-k / .08) * hit * .55
    for f, w in [(1318.5, .9), (2794.0, .5), (4186.0, .3)]:
        out += np.sin(2 * np.pi * f * k) * np.exp(-k / .7) * smooth(k / .003) * hit * .09 * w
    # then: a low drone rising, a pad swelling, the heart beating louder and faster, air rising into the choice
    rise = smooth((x - HIT - .05) / (REVEAL - HIT - .05))
    on = smooth((x - HIT - .02) / .2)
    drone = tone(44 + 30 * rise) + .5 * tone(88 + 61 * rise, .7) + .25 * tone(132 + 92 * rise, 1.3)
    out += drone * on * (.07 + .16 * rise)
    pad = np.zeros(n)
    for i, f in enumerate([293.66, 369.99, 440.0, 587.33]):
        det = 1 + .004 * (i - 1.5)
        pad += low(np.sign(tone(np.full(n, f * det), i)) * .5 + tone(np.full(n, f * det * 1.003), i + .5), 1800)
    out += norm(pad) * on * rise ** 1.5 * .12
    for i, b in enumerate(BEATS):
        out += heartbeat(x, b, .55 + .1 * i)
    out += norm(swept(noise(n, 48), 500 + 2600 * rise ** 2, 2.6)) * rise ** 2.2 * on * .35
    out = mk.finish(out, 'stand_arrow_stab', fade_out=.03)
    return out


def awaken():
    d = 2.6
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    # the burst: a deep boom and a crack of air
    out += tone(34 + 52 * np.exp(-x / .07)) * np.exp(-x / .45) * smooth(x / .004) * 1.0
    out += norm(low(noise(n, 51), 600, 3)) * np.exp(-x / .12) * .6
    out += norm(band(noise(n, 52), 1800, 8000)) * np.exp(-x / .05) * smooth(x / .002) * .55
    # a bright D major chord swelling out of it and ringing on
    chord = np.zeros(n)
    for i, f in enumerate([293.66, 440.0, 587.33, 739.99, 880.0, 1174.66]):
        for det in (.997, 1.0, 1.003):
            chord += tone(np.full(n, f * det), i * 1.3 + det * 7)
    out += norm(low(chord, 5200)) * smooth(x / .12) * np.exp(-np.clip(x - .2, 0, None) / 1.0) * .5
    # sparkles
    rng = np.random.default_rng(53)
    for _ in range(26):
        s = .05 + rng.random() * 1.4
        f = 2600 + rng.random() * 3400
        out += chime(x, s, f, decay=.18 + rng.random() * .2, level=.05 + .05 * rng.random())
    # a rush of air thrown out
    out += norm(swept(noise(n, 54), 3200 * np.exp(-x / .5) + 420, 2.2)) * env(x, 0, .02, .5) * .45
    return mk.finish(out, 'stand_arrow_awaken', fade_out=.15)


def reject():
    d = 2.5
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    # the swell collapsing: a dark falling drone, detuned
    fall = 190 * np.exp(-x / .35) + 46
    drone = tone(fall) + .7 * tone(fall * 1.012, .5) + .4 * tone(fall * 1.5, 1.1)
    out += low(drone, 900) * smooth(x / .01) * np.exp(-x / 1.1) * .3
    out += norm(low(noise(n, 61), 500, 3)) * np.exp(-x / .14) * smooth(x / .003) * .5
    # the heart's last two beats, the second weak
    out += heartbeat(x, .02, .9) + thump(x, .48, .35)
    # the knees hitting the ground, then the body landing on its face
    for when, level, dur in ((KNEES, .55, .09), (LANDS, 1.0, .14)):
        k = at(x, when)
        on = x >= when
        out += tone(48 + 40 * np.exp(-k / .03)) * np.exp(-k / dur) * smooth(k / .003) * on * level
        out += norm(low(noise(n, int(when * 100) + 62), 700, 3)) * np.exp(-k / (dur * .8)) * on * level * .55
        out += norm(band(noise(n, int(when * 100) + 63), 1200, 4200)) * np.exp(-k / .1) * smooth(k / .01) * on * level * .12
    # a dull rumble dying away
    out += norm(low(brown(n, 64), 160, 3)) * smooth(x / .3) * np.exp(-x / .9) * .25
    return mk.finish(out, 'stand_arrow_reject', fade_out=.15)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'stand_arrow_stab': stab(), 'stand_arrow_awaken': awaken(), 'stand_arrow_reject': reject()}
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in made.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()

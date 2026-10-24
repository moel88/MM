"""The sound of a meteor falling, from the recording the user sent (2.49.2: "메테오 떨어지는 소리를 이걸로 할래?",
Small_meteor_falling_-_Sound_Effect.mp3, 5.17 s, 44.1 kHz stereo).

The recording is kept as it is. It swells once as the meteor shows (0.55-0.85 s), rushes more quietly, roars loud
from about 2.1 s, and the loud part ends at 3.65 s; then it fades out by 4.6 s. The game starts it when the fall
starts and the fall lasts 3.6 s (MeteorRules.FALL_TICKS), so the meteor lands as the loud roar ends, and the fade
plays on under the landing. The first 0.1 s (near silence) is cut, because the sound starts about a tick after the
fall is announced. The rest is only made fit for the game:
- mixed to mono (the channels correlate at 0.94), so the game hears it from where the meteor is and fades it with
  distance (a stereo sound is heard the same everywhere);
- resampled to 48 kHz like the other meteor sounds, with rumble under 28 Hz taken out;
- set to the loudness the synthesised roar had (-15 LUFS, under the landing's -12.5; the mono mix is -15.1 already,
  the recording -11.7 in stereo) with its peaks under -1 dB: at that loudness 26 crackles of a few milliseconds would
  go up to 2.9 dB over it, and a look-ahead limiter turns just those down (no clipping).

usage: python3 -I tools/meteor/user_fall_sound.py RECORDING.mp3 [OUT.ogg]
OUT defaults to the mod's meteor_fall.ogg. ffmpeg must be on the PATH.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.ndimage import minimum_filter1d, uniform_filter1d

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_sounds as ms  # noqa: E402  (BS.1770 loudness at 48 kHz, the high-pass)

TRIM = 0.10
LOUDNESS = -15.0


def limit(x, ceiling, look=.005, release=.04):
    """Turns down only what would go over the ceiling: the gain eases down over the `look` seconds before such a peak
    (the lowest gain needed within it, averaged over half of it, so it never lets a peak through) and comes back up
    over `release` seconds after it."""
    n = int(look * ms.SR)
    need = np.minimum(1, ceiling / np.maximum(np.abs(x), 1e-12))
    eased = uniform_filter1d(minimum_filter1d(need, 2 * n + 1), n + 1)
    a = 1 - np.exp(-1 / (release * ms.SR))
    gain = np.empty_like(eased)
    g = 1.0
    for i, e in enumerate(eased):
        g = min(e, g + (1 - g) * a)
        gain[i] = g
    return x * gain, gain


def main():
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ms.ROOT, 'src/main/resources/assets/celestialfall/sounds/meteor_fall.ogg')
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-f', 'f32le', '-ac', '2', '-ar', str(ms.SR), '-'],
                         capture_output=True, check=True).stdout
    stereo = np.frombuffer(raw, np.float32).reshape(-1, 2).astype(np.float64)
    x = ms.high(stereo.mean(axis=1)[int(TRIM * ms.SR):], 28)
    fade_in, fade_out = int(.01 * ms.SR), int(.2 * ms.SR)
    x[:fade_in] *= np.linspace(0, 1, fade_in)
    x[-fade_out:] *= np.linspace(1, 0, fade_out)
    x *= 10 ** ((LOUDNESS - ms.loudness(x)) / 20)
    x, gain = limit(x, 10 ** (ms.CEILING_DB / 20))
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'meteor_fall.wav')
        wavfile.write(wav, ms.SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', out], check=True)
    loud = np.abs(x) > 10 ** (-30 / 20)
    print(f'meteor_fall: {len(x) / ms.SR:.2f} s, {ms.loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB, '
          f'sound from {np.argmax(loud) / ms.SR:.2f} s to {(len(x) - np.argmax(loud[::-1])) / ms.SR:.2f} s; limiter down '
          f'by up to {-20 * np.log10(gain.min()):.1f} dB, for {100 * (gain < .999).mean():.1f}% of it')


if __name__ == '__main__':
    main()

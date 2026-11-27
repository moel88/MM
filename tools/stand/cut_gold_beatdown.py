"""Gold Experience's beatdown (G, 2.150.0): its cutscene's sound, from the video the user sent ("이번에는 G스킬
비트다운이고 7페이지 무다러쉬야 효과음은 영상을 보면 되고", the scene of the seven-page muda, 30.25 s).

usage: python3 -I tools/stand/cut_gold_beatdown.py VIDEO.mp4 [OUT_DIR]

The cutscene is the video without its first shot (asked and answered: "눈 클로즈업 빼고 영상 그대로"): the close-up of
the eyes ends 0.633 s in (the 38th of its 60 frames a second), and the second shot, the first fists, begins there. So
the sound is cut there too and runs to the video's end, 29.62 s of it: "muda muda ..." all through, the "WRYYY" from
14.65 s of the cut, the last "muda!" and its blow at 28.95 s (GoldExperienceRules.BEAT_*: the recording's second tau
is BEAT_CUT + 20 tau ticks into the beatdown). It is played as the opening punch lands (client/stand/GoldBeatdown).

Nothing else is changed: mono (the two channels averaged) at 48 kHz, 3 ms faded in and 80 ms out, its peak put 1 dB
under full scale, as the user's other recordings are (tools/stand/cut_muda.py).
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 48000
PEAK_DB = -1.0
FADE_IN, FADE_OUT = 0.003, 0.080
# where the video's second shot begins (seconds): the cutscene's first moment
START = 38 / 60
NAME = 'gold_experience_beatdown'


def decode(path, channels=2):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', str(channels), '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, channels).astype(np.float64).mean(axis=1)


def fade(x):
    fi, fo = int(FADE_IN * RATE), int(FADE_OUT * RATE)
    x = x.copy()
    x[:fi] *= np.linspace(0, 1, fi, endpoint=False)
    x[-fo:] *= np.linspace(1, 0, fo)
    return x


def write(x, path):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '6', str(path)], input=x.astype(np.float32).tobytes(), check=True)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    x = decode(sys.argv[1])
    cut = fade(x[int(START * RATE):])
    cut *= 10 ** (PEAK_DB / 20) / np.abs(cut).max()
    write(cut, out / (NAME + '.ogg'))
    print('%s.ogg: %.3f s from %.3f s of the video, gain %.2f dB' % (NAME, len(cut) / RATE, START, 20 * np.log10(10 ** (PEAK_DB / 20) / np.abs(x[int(START * RATE):]).max())))


if __name__ == '__main__':
    main()

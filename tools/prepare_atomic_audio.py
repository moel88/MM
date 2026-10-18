"""Usage: python3 tools/prepare_atomic_audio.py /path/to/1000022029.mp4"""
from pathlib import Path
import sys
from audio_remake import atomic
atomic(Path(sys.argv[1]).resolve(strict=True))

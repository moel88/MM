"""Usage: python3 tools/prepare_heaven_audio.py /path/to/1000022226.mp4"""
from pathlib import Path
import sys
from audio_remake import heaven
heaven(Path(sys.argv[1]).resolve(strict=True))

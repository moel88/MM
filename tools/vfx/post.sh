#!/bin/sh
# Runs a Veil post pipeline offscreen on a PNG (Mesa llvmpipe on Xvfb), its stages, framebuffers and
# programs read from src/main/resources/assets/celestialfall/pinwheel the way Veil reads them.
# usage: tools/vfx/post.sh PIPELINE IN.png OUT.png Uniform=value ...   (PIPELINE: pinwheel/post/PIPELINE.json)
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
NAME=$1; IN=$2; OUT=$3; shift 3
[ -f "$ROOT/build/test-classpath.txt" ] || (cd "$ROOT" && ./gradlew --offline -q writeCombatClasspath)
CP=$(cat "$ROOT/build/test-classpath.txt")
mkdir -p "$ROOT/build/vfx-preview"
javac -proc:none -encoding UTF-8 -cp "$CP" -d "$ROOT/build/vfx-preview" "$ROOT/tools/vfx/PostPreview.java"
D=199; while [ -e /tmp/.X$D-lock ]; do D=$((D+1)); done
Xvfb :$D -screen 0 1280x800x24 -nolisten tcp >/dev/null 2>&1 & XP=$!
sleep 1
DISPLAY=:$D LIBGL_ALWAYS_SOFTWARE=true GALLIUM_DRIVER=llvmpipe java -cp "$ROOT/build/vfx-preview:$CP" dev.sangwoo.botandtools.PostPreview \
  "$ROOT/src/main/resources/assets/celestialfall/pinwheel" "$NAME" "$IN" "$OUT" "$@" || { kill $XP; exit 1; }
kill $XP

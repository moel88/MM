#!/bin/sh
# Offscreen preview of the VFX materials with the real shaders (Mesa llvmpipe on Xvfb).
# usage: tools/vfx/preview.sh OUT_DIR WIDTHxHEIGHT R shot...   (shot = name@sec@ex,ey,ez@tx,ty,tz@fov, blocks from the cast spot)
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$1; SIZE=$2; R=$3; shift 3
[ -f "$ROOT/build/test-classpath.txt" ] || (cd "$ROOT" && ./gradlew --offline -q writeCombatClasspath)
CP=$(cat "$ROOT/build/test-classpath.txt")
mkdir -p "$ROOT/build/vfx-preview"
javac -proc:none -encoding UTF-8 -cp "$CP" -d "$ROOT/build/vfx-preview" "$ROOT/tools/vfx/VfxPreview.java"
D=199; while [ -e /tmp/.X$D-lock ]; do D=$((D+1)); done
Xvfb :$D -screen 0 1280x800x24 -nolisten tcp >/dev/null 2>&1 & XP=$!
sleep 1
DISPLAY=:$D LIBGL_ALWAYS_SOFTWARE=true GALLIUM_DRIVER=llvmpipe java -cp "$ROOT/build/vfx-preview:$CP" dev.sangwoo.botandtools.VfxPreview \
  "$ROOT/src/main/resources/assets/celestialfall/shaders/core" "$OUT" "$SIZE" "$R" "$@" || { kill $XP; exit 1; }
kill $XP

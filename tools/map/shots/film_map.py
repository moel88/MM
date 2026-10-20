"""python3 tools/map/shots/film_map.py FRAMES_DIR OUT.mp4 TITLE [CAPTIONS]   (FILM_WIDTH=1280 by default)

Makes a 60 fps film from a flight filmed by run_map_shots.py: FRAMES_DIR/00000.png, 00001.png, ... are consecutive
frames 1/60 s apart on the flight's own clock, each a separate game frame (the tool keeps a frame only once the terrain
in view is complete and then moves the camera on), so no frame repeats. CAPTIONS is "START-END:TEXT|..." in seconds;
the title stays in the corner throughout. Needs Pillow, ffmpeg and a font with Hangul (fonts-wqy-zenhei).
"""
import os, subprocess, sys, tempfile, glob
from PIL import Image, ImageDraw, ImageFont

src, out, title = sys.argv[1], sys.argv[2], sys.argv[3]
captions = []
if len(sys.argv) > 4 and sys.argv[4]:
    for part in sys.argv[4].split('|'):
        span, text = part.split(':', 1)
        a, b = span.split('-')
        captions.append((float(a), float(b), text))
FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
big, small = ImageFont.truetype(FONT, 26), ImageFont.truetype(FONT, 22)
W = int(os.environ.get('FILM_WIDTH', '1280'))
frames = sorted(glob.glob(os.path.join(src, '[0-9]' * 5 + '.png')))
if not frames: sys.exit('no frames in ' + src)
for k, f in enumerate(frames):
    if os.path.basename(f) != '%05d.png' % k: sys.exit('frame %d is missing' % k)
tmp = tempfile.mkdtemp()
for k, f in enumerate(frames):
    im = Image.open(f).convert('RGB')
    if im.width != W: im = im.resize((W, round(im.height * W / im.width)), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    t = k / 60
    def label(xy, text, font):
        x, y = xy
        for dx, dy in ((-2, 0), (2, 0), (0, -2), (0, 2), (-2, -2), (2, 2), (-2, 2), (2, -2)): d.text((x + dx, y + dy), text, font=font, fill=(0, 0, 0))
        d.text((x, y), text, font=font, fill=(255, 255, 255))
    label((18, 14), title, big)
    for a, b, text in captions:
        if a <= t < b:
            w = d.textlength(text, font=small)
            label(((im.width - w) / 2, im.height - 48), text, small)
    im.save(os.path.join(tmp, '%05d.png' % k))
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '60', '-i', os.path.join(tmp, '%05d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                '-crf', os.environ.get('FILM_CRF', '20'), '-preset', 'medium', out], check=True)
print('%d frames, %.2f s at 60 fps, every frame its own game frame (0 repeated)' % (len(frames), len(frames) / 60))

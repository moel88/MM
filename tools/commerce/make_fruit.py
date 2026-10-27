"""Deterministic 32px item sprite, edited as a source-controlled pixel grid."""
from pathlib import Path
from PIL import Image,ImageDraw
out=Path(__file__).resolve().parents[2]/'src/main/resources/assets/celestialfall/textures/item/rokakaka.png'
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(15,3),(19,2),(18,7),(24,5),(25,7),(20,10),(25,11),(27,16),(28,24),(24,28),(18,30),(9,28),(5,24),(5,17),(8,11),(13,9)],fill='#55274e')
d.polygon([(14,8),(18,7),(23,11),(26,17),(26,23),(23,26),(17,28),(10,26),(7,23),(7,16),(10,12)],fill='#c7528e')
d.polygon([(12,11),(17,9),(22,12),(24,17),(23,23),(18,26),(12,24),(9,20),(10,15)],fill='#f087b1')
d.polygon([(12,12),(17,10),(20,12),(18,15),(12,17)],fill='#ffd1d5')
d.polygon([(9,22),(14,25),(20,26),(24,22),(23,27),(17,29),(10,27)],fill='#a93475')
for x,y in [(6,15),(11,9),(21,10),(25,16),(9,23),(17,24),(23,23),(16,17)]:
 d.polygon([(x-2,y+1),(x,y-3),(x+2,y),(x+1,y+2)],fill='#9e315f');d.line([(x,y-2),(x+1,y)],fill='#ffcfb4',width=1)
d.rectangle((15,3,17,9),fill='#638953');d.rectangle((16,2,18,4),fill='#8cad68')
d.polygon([(16,8),(21,4),(27,4),(24,8),(19,10)],fill='#466d52');d.line([(18,8),(24,5)],fill='#b1d180',width=1)
out.parent.mkdir(parents=True,exist_ok=True);im.save(out)

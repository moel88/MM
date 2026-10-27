"""Edit the native 2.29 capture; no synthesized game frames or audio."""
from pathlib import Path
from datetime import datetime
import subprocess, sys, json
proof, highest, reference, output = map(Path, sys.argv[1:5])
temp = output.parent / 'preview_parts_2291'; temp.mkdir(parents=True, exist_ok=True)
start = float((proof/'video-start.txt').read_text())
phases = {}
for line in (proof/'result.txt').read_text().splitlines():
    if line.startswith('PHASE '):
        _, n, ts = line.split()
        phases[int(n)] = datetime.fromisoformat(ts.replace('Z','+00:00')).timestamp()-start
font = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
common = ['-an','-r','30','-c:v','libx264','-preset','veryfast','-crf','21','-pix_fmt','yuv420p','-movflags','+faststart','-threads','2']
clips=[]
def validate_video(path, seconds):
    info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(path)]))
    duration=float(info['format']['duration'])
    assert abs(duration-seconds)<.16, (path,duration,seconds)
def label(text):
    return f"drawbox=x=0:y=0:w=iw:h=68:color=0x10101e@0.88:t=fill,drawtext=fontfile={font}:text='{text}':x=24:y=12:fontsize=23:fontcolor=white,drawtext=fontfile={font}:text='BOTAND 2.29.1 | Actual game capture | No audio':x=24:y=42:fontsize=15:fontcolor=0xb9b0cf"
for order, (phase, title) in enumerate([(8,'M1 - Reduced trail size and glow'),(3,'R - Original motion with grayscale'),(6,'T - Restrained 3D flames'),(7,'T HOLD - Reduced flame volume')]):
    dur = phases[phase+1]-phases[phase] if phase<10 else 23.6
    out=temp/f'{order:02d}.mp4'
    subprocess.run(['ffmpeg','-v','error','-ss',str(phases[phase]),'-i',str(proof/'native-session.mp4'),'-t',str(dur),'-vf',label(title),*common,'-y',str(out)],check=True)
    validate_video(out,dur)
    clips.append(out)
# Final H camera uses its own fresh capture after the framing adjustment.
start_h = float((highest/'video-start.txt').read_text())
for line in (highest/'result.txt').read_text().splitlines():
    if line.startswith('PHASE 9 '):
        phases[9] = datetime.fromisoformat(line.split()[2].replace('Z','+00:00')).timestamp()-start_h
out=temp/'H_final.mp4'
subprocess.run(['ffmpeg','-v','error','-ss',str(phases[9]),'-i',str(highest/'native-session.mp4'),'-t','6.6','-vf',label('H - Side camera and ceiling push-off'),*common,'-y',str(out)],check=True)
validate_video(out,6.6)
clips.append(out)
# Align the initial launch of both H casts; retain each video's own timing.
alignment={'reference_H_start_seconds':123.8,'minecraft_H_start_seconds':phases[9]+.10,'native_recording_fps':30,'comparison_retiming':'same rate for both; second replay half speed','audio':'none','full_capture':str(proof),'H_capture':str(highest)}
(output.parent/'preview_alignment.json').write_text(json.dumps(alignment,indent=2))
for rate, tag in [(1,'1x'),(.5,'0.5x')]:
    out=temp/f'compare_{tag}.mp4'
    speed=1/rate
    filters=f"[0:v]trim=duration=5.1,setpts={speed}*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[l];[1:v]trim=duration=5.1,setpts={speed}*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[r];[l][r]hstack,pad=1280:720:0:180:color=0x10101e,drawtext=fontfile={font}:text='H | Reference / Minecraft | {tag}':x=24:y=28:fontsize=30:fontcolor=white,drawtext=fontfile={font}:text='ORIGINAL REFERENCE':x=24:y=142:fontsize=20:fontcolor=0xcbadff,drawtext=fontfile={font}:text='BOTAND 2.29.1':x=664:y=142:fontsize=20:fontcolor=0xcbadff,drawtext=fontfile={font}:text='Same playback speed on both sides. Default skin and flat test world.':x=24:y=588:fontsize=20:fontcolor=0xb9b0cf[v]"
    subprocess.run(['ffmpeg','-v','error','-ss','123.8','-i',str(reference),'-ss',str(phases[9]+.1),'-i',str(highest/'native-session.mp4'),'-filter_complex',filters,'-map','[v]',*common,'-y',str(out)],check=True)
    validate_video(out,5.1/rate)
    clips.append(out)
concat=temp/'concat.txt';concat.write_text(''.join("file '"+str(p.resolve())+"'\n" for p in clips))
subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','0','-i',str(concat),'-c','copy','-movflags','+faststart','-y',str(output)],check=True)
total=sum(float(json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(p)]))['format']['duration']) for p in clips)
validate_video(output,total)
print(output, output.stat().st_size)

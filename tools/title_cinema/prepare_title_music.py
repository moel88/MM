"""Transcode the user-supplied title track without changing its musical timing or pitch."""
from pathlib import Path
import hashlib,json,subprocess,os,shutil,sys
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
source=HERE/'assets/title_satellites_source.mp3'
if len(sys.argv)>1:
    supplied=Path(sys.argv[1]);shutil.copyfile(supplied,source)
assert source.is_file(), 'Provide the uploaded Satellites MP3 as the only argument'
out=ROOT/'src/main/resources/assets/celestialfall/sounds/cinema';out.mkdir(parents=True,exist_ok=True)
# Measured source silence: 0..1.43267 and 130.795..132.4 seconds.
# Retain 12 ms before the first attack and the complete musical body.
start=1.420;end=130.820;duration=end-start
filters=f'atrim=start={start}:end={end},asetpts=PTS-STARTPTS,volume=-4dB,afade=t=in:d=0.012,afade=t=out:st={duration-.24}:d=0.24,aresample=48000'
encoded=subprocess.check_output(['ffmpeg','-v','error','-i',str(source),'-vn','-af',filters,'-map_metadata','-1','-metadata','title=Satellites','-metadata','artist=Tsukasa','-c:a','libvorbis','-q:a','7','-f','ogg','pipe:1'])
target=out/'title_satellites.ogg'
with target.open('wb') as f:f.write(encoded);f.flush();os.fsync(f.fileno())
p=out.parents[1]/'sounds.json';data=json.loads(p.read_text())
data['cinema_title_satellites']={'sounds':[{'name':'celestialfall:cinema/title_satellites','stream':True,'preload':False}]}
p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
meta={'title':'Satellites','artist_as_supplied':'Tsukasa','source':'User-uploaded MP3',
      'source_filename':'[Cytus IIDEEMO II] Satellites - Tsukasa (HQ) - Music Game Garden (320k).mp3',
      'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'asset_sha256':hashlib.sha256(encoded).hexdigest(),
      'source_trim_start_seconds':start,'source_trim_end_seconds':end,'duration_seconds':duration,
      'gain_db':-4,'speed':1,'pitch_unchanged':True,'streamed':True,'loop_full_track':True,
      'ownership':'User supplied music; no authorship or ownership of this recording is claimed.'}
(out/'music_sources.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:meta[k] for k in ['duration_seconds','source_trim_start_seconds','pitch_unchanged','streamed']}))

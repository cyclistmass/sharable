"""Recheck the delivered palette without Blender, device tools or app changes."""
from pathlib import Path
from html.parser import HTMLParser
from PIL import Image, ImageChops
import ast, hashlib, json, subprocess, sys, re
from io import BytesIO

ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'
sys.path.insert(0,str(Path(__file__).resolve().parent))
from animate_v2 import Overlays, TERRAINS, TAU

class Links(HTMLParser):
    def __init__(self):super().__init__();self.refs=[];self.images=0
    def handle_starttag(self,tag,attrs):
        for key,value in attrs:
            if key in ('src','href'):self.refs.append(value)
        if tag=='img':self.images+=1

def main():
    for path in Path(__file__).resolve().parent.glob('*.py'):ast.parse(path.read_text(encoding='utf-8'))
    count=0
    for r in TERRAINS:
        for tilt in ['flat','mid','max']:
            path=OUT/f'{r}_V2_{tilt}.png';im=Image.open(path)
            assert im.size==(512,512) and im.mode=='RGBA'
            box=im.getchannel('A').getbbox();assert box and min(box[:2])>0 and max(box[2:])<512
            count+=1
    for tilt in ['flat','mid','max']:
        rel=f'docs/design/tiles-3d-models/timber_V2_{tilt}.png'
        original=subprocess.run(['git','show','HEAD:'+rel],cwd=ROOT,capture_output=True,check=True).stdout
        before=Image.open(BytesIO(original)).convert('RGBA')
        after=Image.open(ROOT/rel).convert('RGBA')
        assert before.tobytes()==after.tobytes(),'Approved Timber artwork changed'
    ov=Overlays();loops=json.loads((OUT/'animation-validation.json').read_text())
    for entry in loops:
        r=entry['terrain'];path=OUT/entry['preview']
        assert hashlib.sha256((OUT/f'{r}_V2_mid.png').read_bytes()).hexdigest()==entry['input_sha256']
        # alpha_only=False catches colour differences even when alpha is unchanged.
        assert ImageChops.difference(ov.frame(r,0),ov.frame(r,TAU)).getbbox(alpha_only=False) is None
        im=Image.open(path);assert im.n_frames==64 and im.info['loop']==0
        duration=0;frames=[]
        for i in range(64):
            im.seek(i);im.load();duration+=im.info['duration']
            frame=im.convert('RGBA');assert frame.size==(512,512)
            box=frame.getchannel('A').getbbox();assert box and min(box[:2])>0 and max(box[2:])<512
            frames.append(frame.convert('RGBa'))
        assert duration==8000
        assert ImageChops.difference(frames[0],frames[16]).getbbox(alpha_only=False) is not None,r
    html=(OUT/'palette-v2.html').read_text(encoding='utf-8');parser=Links();parser.feed(html)
    assert parser.images==26
    for ref in parser.refs:assert (OUT/ref).is_file(),ref
    js=re.search(r'<script>(.*?)</script>',html,re.S).group(1)
    checked=subprocess.run(['node','--check'],input=js,text=True,capture_output=True)
    assert checked.returncode==0,checked.stderr
    subprocess.run(['git','diff','--exit-code','--','app'],cwd=ROOT,capture_output=True,check=True)
    result=dict(static_images=count,loop_files=6,decoded_frames=384,loop_ms=8000,approved_timber_pixels_unchanged=True,
                app_sources_unchanged=True,html_images=parser.images,local_references=len(parser.refs),javascript_syntax='passed',python_syntax='passed',rgba_seam_checks='passed')
    (OUT/'checks-v2.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()

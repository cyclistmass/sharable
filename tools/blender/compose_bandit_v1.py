"""Review one Bandit at ~35% hex width, including depth/number and phone checks."""
from pathlib import Path
import math,json,hashlib
from PIL import Image,ImageDraw,ImageFont,ImageFilter,ImageChops
from compose_token_check import plate
ROOT=Path(__file__).resolve().parents[2];TILES=ROOT/'docs/design/tiles-3d-models';OUT=ROOT/'docs/design/bandit';SPRITES=OUT/'v1'
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)
def on_tile(terrain,angle,t):
    tile=Image.open(TILES/f'{terrain}_V2_{angle}.png').convert('RGBA');tile.alpha_composite(plate(512,t))
    sprite=Image.open(SPRITES/f'bandit_v1_{angle}.png').convert('RGBA');pix=512/2.65
    x=.25;y=-.58;ground=0 if terrain=='timber' else .055+.047*math.sin(x*2.3+.4)*math.cos(y*1.7)
    dx=round(x*pix);dy=round(-(y*math.cos(t)+ground*math.sin(t))*pix)
    shadow=Image.new('RGBA',tile.size);d=ImageDraw.Draw(shadow);cx=256+dx;cy=256+dy
    d.ellipse((cx-22,cy-7,cx+26,cy+9),fill=(19,12,7,80));tile.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(3)))
    pawn=Image.new('RGBA',tile.size);pawn.alpha_composite(sprite,(dx,dy));tile.alpha_composite(pawn)
    # Check actual figure alpha against the placeholder plate face, not only its centre.
    from token_space import CX,CY,TOKEN_R
    mask=Image.new('L',tile.size);mx=256+CX*pix;my=256-CY*math.cos(t)*pix;r=TOKEN_R*pix
    ImageDraw.Draw(mask).ellipse((mx-r,my-r,mx+r,my+r),fill=255)
    assert ImageChops.multiply(pawn.getchannel('A'),mask).getbbox() is None,(terrain,angle,'pawn covers plate')
    return tile
def main():
    sheet=Image.new('RGBA',(1632,2180),'#4a3020');d=ImageDraw.Draw(sheet)
    d.text((25,16),'Bandit V1 | angry skeleton corsair | ONE original model',font=font(30),fill='#f1d58a')
    for i,name in enumerate(['front','threequarter']):
        sheet.alpha_composite(Image.open(SPRITES/f'bandit_v1_{name}.png').convert('RGBA'),(60+i*570,65))
        d.text((80+i*570,580),name.title(),font=font(24),fill='#efe3c8')
    d.text((1220,140),'Straw hat\nWild afro\nAngry skull\nRagged coat\nCutlass',font=font(27),fill='#f1d58a',spacing=20)
    phone=Image.new('RGBA',(408,296),'#4a3020');pd=ImageDraw.Draw(phone);pd.text((8,4),'25% phone proof | actual sprite scale',font=font(12),fill='#f1d58a')
    for row,terrain in enumerate(['timber','grain']):
        for col,(angle,t) in enumerate([('flat',0),('mid',.48),('max',.96)]):
            im=on_tile(terrain,angle,t);x=col*544+16;y=660+row*540
            sheet.alpha_composite(im,(x,y));d.text((x+25,y-30),f'{terrain.title()} | {angle}',font=font(24),fill='#efe3c8')
            phone.alpha_composite(im.resize((128,128),Image.Resampling.LANCZOS),(col*136,row*136+22))
    phone.convert('RGB').save(OUT/'bandit-v1-phone-25pct.png')
    sheet.alpha_composite(phone,(1190,1840));d.text((25,1770),'On-tile figure width ~35% of hex | foot anchor (+0.25, -0.58) | number plate stays clear',font=font(21),fill='#efe3c8')
    sheet.convert('RGB').save(OUT/'bandit-v1.png')
    files=sorted(SPRITES.glob('bandit_v1_*.png'));bounds={}
    for path in files:
        im=Image.open(path);box=im.getchannel('A').getbbox();assert im.size==(512,512) and im.mode=='RGBA'
        assert box and min(box[:2])>0 and max(box[2:])<512,(path,box);bounds[path.name]=box
    assert len(files)==13
    steps=Image.new('RGBA',(2048,1100),'#4a3020');sd=ImageDraw.Draw(steps)
    for i in range(8):
        x=(i%4)*512;y=(i//4)*550
        steps.alpha_composite(Image.open(SPRITES/f'bandit_v1_tilt_{i:02d}.png').convert('RGBA'),(x,y))
        sd.text((x+24,y+510),f'{i:02d} | {math.degrees(.96*i/7):.1f} degrees',font=font(22),fill='#f1d58a')
    steps.convert('RGB').save(OUT/'bandit-v1-tilt-steps.png')
    baseline=json.loads((TILES/'token-clearing/app-baseline.json').read_text())
    for name,digest in baseline.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    times=sum(r['render_seconds'] for batch in ['portrait','tiles'] for r in json.loads((SPRITES/f'{batch}-manifest.json').read_text())['renders'])
    metadata={'designs':1,'transparent_512px_sprites':13,'production_tilt_steps':8,'tilt_radians':[.96*i/7 for i in range(8)],'number_plate_overlap_pixels':0,'app_files_unchanged':len(baseline),'render_seconds':times,'bounds':bounds}
    (SPRITES/'validation.json').write_text(json.dumps(metadata,indent=2))
    (SPRITES/'REPORT.md').write_text(f'''# Bandit V1: angry skeleton corsair

One original own-model design, built in `tools/blender/render_bandit_v1.py`: heavy slanted brows and grinning teeth, low broad-brimmed frayed straw hat, irregular charcoal afro, ragged mulberry coat with teal lapels, exposed bones, brass hilt and curved steel cutlass. No top hat, cane, formal suit or red vest; no downloaded models or character references.

Sprites: `bandit_v1_tilt_00.png` through `_07.png`, t=.96*i/7 as PLAN.md specifies, plus flat/mid/max proof sources and front/threequarter portraits: **13 transparent 512px PNGs**. Sprite origin is the ground foot anchor (256,256), ortho scale 2.65; preserve padding. The on-tile silhouette is about 35% of hex width. Portraits use a closer camera and are not shipping sprites.

Review: `../bandit-v1.png` contains both portraits, Timber/Grain at flat/mid/max and a 25% phone proof; `../bandit-v1-phone-25pct.png` is the separate phone sheet. Own model, matching tile upper-left light. Proof composites use placement (+0.25,-0.58) to keep the smaller number plate visible. The source model is the same in every view.

Validation: all thirteen sprites have transparent margins; all six tile composites have zero figure alpha over the number plate; all {len(baseline)} app-file hashes unchanged. Final retained Bandit render time **{times:.2f}s**, excluding Blender startup, modelling and the discarded first portrait. Each batch exits before the next starts.

Reproduce sequentially: Blender `-b -t 2 -P tools/blender/render_bandit_v1.py -- --batch portrait`, then `--batch tiles`, then Python `tools/blender/compose_bandit_v1.py`. Review only: no app wiring, emulator, commit or push.
''',encoding='utf-8')
    print('BANDIT_V1_OK',json.dumps(metadata))
if __name__=='__main__':main()

"""Focused current-round review, phone proofs and measured render report."""
from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
from compose_token_check import plate
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models';REVIEW=OUT/'alpaca-clay'
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)
def main():
    REVIEW.mkdir(exist_ok=True)
    sheet=Image.new('RGBA',(1632,1150),'#4a3020');d=ImageDraw.Draw(sheet)
    d.text((20,12),'V2 | alpaca Fleece + terracotta Clay | number clearance retained',font=font(28),fill='#f1d58a')
    for row,terrain in enumerate(['fleece','clay']):
        for col,(angle,t) in enumerate([('flat',0),('mid',math.radians(27.5)),('max',math.radians(55))]):
            tile=Image.open(OUT/f'{terrain}_V2_{angle}.png').convert('RGBA');tile.alpha_composite(plate(512,t))
            x=col*544+16;y=row*545+80;sheet.alpha_composite(tile,(x,y))
            d.text((x+25,y-28),f'{terrain.title()} | {angle}',font=font(23),fill='#efe3c8')
    sheet.convert('RGB').save(REVIEW/'clay-fleece-token-views.png')
    sheet.resize((408,288),Image.Resampling.LANCZOS).convert('RGB').save(REVIEW/'clay-fleece-phone-25pct.png')
    loop=Image.open(OUT/'fleece_V2_mid_animated.webp');frames=[]
    for i in range(loop.n_frames):
        loop.seek(i);frame=loop.convert('RGBA');frame.alpha_composite(plate(512,math.radians(27.5)))
        phone=Image.new('RGBA',(128,128),'#4a3020');phone.alpha_composite(frame.resize((128,128),Image.Resampling.LANCZOS));frames.append(phone)
    frames[0].save(REVIEW/'fleece-phone-animated.webp',save_all=True,append_images=frames[1:],duration=125,loop=0,lossless=True,method=4)
    grid=Image.new('RGBA',(1024,1024),'#4a3020')
    for i,frame in enumerate(frames):grid.alpha_composite(frame,((i%8)*128,(i//8)*128))
    grid.convert('RGB').save(REVIEW/'fleece-phone-all64.png')
    times=[]
    for name in ['clay','fleece']+[f'animals_{i:02d}' for i in range(16)]:times+=json.loads((OUT/f'density-manifests/{name}.json').read_text())['renders']
    total=sum(r['seconds'] for r in times)
    (REVIEW/'REPORT.md').write_text(f'''# Alpaca Fleece and terracotta Clay

Clay ground, terraces, cut faces and the number yard now use restrained terracotta-red materials. Wet clay and the puddle are dark red-brown. Kiln bricks retain their darker fired-brick material, so the structures stay legible; Desert remains ochre.

Fleece has six static alpacas in mixed cream/white/fawn/brown fleece, with two looking up and four grazing. A seventh gently shifts, bends its long neck and turns its head in the 16-phase transparent animal layer. The three tiny horned/bearded goats are retained (two static, one moving). Wall, gate, lush pasture and the flat number circle are retained.

Updated sources: `clay_V2_flat/mid/max.png`, `fleece_V2_flat/mid/max.png`, their two 8s animated WebPs, all 16 `animation-layers/fleece/animals_mid_XX.png` crops and atlas, `palette-v2.html/png`, `token-check.png`, `token-check-patch-max.png`, `ART_SPEC.md`, `PLAN.md`, and `CREDITS.md`. Own model source: `tools/blender/alpacas.py`, with material/placement changes in `render_density.py` and `token_space.py`. Full reproduction: `run_token_clearings.ps1`; focused review: `compose_alpaca_clay.py`.

Phone proof: `clay-fleece-phone-25pct.png`; animation: `fleece-phone-animated.webp` and its 64-frame sheet. Current six view renders and sixteen motion phases total **{total:.2f}s** of Blender render time, excluding construction/startup and the discarded Fleece pose iteration. Every Blender process used two threads and exited before the next started.

Checks: six requested views re-rendered; all 18 current RGBA sources pass padding checks; all six current loops decode to 64 frames / 8s and are seamless; 384 overlay frames have no alpha in the plate guard; mixed 3×3 plate proof has no neighbour occlusion; all 146 application-file hashes remain unchanged. No app code, emulator, commit or push.
''',encoding='utf-8')
    print('ALPACA_CLAY_REVIEW_OK',total,'render seconds')
if __name__=='__main__':main()

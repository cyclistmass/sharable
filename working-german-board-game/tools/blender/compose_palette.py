"""Pillow contact sheet and sprite-based board patches; no image generation required.
Run after render_models.py with Python 3 + Pillow.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math, json
from io import BytesIO

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/design/tiles-3d-models'
WOOD='#4A3020'; GOLD='#F1D58A'; INK='#EFE3C8'
def font(size):
    return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',size)

def patch(style):
    im=Image.new('RGBA',(1024,680),WOOD); d=ImageDraw.Draw(im)
    d.text((28,18),f'V{style} | 3 x 3 sprite board patch | maximum tilt 55.0°',font=font(23),fill=GOLD)
    d.text((28,54),'Back-to-front, transparent padding retained; terrain overdraws rear neighbours',font=font(15),fill=INK)
    # Local pixels per world unit. Match Layout.project, including mild row perspective.
    scale=153; t=.96; squash=math.cos(t); centers=[]
    for row in range(3):
        y=(row-1)*1.5
        for col in range(3):
            x=(col-1)*math.sqrt(3)+(row%2)*math.sqrt(3)/2-.3
            k=1+.22*math.sin(t)*y*squash/4.2
            resource='timber' if (row+col)%2==0 else 'stone'
            cx=512+x*scale*k;cy=380+y*squash*scale*k
            centers.append(dict(row=row,col=col,resource=resource,x=x,y=y,k=k,cx=cx,cy=cy))
    for c in sorted(centers,key=lambda c:c['y']):
        tile=Image.open(OUT/f"{c['resource']}_V{style}_max.png").convert('RGBA')
        size=round(2.65*scale*c['k']); tile=tile.resize((size,size),Image.Resampling.LANCZOS)
        im.alpha_composite(tile,(round(c['cx']-size/2),round(c['cy']-size/2)))
    d=ImageDraw.Draw(im)
    d.text((28,626),'Preview omits gameplay pieces and number tokens. All nine bases use game spacing (R = 0.93).',font=font(15),fill=INK)
    im.convert('RGB').save(OUT/f'board-patch_V{style}_max.png')
    return im

patches=[patch(1),patch(2)]
sheet=Image.new('RGB',(1450,2370),WOOD); d=ImageDraw.Draw(sheet)
d.text((32,22),'Working German Board Game | real modelled tile palette',font=font(32),fill=GOLD)
d.text((32,66),'Own procedural Blender geometry | upper-left light | transparent orthographic sprites | palette only',font=font(18),fill=INK)
for style in [1,2]:
    top=110+(style-1)*1120
    label='V1 · faceted low-poly' if style==1 else 'V2 · sculpted stylised foliage + detailed crags'
    d.text((32,top),label,font=font(26),fill=GOLD)
    for col,(name,angle) in enumerate([('flat','FLAT · 0°'),('mid','MID · 27.5°'),('max','MAX · 55.0°')]):
        x=130+col*420
        d.text((x+50,top+42),angle,font=font(19),fill=INK)
        for row,res in enumerate(['timber','stone']):
            y=top+75+row*280
            tile=Image.open(OUT/f'{res}_V{style}_{name}.png').convert('RGBA').resize((300,300),Image.Resampling.LANCZOS)
            sheet.paste(tile,(x,y),tile)
            if col==0: d.text((20,y+135),res.title(),font=font(19),fill=INK)
    p=patches[style-1].resize((620,412),Image.Resampling.LANCZOS)
    sheet.paste(p.convert('RGB'),(780,top+650))
    d.text((38,top+690),'Same scene, three camera elevations: 90°, 62.5°, 35.0°.',font=font(18),fill=INK)
    d.text((38,top+722),'Right: nine individually composited sprites, using the current Layout projection.',font=font(16),fill=INK)
sheet.save(OUT/'palette-models.png')

# Validation evidence, measured alpha extents and actual encoding sizes.
records=[]
for path in sorted(OUT.glob('*_V[12]_*.png')):
    if path.name.startswith('board'): continue
    im=Image.open(path); a=im.getchannel('A'); box=a.getbbox()
    assert im.size==(512,512) and im.mode=='RGBA'
    assert box and box[0]>0 and box[1]>0 and box[2]<512 and box[3]<512, (path,box)
    lossless=BytesIO(); im.save(lossless,format='WEBP',lossless=True,method=6)
    lossy=BytesIO(); im.save(lossy,format='WEBP',quality=90,method=6)
    records.append(dict(file=path.name,bytes=path.stat().st_size,alpha_bounds=box,
                        webp_lossless_bytes=len(lossless.getvalue()),webp_q90_bytes=len(lossy.getvalue())))
(OUT/'validation.json').write_text(json.dumps(records,indent=2))
print('Validated',len(records),'RGBA sprites, transparent padding on every edge.')

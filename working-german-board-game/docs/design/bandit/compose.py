"""Compose original Blender renders with existing own tile art. Run with py."""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw, ImageFont
OUT=Path(__file__).resolve().parent
ROOT=OUT.parents[2]
L=Image.Resampling.LANCZOS
def font(n):return ImageFont.truetype('C:/Windows/Fonts/arial.ttf',n)
sources={'flat':ROOT/'docs/design/tiles-3d-models/timber_V2_flat.png','max':ROOT/'docs/design/tiles-3d-models/timber_V2_max.png','painted':ROOT/'app/src/main/res/drawable-nodpi/tile_timber.webp'}
titles={'B1':'Pale-rim cloak','B2':'Lantern carrier','B3':'Ivory beacon ring'}
sheet=Image.new('RGB',(1536,1840),'#252c34');d=ImageDraw.Draw(sheet)
d.text((32,22),'BANDIT / original Blender concepts',font=font(40),fill='#fff2d5')
d.text((32,78),'35% of visible hex width | upper-left light | view 25% proof for phone scale',font=font(24),fill='#b7c4ce')
metrics={}
for col,opt in enumerate(titles):
    raw=Image.open(OUT/f'{opt}-render.png').convert('RGBA');crop=raw.crop(raw.getbbox())
    # All silhouettes share height; transparent padding preserves room for ground effects.
    scale=min(390/crop.height,370/crop.width);body=crop.resize((round(crop.width*scale),round(crop.height*scale)),L)
    sprite=Image.new('RGBA',(512,512));bx=(512-body.width)//2;by=455-body.height
    # Soft contact shadow / lantern pool are premultiplied through Pillow RGBA compositing.
    effects=Image.new('RGBA',(512,512));pix=effects.load();cx=256;cy=445
    for y in range(512):
        for x in range(512):
            r=((x-cx)/130)**2+((y-cy)/34)**2
            a=int(95*math.exp(-2.8*r));pix[x,y]=(6,8,12,a)
    sprite.alpha_composite(effects)
    if opt=='B2':
        glow=Image.new('RGBA',(512,512));p=glow.load()
        for y in range(512):
            for x in range(512):
                r=((x-296)/157)**2+((y-437)/49)**2
                p[x,y]=(255,168,43,int(150*math.exp(-2.9*r)))
        sprite.alpha_composite(glow)
    sprite.alpha_composite(body,(bx,by));sprite.save(OUT/f'{opt}.png')
    d.text((col*512+28,133),f'{opt}  {titles[opt]}',font=font(29),fill='#fff2d5')
    # Large sprite preview, using the exact delivered alpha sprite.
    preview=sprite.resize((300,300),L);sheet.paste(preview,(col*512+106,179),preview)
    for row,(kind,path) in enumerate(sources.items()):
        tile=Image.open(path).convert('RGBA');tile.thumbnail((472,472),L)
        tb=tile.getbbox();hex_width=tb[2]-tb[0]
        # Pawn BODY, excluding soft lighting halo, spans exactly 35% of the hex.
        factor=hex_width*.35/body.width;stamp=sprite.resize((round(512*factor),round(512*factor)),L)
        canvas=Image.new('RGBA',(512,432),'#303b38')
        tx=(512-tile.width)//2;ty=(432-tile.height)//2
        canvas.alpha_composite(tile,(tx,ty))
        footx=tx+(tb[0]+tb[2])/2;footy=ty+tile.height*(.60 if kind=='max' else .58)
        canvas.alpha_composite(stamp,(round(footx-256*factor),round(footy-445*factor)))
        canvas.convert('RGB').save(OUT/f'{opt}-on-timber-{kind}.png')
        sy=500+row*444;sheet.paste(canvas.convert('RGB'),(col*512,sy))
        d.text((col*512+20,sy+8),{'flat':'V2 / flat','max':'V2 / max tilt','painted':'Current painted timber'}[kind],font=font(25),fill='#fff2d5')
        metrics[f'{opt}-{kind}']={'visible_hex_width_px':hex_width,'pawn_body_width_px':round(body.width*factor),'width_ratio':.35,'phone_hex_width_px':hex_width/4,'phone_pawn_width_px':body.width*factor/4}
sheet.save(OUT/'bandit-options.png');sheet.resize((384,460),L).save(OUT/'bandit-options-phone-25pct.png')
(OUT/'validation.json').write_text(json.dumps(metrics,indent=2))
print('Saved 3 RGBA sprites, 9 composites, contact sheet and 25% phone proof.')

"""Number plates use the game's upright circle, not a projected ground ellipse."""
from pathlib import Path
import math,json,hashlib
from PIL import Image,ImageDraw,ImageFont,ImageChops
from token_space import CX,CY,TOKEN_R,CLEAR_R,screen_mask
from animate_v2 import Overlays,TAU
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'
TERRAINS=['timber','stone','fleece','grain','clay','desert'];ANGLES=[('flat',0),('mid',math.radians(27.5)),('max',math.radians(55))]
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)
def plate(size=512,tilt=0,number=6):
    im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im);p=size/2.65
    x=size/2+CX*p;y=size/2-CY*math.cos(tilt)*p;r=TOKEN_R*p
    def disk(xx,yy,fill):d.ellipse((xx-r,yy-r,xx+r,yy+r),fill=fill)
    disk(x+.05*p,y+.07*p,(18,12,7,65));disk(x,y+.04*p,'#9b7440');disk(x,y,'#eed9ad')
    # Subtle face grain, clipped to the circle.
    grain=Image.new('RGBA',im.size);gd=ImageDraw.Draw(grain)
    for j in range(-4,5):gd.line((x-r,y+j*r/5,x+r,y+j*r/5),fill=(134,99,49,26),width=max(1,round(p*.006)))
    mask=Image.new('L',im.size);ImageDraw.Draw(mask).ellipse((x-r,y-r,x+r,y+r),fill=255)
    grain.putalpha(ImageChops.multiply(grain.getchannel('A'),mask));im.alpha_composite(grain);d=ImageDraw.Draw(im)
    d.ellipse((x-r,y-r,x+r,y+r),outline='#684825',width=max(1,round(.02*p)))
    d.text((x,y-r*.05),str(number),font=font(round(.264*p)),fill='#982e23' if number in (6,8) else '#38281b',anchor='mm')
    for i in range(5):d.ellipse((x+(i-2)*r*.12-r*.05,y+r*.55-r*.05,x+(i-2)*r*.12+r*.05,y+r*.55+r*.05),fill='#982e23')
    return im
def main():
    sheet=Image.new('RGBA',(1750,3500),'#4a3020');d=ImageDraw.Draw(sheet)
    d.text((25,20),'V2 | shared number-plate clearing',font=font(32),fill='#f1d58a')
    d.text((25,65),'Centre (-0.36, +0.30) | plate r 0.30 | flat clearing r 0.345 | plate stays round at tilt',font=font(21),fill='#efe3c8')
    for row,r in enumerate(TERRAINS):
        y=120+row*555;d.text((15,y+235),r.title(),font=font(23),fill='#f1d58a')
        for col,(name,t) in enumerate(ANGLES):
            tile=Image.open(OUT/f'{r}_V2_{name}.png').convert('RGBA');tile.alpha_composite(plate(512,t,6 if row%2==0 else 8))
            x=120+col*530;sheet.alpha_composite(tile,(x,y));d.text((x+120,y+495),f'{name.title()} {math.degrees(t):g}°',font=font(20),fill='#efe3c8')
    sheet.convert('RGB').save(OUT/'token-check.png')
    sheet.resize((438,875),Image.Resampling.LANCZOS).convert('RGB').save(OUT/'token-clearing/token-check-phone-25pct.png')
    # Independent sprites at the game's nominal hex spacing. Each tile and plate
    # form one depth-sorted layer: foreground neighbours can actually occlude it.
    board=Image.new('RGBA',(1500,1000),'#4a3020');draw=ImageDraw.Draw(board)
    draw.text((25,20),'V2 | mixed 3 × 3 | maximum tilt with upright plates',font=font(28),fill='#f1d58a')
    order=['stone','timber','fleece','grain','clay','desert','timber','stone','grain'];scale=205;t=math.radians(55)
    plate_masks=[];occlusions=[]
    for row in range(3):
        y=(row-1)*1.5
        for col in range(3):
            x=(col-1)*math.sqrt(3)+(row%2)*math.sqrt(3)/2-.30
            k=1+.22*math.sin(t)*y*math.cos(t)/4.2;cx=745+x*scale*k;cy=580+y*math.cos(t)*scale*k
            tile=Image.open(OUT/f'{order[row*3+col]}_V2_max.png').convert('RGBA');tile.alpha_composite(plate(512,t,6))
            size=round(2.65*scale*k);tile=tile.resize((size,size),Image.Resampling.LANCZOS);xy=(round(cx-size/2),round(cy-size/2))
            alpha=Image.new('L',board.size);alpha.paste(tile.getchannel('A'),xy)
            for index,mask in enumerate(plate_masks):
                overlap=ImageChops.multiply(alpha,mask).point(lambda v:255 if v>128 else 0)
                count=overlap.histogram()[255]
                if count:occlusions.append({'rear_tile_index':index,'front_tile_index':row*3+col,'covered_plate_pixels':count})
            board.alpha_composite(tile,xy)
            mask=Image.new('L',board.size);md=ImageDraw.Draw(mask);px=cx+CX*scale*k;py=cy-CY*math.cos(t)*scale*k;pr=TOKEN_R*scale*k
            md.ellipse((px-pr,py-pr,px+pr,py+pr),fill=255);plate_masks.append(mask)
    draw=ImageDraw.Draw(board);draw.text((25,940),'Desert uses a proof plate here; it is normally unnumbered. Rear-to-front tile + plate layers.',font=font(20),fill='#efe3c8')
    board.convert('RGB').save(OUT/'token-check-patch-max.png');board.resize((375,250),Image.Resampling.LANCZOS).convert('RGB').save(OUT/'token-clearing/token-check-patch-phone-25pct.png')
    # One combined deliverable includes both the eighteen views and the patch.
    combined=Image.new('RGB',(1750,4550),'#4a3020');combined.paste(sheet.convert('RGB'),(0,0));combined.paste(board.convert('RGB'),(125,3525));combined.save(OUT/'token-check.png')
    ov=Overlays();checks=[]
    for terrain in TERRAINS:
        for i in range(64):
            a=ov.overlay(terrain,TAU*i/64).getchannel('A');guard=ImageChops.invert(screen_mask())
            assert ImageChops.multiply(a,guard).getbbox() is None,(terrain,i,'overlay on plate')
        checks.append({'terrain':terrain,'checked_animation_frames':64,'plate_overlap_pixels':0})
    baseline=json.loads((OUT/'token-clearing/app-baseline.json').read_text())
    for rel,digest in baseline.items():assert hashlib.sha256((ROOT/rel).read_bytes()).hexdigest()==digest,rel
    data={'app_files_unchanged':len(baseline),'token_center_model':[CX,CY],'token_radius':TOKEN_R,'clearing_radius':CLEAR_R,'upright_circle':True,'animation_checks':checks,'mixed_patch_neighbour_occlusions':occlusions}
    (OUT/'token-clearing/validation.json').write_text(json.dumps(data,indent=2))
    assert not occlusions,('Foreground neighbour covers a number plate',occlusions)
    print('TOKEN_CHECK_OK',json.dumps(data))
if __name__=='__main__':main()

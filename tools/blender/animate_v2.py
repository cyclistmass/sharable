"""Procedural overlay reference and 8-second animated WebP palette, Python + Pillow.
Five effects have no temporal sprite assets. Fleece mixes 16 small Blender sheep layers.
Grain reuses a masked piece of its static render through a tiny displaced bitmap mesh.
No application sources are changed. Angles/world anchors are shared with the renderer.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageChops, ImageFont, ImageStat, ImageOps
import math, json, hashlib
from io import BytesIO

ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'
TAU=math.tau;TILT=.48;PIX=512/2.65
TERRAINS=['timber','stone','fleece','grain','clay','desert']
WOOD=(74,48,32,255)

def project(x,y,z=0):
    return (256+x*PIX,256-(y*math.cos(TILT)+z*math.sin(TILT))*PIX)

def pasture(x,y):
    return .024+.10*math.exp(-((x+.40)**2+(y-.23)**2)/.17)+.065*math.exp(-((x-.4)**2+(y+.17)**2)/.18)

def font(size):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',size)
def ellipse(d,c,rx,ry,fill):d.ellipse((c[0]-rx,c[1]-ry,c[0]+rx,c[1]+ry),fill=fill)

def wind_patch(source,save_mask=False):
    mask=Image.new('L',(512,512));d=ImageDraw.Draw(mask)
    for row in range(9):
        y=-.45+row*.12;left=-.64;right=.64 if y>=-.24 else .27
        # Covers only the row canopy/stems, with the cart and sheaves excluded.
        pts=[project(left,y-.042,0),project(right,y-.042,0),project(right,y+.028,.29),project(left,y+.028,.29)]
        d.polygon(pts,fill=255)
    mask=mask.filter(ImageFilter.GaussianBlur(1.2))
    if save_mask:
        folder=OUT/'animation-layers/grain';folder.mkdir(parents=True,exist_ok=True)
        mask.resize((256,256),Image.Resampling.LANCZOS).save(folder/'wind_mask_mid.png')
    patch=source.copy();patch.putalpha(ImageChops.multiply(source.getchannel('A'),mask))
    return patch

def wind_mesh(patch,p):
    # Backward source quad per grid cell, equivalent to a cached textured overlay mesh.
    cells=[];n=16
    def source(x,y):
        wave=p*2-y*.038+x*.012
        return (x-2.0*math.sin(wave),y-.65*math.cos(wave))
    for row in range(n):
        for col in range(n):
            x=col*32;y=row*32;box=(x,y,x+32,y+32)
            q=[source(x,y),source(x,y+32),source(x+32,y+32),source(x+32,y)]
            cells.append((box,tuple(v for point in q for v in point)))
    return patch.transform((512,512),Image.Transform.MESH,cells,Image.Resampling.BICUBIC)

class Overlays:
    def __init__(self):
        inputs={r:(OUT/f'{r}_V2_mid.png').read_bytes() for r in TERRAINS}
        self.base={r:Image.open(BytesIO(data)).convert('RGBA') for r,data in inputs.items()}
        self.input_hashes={r:hashlib.sha256(data).hexdigest() for r,data in inputs.items()}
        self.sheep=[]
        for i in range(16):
            layer=Image.new('RGBA',(512,512))
            layer.alpha_composite(Image.open(OUT/'animation-layers/fleece'/f'sheep_mid_{i:02d}.png').convert('RGBA'),(232,232))
            self.sheep.append(layer)
        self.wind=wind_patch(self.base['grain'],True)
        path=OUT/'animation-layers/grain';path.mkdir(parents=True,exist_ok=True)
        self.wind.resize((256,256),Image.Resampling.LANCZOS).save(path/'wind_patch_mid.png')
        self.wind.resize((256,256),Image.Resampling.LANCZOS).save(path/'wind_patch_mid.webp',lossless=True,method=4)

    def overlay(self,terrain,phase):
        p=phase%TAU
        layer=Image.new('RGBA',(512,512));d=ImageDraw.Draw(layer)
        if terrain=='timber':
            x=.04+.17*math.sin(p);y=.55+.06*math.cos(p)
            cx,cy=project(x,y,.94)
            wing=2.5+2*math.cos(p*4)
            d.line([(cx-6,cy-wing),(cx-2,cy-1),(cx,cy+1),(cx+3,cy-1),(cx+7,cy-wing*.75)],fill=(218,212,185,210),width=2)
            for j in range(3):
                a=(p/TAU+j/3)%1;fade=math.sin(math.pi*a)**2
                xx=-.3+j*.24+.035*math.sin(TAU*a);yy=.18-a*.25
                c=project(xx,yy,.55-a*.16)
                ellipse(d,c,1.5,2.3,(176,162,63,round(135*fade)))
        elif terrain=='stone':
            # Three translucent ribbons drift across the saddle, never solid clouds.
            for j in range(3):
                q=p+j*2.1;cx,cy=project(-.04+.13*math.sin(q),.19+j*.035,.57+j*.095)
                alpha=round(46+13*math.cos(q))
                pts=[(cx-29+i*6,cy+2.3*math.sin(i*.7+q)) for i in range(11)]
                d.line(pts,fill=(232,237,230,alpha),width=3+j)
            layer=layer.filter(ImageFilter.GaussianBlur(2.0))
        elif terrain=='fleece':
            x=.19+.07*math.sin(p);y=-.34+.025*math.cos(p)
            c=project(x,y,pasture(x,y)+.005)
            ellipse(d,c,18,5,(42,41,22,36))
            # Premultiplied-alpha interpolation; Pillow's straight RGBA blend would make dark fringes.
            u=p/TAU*16;i=int(u)%16;f=u-int(u)
            a=self.sheep[i].convert('RGBa');b=self.sheep[(i+1)%16].convert('RGBa')
            frame=Image.blend(a,b,f).convert('RGBA');layer.alpha_composite(frame)
        elif terrain=='grain':
            layer=wind_mesh(self.wind,p)
            # The actual image patch moves, not just painted moving glints.
        elif terrain=='clay':
            chimney=project(.485,-.125,.69)
            for j in range(9):
                age=(p/TAU*3+j/9)%1
                fade=math.sin(math.pi*age)**2
                cx=chimney[0]-age*21+4*math.sin(age*TAU+p)
                cy=chimney[1]-age*55-2
                radius=2+age*7
                ellipse(d,(cx,cy),radius,radius*.72,(124,119,106,round(75*fade)))
            layer=layer.filter(ImageFilter.GaussianBlur(1.7))
        elif terrain=='desert':
            cx,cy=project(-.28+.025*math.sin(p),-.30,.17)
            for j in range(6):
                h=j/5;rx=3+5*h
                pts=[(cx+rx*math.sin(p*2+h*6+i*.35),cy-h*32+i*.28) for i in range(14)]
                d.line(pts,fill=(154,108,57,round(68*(1-h*.7))),width=2)
            layer=layer.filter(ImageFilter.GaussianBlur(.9))
        return layer

    def frame(self,r,p):
        im=self.base[r].copy();im.alpha_composite(self.overlay(r,p));return im

def main():
    ov=Overlays();validation=[]
    atlas=Image.new('RGBA',(1024,256))
    for i in range(16):
        frame=Image.open(OUT/'animation-layers/fleece'/f'sheep_mid_{i:02d}.png').convert('RGBA')
        assert frame.size==(128,128)
        box=frame.getchannel('A').getbbox()
        assert box and min(box[:2])>0 and max(box[2:])<128,(i,box)
        atlas.alpha_composite(frame,((i%8)*128,(i//8)*128))
    atlas.save(OUT/'animation-layers/fleece/sheep_mid_atlas.webp',lossless=True,method=4)
    review=OUT/'animation-review';review.mkdir(exist_ok=True)
    for r in TERRAINS:
        start=ov.frame(r,0);end=ov.frame(r,TAU)
        assert ImageChops.difference(start,end).getbbox(alpha_only=False) is None,(r,'seam mismatch')
        frames=[ov.frame(r,TAU*i/64) for i in range(64)]
        # Exactly 64 * 125ms = 8s, no duplicated endpoint/pause.
        path=OUT/f'{r}_V2_mid_animated.webp'
        frames[0].save(path,save_all=True,append_images=frames[1:],duration=125,loop=0,lossless=True,method=3)
        read=Image.open(path);durations=[]
        for i in range(read.n_frames):
            read.seek(i);read.load();durations.append(read.info['duration'])
        assert read.n_frames==64 and sum(durations)==8000 and read.info['loop']==0
        changes=[]
        for a,b in zip(frames,frames[1:]+frames[:1]):
            diff=ImageChops.difference(a.convert('RGB'),b.convert('RGB'))
            changes.append(round(sum(ImageStat.Stat(diff.convert('L')).sum)))
        assert max(changes)>0
        # Full-tile phase sheet for visual inspection; all 64 overlay frames as a second sheet.
        sheet=Image.new('RGBA',(1536,1100),(74,48,32,255));d=ImageDraw.Draw(sheet)
        d.text((20,8),f'{r.title()} | 8-second loop | phases 0, 2, 4, 6 seconds',font=font(25),fill='#F1D58A')
        for k,i in enumerate([0,16,32,48]):
            xy=((k%2)*700+30,(k//2)*520+45)
            sheet.alpha_composite(frames[i],xy)
            ImageDraw.Draw(sheet).text((xy[0]+505,xy[1]+240),f'{i/8:g}s',font=font(20),fill='#EFE3C8')
        sheet.convert('RGB').save(review/f'{r}_phases.png')
        overlays=[ov.overlay(r,TAU*i/64) for i in range(64)]
        boxes=[im.getchannel('A').getbbox() for im in overlays]
        bounds=(max(0,min(b[0] for b in boxes)-12),max(0,min(b[1] for b in boxes)-12),
                min(512,max(b[2] for b in boxes)+12),min(512,max(b[3] for b in boxes)+12))
        grid=Image.new('RGBA',(2048,2048),(74,48,32,255))
        for i in range(64):
            layer=ImageOps.contain(overlays[i].crop(bounds),(256,256),Image.Resampling.LANCZOS)
            grid.alpha_composite(layer,((i%8)*256+(256-layer.width)//2,(i//8)*256+(256-layer.height)//2))
        grid.convert('RGB').save(review/f'{r}_all_overlay_frames.png')
        assert hashlib.sha256((OUT/f'{r}_V2_mid.png').read_bytes()).hexdigest()==ov.input_hashes[r],'Static render changed during animation generation; run serially.'
        validation.append(dict(terrain=r,preview=path.name,bytes=path.stat().st_size,frames=64,duration_ms=sum(durations),period_endpoint_equal=True,input_sha256=ov.input_hashes[r],
                               min_adjacent_change=min(changes),max_adjacent_change=max(changes),loop_boundary_change=changes[-1],review_crop=bounds))
        print('ANIMATION_OK',r,path.stat().st_size,flush=True)
    (OUT/'animation-validation.json').write_text(json.dumps(validation,indent=2))

if __name__=='__main__':main()

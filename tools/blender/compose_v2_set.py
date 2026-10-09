"""Full V2 static palette, nine-tile neighbour mocks, measurements, local HTML player."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from io import BytesIO
import math, json

ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'
TERRAINS=['timber','stone','fleece','grain','clay','desert'];WOOD='#4A3020';GOLD='#F1D58A'
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)

def board(range_only=False):
    factor=2 if range_only else 1
    im=Image.new('RGBA',(1152*factor,768*factor),WOOD);d=ImageDraw.Draw(im)
    title='Stone | R3 Alpine chain across nine neighbours' if range_only else 'V2 | six-terrain board patch'
    d.text((24*factor,16*factor),title,font=font(27*factor),fill=GOLD)
    d.text((24*factor,54*factor),'Maximum tilt 55° | independent sprites, rear-to-front | game hex spacing',font=font(18*factor),fill='#EFE3C8')
    order=['stone','stone','timber','stone','stone','fleece','clay','grain','desert']
    scale=164*factor;t=.96
    for row in range(3):
        y=(row-1)*1.5
        for col in range(3):
            x=(col-1)*math.sqrt(3)+(row%2)*math.sqrt(3)/2-.30
            k=1+.22*math.sin(t)*y*math.cos(t)/4.2
            cx=575*factor+x*scale*k;cy=442*factor+y*math.cos(t)*scale*k
            r='stone' if range_only else order[row*3+col]
            tile=Image.open(OUT/('stone_V2_max_mock1024.png' if range_only else f'{r}_V2_max.png')).convert('RGBA')
            size=round(2.65*scale*k);tile=tile.resize((size,size),Image.Resampling.LANCZOS)
            im.alpha_composite(tile,(round(cx-size/2),round(cy-size/2)))
    d=ImageDraw.Draw(im)
    d.text((24*factor,712*factor),'Palette mock: no number tokens/pieces. Ridge exits align laterally; inset tile gaps stay visible.',font=font(16*factor),fill='#EFE3C8')
    geometry_proof=OUT/'stone-alpine/stone_R3_chain_patch_max_board.png'
    name='stone-range-sprite-patch_V2_max.png' if range_only and geometry_proof.exists() else 'stone-range-patch_V2_max.png' if range_only else 'board-patch_V2_max.png'
    path=OUT/name
    im.convert('RGB').save(path)
    if range_only and geometry_proof.exists():
        Image.open(geometry_proof).convert('RGB').save(OUT/'stone-range-patch_V2_max.png')

def main():
    board();board(True)
    # Every tile angle has its own 512px cell. Static sheets are also inspection artifacts.
    sheet=Image.new('RGBA',(1750,3500),WOOD);d=ImageDraw.Draw(sheet)
    d.text((24,18),'Working German Board Game | full V2 modelled set',font=font(32),fill=GOLD)
    for col,name in enumerate(['FLAT 0°','MID 27.5°','MAX 55°']):
        d.text((150+col*530,72),name,font=font(22),fill='#EFE3C8')
    for row,r in enumerate(TERRAINS):
        top=115+row*555
        d.text((22,top+220),r.title(),font=font(23),fill=GOLD)
        for col,angle in enumerate(['flat','mid','max']):
            tile=Image.open(OUT/f'{r}_V2_{angle}.png').convert('RGBA')
            sheet.alpha_composite(tile,(120+col*530,top))
    sheet.convert('RGB').save(OUT/'palette-v2.png')
    for angle in ['flat','mid','max']:
        contact=Image.new('RGBA',(1560,1140),WOOD);dc=ImageDraw.Draw(contact)
        for i,r in enumerate(TERRAINS):
            x=(i%3)*520;y=(i//3)*560
            dc.text((x+20,y+8),r.title()+' | '+angle,font=font(24),fill=GOLD)
            contact.alpha_composite(Image.open(OUT/f'{r}_V2_{angle}.png').convert('RGBA'),(x,y+40))
        contact.convert('RGB').save(OUT/f'inspection-v2-{angle}.png')
    records=[]
    for r in TERRAINS:
        for angle in ['flat','mid','max']:
            path=OUT/f'{r}_V2_{angle}.png';im=Image.open(path)
            assert im.mode=='RGBA' and im.size==(512,512)
            box=im.getchannel('A').getbbox()
            assert box and box[0]>0 and box[1]>0 and box[2]<512 and box[3]<512,(path,box)
            webp=BytesIO();im.save(webp,format='WEBP',lossless=True,method=4)
            records.append(dict(file=path.name,bytes=path.stat().st_size,webp_lossless_bytes=len(webp.getvalue()),alpha_bounds=box))
    layers=[]
    for path in sorted((OUT/'animation-layers/fleece').glob('animals_mid_*.png')):
        im=Image.open(path);assert im.mode=='RGBA' and im.size==(128,128)
        assert im.getchannel('A').getbbox()
        layers.append(dict(file=str(path.relative_to(OUT)),bytes=path.stat().st_size,alpha_bounds=im.getchannel('A').getbbox()))
    for path in sorted((OUT/'animation-layers/grain').glob('*')):
        layers.append(dict(file=str(path.relative_to(OUT)),bytes=path.stat().st_size))
    for path in sorted((OUT/'animation-layers/desert').glob('*')):
        layers.append(dict(file=str(path.relative_to(OUT)),bytes=path.stat().st_size))
    path=OUT/'animation-layers/fleece/animals_mid_atlas.webp'
    im=Image.open(path);assert im.mode=='RGBA' and im.size==(1024,256)
    layers.append(dict(file=str(path.relative_to(OUT)),bytes=path.stat().st_size,dimensions=im.size))
    stats=dict(tiles=records,layers=layers,tile_png_bytes=sum(r['bytes'] for r in records),tile_lossless_webp_bytes=sum(r['webp_lossless_bytes'] for r in records),
               animal_png_bytes=sum(r['bytes'] for r in layers if 'fleece' in r['file'] and r['file'].endswith('.png')))
    (OUT/'validation-v2.json').write_text(json.dumps(stats,indent=2))
    modes={
        'timber':'Procedural bird wings and three falling leaves. Trees surround a shared flat number glade.',
        'stone':'Selected R3 Alpine chain: seven summits on shared shoulders, warm rock, snow caps, scree and foothill pines. Three mist wisps and one soaring bird.',
        'fleece':'Lush pasture, seven alpacas and three tiny horned goats. 16 alpaca-and-goat layers graze, turn their heads and wander.',
        'grain':'Procedural wind moves a masked wheat patch using a small textured mesh.',
        'clay':'Terracotta-red terraces and a dark red-brown pit floor. Nine fading smoke puffs curl upward from the brick kiln chimney.',
        'desert':'Relief dunes, ripples, scrub and bleached remains. A masked dune patch subtly shimmers in the heat.'}
    cards=[]
    for r in TERRAINS:
        details=''.join(f'<figure><img loading="lazy" src="{r}_V2_{a}.png" alt="{r.title()} {a} tilt"><figcaption>{a.title()}</figcaption></figure>' for a in ['flat','mid','max'])
        cards.append(f'''<article><h2>{r.title()}</h2><div class="motion"><img class="loop" src="{r}_V2_mid_animated.webp" alt="{r.title()} eight-second animation at mid tilt"><canvas hidden width="512" height="512" aria-label="Paused {r.title()} frame"></canvas></div><p>{modes[r]}</p><details><summary>Flat / mid / max renders</summary><div class="angles">{details}</div></details></article>''')
    html='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Working German Board Game — V2 modelled tiles</title>
<style>
*{box-sizing:border-box}body{margin:0;padding:24px;color:#efe3c8;font:18px Georgia,serif;background:#2a1b10 repeating-linear-gradient(3deg,#2a1b10 0px,#302014 3px,#2a1b10 7px)}header,article,.patches{background:#4a3020;border:2px solid #8a6a2a;box-shadow:inset 0 0 0 1px #c9a04a,0 4px 12px #180e09;padding:18px;border-radius:8px}h1,h2,summary{color:#f1d58a}h1{font-size:30px;margin:0 0 12px}h2{margin:0 0 6px}p{line-height:1.45;font-size:16px}button,a{color:#f1d58a}button{font:inherit;border:2px solid #c9a04a;background:#342012;padding:10px 18px;cursor:pointer;border-radius:6px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:20px;max-width:1700px;margin:20px auto}.motion{width:100%;aspect-ratio:1;position:relative}.motion img,.motion canvas{width:100%;height:auto} [hidden]{display:none!important}.angles{display:flex;flex-wrap:wrap}figure{margin:0;flex:1;min-width:95px}figure img{width:100%}figcaption{text-align:center;font-size:13px}summary{cursor:pointer}.patches{max-width:1700px;margin:20px auto}.patches img{max-width:100%;width:740px}header{max-width:1700px;margin:auto}small{display:block;line-height:1.5;margin:12px 0;color:#d8c6a9}
</style><header><h1>Working German Board Game · V2 modelled tiles</h1><p>Six own procedural Blender terrains. All animations loop in eight seconds at mid tilt (27.5°). Transparent renders retain real upright geometry, the same thick hex bases and upper-left light.</p><button id="toggle" type="button" aria-pressed="false">Pause motion</button><small>Palette preview only. Mist, smoke and dust are overlays; All six terrains reserve the same number clearing and mask animations around the upright plate. <a href="palette-v2.png">Full static palette</a> ? <a href="token-check.png">Number plate checks</a> ? <a href="token-check-patch-max.png">Mixed patch with plates</a> ? <a href="ART_SPEC.md">Art specification</a> · <a href="PLAN.md">Integration plan</a></small></header><main>'''+''.join(cards)+'''</main><section class="patches"><h2>Neighbour and overdraw checks · maximum tilt</h2><img src="stone-range-patch_V2_max.png" alt="Nine connected Stone range tiles"><img src="board-patch_V2_max.png" alt="Nine mixed terrain tiles"></section><script>
let paused=false;
function setPaused(value){paused=value;document.querySelectorAll('.motion').forEach(box=>{const img=box.querySelector('img'),c=box.querySelector('canvas');if(paused&&img.complete&&img.naturalWidth)c.getContext('2d').drawImage(img,0,0,512,512);img.hidden=paused;c.hidden=!paused});const b=document.getElementById('toggle');b.textContent=paused?'Play motion':'Pause motion';b.setAttribute('aria-pressed',String(paused));}
document.getElementById('toggle').addEventListener('click',()=>setPaused(!paused));
document.querySelectorAll('.loop').forEach(img=>img.addEventListener('load',()=>{if(paused)setPaused(true)}));
if(matchMedia('(prefers-reduced-motion: reduce)').matches)setPaused(true);
</script></html>'''
    html=html.replace('Mist, smoke and dust are overlays','Mist, smoke and heat shimmer are overlays')
    if (OUT/'stone-alpine/stone_R3_chain_patch_max_board.png').exists():
        proof='<figure><img src="stone-range-sprite-patch_V2_max.png" alt="R3 Alpine chain at current game sprite spacing"><figcaption>Integration check: independent sprites at the game\'s inset spacing. The main Stone patch above shows the edge-sharing model geometry.</figcaption></figure>'
        html=html.replace('</section><script>',proof+'</section><script>')
        html=html.replace('<a href="PLAN.md">Integration plan</a>','<a href="PLAN.md">Integration plan</a> · <a href="stone-alpine/stone-phone-25pct.png">Stone at phone scale</a> · <a href="stone-alpine/REPORT.md">R3 report</a>')
    html=html.replace('<a href="PLAN.md">Integration plan</a>','<a href="PLAN.md">Integration plan</a> · <a href="palette-v2-density.png">Before → after density</a> · <a href="../bandit/bandit-options.png">Bandit options</a>')
    if (OUT/'alpaca-clay/clay-fleece-phone-25pct.png').exists():
        html=html.replace('<a href="ART_SPEC.md">Art specification</a>','<a href="ART_SPEC.md">Art specification</a> ? <a href="alpaca-clay/clay-fleece-phone-25pct.png">Fleece / Clay phone proof</a> ? <a href="alpaca-clay/REPORT.md">Alpaca / terracotta report</a>')
    (OUT/'palette-v2.html').write_text(html,encoding='utf-8')
    print('V2_PALETTE_OK',len(records),'tiles;',len(layers),'layer files;',stats['tile_png_bytes'],'PNG bytes',flush=True)

if __name__=='__main__':main()

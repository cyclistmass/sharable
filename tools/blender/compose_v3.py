"""Painted/V3 review at three tilts, upright plate and neighbour-overdraw proof."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageChops
import math,json,hashlib,ast
from io import BytesIO
import token_space as token
token.CX=.36
from compose_token_check import plate,font
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models';DETAIL=OUT/'v3'
REF=Path('C:/Users/mauro/catan-android/app/src/main/res/drawable-nodpi')
NAMES=['timber','stone','fleece','grain','clay','desert'];ANGLES=[('flat',0),('mid',.48),('max',.96)]
WOOD='#4a3020';GOLD='#f1d58a'

def painted(name):
    im=Image.open(REF/f'tile_{name}.webp').convert('RGBA');box=im.getchannel('A').getbbox();im=im.crop(box)
    # Fair game-width comparison: the modelled hex is 311px wide in a 512px sprite.
    w=round(math.sqrt(3)*.93*512/2.65);h=round(im.height*w/im.width);im=im.resize((w,h),Image.Resampling.LANCZOS)
    cell=Image.new('RGBA',(512,512));cell.alpha_composite(im,((512-w)//2,(512-h)//2));return cell

def patch():
    im=Image.new('RGBA',(1500,1000),WOOD);d=ImageDraw.Draw(im);d.text((24,16),'V3 | mixed 3 x 3 | 55 degrees | top-right number plates',font=font(28),fill=GOLD)
    names=['stone','timber','fleece','clay','stone','grain','timber','desert','grain'];scale=205;t=.96;masks=[];overlaps=[]
    for row in range(3):
        y=(row-1)*1.5
        for col in range(3):
            x=(col-1)*math.sqrt(3)+(row%2)*math.sqrt(3)/2-.30;k=1+.22*math.sin(t)*y*math.cos(t)/4.2;cx=745+x*scale*k;cy=580+y*math.cos(t)*scale*k
            tile=Image.open(OUT/f'{names[row*3+col]}_V3_max.png').convert('RGBA');tile.alpha_composite(plate(512,t,6));size=round(2.65*scale*k);tile=tile.resize((size,size),Image.Resampling.LANCZOS);xy=(round(cx-size/2),round(cy-size/2))
            a=Image.new('L',im.size);a.paste(tile.getchannel('A'),xy)
            for i,m in enumerate(masks):
                n=ImageChops.multiply(a,m).point(lambda v:255 if v>128 else 0).histogram()[255]
                if n:overlaps.append({'rear':i,'front':row*3+col,'plate_pixels':n})
            im.alpha_composite(tile,xy);m=Image.new('L',im.size);r=.27*scale*k;px=cx+.36*scale*k;py=cy-.30*math.cos(t)*scale*k;ImageDraw.Draw(m).ellipse((px-r,py-r,px+r,py+r),fill=255);masks.append(m)
    assert not overlaps,overlaps
    im.convert('RGB').save(OUT/'token-check-v3-patch-max.png');im.resize((375,250),Image.Resampling.LANCZOS).convert('RGB').save(DETAIL/'patch-phone-25pct.png');return im,overlaps

def main():
    DETAIL.mkdir(exist_ok=True);palette=Image.new('RGBA',(2180,3500),WOOD);d=ImageDraw.Draw(palette)
    d.text((22,16),'V3 | second modelling pass | painted reference and real geometry at equal hex width',font=font(28),fill=GOLD)
    for i,label in enumerate(['Painted reference','V3 flat | 0 degrees','V3 mid | 27.5 degrees','V3 max | 55 degrees']):d.text((100+i*520,70),label,font=font(20),fill='#efe3c8')
    check=Image.new('RGBA',(1750,3500),WOOD);cd=ImageDraw.Draw(check);cd.text((22,15),'V3 | top-right clearing | centre (+0.36,+0.30) | plate 0.27 | clearing 0.31',font=font(25),fill=GOLD)
    sizes=[];views=[];geometry=[];black_pixels={};webp_bytes=0
    for row,name in enumerate(NAMES):
        y=115+row*555;d.text((16,y+235),name.title(),font=font(22),fill=GOLD);cd.text((15,y+235),name.title(),font=font(23),fill=GOLD)
        palette.alpha_composite(painted(name),(90,y))
        for col,(angle,t) in enumerate(ANGLES):
            p=OUT/f'{name}_V3_{angle}.png';im=Image.open(p).convert('RGBA');box=im.getchannel('A').getbbox()
            assert im.size==(512,512) and box and min(box[:2])>0 and max(box[2:])<512,(p,box)
            black=sum(a==255 and max(r,g,b)==0 for r,g,b,a in im.get_flattened_data());black_pixels[p.name]=black
            # A few deep-shadow pixels are valid; reject the large coplanar blackout.
            assert black<=64,(p,'large opaque black self-occlusion artifact',black)
            sizes.append(p.stat().st_size);views.append({'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'alpha_bounds':box})
            encoded=BytesIO();im.save(encoded,format='WEBP',lossless=True,method=6);webp_bytes+=len(encoded.getvalue())
            palette.alpha_composite(im,(610+col*520,y));im.alpha_composite(plate(512,t,6));check.alpha_composite(im,(120+col*530,y));cd.text((140+col*530,y+495),angle.title(),font=font(20),fill='#efe3c8')
    palette.convert('RGB').save(OUT/'palette-v3.png');palette.resize((545,875),Image.Resampling.LANCZOS).convert('RGB').save(DETAIL/'palette-v3-phone-25pct.png')
    check.resize((438,875),Image.Resampling.LANCZOS).convert('RGB').save(DETAIL/'token-check-v3-phone-25pct.png');board,overlaps=patch()
    combined=Image.new('RGB',(1750,4550),WOOD);combined.paste(check.convert('RGB'),(0,0));combined.paste(board.convert('RGB'),(125,3525));combined.save(OUT/'token-check-v3.png')
    for angle,t in ANGLES:
        contact=Image.new('RGBA',(1560,1140),WOOD);dc=ImageDraw.Draw(contact)
        for i,name in enumerate(NAMES):
            x=(i%3)*520;y=(i//3)*560;dc.text((x+20,y+8),name.title()+' | V3 '+angle,font=font(24),fill=GOLD);contact.alpha_composite(Image.open(OUT/f'{name}_V3_{angle}.png').convert('RGBA'),(x,y+40))
        contact.convert('RGB').save(DETAIL/f'inspection-{angle}.png')
    baseline=json.loads((OUT/'token-clearing/app-baseline.json').read_text())
    for name,digest in baseline.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    historical=json.loads((DETAIL/'historical-assets-baseline.json').read_text())
    for name,digest in historical.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    for name in NAMES:
        p=DETAIL/f'{name}-geometry-validation.json'
        assert p.exists(),('missing geometry proof',name)
        g=json.loads(p.read_text());assert g['prop_vertices_inside_clearing']==0 and not any(g['foreground_vertices_over_plate_at_eight_steps']);geometry.append(g)
        manifest=json.loads((DETAIL/f'{name}-manifest.json').read_text())
        assert manifest.get('source_hashes')==g.get('source_hashes') and g.get('source_hashes'),('stale geometry proof',name)
        for source,digest in g['source_hashes'].items():
            assert hashlib.sha256((ROOT/'tools/blender'/source).read_bytes()).hexdigest()==digest,('stale render',name,source)
        assert [(r['file'],r['tilt_radians']) for r in manifest['renders']]==[(f'{name}_V3_{angle}.png',t) for angle,t in ANGLES]
        for render in manifest['renders']:
            assert hashlib.sha256((OUT/render['file']).read_bytes()).hexdigest()==render['sha256'],('changed render image',render['file'])
    for p in (ROOT/'tools/blender').glob('*.py'):ast.parse(p.read_text(encoding='utf-8-sig'))
    renders=[r for name in NAMES for r in json.loads((DETAIL/f'{name}-manifest.json').read_text())['renders']];seconds=sum(r['seconds'] for r in renders)
    result={'terrain_views':18,'rgba_512px':True,'new_plate_center':[.36,.30],'plate_radius':.27,'clearing_radius':.31,'neighbour_plate_overlap_pixels':0,'app_files_unchanged':len(baseline),'historical_assets_unchanged':len(historical),'geometry_validated_terrains':len(geometry),'opaque_black_pixels_by_view':black_pixels,'png_bytes':sum(sizes),'retained_render_seconds':round(seconds,2),'views':views,'geometry':geometry}
    result['trial_lossless_webp_bytes']=webp_bytes
    result['eight_step_base_only_estimated_webp_bytes']=round(webp_bytes*8/3)
    result['painted_references']=[{'file':str(REF/f'tile_{name}.webp'),'sha256':hashlib.sha256((REF/f'tile_{name}.webp').read_bytes()).hexdigest()} for name in NAMES]
    (DETAIL/'validation.json').write_text(json.dumps(result,indent=2));print(json.dumps({k:v for k,v in result.items() if k not in ('views','geometry','opaque_black_pixels_by_view','painted_references')}))
    (DETAIL/'REPORT.md').write_text(f'''# V3 dense modelled terrains

Own procedural geometry, based on the six painted face compositions at `{REF.as_posix()}/tile_<terrain>.webp` (read only). No downloaded models, projected painted textures or application changes. Painted references are credited to Mauro in CREDITS.md. Standard colour transform, warm upper-left lights, noise colour/bump and geometry-based ambient occlusion replace the paler V2 treatment.

Second modelling pass: `v3_art.py` supplies branch-by-branch conifers, folded leaves, clustered daisies/lupins, coherent sheep with fine wool locks, fitted walls/coping, overlapping cedar shakes, bark/end-grain rings, ferns, a water trough, irregular rock ledges, mine decks/ladders/wagons and chimney masonry. The main generator adds 36 curved crop rows with bent stems and multi-grain ears, cinched/fanning sheaves, irregular squared clay terraces and asymmetric dune crests. Ground-scale colour, bump, contact shadow and restrained warm lighting apply across the set. The protected apron gains flat ground markings, with no raised props in the plate sightline.

Continuation refinements after the first full phone review: angled logging stacks and a sawbuck, brighter botanical tips, irregular wool locks, larger wheat kernels and raised fencing with flowering margins, finer actual mountain erosion with continuous mineral joints/strata, contour-aligned clay cuts, shorter kiln platforms with handrails and volumetric smoke, quieter sand colour and grouped thorn scrub. `iteration-1-palette.png` and `iteration-1-phone.png` retain the intermediate comparison. Each final image hash is checked against its render manifest.

Fleece retains seven sheep, three small alpacas at 65% of the previous model scale and two tiny horned goats. Stone retains the selected R3 chain and snow, with added surface buttresses. Clay retains its red-orange identity and wet red-brown bottom. Desert stays cactus-free. See ART_REVIEW.md for the initial per-terrain gaps and final visual judgement; `before-push/` preserves the prior V3 renders.

The only reserved empty ground circle is TOP RIGHT: (+0.36,+0.30), radius 0.31, upright plate radius 0.27. The left-hand V2 clearing is filled by new scenery. Low foreground and rear-neighbour guards are applied before rendering. V2 outputs/scripts retain their historical left clearing; V3 overrides the shared centre within its own process. There are no new animations in this static V3 art pass; historical V2 motion masks must not be used with the moved plate until their anchors/guards and scenery paths are rebuilt. Bandit V1 will likewise need a right-centre placement at integration; its source foot anchor remains unchanged.

Review: ../palette-v3.png compares painted faces and all three V3 views at the same visible hex width. ../token-check-v3.png contains 18 upright plate proofs and a mixed 3x3 depth-sorted patch. The 545x875 palette phone proof is exactly 25% of the full sheet; each rendered hex is approximately 78px wide there. Phone proofs and three six-terrain inspection sheets are in this directory. All 18 sources are transparent 512px images. Pixel test: zero rear plate pixels covered by foreground neighbours. All {len(baseline)} application hashes unchanged; all Blender Python sources parse.

Geometry validation runs on each final scene immediately before rendering, with no prop vertices inside the clearing and no foreground vertices projected over the upright plate at any of the eight production angles (**48 terrain/angle checks**). Source hashes bind the geometry proofs and render manifests to the current generator and its dependencies; missing or stale proofs fail composition. The Stone erosion detail is periodic, preserving the R3 shared-edge height checks. All {len(historical)} historical V2/Bandit image hashes are unchanged. The image check rejects large opaque black self-occlusion artifacts; isolated deep-shadow pixels are allowed and counted in validation.json. Visual inspection is still needed for faces/interpolation and runtime piece ordering; these checks do not replace device validation.

Whole-animal checks ensure all seven sheep, three small alpacas and two tiny goats survive the number guard intact. Manual inspection outcomes and any limitations are recorded separately in ART_REVIEW.md so this generated report does not assert an inspection merely because rendering completed.

Retained Blender render time: **{seconds:.2f}s**, excluding startup/modelling and discarded iterations. Current PNG bytes **{sum(sizes):,}**. One terrain per sequential two-thread process; each exits before the next starts. Memory/object counts and individual render times are in the terrain manifests/logs. Eight production angles remain the PLAN.md sequence (0.96*i/7); this round renders the requested flat/mid/max proofs only. No app wiring, emulator, commit or push.

Compression trial: the 18 lossless WebP encodes total {webp_bytes:,} bytes (measurement only, not new shipped assets). Forty-eight eight-angle bases extrapolate to roughly {round(webp_bytes*8/3):,} bytes, excluding future V3 animation atlases/masks and APK/container overhead. The 48 fully decoded RGBA bases would occupy 48 MiB; caching just two adjacent angles for six terrains uses 12 MiB before overlays/decode transients. Do not reuse the historical 72-file V2 animation budget as a measured V3 APK result.

Reproduce: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/blender/run_v3.ps1`. Validation scope: geometry/token guards, complete animal assemblies, transparent sprite bounds, same-scale visual comparison, phone inspection, neighbour overdraw and unchanged app hashes. Rules/unit/fuzz/on-device tests do not apply to this art-only change and no app/device validation is claimed.
''',encoding='utf-8')
    inventory=sorted(p.relative_to(ROOT).as_posix() for p in OUT.glob('*V3*.png'))+sorted(p.relative_to(ROOT).as_posix() for p in [OUT/'palette-v3.png',OUT/'token-check-v3.png',OUT/'token-check-v3-patch-max.png'])+sorted(p.relative_to(ROOT).as_posix() for p in DETAIL.iterdir() if p.is_file())
    inventory+=['tools/blender/render_v3.py','tools/blender/v3_art.py','tools/blender/compose_v3.py','tools/blender/run_v3.ps1','docs/design/tiles-3d-models/ART_SPEC.md','docs/design/tiles-3d-models/PLAN.md','CREDITS.md','docs/design/tiles-3d-models/painted-reference-v3.png']
    (DETAIL/'FILES.txt').write_text('\n'.join(sorted(set(inventory)))+'\n',encoding='utf-8')

if __name__=='__main__':main()

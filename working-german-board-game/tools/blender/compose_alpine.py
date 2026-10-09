"""R3 native geometry patch, exact 25% phone proofs and preserved-source checks."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageChops
import hashlib,json,sys,argparse
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models';PROOF=OUT/'stone-alpine'
WOOD='#4A3020';GOLD='#F1D58A';CREAM='#EFE3C8';L=Image.Resampling.LANCZOS
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--full-set-rebuild',action='store_true');args=parser.parse_args()
    baseline=json.loads((PROOF/'preserved-baseline.json').read_text())
    baseline={f:digest for f,digest in baseline.items() if f.startswith('app/') or Path(f).name.startswith(('timber_V2_','fleece_V2_','grain_V2_','clay_V2_','desert_V2_'))}
    if args.full_set_rebuild:baseline={f:digest for f,digest in baseline.items() if f.startswith('app/')}
    for f,digest in baseline.items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==digest,f
    raw=Image.open(PROOF/'stone_R3_chain_patch_max.png').convert('RGBA')
    assert raw.size==(1536,1024)
    b=raw.getchannel('A').getbbox();assert b and min(b[:2])>0 and b[2]<1536 and b[3]<1024,b
    patch=Image.new('RGBA',(1600,1180),WOOD);d=ImageDraw.Draw(patch)
    d.rectangle((8,8,1591,1171),outline='#C9A04A',width=3)
    d.text((28,20),'Stone | R3 Alpine chain | seven summits per tile',font=font(31),fill=GOLD)
    d.text((28,65),'Nine edge-sharing model instances at maximum tilt | fixed upper-left light',font=font(22),fill=CREAM)
    patch.alpha_composite(raw,(32,112))
    d.text((28,1137),'Geometry continuity proof; the separate sprite mock retains the game\'s inset tile gaps.',font=font(18),fill=CREAM)
    patch.convert('RGB').save(PROOF/'stone_R3_chain_patch_max_board.png')
    sheet=Image.new('RGBA',(1632,650),WOOD);d=ImageDraw.Draw(sheet)
    d.text((22,16),'Stone | selected R3 Alpine chain + three secondary peaks',font=font(31),fill=GOLD)
    for col,(a,label) in enumerate([('flat','FLAT 0 degrees'),('mid','MID 27.5 degrees'),('max','MAX 55 degrees')]):
        d.text((col*544+25,67),label,font=font(22),fill=CREAM)
        sheet.alpha_composite(Image.open(OUT/f'stone_V2_{a}.png').convert('RGBA'),(col*544+16,108))
    sheet.convert('RGB').save(PROOF/'stone-R3-views.png')
    phone=Image.new('RGBA',(384,508),WOOD);d=ImageDraw.Draw(phone)
    d.text((9,10),'Stone | exact 25% phone proof',font=font(17),fill=GOLD)
    for col,a in enumerate(['flat','mid','max']):
        tile=Image.open(OUT/f'stone_V2_{a}.png').convert('RGBA').resize((128,128),L)
        phone.alpha_composite(tile,(col*128,48));d.text((col*128+12,34),a.title(),font=font(12),fill=CREAM)
    d.text((9,184),'Edge-sharing range | max tilt',font=font(15),fill=GOLD)
    phone.alpha_composite(raw.resize((384,256),L),(0,211))
    d.text((9,475),'Hex face ~78px in the single-tile row.',font=font(12),fill=CREAM)
    phone.convert('RGB').save(PROOF/'stone-phone-25pct.png')
    loop=Image.open(OUT/'stone_V2_mid_animated.webp');frames=[]
    grid=Image.new('RGBA',(1024,1024),WOOD)
    for i in range(loop.n_frames):
        loop.seek(i);frame=loop.convert('RGBA').resize((128,128),L);frames.append(frame)
        grid.alpha_composite(frame,((i%8)*128,(i//8)*128))
    assert len(frames)==64
    frames[0].save(PROOF/'stone-mid-phone-animated.webp',save_all=True,append_images=frames[1:],duration=125,loop=0,lossless=True,method=4)
    grid.convert('RGB').save(PROOF/'stone-phone-all64.png')
    check=Image.open(PROOF/'stone-mid-phone-animated.webp');duration=0
    for i in range(check.n_frames):check.seek(i);check.load();duration+=check.info['duration']
    assert duration==8000 and check.n_frames==64
    sys.path.insert(0,str(ROOT/'tools/blender'))
    from alpine_chain import continuity,PEAKS
    records={'preserved_files':len(baseline),'app_hashes_unchanged':True,'all_nonstone_previews_and_app_hashes_unchanged':not args.full_set_rebuild,
             'terrain': 'Stone','selected_study':'R3 Alpine chain','summits':PEAKS,
             'added_peaks':3,'continuity':continuity(),'patch_size':raw.size,'patch_alpha_bounds':b,
             'phone_scale':.25,'phone_static_size':[128,128],'phone_hex_width_px':512/2.65*(3**.5)*.93/4,
             'phone_loop_frames':check.n_frames,'phone_loop_ms':duration}
    (PROOF/'validation.json').write_text(json.dumps(records,indent=2))
    print('R3_PROOF_OK',json.dumps(records))

if __name__=='__main__':main()

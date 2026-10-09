"""Bake an upright opaque shadow seal and prove the blocked number is invisible."""
from pathlib import Path
import math,json,hashlib
from PIL import Image,ImageDraw,ImageChops
from compose_token_check import plate,font
from token_space import CX,CY,TOKEN_R
ROOT=Path(__file__).resolve().parents[2];TILES=ROOT/'docs/design/tiles-3d-models';OUT=ROOT/'docs/design/bandit';SPRITES=OUT/'v1';RAW=SPRITES/'onplate-raw';PIX=512/2.65

def number_mask(t):
    # Exact digit and pip raster isolated by differencing numbered/blank faces.
    im=plate(512,t,6);blank=plate(512,t,'')
    mask=ImageChops.difference(im,blank).convert('RGB').convert('L').point(lambda v:255 if v else 0)
    # Blank face retains pips: add their exact raster independently.
    d=ImageDraw.Draw(mask);x=256+CX*PIX;y=256-CY*math.cos(t)*PIX;r=TOKEN_R*PIX
    for i in range(5):d.ellipse((x+(i-2)*r*.12-r*.05,y+r*.55-r*.05,x+(i-2)*r*.12+r*.05,y+r*.55+r*.05),fill=255)
    return mask

def placed(sprite,t):
    # Inverse-sampled translation preserves the opaque seal at fractional anchors.
    dx=CX*PIX;dy=-CY*math.cos(t)*PIX
    return sprite.transform((512,512),Image.Transform.AFFINE,(1,0,-dx,0,1,-dy),Image.Resampling.BICUBIC)

def main():
    checks=[];proof=Image.new('RGB',(1632,1430),'#4a3020');d=ImageDraw.Draw(proof)
    d.text((20,15),'Bandit V1 | blocks the number plate | 6 hidden',font=font(30),fill='#f1d58a')
    phone=Image.new('RGB',(408,296),'#4a3020');pd=ImageDraw.Draw(phone);pd.text((8,4),'25% phone proof',font=font(13),fill='#f1d58a')
    jobs=[(f'tilt_{i:02d}',.96*i/7) for i in range(8)]+[('flat',0),('mid',.48),('max',.96)]
    allsteps=Image.new('RGB',(2048,1100),'#4a3020');sd=ImageDraw.Draw(allsteps)
    for name,t in jobs:
        raw=Image.open(RAW/f'bandit_v1_{name}.png').convert('RGBA')
        # The game plate remains upright at tilt; so does this shadow seal.
        sprite=Image.new('RGBA',(512,512));r=.278*PIX
        ImageDraw.Draw(sprite).ellipse((256-r,256-r,256+r,256+r),fill=(32,23,20,255))
        sprite.alpha_composite(raw);sprite.save(SPRITES/f'bandit_v1_{name}.png')
        pawn=placed(sprite,t);mask=number_mask(t)
        residual=ImageChops.multiply(mask,ImageChops.invert(pawn.getchannel('A')))
        pixels=sum(residual.histogram()[1:]);assert pixels==0,(name,pixels)
        checks.append({'view':name,'tilt_radians':t,'visible_digit_or_pip_pixels':pixels})
        if name.startswith('tilt_'):
            i=int(name[-2:]);x=(i%4)*512;y=(i//4)*550
            allsteps.paste(sprite,(x,y),sprite);sd.text((x+20,y+510),f'{i:02d} | {math.degrees(t):.1f} degrees',font=font(22),fill='#f1d58a')
        else:
            col=['flat','mid','max'].index(name)
            for row,terrain in enumerate(['timber','grain']):
                tile=Image.open(TILES/f'{terrain}_V2_{name}.png').convert('RGBA');tile.alpha_composite(plate(512,t,6));tile.alpha_composite(pawn)
                # Composite comparison confirms numbered and unnumbered input match under the seal.
                blank=Image.open(TILES/f'{terrain}_V2_{name}.png').convert('RGBA');blank.alpha_composite(plate(512,t,''));blank.alpha_composite(pawn)
                assert ImageChops.difference(tile.convert('RGB'),blank.convert('RGB')).getbbox() is None
                x=col*544+16;y=row*540+85;proof.paste(tile,(x,y),tile);d.text((x+20,y-28),f'{terrain.title()} | {name}',font=font(24),fill='#efe3c8')
                phone.paste(tile.resize((128,128),Image.Resampling.LANCZOS),(col*136,row*136+22),tile.resize((128,128),Image.Resampling.LANCZOS))
    proof.paste(phone,(1200,1130));d.text((25,1200),'Foot anchor (-0.36, +0.30)\nModel scale 0.70 | brim diameter 0.644\nUpright dark base seal radius 0.278',font=font(25),fill='#efe3c8',spacing=12)
    proof.save(OUT/'bandit-v1-onplate.png');phone.save(OUT/'bandit-v1-onplate-phone-25pct.png');allsteps.save(OUT/'bandit-v1-onplate-tilt-steps.png')
    baseline=json.loads((TILES/'token-clearing/app-baseline.json').read_text())
    for name,digest in baseline.items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    manifest=json.loads((RAW/'onplate-manifest.json').read_text());seconds=sum(r['render_seconds'] for r in manifest['renders'])
    result={'views':checks,'app_files_unchanged':len(baseline),'render_seconds':seconds,'anchor':[-.36,.30],'model_scale':.70,'brim_diameter':.644,'upright_seal_radius':.278}
    (SPRITES/'onplate-validation.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
    (SPRITES/'ONPLATE.md').write_text(f'''# Bandit V1 blocks the plate

The existing original skeleton corsair now stands at tile (-0.36,+0.30). Model scale 0.70, versus 0.46 previously; the full-round frayed brim spans 0.644 lattice units, larger than the plate diameter 0.54. Sprite foot anchor stays (256,256), orthographic scale 2.65, transparent 512px. Preserve padding and apply the projected plate-centre translation.

An opaque dark shadow seal of radius 0.278 is baked underneath the feet in each sprite. It remains upright, matching the game's upright number plate rather than a foreshortened ground ellipse. This intentionally hides the whole face including digits and pips at every angle. Render the plate first, then this complete Bandit sprite; never redraw its number above the Bandit. Use premultiplied interpolation so the identical seal stays opaque between samples.

Eight production tilt sprites and flat/mid/max proof sources were regenerated sequentially. Historical front/threequarter portraits remain the earlier scale. Review ../bandit-v1-onplate.png, its 25% phone proof and the eight-step sheet. Exact digit/pip alpha checks passed at all eleven rendered views; numbered versus blank composites match at six Timber/Grain views. All {len(baseline)} app hashes unchanged. Final render time {seconds:.2f}s excluding startup/model construction.

Reproduce: Blender -b -t 2 -P tools/blender/render_bandit_v1.py -- --batch onplate; after Blender exits run Python tools/blender/compose_bandit_onplate.py. No app wiring, emulator, commit or push. Earlier off-plate sheets are historical and their old composer must not be used on these replacement sprites.
''',encoding='utf-8')

if __name__=='__main__':main()

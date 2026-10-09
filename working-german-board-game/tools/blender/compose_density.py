"""Before/after proofs and bandit palette; reuses completed own Blender bandit renders."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,hashlib

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/design/tiles-3d-models';BANDIT=ROOT/'docs/design/bandit'
WOOD='#4A3020';GOLD='#F1D58A';CREAM='#EFE3C8';L=Image.Resampling.LANCZOS
def font(n):return ImageFont.truetype('C:/Windows/Fonts/georgia.ttf',n)

def density():
    terrains=['fleece','grain','clay','desert','stone']
    notes={'fleece':'Rolling meadow · wall and gate · seven sheep / three tiny goats in motion',
           'grain':'Dense golden canopy · curved rows · visible ears · cart / stooks / poppies',
           'clay':'Three excavated terraces · wet floor · kiln / shed / drying racks',
           'desert':'Relief dunes and ripples · rock outcrops · scrub / bleached remains',
           'stone':'Same ridge shape · warmer dark rock · snow caps · larger nine-tile patch'}
    sheet=Image.new('RGBA',(2180,2960),WOOD);d=ImageDraw.Draw(sheet)
    d.text((24,20),'V2 density pass | before to after',font=font(34),fill=GOLD)
    for col,label in enumerate(['BEFORE · mid','AFTER · mid','BEFORE · max','AFTER · max']):
        d.text((80+col*530,76),label,font=font(22),fill=CREAM)
    for row,r in enumerate(terrains):
        y=116+row*560
        for col,(before,angle) in enumerate([(True,'mid'),(False,'mid'),(True,'max'),(False,'max')]):
            folder=OUT/'density-before' if before else OUT
            sheet.alpha_composite(Image.open(folder/f'{r}_V2_{angle}.png').convert('RGBA'),(40+530*col,y))
        d.text((35,y+505),r.title()+' | '+notes[r],font=font(20),fill=GOLD)
    sheet.convert('RGB').save(OUT/'palette-v2-density.png')
    # Actual animal source, enlarged for judging tiny horns/beards, plus in-context crop.
    detail=Image.new('RGBA',(1024,580),WOOD);d=ImageDraw.Draw(detail)
    d.text((24,15),'Fleece | tiny goats remain smaller than sheep',font=font(26),fill=GOLD)
    detail.alpha_composite(Image.open(OUT/'animation-layers/fleece/animals_mid_00.png').convert('RGBA').resize((512,512),L),(0,58))
    detail.alpha_composite(Image.open(OUT/'fleece_V2_max.png').convert('RGBA'),(512,58))
    detail.convert('RGB').save(OUT/'fleece-animals-detail.png')

def bandits():
    titles={'B1':'Pale-rim cloak','B2':'Lantern carrier','B3':'Ivory beacon ring'}
    sheet=Image.new('RGBA',(1536,1780),WOOD);d=ImageDraw.Draw(sheet)
    d.text((24,20),'Bandit | V2 options at game size',font=font(34),fill=GOLD)
    d.text((24,72),'Body = 35% of hex width · upright at both tilts · 25% phone proof included',font=font(22),fill=CREAM)
    metrics={};source_hashes={}
    for col,(option,title) in enumerate(titles.items()):
        source= BANDIT/f'{option}.png';sprite=Image.open(source).convert('RGBA')
        raw=BANDIT/f'{option}-render.png';source_hashes[raw.name]=hashlib.sha256(raw.read_bytes()).hexdigest()
        body=Image.open(raw).convert('RGBA');body=body.crop(body.getchannel('A').getbbox())
        # Existing final sprites normalize raw silhouettes to 390px height, foot at 445px.
        normalization=min(390/body.height,370/body.width);body_width=round(body.width*normalization)
        d.text((col*512+20,120),option+' | '+title,font=font(27),fill=GOLD)
        sheet.alpha_composite(sprite.resize((260,260),L),(col*512+126,158))
        for row,(terrain,angle) in enumerate([('timber','flat'),('timber','max'),('grain','max')]):
            tile=Image.open(OUT/f'{terrain}_V2_{angle}.png').convert('RGBA')
            canvas=Image.new('RGBA',(512,440),WOOD)
            canvas.alpha_composite(tile,(0,-20))
            # R=.93, ortho=2.65; widest ground span is sqrt(3)*R, independent of tilt.
            hex_width=512/2.65*(3**.5)*.93
            factor=hex_width*.35/body_width
            stamp=sprite.resize((round(512*factor),round(512*factor)),L)
            import math
            tilt=0 if angle=='flat' else .96
            footx=256+.15*.93*512/2.65
            footy=256-20+.15*.93*math.cos(tilt)*512/2.65
            # Standing on canopy is deliberately a visibility challenge; actual token priority needs integration.
            canvas.alpha_composite(stamp,(round(footx-256*factor),round(footy-445*factor)))
            path=BANDIT/f'{option}-on-{terrain}-{angle}.png';canvas.convert('RGB').save(path)
            top=450+row*440;sheet.alpha_composite(canvas,(col*512,top))
            d.text((col*512+20,top+12),terrain.title()+' | '+('flat' if angle=='flat' else 'max tilt'),font=font(23),fill=CREAM)
            metrics[f'{option}-{terrain}-{angle}']={'hex_width_px':round(hex_width,2),'body_width_px':round(body_width*factor,2),'width_ratio':.35,'phone_hex_width_px':round(hex_width/4,2),'phone_body_width_px':round(body_width*factor/4,2)}
    sheet.convert('RGB').save(BANDIT/'bandit-options.png')
    sheet.convert('RGB').resize((384,445),L).save(BANDIT/'bandit-options-phone-25pct.png')
    (BANDIT/'validation.json').write_text(json.dumps({'placements':metrics,'reused_own_raw_sha256':source_hashes,'sprite_size':[512,512],'phone_sheet_scale':.25},indent=2))
    print('BANDIT_OK: reused three completed own Blender models; 9 game-scale composites')

if __name__=='__main__':density();bandits()

"""Crop reproducible motion sources, verify preserved files, record current budgets/docs."""
from pathlib import Path
from PIL import Image
import argparse,hashlib,json,re
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'

def crop_animals():
    folder=OUT/'animation-layers/fleece';crop=(232,208,360,336)
    for i in range(16):
        source=Image.open(folder/'raw'/f'animals_mid_{i:02d}_raw.png').convert('RGBA')
        box=source.getchannel('A').getbbox()
        assert box and box[0]>crop[0] and box[1]>crop[1] and box[2]<crop[2] and box[3]<crop[3],box
        source.crop(crop).save(folder/f'animals_mid_{i:02d}.png')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--crop-only',action='store_true');args=parser.parse_args()
    if args.crop_only:crop_animals();print('16 animal crops safe at (232,208), 128px');return
    baseline=json.loads((OUT/'density-before/baseline.json').read_text())
    for f,digest in baseline['timber_sha256'].items():assert hashlib.sha256((OUT/f).read_bytes()).hexdigest()==digest,f
    for f,digest in baseline['app_sha256'].items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==digest,f
    for i in range(16):
        im=Image.open(OUT/f'animation-layers/fleece/animals_mid_{i:02d}.png')
        assert im.size==(128,128) and im.mode=='RGBA'
        b=im.getchannel('A').getbbox();assert b and min(b[:2])>0 and max(b[2:])<128
    stats=json.loads((OUT/'validation-v2.json').read_text())
    animal=(OUT/'animation-layers/fleece/animals_mid_atlas.webp').stat().st_size
    grain=(OUT/'animation-layers/grain/wind_mask_mid.png').stat().st_size
    desert=(OUT/'animation-layers/desert/heat_mask_mid.png').stat().st_size
    estimate=round(stats['tile_lossless_webp_bytes']*8/3+8*(animal+grain+desert))
    timing={p.stem:json.loads(p.read_text()) for p in (OUT/'density-manifests').glob('*.json') if p.stem!='summary'}
    times={r:[item['seconds'] for item in timing[r]['renders']] for r in ['fleece','grain','clay','desert','stone']}
    seconds=sum(sum(x) for x in times.values())+sum(timing[f'animals_{i:02d}']['renders'][0]['seconds'] for i in range(16))
    peaks={}
    logs=list((OUT/'density-manifests/logs').glob('*.log'))+list(ROOT.glob('density-*.log'))
    for p in logs:
        data=p.read_bytes();content=data.decode('utf-16' if data[:2] in (b'\xff\xfe',b'\xfe\xff') else 'utf-8',errors='replace')
        found=re.findall(r'Peak[ :]+(\d+\.\d+)M',content)
        if found:peaks[p.stem]=max(map(float,found))
    inventory=[str(p.relative_to(ROOT)).replace('\\','/') for p in sorted(OUT.rglob('*')) if p.is_file() and (p.name.startswith('animals_mid_') or p.name in ['palette-v2.html','palette-v2.png','palette-v2-density.png','stone-range-patch_V2_max.png','stone_V2_max_mock1024.png','fleece-animals-detail.png'] or re.match(r'(stone|fleece|grain|clay|desert)_V2_',p.name))]
    summary={'preserved_timber_and_app_sha256':True,'static_views_updated':15,'preview_angles_degrees':[0,27.5,55],'motion_frames_per_loop':64,'loop_ms':8000,'animal_frames':16,'animal_crop_origin':[232,208],'animal_crop_size':[128,128],'animal_atlas_bytes':animal,'grain_mask_bytes':grain,'desert_mask_bytes':desert,'base_png_bytes':stats['tile_png_bytes'],'base_lossless_webp_trial_bytes':stats['tile_lossless_webp_bytes'],'production_eight_step_file_count':72,'production_eight_step_estimated_bytes':estimate,'final_source_render_seconds':round(seconds,2),'final_batch_times':times,'blender_reported_peaks_MB':peaks,'inventory':inventory}
    (OUT/'density-manifests/summary.json').write_text(json.dumps(summary,indent=2))
    plan=OUT/'PLAN.md';old=plan.read_text(encoding='utf-8');tail=old[old.index('## Rendering and cross-fade'):]
    tail=tail.replace('(.485,-.125, approximately .69 world z)','(.515,-.045, approximately .726 world z)')
    tail=tail.replace('bird/mist/smoke/dust/sheep','bird/mist/smoke/heat/animal')
    tail=tail.replace('stored sheep crop offset','stored animal crop offset')
    tail=tail.replace('extend `render_v2_set.py`','extend `render_density.py`')
    tail=tail.replace('Extend `render_v2_set.py`','Extend `render_density.py`')
    tail=tail.replace('64-file budget','72-file budget').replace('sheep-only atlases and Grain masks','sheep-and-goat atlases and Grain/Desert masks')
    tail=tail.replace('16 cropped sheep layers','16 cropped sheep-and-goat layers')
    # Supersede the previous round's validation/reproduction paragraph with current evidence.
    start=tail.index('## Validation plan and current results')
    end=tail.index('Before app integration is accepted:',start)
    tail=tail[:start]+'''## Validation plan and current results

Inspected all updated flat/mid/max views, the two neighbour patches, five phase sheets and all 64 cells of each updated overlay sheet; checked all bandit options at 25%. Iterations tapered Grain's canopy edges and cleared space for its cart, softened Stone's cleft contrast and fixed bandit proof clipping. `checks-v2.json` records 18 RGBA static views, six loops / 384 decoded frames, exact 0/8s equality, valid HTML links/JavaScript and unchanged application sources. `density-manifests/summary.json` records current bytes, timings, preserved Timber/app hashes and the file inventory. Old `render-manifest-v2.json` and the original REPORT.md describe the preceding round; use REPORT-density.md for this round.

'''+tail[end:]
    prefix=f'''# Modelled V2 tiles: density pass and integration plan

Status: density palette only. Timber PNGs and its loop retained byte-for-byte; MainActivity and TileLife unchanged. Stone retains the current connected ridge geometry while R1/R2/R3 selection remains pending. No downloaded art or app integration. Open palette-v2.html, palette-v2-density.png and ../bandit/bandit-options.png.

## Geometry and projection

- Fleece: lush rolling grass, flowers, bushes, three-course irregular dry-stone wall and wooden gate; six baked sheep and two tiny brown/white goats on rock outcrops. The exclusive moving layer adds one grazing/wandering sheep and one grazing goat: seven sheep / three goats in motion. Goats have slimmer bodies, upright tails, horns and beards.
- Grain: 15 curved rows, {timing['grain']['metadata']['ears']} modelled ears with kernels/awns, continuous rounded canopy masses, three stooks, a wooden cart with wheels/spokes and poppies along the field edge.
- Clay: one deep excavation with three cut terraces, wet dark floor/puddle, kiln/chimney, brick stacks, two slatted drying racks and plank shed. The hex top cap is opened so it cannot cover the below-ground pit; floor z=-.115 remains above the base bottom -.22.
- Desert: diagonal relief dunes with modelled ripples, rock clusters, loose stones, scrub, dead bleached tree and tiny skull. Deliberately retains more open sand than the other terrains.
- Stone: unchanged connected ridges/saddles/spurs, warmer dark bedrock and cleft faces, scree and snow caps. Native 1024px max view supplies a larger 2304 x 1536 nine-tile mock; default terrain sprites remain 512px.

Names, hex base/thickness and upper-left sun/soft sky follow the approved V2 set. Camera.MAX_TILT=.96 radians; Layout squash=cos(tilt). Orthographic elevations are 90, 62.498, 34.996 degrees for tilt 0, .48, .96. World +Y maps screen-up. Pointy hex radius .93, depth .22, bevel .018; viewport 2.65 world units. Every static sprite is padded 512 x 512 RGBA, origin (256,256); widest hex face is about 311px. Preserve padding/anchor and do not squash the already rendered geometry again.

`render_density.py` merges grass, flowers, wheat and rubble by material to avoid thousands of scene objects. Run with two threads and close Blender after each terrain / animal-phase process. `run_density.ps1` waits for each process exit before starting the next. Final source render time totals {seconds:.2f}s, excluding scene construction/startup and discarded iterations; per-view timings below are measured.

## Assets, APK and memory

Current delivery: six terrains x three views = 18 static sources; 15 updated this round. Combined PNGs: **{stats['tile_png_bytes']:,} bytes**; trial lossless WebP: **{stats['tile_lossless_webp_bytes']:,} bytes**. Eight production steps remain unrendered: t=.96*i/7, i=0..7. Their compressed estimates use the current three-view mean, not an APK build.

| Proposed eight-step shipping assets | Count | Estimated compressed bytes |
| --- | ---: | ---: |
| Six terrain bases | 48 | {round(stats['tile_lossless_webp_bytes']*8/3):,} |
| 16-phase sheep-and-goat atlases | 8 | {animal*8:,} |
| Grain wind masks | 8 | {grain*8:,} |
| Desert heat masks | 8 | {desert*8:,} |
| Total | **72 files + metadata** | **{estimate:,} (about {estimate/1e6:.2f} MB)** |

At six steps: 36 bases + 6 atlases + 12 masks = 54 files, about {estimate*.75/1e6:.2f} MB. Preview animated WebPs, raw 512px animal sources, historical sheep-only sprites, comparison sheets, 1024px Stone mock and bandit options are excluded. Ship only the selected bandit, separately measured. PNG/WebP ZIP savings are small; these are asset estimates, not measured APK deltas.

Decoded RGBA at 512px: all eight angles cost 48 MiB; the recommended adjacent-angle pair shared across six terrains costs 12 MiB. Two 1024 x 256 animal atlases add 2 MiB; two derived Grain patches and two Desert patches add 4 MiB. Four 256px ALPHA8 masks add .25 MiB (1 MiB if RGBA). Total active CPU bitmap estimate **18.25-19 MiB**, plus decode intermediates, board layers and possible GPU copies. Avoid per-hex duplicates and eagerly loading all angles. Mipmaps add about one third; a 1024px tier quadruples memory.

## Animation layers

Use TileLife's existing eight-second clock with deterministic offsets. Full-tile animated WebPs are review files: 64 x 125ms, infinite loops and exact phase 0/8 equality. Keep the still animal phase when motion is paused.

| Terrain | Cheapest selected runtime approach |
| --- | --- |
| Timber | Approved procedural bird polyline and three fading leaves; no new temporal assets |
| Stone | Three drifting blurred ribbon paths, cached brush; no temporal assets |
| Fleece | One 16-phase combined sheep/goat atlas per tilt, premultiplied interpolation, one shadow ellipse |
| Grain | Masked static wheat patch on a reused 16 x 16 mesh, 2px sine displacement |
| Clay | Nine looping smoke ellipses with cached soft brush, anchored at chimney (.515,-.045,.726) |
| Desert | Masked dune patch on reused mesh, .8px displacement for subtle heat shimmer |

Animal sources: 16 transparent 128 x 128 crops at fixed origin **(232,208)** in the 512px tile, safely padded over the entire wander loop. `animals_mid_atlas.webp`: 8 columns x 2 rows, 1024 x 256, **{animal:,} bytes**, decoded 1 MiB. Historical `sheep_mid_*` files are not used by the density palette. At a new tilt, rerender the animal layer and remeasure a common crop; do not reuse a mid-only anchor at max.

Grain `wind_mask_mid.png`: {grain:,} bytes; Desert `heat_mask_mid.png`: {desert:,} bytes, both 256px. Derive masked patches once from the corresponding base at cache fill; patch PNG/WebP examples need not ship. Grain excludes the cart/stooks; Desert limits distortion to central dunes, away from the base outline. Mesh arrays and blur brushes must be cached, not allocated/blurred each Android frame. Interpolation and effect occlusion still need integration measurement.

'''
    if timing['stone']['metadata'].get('selected_study')=='R3 Alpine chain':
        prefix=prefix.replace('Stone retains the current connected ridge geometry while R1/R2/R3 selection remains pending.', 'Mauro selected R3 Alpine chain for Stone: seven summits with a dominant 1.08-unit peak and three added secondary peaks.')
        prefix=prefix.replace('- Stone: unchanged connected ridges/saddles/spurs, warmer dark bedrock and cleft faces, scree and snow caps. Native 1024px max view supplies a larger 2304 x 1536 nine-tile mock; default terrain sprites remain 512px.', '- Stone: selected R3 Alpine chain with four original summits plus three new peaks (.65/.74/.79), shared shoulders/saddles, warm dark bedrock/clefts, snow caps, scree and five pines. Three 512px views plus a 1024px single source; the main neighbour proof renders nine edge-sharing model instances at 1536 x 1024. A separate 2304 x 1536 sprite mock retains game spacing. See stone-alpine/REPORT.md.')
        prefix=prefix.replace('Three drifting blurred ribbon paths, cached brush; no temporal assets', 'Three drifting blurred saddle wisps plus one soaring bird; cached brush/polyline, no temporal assets')
        prefix=prefix.replace('18 static sources; 15 updated this round.', '18 static sources; the density round updated 15, and this R3 follow-up replaces the three Stone views.')
        prefix=prefix.replace('Final source render time totals', 'Current static/animal manifests, combining the density round and current R3 single views, total')
        tail=tail.replace('Inspected all updated flat/mid/max views,', 'Stone R3 is now current; its views, native nine-instance patch, sprite mock, phone proof and full/phone loop frames were inspected. Inspected all updated flat/mid/max views,')
        if 'R3 shoulder heights are periodic' not in tail:
            tail=tail.replace('The rear tile is drawn first so nearer peaks cover it.', 'The rear tile is drawn first so nearer peaks cover it. R3 shoulder heights are periodic across physical hex edges (radius .93); the current Layout centers use nominal radius 1 and retain inset gaps. The geometry proof is edge-sharing, while stone-range-sprite-patch_V2_max.png shows the actual game-spacing approximation. Measure these gaps before integration; extend border terrain or use a shared ground pass if they break continuity.')
    plan.write_text(prefix+tail,encoding='utf-8')
    table='\n'.join(f'| {r.title()} | '+ ' / '.join(f'{n:.2f}s' for n in times[r][:3])+' |' for r in times)
    report=f'''# V2 density round

Open [animated palette](palette-v2.html), [static palette](palette-v2.png), [before / after](palette-v2-density.png), [larger Stone patch](stone-range-patch_V2_max.png), [goat detail](fleece-animals-detail.png), [bandit options](../bandit/bandit-options.png) and [25% bandit proof](../bandit/bandit-options-phone-25pct.png).

Fleece and Grain show the strongest density gains. Fleece's small brown/white goats retain horns, beard, thin legs and upright tails; sheep retain chunky white wool. Clay uses an actual recessed three-level cut, not rings above a flat surface. Desert retains intentional emptiness between relief dunes. Stone's shape is unchanged, with warmer rock and snow. No R1/R2/R3 studies were changed.

Bandit recommendation: **B3** for the clearest ground-level silhouette at 25%; **B2** for atmosphere and its warm lantern/pool. B1 remains clear but has the weakest ground cue. Reused all three completed own Blender raw renders and 512px sprites from the earlier run. New composite proof is 35% of physical hex width (about 109px model width; 27px at 25%) on Timber flat/max and dense Grain max. Bodies stay upright. This is an art-size proof; gameplay grounding/occlusion and device readability remain integration work.

## Render timing

Blender 4.5.1, Cycles32 denoise (animals24), two threads, one terrain/phase per process. Scene construction/startup not included:

| Terrain | Flat / mid / max |
| --- | --- |
{table}

Stone native1024 max: {times['stone'][3]:.2f}s. Each of sixteen animal source phases: 2.59-2.80s. Final source views/animal layers total {seconds:.2f}s; earlier iterations add time. Blender-reported peak counters are in density-manifests/summary.json; these are not total process resident memory. Bandit reused renders: no Blender render time this round.

## Files

- `{{stone,fleece,grain,clay,desert}}_V2_{{flat,mid,max}}.png`: 15 updated transparent512 sources.
- Those five `*_V2_mid_animated.webp`: updated seamless8s previews. Timber's three PNGs and animatedWebP remain byte-identical.
- `palette-v2.html`, `palette-v2.png`, `palette-v2-density.png`; three `inspection-v2-*.png` sheets.
- `stone_V2_max_mock1024.png`, `stone-range-patch_V2_max.png` (2304x1536), `board-patch_V2_max.png`.
- `fleece-animals-detail.png`; `animation-layers/fleece/animals_mid_00..15.png`, `animals_mid_atlas.webp`, and `raw/` source renders.
- `animation-layers/grain/wind_mask_mid.png`, illustrative wind patches; `animation-layers/desert/heat_mask_mid.png` and illustrative heat patch.
- `animation-review/`: phases and all64 overlay cells per updated terrain. `animation-validation.json`, `validation-v2.json`, `checks-v2.json`, `density-manifests/` and preserved `density-before/` sources/hashes.
- `../bandit/bandit-options.png` (1536x1780), `bandit-options-phone-25pct.png` (384x445), nine `B*-on-{{timber-flat,timber-max,grain-max}}.png` composites, reused B1/B2/B3 512pxRGBA sprites/raws, updated validation/report.
- `tools/blender/render_density.py`, `animate_v2.py`, `compose_v2_set.py`, `compose_density.py`, `finalize_density.py`, `run_density.ps1`; PLAN.md and CREDITS.md.

Run `powershell -File tools/blender/run_density.ps1` from this worktree to reproduce sequentially. Pillow is required. The script blocks on every Blender process and fails on nonzero exits. Before snapshots are retained rather than recreated from the after artwork. Existing bandit raw renders/final sprites are reused by composition. Full shipping eight-step views remain planned; only flat/mid/max and mid animations exist.

Validation: 18 static RGBA views/margins; 16 safe animal crops; six decoded loops, 384 frames, exactly8000ms each; exact phase endpoint equality and nonzero motion; HTML local references and JavaScript syntax. Timber + MainActivity + TileLife hashes unchanged. Inspected all final views, phase/overlay sheets, neighbour patches and bandit phone proof. No emulator/adb, app wiring, commit or push.
'''
    if timing['stone']['metadata'].get('selected_study')=='R3 Alpine chain':
        report=report.replace("Stone's shape is unchanged, with warmer rock and snow. No R1/R2/R3 studies were changed.", 'Stone now uses the selected R3 Alpine chain plus three secondary peaks, shared shoulders, warmer rock and snow. Historical R1/R2/R3 study images are retained. See stone-alpine/REPORT.md for the Stone follow-up.')
        report=report.replace('`stone-range-patch_V2_max.png` (2304x1536)', '`stone-range-patch_V2_max.png` (1600x1180 edge-sharing geometry proof), `stone-range-sprite-patch_V2_max.png` (2304x1536 game-spacing mock)')
    (OUT/'REPORT-density.md').write_text(report,encoding='utf-8')
    print('DENSITY_OK: preserved hashes, animal margins, budgets, timings, plan and report')

if __name__=='__main__':main()

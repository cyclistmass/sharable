"""Record current palette budgets and supersede earlier density-only notes."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/design/tiles-3d-models'
def main():
    stats=json.loads((OUT/'validation-v2.json').read_text());validation=json.loads((OUT/'token-clearing/validation.json').read_text())
    layers=OUT/'animation-layers';atlas=(layers/'fleece/animals_mid_atlas.webp').stat().st_size
    masks=sum((layers/p).stat().st_size for p in ['grain/wind_mask_mid.png','desert/heat_mask_mid.png'])
    base=round(stats['tile_lossless_webp_bytes']*8/3);estimate=base+8*(atlas+masks)
    timings=[]
    for terrain in ['timber','stone','fleece','grain','clay','desert']:
        timings+=json.loads((OUT/f'density-manifests/{terrain}.json').read_text())['renders']
    timings+=json.loads((OUT/'density-manifests/stone_patch.json').read_text())['renders']
    report=f'''# V2 number-clearance pass

All six terrains have a flat circle at model (-0.36, +0.30), radius 0.31 for the upright chosen prototype's radius-0.27 plate (the app still draws 0.30 pending integration). The low foreground apron also protects the plate at 55°. See ART_SPEC.md for the shared rule and source coordinates.

Terrain changes: Timber trees form a rear/right forest around a glade; Stone keeps seven Alpine summits around a flat warm-rock shelf; Fleece has a trampled patch, grouped alpacas and tiny goats and a rear wall/gate; Grain rows stop at a threshed clearing; Clay has a level yard with the shed moved behind/right; Desert has a flat sand pan and relocated outcrops. Surface spacing avoids coincident-face black artifacts. Tall content stays behind or beside the plate.

Review: `palette-v2.html`, `palette-v2.png`, `token-check.png` (18 views plus mixed 3×3 patch), `token-check-patch-max.png`, and `token-clearing/*phone-25pct.png`. All six 8s animated WebPs and their phase/overlay-frame sheets were refreshed. The 16-phase Fleece sprites now contain a long-necked alpaca shifting, grazing and turning its head, plus a tiny goat. Six static alpacas include two looking up; all motion paths stay outside the clearing. A cached analytic mask keeps every moving layer outside the upright plate, including after blur/warping/interpolation.

Current 18 static PNGs: **{stats['tile_png_bytes']:,} bytes**. Trial lossless WebP total: **{stats['tile_lossless_webp_bytes']:,} bytes**. Eight-step production estimate: 48 bases ({base:,} bytes) + eight animal atlases + sixteen effect masks = **72 files + metadata, {estimate:,} bytes**. The plate guard is procedural, not another shipped bitmap. Eight-angle assets are not yet rendered; this is an estimate, not an APK measurement.

Final retained renders: **{sum(t['seconds'] for t in timings):.2f}s** of Blender render time for 18 views, the 1024px Stone source and native 3×3 Stone patch, excluding scene construction/startup and discarded iterations. Each process used two threads and exited before the next started. Per-view timings are in density-manifests; logs are in token-clearing.

Validation: all 18 RGBA views have transparent margins; six loops / 384 frames are eight seconds and seamless; all 384 overlay frames have zero alpha in the guarded plate area; all {validation['app_files_unchanged']} application-file hashes match the read-only baseline. The mixed patch has **zero plate pixels covered by foreground neighbours**. No app edits, emulator, commit or push. The proof deliberately draws each tile with its plate in rear-to-front order. Inspected every view, every terrain's phase/overlay sheets and the 25% phone proofs. The current preview file list is `FILES.txt`; reproduce with `tools/blender/run_token_clearings.ps1`.
'''
    (OUT/'token-clearing/REPORT.md').write_text(report,encoding='utf-8')
    inventory=[f'{r}_V2_{a}.png' for r in ['timber','stone','fleece','grain','clay','desert'] for a in ['flat','mid','max']]
    inventory += [f'{r}_V2_mid_animated.webp' for r in ['timber','stone','fleece','grain','clay','desert']]
    inventory += ['palette-v2.html','palette-v2.png','token-check.png','token-check-patch-max.png','ART_SPEC.md','PLAN.md',
                  'token-clearing/token-check-phone-25pct.png','token-clearing/token-check-patch-phone-25pct.png',
                  'stone-range-patch_V2_max.png','stone-range-sprite-patch_V2_max.png']
    (OUT/'token-clearing/FILES.txt').write_text('\n'.join(inventory)+'\n',encoding='utf-8')
    plan=OUT/'PLAN.md';text=plan.read_text(encoding='utf-8')
    text=text.split('\n## Shared number clearing (current revision)')[0]
    text=re.sub(r'Status:.*?\n', 'Status: number-clearing palette revision across all six terrains; MainActivity and TileLife unchanged. Current proof and measurements: token-clearing/REPORT.md, token-check.png and ART_SPEC.md. Earlier density/R3 reports are historical.\n',text,count=1)
    text=re.sub(r'Current delivery:.*?\n\n',f'Current delivery: six terrains × three views = 18 static sources, all refreshed for number clearance. PNGs: **{stats["tile_png_bytes"]:,} bytes**; trial lossless WebP: **{stats["tile_lossless_webp_bytes"]:,} bytes**. Eight-step estimate remains 72 shipping files + metadata, **{estimate:,} bytes**, excluding review animations and sheets.\n\n',text,count=1,flags=re.S)
    text=re.sub(r'\| Six terrain bases \| 48 \|.*?\|',f'| Six terrain bases | 48 | {base:,} |',text)
    text=re.sub(r'\| Total \| \*\*72 files \+ metadata\*\* \|.*?\|',f'| Total | **72 files + metadata** | **{estimate:,} bytes** |',text)
    text=re.sub(r'\| 16-phase alpaca-and-goat atlases \| 8 \|.*?\|',f'| 16-phase alpaca-and-goat atlases | 8 | {8*atlas:,} |',text)
    for label,path in [('Grain wind masks','grain/wind_mask_mid.png'),('Desert heat masks','desert/heat_mask_mid.png')]:
        text=re.sub(r'\| '+label+r' \| 8 \|.*?\|',f'| {label} | 8 | {8*(layers/path).stat().st_size:,} |',text)
    text=text.replace('Timber PNGs and its loop retained byte-for-byte','Timber refreshed around a common glade')
    text=text.replace('six baked sheep','six baked alpacas').replace('one grazing/wandering sheep','one grazing/wandering alpaca').replace('seven sheep / three goats','seven alpacas / three goats').replace('than a smaller wool sphere','than the alpacas')
    text=text.replace('preserved Timber/app hashes','application hashes')
    text=text.replace('four original summits plus three new peaks','seven varied summits arranged around the number shelf')
    text=text.replace('15 curved rows, 1018 modelled ears','curved rows cut around the plate reservation, dense modelled ears')
    text=text.replace('scree and five pines','scree and foothill pines')
    text=text.replace('See stone-alpine/REPORT.md.','See token-clearing/REPORT.md for the current revision.')
    text=re.sub(r'Current static/animal manifests,.*?per-view timings below are measured\.',
                f'Current retained static renders and the native Stone patch total {sum(t["seconds"] for t in timings):.2f}s, excluding construction/startup and discarded iterations; current per-view timings are in token-clearing/budget.json.',text)
    text=re.sub(r'At six steps:.*?Preview animated WebPs',
                f'At six steps: 36 bases + 6 atlases + 12 masks = 54 files, about {estimate*.75/1e6:.2f} MB. Preview animated WebPs',text)
    start=text.index('## Validation plan and current results');end=text.index('Before app integration is accepted:',start)
    text=text[:start]+'''## Validation plan and current results

Inspected all eighteen views, six four-phase sheets and all 64 cells of each overlay sheet, the native Stone patch, and token checks at 25% phone scale. The mixed nine-tile proof draws terrain + plate layers rear-to-front and passes with zero covered plate pixels. All 384 overlay frames have zero alpha in the reserved screen circles; six decoded loops are seamless, 64 frames / 8 seconds each. All 146 application-file hashes match the baseline. ART_SPEC.md is the shared geometry rule; token-clearing/REPORT.md and budget.json record the current sources, timings and bytes. Earlier density and R3 reports are historical.

'''+text[end:]
    text=text.replace('Reproduction commands and file inventory are in REPORT.md.','Reproduce with tools/blender/run_token_clearings.ps1; current preview file inventory is token-clearing/FILES.txt.')
    text+='''
## Shared number clearing (current revision)

Follow ART_SPEC.md and the common `token_space.py` constants. Store centre (-0.36, +0.30), ground radius 0.31 and upright plate radius 0.27 in renderer metadata. At runtime project the centre with Layout's tilt/scale, but keep the plate circle upright. Cache one ALPHA8 guard per angle (or generate it analytically); no new shipping files are needed. Apply it after all TileLife blur, sprite interpolation and Grain/Desert displacement. A pair of 512px ALPHA8 guards adds 0.5 MiB to the active bitmap estimate; use 256px cached masks with a conservative margin for 0.125 MiB instead. Do not allocate per hex or per frame.

Order each terrain sprite and its number plate together back-to-front; keep pieces/UI in their existing passes. Verify neighbour overdraw using token-check-patch-max.png before wiring. The low foreground geometry is part of the art, not only a compositing mask. All 18 sources and six review loops were regenerated; the eight production tilt steps still need sequential rendering. Current budget/render evidence is token-clearing/REPORT.md; use `run_token_clearings.ps1` to reproduce this revision.
'''
    plan.write_text(text,encoding='utf-8')
    (OUT/'token-clearing/budget.json').write_text(json.dumps({'png_bytes':stats['tile_png_bytes'],'trial_webp_bytes':stats['tile_lossless_webp_bytes'],'eight_step_shipping_files':72,'eight_step_estimated_bytes':estimate,'final_render_seconds':sum(t['seconds'] for t in timings),'renders':timings},indent=2))
    print('TOKEN_REPORT_OK',estimate,'estimated shipping bytes')
if __name__=='__main__':main()

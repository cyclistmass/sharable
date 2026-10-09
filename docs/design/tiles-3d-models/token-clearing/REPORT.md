# V2 number-clearance pass

All six terrains have a flat circle at model (-0.36, +0.30), radius 0.345 for the upright game's radius-0.30 plate. The low foreground apron also protects the plate at 55°. See ART_SPEC.md for the shared rule and source coordinates.

Terrain changes: Timber trees form a rear/right forest around a glade; Stone keeps seven Alpine summits around a flat warm-rock shelf; Fleece has a trampled patch, relocated sheep/goats and a rear wall/gate; Grain rows stop at a threshed clearing; Clay has a level yard with the shed moved behind/right; Desert has a flat sand pan and relocated outcrops. Surface spacing avoids coincident-face black artifacts. Tall content stays behind or beside the plate.

Review: `palette-v2.html`, `palette-v2.png`, `token-check.png` (18 views plus mixed 3×3 patch), `token-check-patch-max.png`, and `token-clearing/*phone-25pct.png`. All six 8s animated WebPs and their phase/overlay-frame sheets were refreshed. The existing 16-phase sheep/goat sprites are reused: their motion paths and ground heights remain outside the changed ground. A cached analytic mask keeps every moving layer outside the upright plate, including after blur/warping/interpolation.

Current 18 static PNGs: **3,388,525 bytes**. Trial lossless WebP total: **1,277,706 bytes**. Eight-step production estimate: 48 bases (3,407,216 bytes) + eight animal atlases + sixteen effect masks = **72 files + metadata, 3,816,712 bytes**. The plate guard is procedural, not another shipped bitmap. Eight-angle assets are not yet rendered; this is an estimate, not an APK measurement.

Final retained renders: **158.87s** of Blender render time for 18 views, the 1024px Stone source and native 3×3 Stone patch, excluding scene construction/startup and discarded iterations. Each process used two threads and exited before the next started. Per-view timings are in density-manifests; logs are in token-clearing.

Validation: all 18 RGBA views have transparent margins; six loops / 384 frames are eight seconds and seamless; all 384 overlay frames have zero alpha in the guarded plate area; all 146 application-file hashes match the read-only baseline. The mixed patch has **zero plate pixels covered by foreground neighbours**. No app edits, emulator, commit or push. The proof deliberately draws each tile with its plate in rear-to-front order. Inspected every view, every terrain's phase/overlay sheets and the 25% phone proofs. The current preview file list is `FILES.txt`; reproduce with `tools/blender/run_token_clearings.ps1`.

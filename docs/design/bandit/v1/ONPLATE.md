# Bandit V1 blocks the plate

The existing original skeleton corsair now stands at tile (-0.36,+0.30). Model scale 0.70, versus 0.46 previously; the full-round frayed brim spans 0.644 lattice units, larger than the plate diameter 0.54. Sprite foot anchor stays (256,256), orthographic scale 2.65, transparent 512px. Preserve padding and apply the projected plate-centre translation.

An opaque dark shadow seal of radius 0.278 is baked underneath the feet in each sprite. It remains upright, matching the game's upright number plate rather than a foreshortened ground ellipse. This intentionally hides the whole face including digits and pips at every angle. Render the plate first, then this complete Bandit sprite; never redraw its number above the Bandit. Use premultiplied interpolation so the identical seal stays opaque between samples.

Eight production tilt sprites and flat/mid/max proof sources were regenerated sequentially. Historical front/threequarter portraits remain the earlier scale. Review ../bandit-v1-onplate.png, its 25% phone proof and the eight-step sheet. Exact digit/pip alpha checks passed at all eleven rendered views; numbered versus blank composites match at six Timber/Grain views. All 146 app hashes unchanged. Final render time 33.83s excluding startup/model construction.

Reproduce: Blender -b -t 2 -P tools/blender/render_bandit_v1.py -- --batch onplate; after Blender exits run Python tools/blender/compose_bandit_onplate.py. No app wiring, emulator, commit or push. Earlier off-plate sheets are historical and their old composer must not be used on these replacement sprites.

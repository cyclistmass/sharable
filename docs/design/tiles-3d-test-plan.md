# Tile depth and life validation

Before implementation: compare A1 continuous extrusion + height-baked faces and A2
three discrete angle sprites, for Timber, Clay, Fleece, Grain, Stone and Desert at
0, 35 and 60 degrees. Use existing owned paintings, with procedural relief.

Unit checks: corners, extrusion depth, upper-left edge lighting, tilt quantization,
deterministic periodic animation and bounded density. Measure hot-loop allocation
with the JVM thread allocation counter where supported. Build all four modules
and the debug APK. Existing random-play and illegal-action suites provide regression
coverage; no rules or network behavior changes are intended.

Device board/Inventory screenshots, frame timing and monkey testing are deferred
to the emulator owner under Mauro's explicit no-emulator/no-adb instruction.
Palette screenshots are desktop artifacts, not evidence of Android performance.

# Scenery regression test plan

#34: Simulate six seeded fleets for 120 seconds at 60 fps. Check lane and actual heading reversals are at least 20 seconds apart, heading changes respect 0.35 rad/s, and pass sides stay fixed near the island. Existing SeaTest checks island and screen clearance. Build the debug APK. Device inspection is deferred because this task prohibits emulator and adb use.

Rules are unchanged. Run the existing engine/AI/network unit, random-play fuzz and illegal-action suites as regression coverage; add seeded animation simulation for the changed movement.


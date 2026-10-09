# Scenery regression test plan

#34: Simulate six seeded fleets for 120 seconds at 60 fps. Check lane and actual heading reversals are at least 20 seconds apart, heading changes respect 0.35 rad/s, and pass sides stay fixed near the island. Existing SeaTest checks island and screen clearance. Build the debug APK. Device inspection is deferred because this task prohibits emulator and adb use.

Rules are unchanged. Run the existing engine/AI/network unit, random-play fuzz and illegal-action suites as regression coverage; add seeded animation simulation for the changed movement.

#37: Sample 721 angles at every Raiders track position. Check open-sea and sprite margins, strictly shrinking radius and continuous positions. Simulate track advances, resets and the battle run-in. In a later device session, inspect start=chivalry for HUD/panel clearance and the wake at fit zoom and tilt; device checks are not performed here.


# Working German Board Game

Open-source Android hex-trading board game, compatible with the rules of the
classic German settlement-building game, for up to 4 players plus AI.
Not affiliated with or endorsed by Catan GmbH.

License: GPL-3.0 (see `LICENSE`).

## Modules

| Module | Purpose |
|---|---|
| `engine/` | Pure Kotlin, deterministic rules engine (base game, then Cities & Knights). Runs on PC. |
| `ai/` | Pure Kotlin bots: Catanatron-style value function + AlphaBeta, later ONNX-guided search. |
| `app/` | Android UI (Jetpack Compose). |
| `trainer/` *(planned)* | PC-side Python training on a Catanatron fork, exports ONNX. |
| `net/` *(planned)* | Firebase transport. |

## Build

Requires JDK 17 and the Android SDK (`local.properties` with `sdk.dir`, not committed).

```
./gradlew :engine:test          # rules tests on PC
./gradlew :app:installDebug     # install on running emulator/device
```

Secrets (`google-services.json`, keystores, `.env`) are gitignored and must never be committed.

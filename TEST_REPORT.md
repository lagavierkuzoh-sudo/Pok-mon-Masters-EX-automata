# v2.2 test report

## Pure Java planner

Executed:

```bash
javac -d /tmp/pmcore app/src/main/java/com/example/pomastersstrategist/core/*.java tools/PlannerHarness.java
java -cp /tmp/pmcore PlannerHarness
```

Observed output included:

```text
Opening: P2 Electric Terrain
First-sync choice: P3 Cynthia Sync
Red-1996 first-sync choice: P2 Red 1996 Sync
Gauge: 0.211 bars/s normal, 0.316 accelerated
ALL CORE PLANNER TESTS PASSED
```

Coverage includes:

- Ash + SST Red + SSA Cynthia opening planning;
- Cynthia Support + Sprint first-sync economics;
- Red (1996) Support EX Role/unique first-sync behavior;
- exact-Speed move-gauge regeneration and Move Gauge Acceleration;
- imported sync-gated moves;
- enemy-target variants and low-HP finishing;
- sequence-aware learning contexts.

## Trainer.txt compiler

Executed:

```bash
python tools/test_parser.py
```

Observed:

```text
TRAINER.TXT PARSER TEST PASSED
```

The parser test checks role/EX role, Lv.200 stats, Buddy/sync gating, flinch inference, Tera handling and Tera-entry semantics on a v2.73-style sample.

## New autonomous Android components

v2.2 adds Android-dependent `BattleVision` automatic HP/gauge fallback, `AutonomousLoop`, `AutoButtonFinder`, terminal/replay state handling and self-play learning integration. The container used for generation has no Android SDK, so those classes could not be compiled into an APK here. They are isolated from the Android-free strategy harness so the planner/data layer remains directly testable.

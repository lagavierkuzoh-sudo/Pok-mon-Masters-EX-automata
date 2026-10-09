# Pokémon Masters EX Autonomous Strategist v2.2

Android source project for a screen-only **self-learning Pokémon Masters EX battle bot** built around Android MediaProjection + Accessibility.

The important v2.2 change is that **SELF mode does not need human reward feedback**. You do not press `+` or `−`, you do not label a victory screen, and you do not tell it which move was good. Once Android permissions, team/build data, and any device-specific tap coordinates are set up, the battle/training loop is autonomous.

The project is researched against **Pokémon Masters EX Ver. 2.73.0** (September 27, 2026 PT) and retains the v2.1 semantic knowledge model for Lv. 1–200 Sync Pairs, Move Level, 6★ EX, EX Role, Superawakening, exact current stats, fields/zones/weather, Buddy moves, Tera/Max behavior, Sync Grid emphasis and pair-specific mechanics.

## What “learns by itself” means

SELF mode automatically does the whole reinforcement loop:

1. reads the battle screen;
2. maintains a semantic battle state;
3. plans from known Sync Pair abilities and your exact build;
4. selects and taps a legal action;
5. observes enemy HP loss, enemy KOs, ally HP loss and survival;
6. reinforces or penalizes the recent action sequence from those observations;
7. infers victory or defeat without a manually taught terminal screenshot;
8. updates the stage/team-specific policy;
9. advances result screens / tries to replay;
10. starts the next episode and keeps training.

This is deliberately not the Sonic approach of “randomly fail until raw pixels somehow become meaningful.” Pokémon Masters has a structured action space and known move mechanics, so the bot begins with a strong expert prior and uses reinforcement learning to specialize that prior for the exact stage and team.

## Autonomous learning architecture

### Expert prior

`BattlePlanner` uses a **7-action bounded beam search (width 36)**. It evaluates future move gauge, sync countdown, first-sync economics, fields, setup, limited MP, healing, damage windows, Buddy activation, Tera/Max state and build-aware damage before choosing a move.

### Dense self-generated reward

`BattleRuntime` continuously compares visually observed HP changes:

- enemy damage → positive reward;
- enemy KO → larger positive reward;
- ally damage → smaller negative reward;
- victory → strong terminal positive reward;
- defeat → strong terminal negative reward.

This gives the learner many training signals during a battle rather than only one signal at the end.

### Stage/team-specific memory

`LearningBrain` stores contextual action values under:

`stage fingerprint + full team/build signature + battle context + semantic action`

Each stage/team also gets its **own high-level strategy profile** rather than sharing one global “best strategy.” That profile controls things such as healing threshold, setup bias, field bias, survival bias, first-sync preference, sync urgency and gauge conservation.

### Autonomous exploration

A new stage/team begins with meaningful exploration, but the bot only explores **legal actions accepted by the semantic planner**. It uses an upper-confidence bonus for under-tested actions and evolves nearby strategy profiles after episodes.

Exploration automatically anneals with experience on that particular stage/team:

- high early exploration to discover useful alternatives;
- progressively more exploitation as evidence accumulates;
- a small exploration floor so it can still recover from a bad local policy.

This means training a different stage does not make a well-trained stage “forget” how to play.

## Automatic win/loss detection

v2.2 no longer requires long-pressing `+` on a win screen or `−` on a loss screen.

The autonomous loop uses:

- visually observed allied/enemy HP bars;
- disappeared individual bars as KO evidence when the rest of the battle UI is still present;
- cumulative enemy-vs-ally damage;
- disappearance of the battle HUD/result-screen transition;
- current alive combatants.

The old `TerminalClassifier` is retained only for legacy/manual AUTO behavior; SELF mode does not depend on it.

## Automatic result / replay navigation

After SELF infers a terminal result, battle planning stops. The bot waits for the result flow, then:

- uses calibrated `NEXT`, `OK`, or `REPLAY` coordinates when available;
- otherwise uses `AutoButtonFinder`, which searches the lower result screen for a large bright/saturated button and taps it;
- watches for the battle HUD to return;
- resets the internal simulator and starts a new learning episode automatically.

This allows repeated training runs without touching the phone, provided the selected game mode actually offers replay/continue buttons and does not require some unrelated manual choice.

## Automatic HP/gauge vision fallback

Manual ROI calibration is no longer mandatory for learning. `BattleVision` now has a screen-only fallback that searches the three expected portrait battle columns for horizontal HP-colored bars and the bottom of the screen for the blue/cyan move gauge.

Manual region calibration is still supported and will override the fallback. It is recommended if your resolution, aspect ratio, UI scaling, language, accessibility settings or a future game update makes the automatic detector unreliable.

## Requested high-detail Sync Pairs

The bundled knowledge pack includes hand-tuned definitions for:

- **Ash & Pikachu**
- **Sygna Suit Red (Thunderbolt) & Pikachu**
- **Red (1996) & Pikachu**
- **Sygna Suit Cynthia (Aura) & Lucario**

For an Ash + SST Red + SSA Cynthia team, the planner already understands examples such as Electric Terrain timing, Origin Volt Tackle’s field requirement, Ash’s Buddy activation sequence and gauge scaling, Cynthia’s Fighting Zone / hit-gated Buddy behavior, and Support/Sprint/Field first-sync economics. SELF then learns stage-specific ordering and timing on top of that prior.

## Current-data support

`tools/trainer_txt_to_pack.py` converts current/monthly `Trainer.txt` data into the app’s semantic knowledge-pack format. This is the intended route for keeping the roster current without rewriting the planner.

The importer already understands many common effects including:

- stat buffs/debuffs;
- heals and gauge fills;
- sync-countdown manipulation;
- weather / terrain / zones / circles;
- Move Gauge Acceleration;
- Physical/Special/Sync Moves Up Next;
- paralysis / flinch / trap / confusion;
- common Buddy activation conditions;
- some Tera entry/move behavior.

Unusual one-off passives may still need hand-authored semantic tags. Unknown mechanics are intentionally left unknown instead of being invented.

## Modes

**ASSIST**: plans and explains, no taps.

**AUTO**: executes the strongest current expert+learned policy without autonomous exploration/replay training.

**SELF**: full autonomous learning mode. This is the default in v2.2. It explores, learns from visual outcomes, infers terminals, navigates result screens and starts the next episode.

## One-time setup vs intervention during learning

SELF removes human intervention from the **learning loop**, but Android does not allow an app to silently grant itself Accessibility or MediaProjection privileges. Those permissions are still a one-time user action.

Battle action coordinates also depend on device/UI geometry. If you are updating the same application from v2.1, the existing `action_profile_v2` calibration is intentionally retained. On a fresh install, calibrate PAIR-1/2/3 and the move/sync/max controls if needed. Once that is done, SELF does not require you to reward, punish, reset, label wins/losses, or start each training episode manually.

## Recommended training procedure

1. Configure the three Sync Pairs and their actual build/stats.
2. Enable the Accessibility service and screen capture permission.
3. Reuse or calibrate battle tap coordinates once.
4. Start with a repeatable solo stage that can be replayed.
5. Switch to **SELF** (v2.2 defaults to SELF).
6. Watch the first few battles only to confirm that taps and HP detection match your screen.
7. Leave it training. The overlay shows global wins/losses, current stage record, exploration rate (`eps`), learned state count, gauge/HP observations and current planned action.

For a newly seen stage, expect more experimentation. As that stage/team accumulates battles, `eps` decays and the learner increasingly reuses the best observed policy.

## Testing performed here

The Android SDK is not installed in the generation environment, so an APK was not compiled here. The Android project targets SDK 35 / min SDK 26.

The Android-free planner and data importer were tested:

```text
Opening: P2 Electric Terrain
First-sync choice: P3 Cynthia Sync
Red-1996 first-sync choice: P2 Red 1996 Sync
Gauge: 0.211 bars/s normal, 0.316 accelerated
ALL CORE PLANNER TESTS PASSED
TRAINER.TXT PARSER TEST PASSED
```

See `TEST_REPORT.md` for coverage.

## Important limitations

No screen-only bot can guarantee perfect play on every Pokémon Masters EX battle. Stage gimmicks, unusual passives, UI changes, animations that obscure HP, unusual result flows and incomplete pair knowledge can all reduce reliability. The autonomous fallback is designed to learn without labels, not to claim access to hidden game state.

The project does **not** inject into Pokémon Masters EX, read process memory, modify the game, bypass anti-cheat, conceal automation or implement ban evasion. It only uses user-approved screen capture and Android Accessibility. Automation may still violate the game’s terms or fair-play rules.

# Architecture — v2.2 autonomous SELF

`MainActivity` — team/build editor, permissions, capture start and custom knowledge-pack import.

`PairCatalog` — bundled Ver. 2.73 semantic pair data plus an optional imported pack.

`BattleVision` — 24×14 HSV fingerprint plus HP/gauge extraction. Calibrated ROIs override a new automatic portrait-layout fallback.

`BattleRuntime` — fuses vision with semantic battle state, emits dense self-reward from HP/KO movement, tracks missing bars as KO evidence, scopes learning by stage+build, and infers terminal outcomes.

`BattlePlanner` — legal-action generator + 7-ply width-36 beam search. The learner never has to rediscover that a move costs gauge, requires a field, has limited MP, or only activates after a condition.

`LearningBrain` — persistent contextual Q memory and **per-stage/per-team** policy profiles. SELF uses stage-local UCB exploration with an automatically decaying epsilon and strategy mutation/selection between episodes.

`AutonomousLoop` — hands-off episode state machine: battle → terminal inference → result navigation → fresh battle → reset/restart learning.

`AutoButtonFinder` — result-screen-only visual fallback for finding a large lower-screen button when explicit NEXT/OK/REPLAY coordinates were not calibrated.

`ActionExecutor` — semantic action to Accessibility tap sequence, with adaptive inter-tap timing.

`FloatingOverlayService` — ASSIST/AUTO/SELF mode selection, status, and optional one-time coordinate/ROI calibration. Human reward controls were removed from the normal loop.

`TerminalClassifier` — legacy labelled screenshot classifier retained for non-SELF AUTO compatibility; SELF does not depend on it.

## SELF decision loop

1. Capture a frame.
2. Detect battle HUD confidence, gauge and allied/enemy HP.
3. Update semantic state and generate dense damage/KO reward.
4. If combat ended, autonomously infer win/loss and stop combat taps.
5. Otherwise ask `BattlePlanner` for the best 7-ply legal plan under the current stage policy plus learned contextual action values/UCB.
6. Dispatch the semantic action through Accessibility.
7. Observe what actually happened on screen and reinforce the recent sequence.
8. On terminal result, reinforce the whole episode and evolve/retain the stage policy.
9. Navigate result/replay screens.
10. Detect the fresh battle HUD, reset the simulator and repeat.

The design principle remains **knowledge first, self-learning second**. This gives the learner useful prior structure while still letting repeated real battles teach it stage-specific sequencing that the hand-authored planner did not know in advance.

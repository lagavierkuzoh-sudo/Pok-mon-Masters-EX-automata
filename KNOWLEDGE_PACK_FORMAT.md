# Knowledge pack format — schema 2

The app loads `app/src/main/assets/pair_catalog_v273.json`, then merges an optional imported JSON pack. A custom pair with the same `id` replaces the bundled definition.

```json
{
  "schema": 2,
  "gameVersion": "2.73.0",
  "plannerVersion": "2.0",
  "pairs": [
    {
      "id": "unique_pair_id",
      "trainer": "Trainer",
      "pokemon": "Pokemon",
      "role": "TECH",
      "exRole": "FIELD",
      "type": "Electric",
      "stats140": [700, 300, 200, 400, 200, 300],
      "stats140Max": [860, 340, 260, 440, 260, 380],
      "stats200": [900, 390, 300, 520, 300, 410],
      "stats200Max": [980, 430, 340, 560, 340, 450],
      "themes": ["Electric", "Kanto"],
      "passives": ["CLUTCH_CRIT"],
      "moves": [],
      "sync": null,
      "maxMoves": [],
      "sourceNote": "Short provenance/review note"
    }
  ]
}
```

`stats200` / `stats200Max` are optional. When present, the build engine interpolates owned stats between Lv.140 and Lv.200 instead of using the fallback level scale. Exact stats entered in the Android UI still override all estimates.

A move object:

```json
{
  "id": "move_1",
  "name": "Move name",
  "kind": "MOVE",
  "slot": 1,
  "type": "Electric",
  "category": "SPECIAL",
  "gauge": 2,
  "uses": -1,
  "power1": 100,
  "power5": 120,
  "target": "ENEMY",
  "tags": ["DAMAGE", "PARALYZE"]
}
```

`kind` is `MOVE`, `SYNC` or `MAX`. Targets are `ENEMY`, `ALL_ENEMIES`, `SELF`, `ALLY`, `ALL_ALLIES` or `FIELD`. `uses: -1` means effectively unlimited. Slot 5 maps to the app’s dedicated calibrated `TERA-BLAST` action.

## Generic semantic move tags

The v2 planner understands hand-authored tags and compiler-produced parameterized tags. Useful generic tags include:

- `SET_FIELD:Electric Terrain`, `SET_FIELD:Ground Zone`, `SET_FIELD:Rainy Weather`
- `FIELD_STEPS:8`
- `REQUIRES_FIELD:Electric Terrain`
- `POWERED_BY_FIELD:Rainy Weather`
- `REQUIRES_SYNC_BUFF:1`
- `REQUIRES_SPATK_POS`, `REQUIRES_ATK_POS`, `REQUIRES_TERA`
- `REQUIRES_MOVE_COUNT:3`, `REQUIRES_HITS:2`
- `BUFF_SELF_ATK:6`, `BUFF_SELF_SPATK:6`, `BUFF_SELF_DEF:4`, `BUFF_SELF_SPDEF:4`, `BUFF_SELF_SPEED:2`, `BUFF_SELF_CRIT:3`
- team equivalents: `BUFF_TEAM_ATK:n`, `BUFF_TEAM_SPATK:n`, `BUFF_TEAM_DEF:n`, `BUFF_TEAM_SPDEF:n`, `BUFF_TEAM_SPEED:n`, `BUFF_TEAM_CRIT:n`
- `DEBUFF_ENEMY_ATK:n`, `DEBUFF_ENEMY_SPATK:n`, `DEBUFF_ENEMY_DEF:n`, `DEBUFF_ENEMY_SPDEF:n`, `DEBUFF_ENEMY_SPEED:n`
- `HEAL_SELF:0.20`, `HEAL_ALLY:0.40`
- `GAUGE_PLUS:6`, `SYNC_CD_MINUS:3`, `MOVE_GAUGE_ACCEL`
- `TEAM_PHYSICAL_NEXT:2`, `TEAM_SPECIAL_NEXT:2`, `TEAM_SYNC_NEXT:3`
- `PARALYZE`, `FLINCH`, `TRAP`, `CONFUSE`, `SURE_HIT`
- `TERASTALLIZE`, `TERA_BLAST`, `TERA_SAME_TYPE_POWER`

The hand-tuned built-in pairs also use specialized tags such as `ASH_BUDDY`, `CYNTHIA_SUPERIOR`, `RED1996_ORIGINAL`, `RED1996_GLORIOUS`, `SET_ELECTRIC_TERRAIN` and `TEAM_DEF_SPDEF_4_6` when a generic tag would lose important sequencing information.

## Generic passive tags

- `ENTRY_FIELD:<name>`
- `SYNC_FIELD:<name>`
- `ENTRY_ATK:n`, `ENTRY_SPATK:n`, `ENTRY_DEF:n`, `ENTRY_SPDEF:n`, `ENTRY_SPEED:n`, `ENTRY_CRIT:n`
- `AUTO_TERA_ENTRY`, `AUTO_TERA_FIRST_SYNC`
- `HEAD_START_1`, `HEAD_START_2`
- `MASTER_THEME:<theme>:<baseBonus>:<perAdditionalAlly>:<cap>`

Example: `MASTER_THEME:Kanto:0.20:0.15:0.50`.

## Current roster compiler

Use `tools/trainer_txt_to_pack.py` to compile one or more community `Trainer.txt` exports:

```bash
python tools/trainer_txt_to_pack.py Trainer-2.72.txt Trainer-2.73.txt \
  --game-version 2.73.0 --pretty -o custom_pair_pack.json
```

Later input files override earlier versions of the same generated pair ID. The compiler intentionally handles common mechanics conservatively and adds a review note because exact grids and one-off passives can require manual tags.

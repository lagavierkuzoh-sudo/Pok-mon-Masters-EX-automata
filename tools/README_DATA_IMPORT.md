# Building a current/full pair knowledge pack from Trainer.txt

The app deliberately does **not** hard-code a huge copied database. Instead, `trainer_txt_to_pack.py` can compile the public/community `Trainer.txt` exports you choose to download into the compact semantic JSON used by the planner.

Example:

```bash
python tools/trainer_txt_to_pack.py \
  ~/Downloads/Trainer-2.71.txt \
  ~/Downloads/Trainer-2.72.txt \
  ~/Downloads/Trainer-2.73.txt \
  --game-version 2.73.0 \
  --pretty \
  -o custom_pair_pack.json
```

Later files override earlier definitions with the same generated pair ID, so you can feed several monthly Trainer files in chronological order. Transfer `custom_pair_pack.json` to the Android device and use **IMPORT / REPLACE CUSTOM PAIR KNOWLEDGE PACK** in the app.

The compiler extracts role/EX role, type, Lv.140 stats, regular moves, sync move, Tera move, gauge cost, MP, power, targets, team themes, and common semantic effects such as stat buffs/debuffs, healing, paralysis/flinch/trap/confusion, weather/terrain/zone creation, move-gauge acceleration, Next effects, sync-countdown reduction, Tera entry, and common activation requirements.

The parser is intentionally conservative. Pokémon Masters EX has many one-off passives and grid nodes, so unusual effects may need manual tags after import. The planner will still learn stage/team outcome preferences for effects that are not modeled exactly.

# Research sources used for v2.2

Checked 2026-10-08 / 2026-10-09.

## Official Pokémon Masters EX

- **Ver. 2.73.0 Update Notification** — released September 27, 2026 PT:
  https://pokemonmasters-game.com/en-US/announcements/Update_8090_1W_1
- **Late-September v2.73 preview**:
  https://pokemonmasters-game.com/en-US/announcements/Update_8080_2W_3
- **Current Treasures of Ruin / current-pair producer information**:
  https://pokemonmasters-game.com/en-US/announcements/Other_8080_5W_1
- **Level cap 200** — Certificates of Excellence through 180 and Plaques of Perfection beyond 180:
  https://pokemonmasters-game.com/en-US/announcements/Update_7070_1W_4
- **Superawakening** — maximum SA 5, unlock at Move Level 5 for capable pairs, role-based progression:
  https://pokemonmasters-game.com/en-US/announcements/Update_6100_1W_2
- **Battle basics** — real-time 3-on-3 battles and shared move gauge:
  https://pokemonmasters-game.com/en-US/battles
- **3rd anniversary Buddy Move explanation for Ash, SST Red and SSA Cynthia**:
  https://pokemonmasters-game.com/en-US/announcements/Other_4070_5W_1
- **SST Red Field EX Role and SSA Cynthia Sprint EX Role**:
  https://pokemonmasters-game.com/en-US/announcements/Other_5120_1W_1

## Pair-detail references used for the bundled hand-tuned examples

Current Lv.200 stats, roles, EX Roles, move powers and move text were cross-checked against Serebii's current Sync Pair pages in addition to the historical/official references below.

- Ash current Sync Pair page: https://www.serebii.net/pokemonmasters/syncpairs/ash.shtml
- Red current Sync Pair page (includes Sygna Suit Thunderbolt and Red 1996): https://www.serebii.net/pokemonmasters/syncpairs/red.shtml
- Cynthia current Sync Pair page: https://www.serebii.net/pokemonmasters/syncpairs/cynthia.shtml

- Ash & Pikachu:
  https://bulbapedia.bulbagarden.net/wiki/Ash_(Masters)
- Red variants including Sygna Suit Red (Thunderbolt) & Pikachu and Red (1996) & Pikachu:
  https://bulbapedia.bulbagarden.net/wiki/Red_(Masters)
- Sygna Suit Cynthia (Aura) & Lucario:
  https://bulbapedia.bulbagarden.net/wiki/Sygna_Suit_Cynthia

## Current roster / update cross-check

- Community v2.73 datamine repository and Trainer.txt:
  https://github.com/absolutelypm/pokemas-datamine/tree/main/2.73
- v2.73 Trainer.txt contains the late-September/October 2026 additions and is compatible with `tools/trainer_txt_to_pack.py`.

## Gauge model

The app uses exact owned Speed when supplied and a community-derived approximation for converting team Speed/stat ranks into move-gauge bars per second, with a 1.5× Move Gauge Acceleration factor. Because animation/queue timing varies, the planner treats this as an estimate rather than an exact game-memory value.

The Android app does not fetch or scrape these sites at runtime. The built-in JSON is a compact semantic summary, and the optional compiler runs offline on files the user chooses to provide.

#!/usr/bin/env python3
from pathlib import Path
import tempfile, json, importlib.util

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('trainer_txt_to_pack', HERE/'trainer_txt_to_pack.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

SAMPLE='''No. 124 Sygna Suit Ghetsis & Chien-Pao (Genderless)\nRole: Strike | EX Role 🌈: Tech\nType: Ice | Weakness: Fighting\n👥 Team Skill\n1. Ice Strike\n2. Unova Strike\nSync Pair Available: 30/9/2026 06:00:00\n⚔️ Moves Details ⚔️\nMove 1: Protect\nType: Normal\nCategory: Status\nUser: Pokemon\nDescription: Applies the Damage Guard Next effect to the user.\nPower: -- | Accuracy: -- | Gauge: 2 | Target: Self | Effect Tag: -- | Max uses: 1\nMove 2: EX Attack\nCategory: Status\nUser: Trainer\nDescription: Raises the user's Attack by six stat ranks. Raises the user's critical-hit rate by three stat ranks.\nPower: -- | Accuracy: -- | Gauge: -- | Target: Self | Effect Tag: -- | Max uses: 1\nMove 3: Icerend Icicle Crash\nType: Ice\nCategory: Physical\nUser: Pokemon\nDescription: Activation Condition: When your team's sync pair uses a sync move once. Deactivation Condition: When this move is used. Never misses. Makes the target flinch.\nPower: 270 (1)/324 (5↑ MAX) | Accuracy: -- | Gauge: 2 | Target: All opponents | Effect Tag: Sure Hit | Max uses: --\nSync Move: Frozen Ambition Ice Impact\nType: Ice\nCategory: Physical\nUser: Pokemon\nDescription: No additional effect.\nPower: 250 (1)/300 (5↑ MAX) | Accuracy: -- | Gauge: -- | Target: An opponent | Effect Tag: -- | Max uses: --\n📊 Base Stats 📊\nLv. 140\nHP : 700 | Attack : 400 | Defense : 220 | Sp. Atk : 200 | Sp. Def : 210 | Speed : 300\nLv. 200\nHP : 900 | Attack : 510 | Defense : 290 | Sp. Atk : 260 | Sp. Def : 280 | Speed : 390\n📌 Tera Details 📌\n💎 Tera Move: Glacial Tera Blast\nType: Ice\nCategory: Physical\nUser: Pokemon\nDescription: Never misses.\nPower: 150 (1)/180 (5↑ MAX) | Accuracy: 100 | Gauge: 1 | Target: All opponents | Effect Tag: -- | Max uses: --\n🛡️ Passives Details 🌟\nPassive 1: Frozen Debut\nSync Terastallizes the user the first time it enters a battle each battle.\n'''

with tempfile.TemporaryDirectory() as td:
    p=Path(td)/'Trainer.txt';p.write_text(SAMPLE,encoding='utf-8')
    pairs=m.parse_file(p)
    assert len(pairs)==1
    x=pairs[0]
    assert x['trainer']=='Sygna Suit Ghetsis'
    assert x['pokemon']=='Chien-Pao'
    assert x['role'].startswith('STRIKE')
    assert x['exRole']=='TECH'
    assert x['stats200'][1]==510
    move3=[z for z in x['moves'] if z['slot']==3][0]
    assert 'REQUIRES_SYNC_BUFF:1' in move3['tags']
    assert 'FLINCH' in move3['tags']
    assert any(z['slot']==5 and 'REQUIRES_TERA' in z['tags'] for z in x['moves'])
print('TRAINER.TXT PARSER TEST PASSED')

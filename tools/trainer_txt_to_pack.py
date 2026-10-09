#!/usr/bin/env python3
"""Compile community Pokémon Masters EX Trainer.txt exports into a Strategist knowledge pack.

The parser is intentionally conservative: it captures move metadata and common semantic effects,
then leaves exotic passives as source notes rather than pretending to understand them perfectly.
Later input files override earlier pairs with the same generated id.
"""
from __future__ import annotations
import argparse, json, re, sys, unicodedata
from pathlib import Path

NUM_WORDS={"one":1,"two":2,"three":3,"four":4,"five":5,"six":6}

def clean(s:str)->str:
    return s.replace("\u00a0"," ").replace("↑"," up ").replace("↓"," down ")

def slug(s:str)->str:
    s=unicodedata.normalize("NFKD",s).encode("ascii","ignore").decode().lower()
    s=re.sub(r"[^a-z0-9]+","_",s).strip("_")
    return s or "pair"

def wordnum(s:str, default=1):
    s=s.lower().strip()
    if s.isdigit(): return int(s)
    return NUM_WORDS.get(s,default)

def norm_role(raw:str)->str:
    r=raw.upper().strip()
    if r.startswith("STRIKE"):
        if "PHYSICAL" in r:return "STRIKE_PHYSICAL"
        if "SPECIAL" in r:return "STRIKE_SPECIAL"
        return "STRIKE"
    for x in ("TECH","SUPPORT","SPRINT","FIELD"):
        if r.startswith(x):return x
    return re.sub(r"\W+","_",r) or "UNKNOWN"

def target(raw:str)->str:
    r=raw.lower()
    if "all opponent" in r:return "ALL_ENEMIES"
    if "opponent" in r:return "ENEMY"
    if "all alli" in r:return "ALL_ALLIES"
    if "an ally" in r or "ally" in r:return "ALLY"
    if "self" in r:return "SELF"
    if "field" in r:return "FIELD"
    return "ENEMY"

def normalize_field(name:str)->str:
    n=name.strip(" .()")
    n=re.sub(r"^EX\s+","",n,flags=re.I)
    aliases={"sunny":"Sunny Weather","rainy":"Rainy Weather","rain":"Rainy Weather","sandstorm":"Sandstorm","hail":"Hail"}
    return aliases.get(n.lower(),n)

def infer_tags(desc:str, name:str="", kind:str="MOVE") -> list[str]:
    d=clean(desc); lo=d.lower(); tags=[]
    def add(t):
        if t and t not in tags: tags.append(t)
    if re.search(r"leaves? the target trapped",lo):add("TRAP")
    if "makes the target flinch" in lo:add("FLINCH")
    else:
        m=re.search(r"chance \((\d+)%\).*flinch",lo)
        if m:add("FLINCH_"+m.group(1))
    if "leaving the target paralyzed" in lo or "leaves the target paralyzed" in lo:add("PARALYZE")
    else:
        m=re.search(r"chance \((\d+)%\).*paraly",lo)
        if m:add("PARALYZE" if int(m.group(1))>=50 else "PARALYZE_CHANCE:"+m.group(1))
    if "confused" in lo:add("CONFUSE")
    if "never misses" in lo:add("SURE_HIT")
    if "damage guard next" in lo:add("DAMAGE_GUARD_NEXT")
    if "enduring effect" in lo:add("ENDURE")
    if "move gauge acceleration" in lo:add("MOVE_GAUGE_ACCEL")
    if "sync terastallizes" in lo or "sync terastallize" in lo:add("TERASTALLIZE")

    # Field creation / weather.
    field_patterns=[
        r"turns? the field of play(?:'s|’s)? (?:terrain|zone) into (?:an? )?([^\.]+?(?:terrain|zone))(?=\.|,|$)",
        r"applies ([^\.]+? circle \([^\)]+\)) to the allied field",
    ]
    for pat in field_patterns:
        for m in re.finditer(pat,lo,re.I): add("SET_FIELD:"+normalize_field(m.group(1).title()))
    if "makes the weather sunny" in lo:add("SET_FIELD:Sunny Weather")
    if "makes the weather rainy" in lo:add("SET_FIELD:Rainy Weather")
    if "makes the weather a sandstorm" in lo or "starts a sandstorm" in lo:add("SET_FIELD:Sandstorm")
    if "makes the weather hail" in lo:add("SET_FIELD:Hail")

    # Activation requirements.
    if re.search(r"activation condition:.*team(?:'s|’s) sync pair uses? a sync move once",lo): add("REQUIRES_SYNC_BUFF:1")
    if re.search(r"activation condition:.*sp\. atk is raised",lo):add("REQUIRES_SPATK_POS")
    if re.search(r"activation condition:.*attack is raised",lo):add("REQUIRES_ATK_POS")

    # Self/team stat buffs.
    stats=[("attack","ATK"),("sp. atk","SPATK"),("sp. atk.","SPATK"),("defense","DEF"),("sp. def","SPDEF"),("speed","SPEED")]
    for phrase,key in stats:
        esc=re.escape(phrase).replace(r"\ ",r"\s+")
        for who,prefix in [(r"user(?:'s|’s)?","BUFF_SELF"),(r"all allied sync pairs(?:'|’)?","BUFF_TEAM")]:
            pat=who+r"\s+"+esc+r"\s+by\s+(one|two|three|four|five|six|\d+)\s+stat ranks?"
            m=re.search(r"raises? the "+pat,lo)
            if m:add(f"{prefix}_{key}:{wordnum(m.group(1))}")
        # Wording: Raises the Attack and Sp. Atk of all allied sync pairs by two stat ranks.
    m=re.search(r"raises? the attack and sp\.\s*atk of all allied sync pairs by (one|two|three|four|five|six|\d+) stat ranks?",lo)
    if m:
        n=wordnum(m.group(1));add(f"BUFF_TEAM_ATK:{n}");add(f"BUFF_TEAM_SPATK:{n}")
    m=re.search(r"raises? the defense and sp\.\s*def of all allied sync pairs by (one|two|three|four|five|six|\d+) stat ranks?",lo)
    if m:
        n=wordnum(m.group(1));add(f"BUFF_TEAM_DEF:{n}");add(f"BUFF_TEAM_SPDEF:{n}")
    m=re.search(r"raises? the user(?:'s|’s)? attack and sp\.\s*atk by (one|two|three|four|five|six|\d+) stat ranks?",lo)
    if m:
        n=wordnum(m.group(1));add(f"BUFF_SELF_ATK:{n}");add(f"BUFF_SELF_SPATK:{n}")
    m=re.search(r"raises? the user(?:'s|’s)? critical-hit rate by (one|two|three|\d+) stat ranks?",lo)
    if m:add("BUFF_SELF_CRIT:"+str(wordnum(m.group(1))))
    m=re.search(r"raises? the critical-hit rate of all allied sync pairs by (one|two|three|\d+) stat ranks?",lo)
    if m:add("BUFF_TEAM_CRIT:"+str(wordnum(m.group(1))))

    # Enemy debuffs.
    for phrase,key in [("attack","ATK"),("sp. atk","SPATK"),("defense","DEF"),("sp. def","SPDEF"),("speed","SPEED")]:
        pat=re.escape(phrase).replace(r"\ ",r"\s+")
        m=re.search(r"lowers? (?:the )?target(?:'s|’s)? "+pat+r" by (one|two|three|four|five|six|\d+) stat ranks?",lo)
        if m:add(f"DEBUFF_ENEMY_{key}:{wordnum(m.group(1))}")
        m=re.search(r"lowers? the "+pat+r" of all opposing sync pairs by (one|two|three|four|five|six|\d+) stat ranks?",lo)
        if m:add(f"DEBUFF_ENEMY_{key}:{wordnum(m.group(1))}")

    # Next effects.
    m=re.search(r"physical moves.*?next effect(?: and special moves.*?next effect)? of all allied sync pairs by (one|two|three|four|five|six|\d+) ranks?",lo)
    if m:
        n=wordnum(m.group(1));add(f"TEAM_PHYSICAL_NEXT:{n}")
        if "special moves" in m.group(0):add(f"TEAM_SPECIAL_NEXT:{n}")
    m=re.search(r"special moves.*?next effect of all allied sync pairs by (one|two|three|four|five|six|\d+) ranks?",lo)
    if m:add(f"TEAM_SPECIAL_NEXT:{wordnum(m.group(1))}")
    m=re.search(r"sync move.*?next effect of all allied sync pairs by (one|two|three|four|five|six|\d+) ranks?",lo)
    if m:add(f"TEAM_SYNC_NEXT:{wordnum(m.group(1))}")

    # Gauge / countdown / heals.
    m=re.search(r"reduces? the user(?:'s|’s)? sync move countdown by (one|two|three|four|\d+)",lo)
    if m:add(f"SYNC_CD_MINUS:{wordnum(m.group(1))}")
    m=re.search(r"restores? an ally(?:'s|’s)? hp by approximately (\d+)%",lo)
    if m:add("HEAL_ALLY:"+format(int(m.group(1))/100,".2f"))
    m=re.search(r"restores? the user(?:'s|’s)? hp by approximately (\d+)%",lo)
    if m:add("HEAL_SELF:"+format(int(m.group(1))/100,".2f"))
    m=re.search(r"increases? the user(?:'s|’s)? move gauge by (one|two|three|four|five|six|\d+)",lo)
    if m:add(f"GAUGE_PLUS:{wordnum(m.group(1))}")

    # Power conditions.
    if "power increases when the weather is rainy" in lo:add("POWERED_BY_FIELD:Rainy Weather")
    if "power increases when the weather is sunny" in lo:add("POWERED_BY_FIELD:Sunny Weather")
    for typ in ("electric","psychic","grassy"):
        if f"power increases when the terrain is {typ}" in lo:add("POWERED_BY_FIELD:"+typ.title()+" Terrain")
    z=re.search(r"power increases when the zone is an? ([a-z]+) zone",lo)
    if z:add("POWERED_BY_FIELD:"+z.group(1).title()+" Zone")
    if "tera blast" in name.lower():add("TERA_BLAST")
    if kind=="SYNC" and "tera blast" in name.lower():add("TERA_SAME_TYPE_POWER")
    return tags

def parse_move_block(label:str, body:str, slot:int, kind:str)->dict:
    line0=body.splitlines()[0].strip() if body.splitlines() else "Unknown"
    name=line0
    typ=(re.search(r"(?m)^Type:\s*(.+)$",body) or [None,"None"])[1].strip() if re.search(r"(?m)^Type:\s*(.+)$",body) else "None"
    cat=(re.search(r"(?m)^Category:\s*(.+)$",body) or [None,"Status"])[1].strip().upper() if re.search(r"(?m)^Category:\s*(.+)$",body) else "STATUS"
    descm=re.search(r"(?ms)^Description:\s*(.*?)(?=^Power:|^Accuracy:|\Z)",body)
    desc=descm.group(1).strip() if descm else ""
    meta=re.search(r"(?m)^Power:\s*(.*?)\s*\|\s*Accuracy:.*?\|\s*Gauge:\s*(.*?)\s*\|\s*Target:\s*(.*?)\s*\|\s*Effect Tag:.*?\|\s*Max uses:\s*(.*?)\s*$",body)
    p1=p5=0.0; gauge=0; uses=-1; targ="ENEMY"
    if meta:
        ps=meta.group(1).strip(); gm=meta.group(2).strip(); targ=target(meta.group(3)); um=meta.group(4).strip()
        pm=re.search(r"([0-9.]+)\s*\(1\)\s*/\s*([0-9.]+)",ps)
        if pm:p1=float(pm.group(1));p5=float(pm.group(2))
        else:
            sm=re.search(r"([0-9.]+)",ps)
            if sm:p1=p5=float(sm.group(1))
        gmnum=re.search(r"\d+",gm);gauge=int(gmnum.group()) if gmnum else 0
        umnum=re.search(r"\d+",um);uses=int(umnum.group()) if umnum else -1
    tags=infer_tags(desc,name,kind)
    if p1>0 and "DAMAGE" not in tags:tags.insert(0,"DAMAGE")
    return {"id":slug(label+"_"+name),"name":name,"kind":kind,"slot":slot,"type":typ,"category":cat,
            "gauge":gauge,"uses":uses,"power1":p1,"power5":p5,"target":targ,"tags":tags}

def parse_pair(block:str, source:str):
    h=re.match(r"No\.\s*(\d+)\s+(.+?)\s*&\s*(.+?)(?:\s+\([^\n]*\))?\s*$",block.splitlines()[0].strip())
    if not h:return None
    no,trainer,pokemon=h.group(1),h.group(2).strip(),h.group(3).strip()
    pokemon=re.sub(r"\s*\((?:Male|Female|Genderless).*?\)\s*$","",pokemon).strip()
    rolem=re.search(r"(?m)^Role:\s*(.*?)(?:\s*\|\s*EX Role[^:]*:\s*(.*))?$",block)
    role=norm_role(rolem.group(1).strip()) if rolem else "UNKNOWN"
    exrole=norm_role(rolem.group(2).strip()) if rolem and rolem.group(2) else "NONE"
    typem=re.search(r"(?m)^Type:\s*([^|\n]+)",block);typ=typem.group(1).strip() if typem else "Unknown"

    # Team skills -> themes; remove the role/type suffix when present.
    themes=[]
    tm=re.search(r"(?ms)^👥 Team Skill\s*(.*?)(?=^Sync Pair Available:|^EX Effect Available:|^⚔️ Moves Details|\Z)",block)
    if tm:
        for x in re.findall(r"(?m)^\s*\d+\.\s*(.+)$",tm.group(1)):
            x=x.strip();x=re.sub(r"\s+(Strike|Tech|Support|Sprint|Field)$","",x,flags=re.I).strip()
            if x and x not in themes:themes.append(x)

    stats=[600,300,200,300,200,300]
    sm=re.search(r"(?ms)^Lv\.\s*140\s*\n\s*HP\s*:\s*(\d+)\s*\|\s*Attack\s*:\s*(\d+)\s*\|\s*Defense\s*:\s*(\d+)\s*\|\s*Sp\. Atk\s*:\s*(\d+)\s*\|\s*Sp\. Def\s*:\s*(\d+)\s*\|\s*Speed\s*:\s*(\d+)",block)
    if sm:stats=list(map(int,sm.groups()))
    stats200=None
    sm200=re.search(r"(?ms)^Lv\.\s*200\s*\n\s*HP\s*:\s*(\d+)\s*\|\s*Attack\s*:\s*(\d+)\s*\|\s*Defense\s*:\s*(\d+)\s*\|\s*Sp\. Atk\s*:\s*(\d+)\s*\|\s*Sp\. Def\s*:\s*(\d+)\s*\|\s*Speed\s*:\s*(\d+)",block)
    if sm200:stats200=list(map(int,sm200.groups()))

    moves=[]
    # Capture regular moves anywhere in the block, bounded by next move/sync/passive/stats/tera heading.
    pat=re.compile(r"(?ms)^Move\s+([1-4]):\s*(.+?)\n(.*?)(?=^Move\s+[1-4]:|^Sync Move:|^🛡️|^📊|^📌|\Z)")
    for mm in pat.finditer(block):
        slot=int(mm.group(1));body=mm.group(2).strip()+"\n"+mm.group(3)
        moves.append(parse_move_block("move"+str(slot),body,slot,"MOVE"))
    sync=None
    sy=re.search(r"(?ms)^Sync Move:\s*(.+?)\n(.*?)(?=^🛡️|^📊|^📌|\Z)",block)
    if sy:sync=parse_move_block("sync",sy.group(1).strip()+"\n"+sy.group(2),0,"SYNC")
    # Tera moves are exposed as slot 5, matching the app's dedicated TERA-BLAST calibration action.
    for tmv in re.finditer(r"(?ms)^💎 Tera Move:\s*(.+?)\n(.*?)(?=^💎 Tera Move:|^🛡️|^📊|\Z)",block):
        mv=parse_move_block("tera",tmv.group(1).strip()+"\n"+tmv.group(2),5,"MOVE")
        if "REQUIRES_TERA" not in mv["tags"]:mv["tags"].append("REQUIRES_TERA")
        moves.append(mv)

    passive=[]
    # Conservative passive inference from descriptions.
    for pm in re.finditer(r"(?ms)^Passive\s+\d+[^:]*:\s*([^\n]+)\n(.*?)(?=^Passive\s+\d+|^\(🌅|^📊|^📌|^No\. |\Z)",block):
        name,desc=pm.group(1).strip(),pm.group(2).strip();lo=clean(desc).lower()
        if "sync terastallizes" in lo and "first time it enters" in lo: passive.append("AUTO_TERA_ENTRY")
        if "reduces the user’s sync move countdown by one the first time it enters" in desc or "reduces the user's sync move countdown by one the first time it enters" in desc:passive.append("HEAD_START_1")
        fm=re.search(r"turns? the field of play(?:'s|’s)? (?:terrain|zone) into (?:an? )?([^\.]+?(?:terrain|zone)) the first time the user enters",lo,re.I)
        if fm:passive.append("ENTRY_FIELD:"+normalize_field(fm.group(1).title()))
        if "makes the weather sunny the first time the user enters" in lo:passive.append("ENTRY_FIELD:Sunny Weather")
        if "makes the weather rainy the first time the user enters" in lo:passive.append("ENTRY_FIELD:Rainy Weather")
        sf=re.search(r"turns? the field of play(?:'s|’s)? (?:terrain|zone) into (?:an? )?([^\.]+?(?:terrain|zone)) the first time the user(?:'s|’s)? sync move is used",lo,re.I)
        if sf:passive.append("SYNC_FIELD:"+normalize_field(sf.group(1).title()))
    # Named Debut lines sometimes say EX Grassy Terrain etc.
    for fm in re.finditer(r"(?mi)^Passive\s+\d+[^:]*:\s*Debut:\s*(?:EX\s+)?([^\n]+(?:Terrain|Zone|Weather))",block):
        passive.append("ENTRY_FIELD:"+normalize_field(fm.group(1).title()))
    passive=list(dict.fromkeys(passive))

    return {"id":slug(trainer+"_"+pokemon),"trainer":trainer,"pokemon":pokemon,"role":role,"exRole":exrole,"type":typ,
            "stats140":stats,"stats140Max":stats,**({"stats200":stats200,"stats200Max":stats200} if stats200 else {}),"passives":passive,"themes":themes,"moves":moves,"sync":sync,
            "maxMoves":[],"sourceNote":f"Compiled conservatively from {source}, Trainer No. {no}; review exotic passives/grid-specific effects manually."}

def parse_file(path:Path):
    text=path.read_text(encoding="utf-8",errors="replace").replace("\r\n","\n")
    starts=[m.start() for m in re.finditer(r"(?m)^No\.\s*\d+\s+",text)]
    pairs=[]
    for j,start in enumerate(starts):
        end=starts[j+1] if j+1<len(starts) else len(text)
        p=parse_pair(text[start:end].strip(),path.name)
        if p and (p["moves"] or p["sync"]):pairs.append(p)
    return pairs

def main():
    ap=argparse.ArgumentParser(description="Compile one or more Pokémon Masters EX Trainer.txt files into a Strategist JSON pack")
    ap.add_argument("inputs",nargs="+",type=Path)
    ap.add_argument("-o","--output",type=Path,required=True)
    ap.add_argument("--game-version",default="custom")
    ap.add_argument("--pretty",action="store_true")
    args=ap.parse_args()
    merged={}
    for p in args.inputs:
        for pair in parse_file(p):merged[pair["id"]]=pair
    root={"schema":2,"gameVersion":args.game_version,"plannerVersion":"2.0","pairs":list(merged.values())}
    args.output.write_text(json.dumps(root,ensure_ascii=False,indent=2 if args.pretty else None),encoding="utf-8")
    print(f"wrote {len(merged)} pairs -> {args.output}")
    if not merged:return 2
    return 0
if __name__=="__main__":sys.exit(main())

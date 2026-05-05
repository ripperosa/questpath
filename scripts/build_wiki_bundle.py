#!/usr/bin/env python3
"""build_wiki_bundle.py — Extract quest data from Quest Helper source.

Fetches Quest Helper Java source files from GitHub, parses out
SkillRequirement / QuestRequirement / QuestPointRequirement calls
out of each helper class's getGeneralRequirements() method, and writes
src/main/resources/data/wiki_data.json for the QuestPath plugin.

Re-run any time Quest Helper publishes new quest support.

USAGE
    python scripts/build_wiki_bundle.py
    python scripts/build_wiki_bundle.py --out path/to/wiki_data.json

REQUIREMENTS
    Python 3.8+. Only stdlib. No third-party libraries.

DATA SOURCE / LICENSE
    Quest Helper plugin source (https://github.com/Zoinkwiz/quest-helper),
    BSD-2-Clause licensed. We extract structured data only — no source code
    is bundled in our plugin. Attribution: see README.

LIMITATIONS
    * Regex-based parsing — robust to QH's typical patterns but not perfect.
      A handful of quests with non-standard requirement structures will come
      back with empty data. Hand-author entries in overrides.json to patch.
    * Quest IDs follow QuestHelperQuest enum naming (lowercased), which
      mostly matches RuneLite's Quest enum but occasionally differs.
      Mismatches affect game-state-driven completion detection only.
"""
import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

QH_REPO = "Zoinkwiz/quest-helper"
QH_BRANCH = "master"
GITHUB_RAW = f"https://raw.githubusercontent.com/{QH_REPO}/{QH_BRANCH}"
GITHUB_API = f"https://api.github.com/repos/{QH_REPO}"

# Matches RuneLite's Skill enum (skip OVERALL — synthetic).
VALID_SKILLS = {
    "ATTACK", "STRENGTH", "DEFENCE", "RANGED", "PRAYER", "MAGIC", "RUNECRAFT",
    "CONSTRUCTION", "HITPOINTS", "AGILITY", "HERBLORE", "THIEVING", "CRAFTING",
    "FLETCHING", "SLAYER", "HUNTER", "MINING", "SMITHING", "FISHING", "COOKING",
    "FIREMAKING", "WOODCUTTING", "FARMING",
}

# Type-based filtering (see is_real_quest below) is now the source of truth.
# These name-based blacklists were used when we explicitly skipped diaries +
# miniquests; now we INCLUDE those types, so the blacklists are obsolete.
NON_QUEST_ENUMS = set()
NON_QUEST_PREFIXES = ()


def http_get(url: str, retries: int = 3) -> str:
    """GET a URL. GitHub raw + api endpoints both require User-Agent."""
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "questpath-build/1.0",
                "Accept": "application/vnd.github+json",
            })
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read().decode("utf-8")
        except (urllib.error.URLError, urllib.error.HTTPError) as e:
            last_err = e
            time.sleep(1 + attempt)  # gentle backoff
    raise RuntimeError(f"Failed after {retries} attempts: {url}\n  Last error: {last_err}")


def list_quest_files():
    """Find every helpers/quests/<folder>/<MainClass>.java via the tree API."""
    tree_url = f"{GITHUB_API}/git/trees/{QH_BRANCH}?recursive=1"
    raw = http_get(tree_url)
    data = json.loads(raw)
    if data.get("truncated"):
        print("WARNING: GitHub tree response was truncated. Some quests may be missing.",
              file=sys.stderr)
    # Quest Helper organises helpers into sibling folders under helpers/:
    #   quests/, achievementdiaries/, miniquests/, skills/, mischelpers/, ...
    # We want quest, miniquest, and diary helpers — the diary/miniquest folders
    # use a slightly different layout but the leaf .java file is still
    # PascalCase under a snake-named folder, so one pattern catches them all.
    pattern = re.compile(
        r"^src/main/java/com/questhelper/helpers/"
        r"(?:quests|achievementdiaries|miniquests)/"
        r"([^/]+)/([^/]+)\.java$"
    )
    files = []
    for entry in data.get("tree", []):
        if entry.get("type") != "blob":
            continue
        m = pattern.match(entry["path"])
        if m:
            folder, class_name = m.group(1), m.group(2)
            # The main helper class always sits directly under the quest folder
            # and matches PascalCase derived from the folder. Skip helper sub-files
            # by checking the class name doesn't contain unexpected suffixes.
            files.append({
                "folder": folder,
                "class_name": class_name,
                "path": entry["path"],
                "url": f"{GITHUB_RAW}/{entry['path']}",
            })
    return files


# ---- Regex patterns ----
RE_SKILL_REQ = re.compile(
    r"new\s+SkillRequirement\s*\(\s*Skill\.([A-Z_]+)\s*,\s*(\d+)"
)
RE_QUEST_REQ = re.compile(
    r"new\s+QuestRequirement\s*\(\s*QuestHelperQuest\.([A-Z_]+)\s*(?:,\s*QuestState\.(\w+))?"
)
RE_QP_REQ = re.compile(r"new\s+QuestPointRequirement\s*\(\s*(\d+)")
RE_GETID = re.compile(
    r"public\s+QuestHelperQuest\s+getQuest\s*\(\s*\)\s*\{\s*return\s+QuestHelperQuest\.([A-Z_]+)",
    re.MULTILINE,
)

# QuestHelperQuest enum entries look like:
#     LUNAR_DIPLOMACY(new LunarDiplomacy(), Quest.LUNAR_DIPLOMACY,
#         QuestVarbits.QUEST_LUNAR_DIPLOMACY, QuestDetails.Type.MEMBERS,
#         QuestDetails.Difficulty.MASTER),
# Captures: (1) enum name, (2) class name, (3) F2P / MEMBERS / MINIQUEST.
RE_ENUM_ENTRY = re.compile(
    r"^\s*([A-Z][A-Z0-9_]+)\s*\(\s*new\s+([A-Z][A-Za-z0-9]+)\s*\("
    # .*? handles nested parens like Quest.ANIMAL_MAGNETISM.getId() that some
    # entries put between the class instance and the type. Non-greedy so it
    # stops at the first QuestDetails.Type — won't span across enum entries.
    r".*?QuestDetails\.Type\.([A-Z0-9_]+)\s*,\s*"
    r"QuestDetails\.Difficulty\.([A-Z0-9_]+)",
    re.MULTILINE | re.DOTALL,
)


def fetch_class_to_enum_map():
    """Pull QuestHelperQuest.java and extract ClassName → metadata.

    Quest Helper Type values: F2P, P2P (members), MINIQUEST, ACHIEVEMENT_DIARY,
    GENERIC, SKILL_F2P, SKILL_P2P, PLAYER_QUEST. We accept the first four as
    "things players plan toward."

    Difficulty values: NOVICE, INTERMEDIATE, EXPERIENCED, MASTER, GRANDMASTER,
    SPECIAL, MINIQUEST, ACHIEVEMENT_DIARY, GENERIC, SKILL.
    """
    url = f"{GITHUB_RAW}/src/main/java/com/questhelper/questinfo/QuestHelperQuest.java"
    raw = http_get(url)
    mapping = {}
    for m in RE_ENUM_ENTRY.finditer(raw):
        enum_name, class_name, qtype, difficulty = m.group(1), m.group(2), m.group(3), m.group(4)
        mapping[class_name] = {
            "enum": enum_name,
            "members": qtype == "P2P",
            "type": qtype,
            "difficulty": difficulty,
        }
    return mapping


def find_method_body(source: str, method_names) -> str:
    """Extract the body (between matching braces) of the first method whose
    name matches any of {method_names}. Returns empty string if not found.

    The signature regex tolerates multi-line return types — QH frequently
    splits ``public List<Requirement>\\n    getGeneralRequirements()`` across
    two lines. DOTALL lets ``.`` match newlines for that one-shot lookup;
    we cap the gap at 200 chars so we can't accidentally span past an
    earlier method's body.
    """
    for name in method_names:
        sig = re.compile(
            rf"public\s+[^{{;]{{0,200}}\b{re.escape(name)}\s*\([^)]*\)\s*\{{",
            re.DOTALL,
        )
        m = sig.search(source)
        if not m:
            continue
        start = m.end() - 1
        depth = 0
        for i in range(start, len(source)):
            c = source[i]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return source[start + 1:i]
    return ""


def parse_source(source: str, enum_name: str = None) -> dict:
    """Parse a Quest Helper Java source file into structured quest data.

    {enum_name} is supplied by the caller (looked up via class→enum map). The
    inline @Override getQuest() method is not always present.
    """
    if enum_name is None:
        enum_match = RE_GETID.search(source)
        enum_name = enum_match.group(1) if enum_match else None

    # We used to scan only `getGeneralRequirements()` body, but many QH classes
    # declare requirements as private fields (`SkillRequirement smithReq = new ...`)
    # and just reference them by variable name in getGeneralRequirements. Those
    # came back as parsed-as-empty. Scanning the whole source gets us everything;
    # the trade-off is occasionally grabbing a requirement that isn't truly a
    # "general" requirement, but in QH's pattern that's rare — almost every
    # SkillRequirement/QuestRequirement in a helper class IS a general req.

    skill_reqs = {}
    for m in RE_SKILL_REQ.finditer(source):
        skill = m.group(1)
        level = int(m.group(2))
        if skill not in VALID_SKILLS:
            continue
        # If the same skill appears multiple times, keep the highest requirement.
        if level > skill_reqs.get(skill, 0):
            skill_reqs[skill] = level

    prereq_quests = []
    seen = set()
    for m in RE_QUEST_REQ.finditer(source):
        target = m.group(1)
        state = m.group(2)
        # Only "FINISHED" QuestRequirement counts as a prereq. State omitted
        # also defaults to FINISHED in Quest Helper's constructor.
        if state and state != "FINISHED":
            continue
        if target in seen:
            continue
        seen.add(target)
        prereq_quests.append(target)

    qp_req = 0
    m = RE_QP_REQ.search(source)
    if m:
        qp_req = int(m.group(1))

    return {
        "enum_name": enum_name,
        "skill_requirements": skill_reqs,
        "prerequisite_enum_names": prereq_quests,
        "quest_point_requirement": qp_req,
    }


def is_real_quest(enum_name: str) -> bool:
    if enum_name in NON_QUEST_ENUMS:
        return False
    for prefix in NON_QUEST_PREFIXES:
        if enum_name.startswith(prefix):
            return False
    return True


def enum_to_id(enum_name: str) -> str:
    """Convert ENUM_NAME → snake_case id. Quest Helper occasionally uses double
    underscores (ROMEO__JULIET, FAIRYTALE_I__GROWING_PAINS) — collapse those.
    """
    return re.sub(r"_+", "_", enum_name.lower())


def display_name_from_class(class_name: str) -> str:
    """Best-effort PascalCase → 'Title Case'. Works as fallback when we can't
    pull the displayName from the QuestHelperQuest enum entry.
    """
    # Insert spaces before capital letters preceded by a lowercase letter or end.
    spaced = re.sub(r"([a-z])([A-Z])", r"\1 \2", class_name)
    spaced = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", spaced)
    return spaced


def build():
    print("Fetching QuestHelperQuest.java for class→enum mapping...", file=sys.stderr)
    class_map = fetch_class_to_enum_map()
    print(f"  Loaded {len(class_map)} class→enum mappings.", file=sys.stderr)

    files = list_quest_files()
    # Only fetch files whose class name we recognize from the enum — saves
    # ~150 wasted requests on utility/helper files that aren't quests.
    files = [f for f in files if f["class_name"] in class_map]
    print(f"Found {len(files)} known quest helper classes (after filter).", file=sys.stderr)

    quests = {}
    skipped = 0
    failed = 0
    empty = 0

    for i, f in enumerate(files):
        info = class_map[f["class_name"]]
        enum_name = info["enum"]
        members = info["members"]
        qtype = info["type"]
        difficulty = info["difficulty"]
        # Derive display name from class name; QH enum doesn't store it as a string.
        display = display_name_from_class(f["class_name"])

        # Include actual quests + miniquests + achievement diaries — players
        # plan toward all three. Skip generic helpers, skill helpers, player quests.
        if qtype not in ("F2P", "P2P", "MINIQUEST", "ACHIEVEMENT_DIARY"):
            skipped += 1
            continue
        if not is_real_quest(enum_name):
            skipped += 1
            continue

        try:
            source = http_get(f["url"])
        except Exception as e:
            print(f"  [{i+1}/{len(files)}] FAIL  {enum_name}: {e}", file=sys.stderr)
            failed += 1
            continue

        parsed = parse_source(source, enum_name=enum_name)

        quest_id = enum_to_id(enum_name)
        prereq_ids = [enum_to_id(e) for e in parsed["prerequisite_enum_names"]]

        quests[quest_id] = {
            "id": quest_id,
            "displayName": display,
            "members": members,
            "questType": qtype,                # F2P / P2P / MINIQUEST / ACHIEVEMENT_DIARY
            "difficulty": difficulty,          # NOVICE / INTERMEDIATE / EXPERIENCED / MASTER / GRANDMASTER / SPECIAL / etc.
            "questPointReward": 0,
            "questPointRequirement": parsed["quest_point_requirement"],
            "prerequisiteQuestIds": prereq_ids,
            "skillRequirements": parsed["skill_requirements"],
            "skillRewards": {},
            "wikiUrl": f"https://oldschool.runescape.wiki/w/{display.replace(' ', '_')}",
            "estimatedMinutes": 0,
        }

        if not prereq_ids and not parsed["skill_requirements"]:
            empty += 1

        print(
            f"  [{i+1}/{len(files)}] {quest_id}  "
            f"prereqs={len(prereq_ids):2d}  skills={len(parsed['skill_requirements']):2d}",
            file=sys.stderr,
        )

    # Drop prereq references that point to quests we didn't bundle. This
    # happens when a helper requires a QuestHelperQuest enum entry whose Type
    # we filter out (e.g. KNIGHT_WAVES_TRAINING_GROUNDS — included in QH's
    # enum but classified as a non-quest by us). The runtime QuestRepository
    # sanitizer also handles this, but it's cleaner to ship a self-consistent
    # bundle so the JUnit `prereqsResolveToBundleQuests` test stays green.
    dropped = 0
    for q in quests.values():
        prereqs = q.get("prerequisiteQuestIds") or []
        cleaned = [p for p in prereqs if p in quests]
        if len(cleaned) != len(prereqs):
            dropped += len(prereqs) - len(cleaned)
            q["prerequisiteQuestIds"] = cleaned
    if dropped:
        print(f"Dropped {dropped} dangling prereq references.", file=sys.stderr)

    print(f"\nWrote {len(quests)} quests, skipped {skipped} non-quests, "
          f"{failed} fetch failures, {empty} parsed-as-empty.", file=sys.stderr)

    return {"quests": quests, "trainingMethods": []}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument(
        "--out",
        default="src/main/resources/data/wiki_data.json",
        help="Output path. Default writes to the bundled resource location.",
    )
    args = parser.parse_args()

    bundle = build()

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(bundle, indent=2, sort_keys=True))
    print(f"\nWrote {out} ({out.stat().st_size:,} bytes)", file=sys.stderr)


if __name__ == "__main__":
    main()

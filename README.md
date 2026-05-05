# QuestPath

Quest prerequisite visualizer & planner for [RuneLite](https://runelite.net/).

Pick any target quest, miniquest, or achievement diary. QuestPath shows the full prerequisite tree color-coded by completion status, the skill walls between you and the goal, and a step-by-step recommended order — including training suggestions weighted by your time / GP / AFK preferences.

## Features

- **257 quests, miniquests, and achievement diaries** sourced from the [Quest Helper](https://github.com/Zoinkwiz/quest-helper) plugin's data
- **Prerequisite tree view** with hover-to-collapse — see exactly which prereqs are blocking your target and why
- **Skill requirement panel** — green/red rows showing the difference between what you have and what you need
- **Recommended order** — quest steps and training fillers laid out in a topologically-sorted plan
- **Weighted training preferences** — three sliders (Time / GP / AFK) tune which training methods the planner picks for skill gaps
- **Search + filters** — type to filter, narrow by Quest / Miniquest / Diary / F2P / Members / Meets-Reqs, sort A→Z or by quest points
- **Status dots in the list** — at a glance, see which quests you can start right now (blue), have already done (green), or are blocked by a skill (orange) or another quest (red)
- **Quest Helper hand-off (opt-in)** — turn on in plugin settings and the "Open in Quest Helper" button asks the Quest Helper plugin (if installed) to start tracking the quest so its step-by-step walkthrough takes over

## Install

Once accepted on the Plugin Hub, install from inside RuneLite:

1. Open RuneLite
2. Click the wrench icon in the top-right
3. Open the **Plugin Hub** tab
4. Search for **QuestPath**, click **Install**

Then click the compass icon in the right-edge sidebar to open the panel.

## How it works

Quest data is bundled in `src/main/resources/data/wiki_data.json` — a snapshot of structured requirement data extracted from Quest Helper's open-source helper classes (BSD-2-Clause). The plugin loads it at startup; no network calls are made at runtime.

Hand-authored entries in `src/main/resources/data/overrides.json` win on id collision and patch any quest where the extracted data needs adjustment (e.g. quests that use `IN_PROGRESS` state requirements which our DONE-only model doesn't capture directly).

The runtime planner reads the merged data, builds a topological dependency graph, snapshots your current skill levels and quest completion state from the live game, and produces an ordered plan: which quests to do, in what order, and which skills to train (and how) before each one.

## Attribution

QuestPath uses structured data extracted from the [Quest Helper](https://github.com/Zoinkwiz/quest-helper) RuneLite plugin by Zoinkwiz and contributors, licensed under BSD-2-Clause. **No Quest Helper source code is bundled in this plugin** — only derived data (quest IDs, prerequisite edges, skill requirement levels) parsed from their open-source code.

If you find QuestPath useful, please also support Quest Helper.

## Privacy

QuestPath:

- **Does not** make any HTTP requests at runtime
- **Does not** read or write files outside of standard RuneLite plugin config
- **Does not** transmit any data to third-party servers
- The optional Quest Helper hand-off feature uses Java reflection to invoke a method on the Quest Helper plugin instance (if installed). It is **off by default**; turn it on in plugin settings if you want it. When off, no reflection is performed.

## Development

### Build

```
./gradlew test    # run JUnit tests
./gradlew run     # boot the RuneLite test client with QuestPath loaded
```

JDK 11+ required. The Gradle build targets `release 11`.

### Refresh the bundled quest data

Re-run when Quest Helper publishes support for new quests:

```
python scripts/build_wiki_bundle.py
```

That fetches every helper class from Quest Helper's GitHub `master` branch, parses skill / quest / QP requirements, and rewrites `src/main/resources/data/wiki_data.json`. The script makes ~260 HTTP requests at build time only — never at plugin runtime.

### Project layout

```
questpath/
├── LICENSE                                 ← BSD-2-Clause (matches Quest Helper)
├── icon.png                                ← Plugin Hub display icon
├── build.gradle                            ← JDK 11 target
├── runelite-plugin.properties              ← Plugin Hub metadata
├── scripts/build_wiki_bundle.py            ← QH data extractor
├── src/main/java/com/questpath/
│   ├── QuestPathPlugin.java                ← @PluginDescriptor, lifecycle
│   ├── QuestPathConfig.java                ← user config
│   ├── QuestPathPanel.java                 ← sidebar Swing panel
│   ├── data/                               ← QuestDefinition, repository, JSON loaders
│   ├── game/                               ← live PlayerState snapshot from RuneLite
│   ├── planner/                            ← dependency graph + plan generation
│   ├── ui/                                 ← QuestListPanel, QuestInfoCard, etc.
│   └── integration/QuestHelperBridge.java  ← optional reflection-based QH hand-off
├── src/main/resources/
│   ├── icon.png                            ← 16×16 toolbar icon
│   └── data/{wiki_data.json,overrides.json}
└── src/test/java/com/questpath/            ← JUnit tests + RuneLite test launcher
```

## Roadmap

| Phase | Goal | Status |
|-------|------|--------|
| 1 | Skeleton plugin — panel, skill read, console print | ✅ done |
| 2 | Data layer — `QuestDefinition`, `TrainingMethod`, `overrides.json` | ✅ done |
| 3 | Planner core — dependency graph, gap analysis, plan generation | ✅ done |
| 4 | UI — target dropdown, tree view, plan list, color coding | ✅ done |
| 5 | Weight sliders (Time / GP / AFK) — dynamic method scoring | ✅ done |
| 6 | Quest Helper data extraction — 257 quests/diaries/miniquests | ✅ done |
| 7 | Filters & sort (Type / Difficulty / Sort / Meets-Reqs) | ✅ done |
| 8 | Polish — quest info card, skill-reqs panel, Quest Helper hand-off | ✅ done |
| 9 | Browse / Plan / Settings cards — QH-style scrollable quest list | ✅ done |
| 10 | Parser improvements + override patches for parsed-as-empty quests | ✅ done |
| 11 | Plugin Hub submission | ✅ done |

## License

BSD-2-Clause. See [`LICENSE`](LICENSE).

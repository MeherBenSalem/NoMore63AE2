# Core RPG - Quests Engine

A complete RPG quest system for Minecraft **1.21.1** on **NeoForge** and **Fabric**: a quest book with chapters and
branching quests, an in-game editor, teams and parties with shared progress, team stages, smart item filters and
vein mining. One mod covers what the FTB Quests suite (Quests, Library, Teams, Filter System, XMod Compat and
Ultimine) does, with original code.

> This repository used to hold **No More 63 AE2**. That mod is retired here; its source stays in the git history
> (commit `9fda2fe`). The release scripts no longer point at its Modrinth or CurseForge projects.

## Features

**Quest book**
- Chapters in collapsible groups, each a pannable, zoomable canvas of quests drawn as circles, squares, rounded
  squares, diamonds, pentagons, hexagons, octagons, hearts, gears, shields, stars or bare icons, in any size.
- Dependency lines that show state: locked, available (animated), in progress, claimable, completed, excluded,
  cooldown. Quest links show a quest from another chapter; chapter images decorate the canvas and can open a URL,
  a quest or a chapter, or run a command.
- Quest panel with paged, colour-coded story text and inline images, tasks with progress bars and hand-in buttons,
  rewards with claim buttons, choice rewards, requirements and what a quest unlocks.
- Search, a pinned-quest tracker on the HUD, and toasts for quests, chapters, the whole book and received rewards.

**Progression rules**
- Dependencies on quests or whole chapters: all, any, all started, any started, or "N of these".
- Linear or flexible progression, sequential tasks, optional tasks and optional quests.
- Branching choices (`max_completable_dependents`): pick one path and the others close.
- Visibility: invisible, invisible until N tasks, hidden until dependencies are done or visible, hidden text,
  hidden details, always-invisible chapters, hidden excluded quests.
- Repeatable quests with cooldowns, completion commands, and per-team reward blocking and progress locking.

**24 task types**: item (have or hand in, with smart filters and component matching), checkmark, kill (type, tag,
named bosses), advancement (or one criterion), location, dimension, biome, structure, stat, experience, observation
(look at a block or entity), interaction (right-click a block or entity), stage, custom (commands and scripts), fluid,
break block, place block, craft, consume, fish, trade, breed, tame and enchant.

**12 reward types**: item (with a random bonus), experience points, experience levels, a random roll from a reward
table, one chosen entry from a table, everything in a table, a vanilla loot table, command, advancement, toast,
stage and custom. Rewards can be per player or per team, auto-claimed (normally, silently or invisibly) and left out
of "claim all". Reward tables can become **loot crates** that monsters, animals and bosses drop.

**Editor** (for operators, in game): right-click to add quests, links and images; drag to move (snapped to the grid);
shift-click or shift-drag to select; ctrl-click to link dependencies; copy and paste between chapters. Every field of
every task and reward is editable in a property editor generated from the object's schema. There is a text editor
with formatting buttons and a live preview, a reward table editor, chapter and group management, and
complete/reset buttons for testing.

**Teams**: everyone has a personal team. Parties have an owner, officers, members, allies and invites; they can be
free to join, have their own chat, colour and description, and a size limit. Admins can make server teams. Progress
and stages belong to the team. There is a team screen and a `/team` command.

**Smart filters**: a readable filter language such as `any(#c:ingots, all(@create, not(block())), food(>=6))`, with
item, tag, mod, food, durability, stack size, name, component, enchantment and rarity filters. Use it in tasks
directly or through the Smart Filter item, which has an editor with live validation and a preview of what matches.

**Vein mining**: hold the key (`` ` `` by default) and break a block to break the whole vein. Shapes: shapeless,
1x1 tunnel, 1x2 tunnel, 3x3 tunnel, 3x3 square and escape staircase (scroll to switch). Limits and costs: a block cap,
hunger, experience, a cooldown, the correct tool, tool-break protection, drop gathering, merge tags (`c:ores/*`) and
a blacklist tag. With the key held, right-click to harvest and replant a field, strip logs, make paths or till soil.

## Supported versions

| Minecraft | NeoForge | Fabric |
|-----------|----------|--------|
| 1.21.1    | 21.1.0+  | Loader 0.16.9+ with Fabric API |

## Installing

Put `corerpg-quests-1.21.1-<loader>-<version>.jar` in `mods/` on the client and on the server (Fabric also needs
Fabric API). The first server start writes an example book, "The First Ember", to `config/corerpg_quests/book/`, and
settings to `config/corerpg_quests/server.toml` and `client.toml`.

## Making a quest book

1. As an operator, open the book (`J`, the Quest Book item, or `/cq open`).
2. Click the pencil to enter editing mode. Right-click the canvas to add a quest; right-click a quest to add tasks and
   rewards; press `E` or choose Edit to change any field.
3. Changes are saved to `config/corerpg_quests/book/` and reach every player at once. Ship that folder with your pack.

The files are plain JSON, one per chapter and per reward table, so they work well with version control and pack
tools. Every field is listed in [docs/BOOK_FORMAT.md](docs/BOOK_FORMAT.md), which is generated from the source.
`/cq reload` re-reads the files.

## Commands

| Command | Who | What |
|---|---|---|
| `/cq open [quest or chapter]` | everyone | Open the quest book |
| `/cq emergency_items` | everyone | Get the book's emergency items (with a cooldown) |
| `/cq editing_mode [true/false]` | editors | Toggle editing mode |
| `/cq reload`, `/cq export`, `/cq info` | ops | Reload the book from disk, export it as one file, show counts |
| `/cq complete <players> <id or book>`, `/cq reset <players> <id or book>` | ops | Complete or reset a quest, a chapter or the whole book for the players' teams |
| `/cq lock <players> <bool>`, `/cq block_rewards <players> <bool>` | ops | Freeze progress, or block rewards |
| `/cq stage add/remove/list ...` | ops | Team stages |
| `/cq custom <players> <key> [amount]` | ops | Progress custom tasks (for command blocks, datapacks and scripts) |
| `/cq give_book <players>` | ops | Give the quest book |
| `/team ...` | everyone | `create`, `invite`, `accept`, `decline`, `join`, `leave`, `kick`, `promote`, `demote`, `transfer`, `ally add/remove`, `disband`, `chat`, `msg`, `settings name/description/color/free_to_join`, `info`, `list` |
| `/team server create/delete/add ...` | ops | Server teams |

`/cq` is short for `/corequests`.

## For developers

Server events are in `dev.nightbeam.corerpg.quests.api.CoreQuestsEvents`: quest started and completed, task
completed, chapter completed, reward claimed, custom reward, stage changed and team changed. Register new task and
reward types with `TaskTypes.register` and `RewardTypes.register`, and predicates for `custom(key)` in filter
expressions with `FilterTypes.registerCustom`.

## Building

Needs JDK 21.

```bash
cd 1.21.1
./gradlew build
```

The jars are written to `1.21.1/neoforge/build/libs/` and `1.21.1/fabric/build/libs/`. Tests:

```bash
./gradlew :common:test                 # unit tests
./gradlew :neoforge:runGameTestServer  # in-game tests on NeoForge
./gradlew :fabric:runGametest          # in-game tests on Fabric
```

## Project layout

```
1.21.1/common/     game logic, GUI and mixins, shared by both loaders
1.21.1/neoforge/   NeoForge entry points and platform code
1.21.1/fabric/     Fabric entry points and platform code
1.21.1/tools/art/  scripts that generate the textures
docs/              quest book format reference
marketing/         CurseForge and Modrinth page and artwork
tools/             release and documentation scripts
```

## License

Apache License 2.0. All code and art are original; the mod contains no code or assets from FTB mods.

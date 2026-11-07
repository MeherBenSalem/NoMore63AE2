# Core RPG - Quests Engine — Changelog

## 1.0.0

First release, for Minecraft 1.21.1 on NeoForge and Fabric. This repository previously held No More 63 AE2, which is
retired; its history stays in git.

### Quest book
* Chapters and chapter groups, quest canvas with pan, zoom, 11 node shapes, dependency lines that show state,
  quest links between chapters and decorative, clickable chapter images.
* Quest panel with paged story text, inline images, tasks, rewards, choice rewards, requirements and unlocks.
* Search, pinned-quest HUD tracker, toasts, emergency items, quest book item kept on death.

### Progression
* Quest and chapter dependencies with all/any/started modes and "N of", linear and flexible progression,
  sequential and optional tasks, optional and repeatable quests with cooldowns, branching choices, visibility rules,
  completion commands, progress locking and reward blocking.
* 24 task types and 12 reward types; reward tables with random, choice and all modes; loot crates that mobs drop.

### Editor
* In-game editing for operators: create, move, select, copy/paste and delete; dependency linking; a property editor
  generated from each object's schema; text editor with formatting and preview; reward table editor.
* Books are plain JSON in `config/corerpg_quests/book/`, one file per chapter and reward table.

### Teams, stages, filters, vein mining
* Personal teams, parties (owner, officers, members, allies, invites, free to join), server teams, team chat,
  shared progress and team stages; team screen and `/team` commands.
* Smart filter language and Smart Filter item with an editor and live preview.
* Vein mining with six shapes, limits and costs, merge tags, drop gathering, crop harvesting and area tools.

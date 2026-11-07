# Marketing

Listing copy and artwork for CurseForge and Modrinth, in the same style as NightBeam's other RPG mods (RPG Attribute
System, Remnant Bosses): gold Cinzel titles over a dark, blurred in-game backdrop with slow golden motes.

| Path | What it is |
|------|------------|
| `curseforge/description.md` | The project description (Markdown; also works on Modrinth). Images load from this repo's `main` branch. |
| `curseforge/summary.txt` | The one-line summary (under 250 characters). |
| `images/icon.png` | 512×512 project icon: the quest tome in a gold ring, tagged QUESTS. |
| `images/banner.png` | 1800×600 header banner. |
| `images/card-*.png` | 1600×900 feature cards for the description and the gallery. |
| `images/header-*.png`, `images/divider.png` | Transparent section headers and a divider. |
| `images/screenshots/*.jpg` | Real in-game captures (NeoForge 1.21.1, 1920×1080, GUI scale 3) used by the cards and the gallery. |
| `source/render.cjs` | Renders every PNG above from the screenshots and the mod's bundled fonts. |
| `source/backdrop.jpg` | The blurred scenery behind the banner and cards (an in-game capture). |

Image links point at `raw.githubusercontent.com/MeherBenSalem/NoMore63AE2/main/...`. If the repository is renamed,
GitHub redirects the old links, but update the base URL in `description.md` when convenient.

## Regenerating the images

Needs Node.js and Playwright with Chromium:

```bash
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs              # everything
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs card-editor  # one asset
```

The version and update name are at the top of `render.cjs`.

## Retaking the screenshots

The mod has a scripted QA client (`ClientQa`, inactive unless `corerpg_quests.qa.script` is set). From `1.21.1/`:

```bash
./gradlew :neoforge:runQaServer      # generates a world and stops by itself
```

Copy `neoforge/runs/qa-server/world` to `neoforge/runs/qa/saves/qa` (with cheats enabled in `level.dat`) and the
server's `config/corerpg_quests` to `neoforge/runs/qa/config/`, set `guiScale:3` in `neoforge/runs/qa/options.txt`,
then run the client with a script, for example under a virtual display:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew :neoforge:runQaClient \
  "-PqaScript=wait:120;command:cq+complete+@s+first_ember/awaken;open:;wait:40;screenshot:book;quit"
```

Steps: `wait:ticks`, `open:questOrChapter`, `screenshot:name`, `edit:on|off`, `team`, `tables`, `editor:objectId`,
`filter:expression`, `command:text` (use `+` for spaces), `pin:questId`, `vein:on|off`, `vein_shape:index`,
`hud:on|off`, `look:yaw,pitch`, `close`, `quit`. Screenshots land in `neoforge/runs/qa/screenshots/`; convert the
ones you keep to JPEG in `images/screenshots/`.

| Screenshot | Scene |
|------------|-------|
| `book.jpg` | The First Ember chapter with completed, claimable, available and locked quests |
| `panel-claim.jpg` | A completed quest with its reward waiting to be claimed |
| `panel.jpg` | A locked quest: story, requirements and what it unlocks |
| `chapter.jpg` | The Wider World chapter: a quest link, a branch and a chapter image |
| `editor.jpg` | The same chapter in editing mode |
| `properties.jpg` | The property editor for a quest |
| `team.jpg` | The team screen for a party |
| `filter.jpg` | The smart filter editor with its live preview |
| `tables.jpg` | The reward table editor |
| `hud.jpg` | The pinned-quest tracker in game |
| `vein.jpg` | The vein mining outline and overlay on an iron vein (Fabric client) |

## Disclosure

The card and banner layouts were written as HTML/CSS code by an AI assistant, following the existing NightBeam
templates; every picture inside them is a real, unedited in-game frame of the mod.

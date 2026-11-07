// Renders the listing artwork in marketing/images/ from real in-game screenshots and the mod's bundled fonts.
// Same look as NightBeam's other RPG mods (RPG Attribute System, Remnant Bosses): gold serif titles on a dark,
// blurred in-game backdrop with slow golden motes.
// Usage (from the repository root): NODE_PATH="$(npm root -g)" node marketing/source/render.cjs [name...]
// Requires the `playwright` package with a Chromium build available.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..', '..');
const out = path.join(root, 'marketing', 'images');
const shots = path.join(out, 'screenshots');
const fonts = path.join(root, '1.21.1', 'common', 'src', 'main', 'resources', 'assets', 'corerpg_quests', 'font');
const textures = path.join(root, '1.21.1', 'common', 'src', 'main', 'resources', 'assets', 'corerpg_quests', 'textures');
const url = (file) => 'file://' + file;
const shot = (name) => url(path.join(shots, name));
const source = (name) => url(path.join(__dirname, name));
const texture = (name) => url(path.join(textures, name));

const VERSION = '1.0';
const UPDATE = 'The First Ember';

const css = `
@font-face { font-family: QSerif; src: url('${url(path.join(fonts, 'eb_garamond.ttf'))}'); }
@font-face { font-family: QDisplay; src: url('${url(path.join(fonts, 'cinzel.ttf'))}'); }
:root { --text:#dad3c1; --dim:#9c9584; --faint:#6e685c; --header:#e8dfc8; --gold:#d4b06a; --gold-hi:#f0d58c;
        --ember:#e8743b; --sage:#8fc27a; --sky:#6fb6e0; --bg:#0b0a09; --line:rgba(216,207,184,.32); }
* { box-sizing: border-box; }
html, body { margin: 0; background: var(--bg); color: var(--text); font-family: QSerif, serif; }
.stage { position: relative; overflow: hidden; background: radial-gradient(ellipse at 30% 40%, #1c1915 0%, #0b0a09 70%); }
.display { font-family: QDisplay, serif; letter-spacing: .04em; }
.gold { background: linear-gradient(180deg, #fff2c8 0%, #f0d58c 35%, #d4b06a 70%, #8c7444 100%);
        -webkit-background-clip: text; background-clip: text; color: transparent; }
.glow { filter: drop-shadow(0 0 18px rgba(212,176,106,.35)); }
.kicker { font-family: QDisplay, serif; color: var(--gold); letter-spacing: .22em; font-size: 22px; }
.rule { height: 1px; background: linear-gradient(90deg, rgba(212,176,106,0), rgba(212,176,106,.9) 20%, rgba(212,176,106,.9) 70%, rgba(212,176,106,0)); }
.diamond { display: inline-block; width: 9px; height: 9px; background: var(--gold); transform: rotate(45deg); margin: 0 12px 3px 0; }
.frame { border: 1px solid rgba(216,207,184,.45); box-shadow: 0 0 0 1px rgba(0,0,0,.6), 0 30px 80px rgba(0,0,0,.65), 0 0 60px rgba(212,176,106,.16); background:#0b0a09; }
.frame img { display: block; width: 100%; }
canvas.motes { position: absolute; left: 0; top: 0; width: 100%; height: 100%; pointer-events: none; }
.vignette { position: absolute; inset: 0; background: radial-gradient(ellipse at center, rgba(0,0,0,0) 45%, rgba(0,0,0,.75) 100%); }
.backdrop { position: absolute; inset: -40px; background-size: cover; background-position: center; filter: blur(16px) brightness(.42) saturate(1.1); }
.pill { display: inline-block; border: 1px solid rgba(216,207,184,.55); padding: 6px 14px 4px; margin-right: 10px;
        font-family: QDisplay, serif; font-size: 18px; letter-spacing: .12em; color: var(--header); background: rgba(0,0,0,.35); }
.ring { position: absolute; border: 2px solid var(--gold); border-radius: 4px; box-shadow: 0 0 18px rgba(212,176,106,.55); }
.badge { position: absolute; width: 46px; height: 46px; margin: -23px 0 0 -23px; border-radius: 50%; background: #12100d;
         border: 2px solid var(--gold); box-shadow: 0 0 18px rgba(212,176,106,.7); font-family: QDisplay, serif; font-size: 26px;
         color: var(--gold-hi); display: flex; align-items: center; justify-content: center; }
ul.points { list-style: none; padding: 0; margin: 0; }
ul.points li { position: relative; padding-left: 26px; margin: 0 0 14px; font-size: 27px; line-height: 1.32; color: var(--text); }
ul.points li::before { content: ''; position: absolute; left: 2px; top: 13px; width: 8px; height: 8px; background: var(--gold); transform: rotate(45deg); }
.k { display: inline-block; border: 1px solid rgba(216,207,184,.6); padding: 0 8px; margin: 0 2px; background: rgba(0,0,0,.35); font-size: .9em; }
.hi { color: var(--gold-hi); }
.code { font-family: 'DejaVu Sans Mono', monospace; }
`;

// Slow golden motes, deterministic so renders are reproducible.
const motes = `<canvas class="motes"></canvas><script>
(() => { const c = document.querySelector('canvas.motes'); const r = c.getBoundingClientRect();
  c.width = r.width; c.height = r.height; const g = c.getContext('2d'); let s = 7;
  const rnd = () => (s = (s * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 90; i++) { const x = rnd() * c.width, y = rnd() * c.height, a = .25 + rnd() * .6, z = 1 + rnd() * 2.2;
    const h = g.createRadialGradient(x, y, 0, x, y, z * 5); h.addColorStop(0, 'rgba(246,222,156,' + a * .55 + ')'); h.addColorStop(1, 'rgba(246,222,156,0)');
    g.fillStyle = h; g.beginPath(); g.arc(x, y, z * 5, 0, Math.PI * 2); g.fill();
    g.fillStyle = 'rgba(255,240,200,' + a + ')'; g.fillRect(x - z / 2, y - z / 2, z, z); } })();
</script>`;

const page = (width, height, body, transparent = false) => `<!doctype html><html><head><meta charset="utf-8"><style>${css}
body { width: ${width}px; height: ${height}px; } .stage { width: ${width}px; height: ${height}px; }
${transparent ? 'html, body, .stage { background: transparent; }' : ''}</style></head>
<body><div class="stage">${body}</div></body></html>`;

const header = (title) => page(1400, 120, `
  <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:28px;background:linear-gradient(90deg,rgba(0,0,0,0),rgba(20,18,15,.9) 30%,rgba(20,18,15,.9) 70%,rgba(0,0,0,0))">
    <div class="rule" style="width:300px;transform:scaleX(-1)"></div>
    <span class="diamond" style="margin:0"></span>
    <div class="display gold glow" style="font-size:44px;white-space:nowrap">${title}</div>
    <span class="diamond" style="margin:0"></span>
    <div class="rule" style="width:300px"></div>
  </div>`, true);

// A feature card: copy on the left, artwork on the right.
const card = ({ kicker, title, points, art, width = 1600, height = 900, artWidth = 980 }) => page(width, height, `
  <div class="backdrop" style="background-image:url('${source('backdrop.jpg')}');opacity:.55"></div>
  <div class="vignette"></div>${motes}
  <div style="position:absolute;left:80px;top:0;bottom:0;width:${width - artWidth - 150}px;display:flex;flex-direction:column;justify-content:center">
    <div class="kicker"><span class="diamond"></span>${kicker}</div>
    <div class="display gold glow" style="font-size:58px;line-height:1.08;margin:18px 0 22px">${title}</div>
    <div class="rule" style="width:80%;margin-bottom:30px"></div>
    <ul class="points">${points.map((point) => `<li>${point}</li>`).join('')}</ul>
  </div>
  <div style="position:absolute;right:60px;top:50%;transform:translateY(-50%);width:${artWidth}px">${art}</div>`);

// A screenshot cropped to a region: x, y, w, h as fractions of the full shot.
const crop = (name, x, y, w, h, height) => `<div class="frame" style="height:${height}px;overflow:hidden;position:relative">
  <img src="${shot(name)}" style="position:absolute;width:${100 / w}%;left:${-x / w * 100}%;top:${-y / h * height}px"></div>`;

const TASKS = [
  ['Item', 'have it or hand it in'], ['Kill', 'types, tags, named bosses'], ['Advancement', 'or one criterion'],
  ['Break · Place', 'mine ore, build a camp'], ['Craft · Consume', 'any crafting slot, any food'], ['Fish · Trade', 'catch it, buy it'],
  ['Breed · Tame · Enchant', 'the living world'], ['Location · Biome', 'reach the summit'], ['Structure · Dimension', 'find the ruin, cross over'],
  ['Observation', 'study the altar'], ['Interaction', 'talk to the hermit'], ['Stat · Experience', 'walk, jump, pay levels'],
  ['Stage · Custom', 'scripts and commands'], ['Fluid · Checkmark', 'buckets and promises'],
];
const REWARDS = ['Items with random bonus', 'Experience or levels', 'Random roll from a table', 'Choose one from a table', 'Everything in a table',
  'Vanilla loot tables', 'Commands', 'Advancements', 'Story toasts', 'Team stages', 'Loot crates that mobs drop'];

const assets = {
  banner: () => page(1800, 600, `
    <div class="backdrop" style="background-image:url('${source('backdrop.jpg')}')"></div>
    <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(8,7,6,.94) 0%,rgba(8,7,6,.78) 42%,rgba(8,7,6,.15) 100%)"></div>
    <div class="vignette"></div>${motes}
    <div style="position:absolute;right:-60px;top:70px;width:980px;transform:perspective(1600px) rotateY(-16deg) rotateX(3deg);transform-origin:left center">
      <div class="frame"><img src="${shot('book.jpg')}"></div></div>
    <div style="position:absolute;left:90px;top:92px;width:900px">
      <div class="kicker"><span class="diamond"></span>${VERSION} · ${UPDATE}</div>
      <div class="display gold glow" style="font-size:78px;line-height:1.06;margin:22px 0 26px;white-space:nowrap">Core RPG<br>Quests Engine</div>
      <div class="rule" style="width:640px;margin-bottom:26px"></div>
      <div style="font-size:34px;color:var(--header);line-height:1.3">Write the story. Chart the path.<br>Share the journey.</div>
      <div style="margin-top:36px"><span class="pill">Fabric</span><span class="pill">NeoForge</span>
        <span style="font-size:24px;color:var(--dim);margin-left:8px">1.21.1</span></div>
    </div>`),

  icon: () => page(512, 512, `
    <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 45%,#2a1d16 0%,#120d0b 55%,#070505 100%)"></div>${motes}
    <div style="position:absolute;left:56px;top:56px;width:400px;height:400px;border-radius:50%;border:3px solid var(--gold);box-shadow:0 0 40px rgba(212,176,106,.45), inset 0 0 40px rgba(212,176,106,.25)"></div>
    <div style="position:absolute;left:78px;top:78px;width:356px;height:356px;border-radius:50%;border:1px solid rgba(212,176,106,.55)"></div>
    ${[[256, 56], [456, 256], [256, 456], [56, 256]].map(([x, y]) => `<div style="position:absolute;left:${x - 12}px;top:${y - 12}px;width:24px;height:24px;background:var(--gold);transform:rotate(45deg);box-shadow:0 0 16px rgba(240,213,140,.8)"></div>`).join('')}
    <div style="position:absolute;left:116px;top:104px;width:280px;height:280px;background:radial-gradient(circle,rgba(232,116,59,.35),rgba(232,116,59,0) 65%)"></div>
    <img src="${texture('item/quest_book.png')}" style="position:absolute;left:136px;top:112px;width:240px;height:240px;image-rendering:pixelated;filter:drop-shadow(0 10px 24px rgba(0,0,0,.7))">
    <div class="display gold" style="position:absolute;left:0;right:0;top:372px;text-align:center;font-size:46px;letter-spacing:.16em">QUESTS</div>`),

  'card-book': () => card({
    kicker: 'The quest book', title: 'Every path<br>on one map',
    points: ['Chapters in collapsible groups, each a canvas you can <b class="hi">pan and zoom</b>',
      'Lines show what is <span style="color:var(--sky)">ready</span>, <span style="color:var(--ember)">underway</span> and <span style="color:var(--sage)">done</span>',
      'Eleven quest shapes, any size, links to quests in other chapters', 'Search the whole book with <span class="k">Ctrl</span><span class="k">F</span>'],
    art: `<div class="frame"><img src="${shot('book.jpg')}"></div>` }),

  'card-quest': () => card({
    kicker: 'The quest panel', title: 'Every quest<br>tells a story',
    points: ['Paged story text with colours and inline images', 'Tasks with live progress and <b class="hi">hand-in</b> buttons',
      'Rewards claimed per player or once for the <b class="hi">whole team</b>', 'See what a quest needs, and what it unlocks next'],
    art: `<div class="frame"><img src="${shot('panel-claim.jpg')}"></div>` }),

  'card-progression': () => card({
    kicker: 'Progression rules', title: 'Branches<br>and secrets',
    points: ['Depend on quests or whole chapters: all, any, started, or <b class="hi">N of these</b>',
      '<b class="hi">Branching choices</b>: pick one path and the others close', 'Hidden and secret quests that appear when their time comes',
      'Repeatable quests with cooldowns, optional quests, sequential tasks', 'Linear or flexible progression, per book, chapter or quest'],
    art: `<div class="frame"><img src="${shot('chapter.jpg')}"></div>` }),

  'card-tasks': () => card({
    kicker: '24 tasks · 12 rewards', title: 'Any task.<br>Any reward.',
    points: ['Everything is detected on the server, through Minecraft\'s own triggers', 'Works the same on NeoForge and Fabric',
      'Rewards: items, experience, tables, choices, loot, commands, stages', 'Auto-claim, silent claim or invisible rewards'],
    art: `<div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">
      ${TASKS.map(([t, s]) => `<div class="frame" style="background:rgba(12,11,9,.9);padding:14px 22px">
        <div class="display" style="font-size:24px;color:var(--header)">${t}</div><div style="font-size:21px;color:var(--dim);margin-top:2px">${s}</div></div>`).join('')}</div>` }),

  'card-rewards': () => card({
    kicker: 'Rewards & loot', title: 'Rewards worth<br>the journey',
    points: REWARDS.slice(0, 6).map((r) => r).concat(['Reward tables become <b class="hi">loot crates</b> that monsters, animals and bosses drop']),
    art: `<div class="frame"><img src="${shot('tables.jpg')}"></div>` }),

  'card-editor': () => card({
    kicker: 'The editor', title: 'Build it<br>in game',
    points: ['Right-click to add quests, links and images; drag to arrange', '<span class="k">Ctrl</span>-click to link dependencies, <span class="k">Ctrl</span><span class="k">C</span> / <span class="k">V</span> to copy',
      'Every field of every task and reward, in one editor', 'Changes reach every player at once, and save as plain JSON'],
    art: `<div style="position:relative;height:640px">
      <div class="frame" style="position:absolute;left:0;top:0;width:78%"><img src="${shot('editor.jpg')}"></div>
      <div class="frame" style="position:absolute;right:0;bottom:0;width:70%"><img src="${shot('properties.jpg')}"></div></div>` }),

  'card-teams': () => card({
    kicker: 'Teams & parties', title: 'Adventure<br>together',
    points: ['Party up: progress, stages and team rewards are shared', 'Owner, officers, members, allies and invites',
      'Free-to-join parties, team chat, colours and descriptions', 'Server teams for factions and events'],
    art: `<div class="frame"><img src="${shot('team.jpg')}"></div>` }),

  'card-filters': () => card({
    kicker: 'Smart filters', title: 'Any ingot.<br>Any blade.',
    points: ['A readable filter language: <span class="code" style="font-size:22px;color:var(--gold-hi)">any(#c:ingots, #c:gems)</span>',
      'Items, tags, mods, food, durability, enchantments, names and more', 'Live validation and a preview of every matching item',
      'Use it in tasks directly, or carry it on a Smart Filter item'],
    art: `<div class="frame"><img src="${shot('filter.jpg')}"></div>` }),

  'card-vein': () => card({
    kicker: 'Vein mining', title: 'Take the<br>whole vein',
    points: ['Hold <span class="k">`</span> and break a block: the connected ore comes with it', 'Six shapes: shapeless, tunnels, square and an escape staircase',
      'Hunger, experience, cooldowns, tool protection: all configurable', 'Harvest and replant a field, strip logs or till soil in one click'],
    art: `<div class="frame"><img src="${shot('vein.jpg')}"></div>` }),

  'card-hud': () => card({
    kicker: 'On your screen', title: 'Never lose<br>the thread',
    points: ['Pin quests to a tracker in any corner of the screen', 'Toasts for quests, chapters and rewards received',
      'Your book stays with you after death', 'Emergency items for players who lose their kit'],
    art: `<div class="frame"><img src="${shot('hud.jpg')}"></div>` }),

  'card-files': () => card({
    kicker: 'Made for modpacks', title: 'Plain JSON,<br>ready for git',
    points: ['One file per chapter and reward table', 'Readable ids like <span class="code" style="font-size:22px;color:var(--gold-hi)">act1/kindle</span>',
      'Every field documented, generated from the source', 'Items from removed mods are kept, not lost'],
    art: `<div class="frame" style="background:rgba(12,11,9,.92);padding:34px 44px;font-family:'DejaVu Sans Mono',monospace;font-size:24px;line-height:1.5;color:var(--text)">
      <div style="color:var(--dim)">config/corerpg_quests/book/chapters/first_ember.json</div>
      <pre style="margin:14px 0 0;font-family:inherit">{
  <span style="color:var(--gold)">"id"</span>: <span style="color:var(--sky)">"first_ember/kindle"</span>,
  <span style="color:var(--gold)">"title"</span>: <span style="color:var(--sky)">"&6Kindle"</span>,
  <span style="color:var(--gold)">"shape"</span>: <span style="color:var(--sky)">"hexagon"</span>,
  <span style="color:var(--gold)">"dependencies"</span>: [<span style="color:var(--sky)">"first_ember/stone"</span>, <span style="color:var(--sky)">"first_ember/offering"</span>],
  <span style="color:var(--gold)">"tasks"</span>: [{
    <span style="color:var(--gold)">"type"</span>: <span style="color:var(--sky)">"place_block"</span>,
    <span style="color:var(--gold)">"block"</span>: <span style="color:var(--sky)">"minecraft:torch"</span>, <span style="color:var(--gold)">"value"</span>: <span style="color:var(--sky)">8</span>
  }],
  <span style="color:var(--gold)">"rewards"</span>: [
    { <span style="color:var(--gold)">"type"</span>: <span style="color:var(--sky)">"xp_levels"</span>, <span style="color:var(--gold)">"levels"</span>: <span style="color:var(--sky)">2</span> },
    { <span style="color:var(--gold)">"type"</span>: <span style="color:var(--sky)">"toast"</span>, <span style="color:var(--gold)">"title"</span>: <span style="color:var(--sky)">"&6The first ember burns"</span> }
  ]
}</pre></div>` }),
};

const headers = {
  'header-book': 'The Quest Book', 'header-progression': 'Progression', 'header-tasks': 'Tasks & Rewards', 'header-editor': 'The Editor',
  'header-teams': 'Teams & Parties', 'header-filters': 'Smart Filters', 'header-vein': 'Vein Mining', 'header-modpacks': 'For Modpacks',
  'header-everything': 'Everything Else', 'header-compatibility': 'Downloads & Compatibility', 'header-faq': 'Questions', 'header-gallery': 'Gallery',
};
for (const [name, title] of Object.entries(headers)) assets[name] = () => header(title);
assets.divider = () => page(1400, 40, `<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:18px">
  <div class="rule" style="width:520px;transform:scaleX(-1)"></div><span class="diamond" style="margin:0"></span><div class="rule" style="width:520px"></div></div>`, true);

(async () => {
  const wanted = process.argv.slice(2);
  const browser = await chromium.launch();
  fs.mkdirSync(out, { recursive: true });
  for (const [name, build] of Object.entries(assets)) {
    if (wanted.length && !wanted.includes(name)) continue;
    const html = build();
    const size = html.match(/body \{ width: (\d+)px; height: (\d+)px; \}/);
    const tab = await browser.newPage({ viewport: { width: +size[1], height: +size[2] }, deviceScaleFactor: 1 });
    const file = path.join(__dirname, `.render-${name}.html`);
    fs.writeFileSync(file, html);
    await tab.goto(url(file));
    await tab.evaluate(() => document.fonts.ready);
    await tab.waitForTimeout(150);
    await tab.screenshot({ path: path.join(out, `${name}.png`), omitBackground: name === 'divider' || name.startsWith('header-') });
    fs.unlinkSync(file);
    await tab.close();
    console.log('rendered', name);
  }
  await browser.close();
})().catch((error) => { console.error(error); process.exit(1); });

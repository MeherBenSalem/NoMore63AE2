#!/usr/bin/env bash
# Boots a dev server for one Minecraft version and loader and proves the quest engine works at runtime, not only
# compiles. A datapack runs the engine's server commands with no player online:
#   /cq info (the example book written on first start must have quests), /cq reload, /cq export,
#   /team server create, /team list, /team server delete, a guarded /reload and /save-all,
# summons every Core RPG item as an item entity, runs for ~600 ticks and stops.
# Every class a common mixin targets is loaded at startup (corerpg_quests.qa.load_classes), so a broken injection
# fails here instead of when the first player joins.
# Fails on a crash report, a mixin error, an ERROR/WARN/exception line mentioning the mod, a failed command,
# a mixin target that did not load, or when the RAN/OK markers never show up.
#   usage: scripts/server-smoke-test.sh <1.20.1|1.21.1|26.2|26.3> <fabric|forge|neoforge>
set -u
VERSION="$1"; LOADER="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/$VERSION"
MOD=corerpg_quests
NS=cq_smoke
RUN="$LOADER/runs/server"
rm -rf "$RUN/world" "$RUN/crash-reports" "$RUN/logs" "$RUN/config/$MOD"
PACK="$RUN/world/datapacks/$NS"
mkdir -p "$PACK"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<PROPS
online-mode=false
function-permission-level=4
spawn-protection=0
view-distance=4
simulation-distance=4
level-seed=20261123
initial-enabled-packs=vanilla,file/$NS
PROPS
if [ "$LOADER" = forge ]; then
  # In the dev environment Forge's LAN advertiser loads a client-only class and can stop the server.
  mkdir -p "$RUN/defaultconfigs"
  printf '[server]\n\tadvertiseDedicatedServerToLan = false\n' > "$RUN/defaultconfigs/forge-server.toml"
fi

# pack.mcmeta as valid JSON, with the same format (range) as the mod's own pack (26.x uses min_format/max_format).
python3 - common/src/main/resources/pack.mcmeta "$PACK/pack.mcmeta" <<'PY'
import json, sys
src = json.load(open(sys.argv[1]))["pack"]
pack = {"description": "Core RPG - Quests Engine smoke test"}
for key in ("pack_format", "min_format", "max_format", "supported_formats"):
    if key in src:
        pack[key] = src[key]
json.dump({"pack": pack}, open(sys.argv[2], "w"))
PY
cat "$PACK/pack.mcmeta"; echo

# Every class a common (non-client) mixin targets, fully qualified from the mixin's imports.
TARGETS="$(python3 - common/src/main/java common/src/main/resources/$MOD.mixins.json <<'PY'
import json, re, sys
from pathlib import Path
src, cfg = Path(sys.argv[1]), json.load(open(sys.argv[2]))
pkg = Path(*cfg["package"].split("."))
names = cfg.get("mixins", []) + cfg.get("server", [])
out = []
for n in names:
    text = (src / pkg / (n.replace(".", "/") + ".java")).read_text()
    imports = {m.group(2): m.group(1) + m.group(2) for m in re.finditer(r"^import ([\w.]+\.)(\w+);", text, re.M)}
    m = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?\{?([^)}]*)\}?", text)
    for ref in re.findall(r"([\w.]+)\.class", m.group(1)):
        head, *rest = ref.split(".")
        fqn = imports.get(head, head) + "".join("$" + r for r in rest)
        if fqn not in out:
            out.append(fqn)
print(",".join(out))
PY
)"
TARGET_COUNT=$(echo "$TARGETS" | tr ',' '\n' | grep -c .)
echo "Mixin targets ($TARGET_COUNT): $TARGETS"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dcorerpg_quests.qa.load_classes=$TARGETS"

if [ "$VERSION" = 1.20.1 ]; then COUNT='Count:1b'; else COUNT='count:1'; fi
ITEMS="quest_book smart_filter loot_crate missing_item"

FN="$PACK/data/$NS"
for dir in function functions; do
  mkdir -p "$FN/$dir" "$PACK/data/minecraft/tags/$dir"
  echo "{\"values\":[\"$NS:start\"]}" > "$PACK/data/minecraft/tags/$dir/load.json"
  {
    echo 'scoreboard objectives add cqs dummy'
    # /reload in run.mcfunction runs this load function again; only the first load starts the test.
    echo 'execute if score #started cqs matches 1 run say CQ_SMOKE_RELOADED'
    echo "execute unless score #started cqs matches 1 run function $NS:begin"
  } > "$FN/$dir/start.mcfunction"
  {
    echo 'scoreboard players set #started cqs 1'
    echo 'forceload add -32 -32 32 32'
    echo 'say CQ_SMOKE_LOADED'
    echo "schedule function $NS:run 100t"
  } > "$FN/$dir/begin.mcfunction"

  {
    check() { # marker command...
      local marker="$1"; shift
      echo "scoreboard players set #r cqs 0"
      echo "execute store success score #r cqs run $*"
      echo "execute if score #r cqs matches 1 run say CQ_OK $marker"
      echo "execute unless score #r cqs matches 1 run say CQ_FAILED $marker"
    }
    echo "scoreboard players set #q cqs 0"
    echo "execute store result score #q cqs run cq info"
    echo "execute if score #q cqs matches 1.. run say CQ_OK book_has_quests"
    echo "execute unless score #q cqs matches 1.. run say CQ_FAILED book_has_quests"
    check reload cq reload
    echo "scoreboard players set #q2 cqs 0"
    echo "execute store result score #q2 cqs run cq info"
    echo "execute if score #q2 cqs = #q cqs run say CQ_OK reload_same_quest_count"
    echo "execute unless score #q2 cqs = #q cqs run say CQ_FAILED reload_same_quest_count"
    check export cq export
    check team_create team server create CqSmokeGuild
    check team_list team list
    check team_delete team server delete CqSmokeGuild
    x=-6
    for item in $ITEMS; do
      check "summon_$item" summon minecraft:item $x 120 0 "{Item:{id:\"$MOD:$item\",$COUNT},Tags:[\"cq_smoke\"],NoGravity:1b,PickupDelay:32767}"
      x=$((x + 4))
    done
    check items_present execute if entity @e[type=minecraft:item,tag=cq_smoke]
    check save save-all flush
    echo 'reload'
    echo 'say CQ_SMOKE_RAN'
    echo "schedule function $NS:finish 600t"
  } > "$FN/$dir/run.mcfunction"

  {
    echo "execute store result score #q3 cqs run cq info"
    echo "execute if score #q3 cqs = #q cqs run say CQ_OK quests_after_reload"
    echo "execute unless score #q3 cqs = #q cqs run say CQ_FAILED quests_after_reload"
    echo 'kill @e[tag=cq_smoke]'
    echo 'say CQ_SMOKE_OK'
    echo 'stop'
  } > "$FN/$dir/finish.mcfunction"
done
EXPECTED_OK=$(cat "$FN/function/run.mcfunction" "$FN/function/finish.mcfunction" | grep -c "say CQ_OK")

LOG="$ROOT/server-smoke-$VERSION-$LOADER.log"
./gradlew ":$LOADER:runServer" --stacktrace -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64,JAVA_HOME_25_X64 < /dev/null > "$LOG" 2>&1 &
PID=$!
CRASH='This crash report has been saved|Encountered an unexpected exception|Failed to start the minecraft server|Mixin apply.*failed|InvalidInjectionException|MixinApplyError|MixinTransformerError|Mixin.*(error|failed)'
result=timeout
for _ in $(seq 1 240); do
  sleep 5
  if grep -q "CQ_SMOKE_OK" "$LOG"; then result=ok; break; fi
  if grep -q -E "$CRASH" "$LOG"; then result=crash; break; fi
  if ! kill -0 $PID 2>/dev/null; then result=exited; break; fi
done
sleep 10
pkill -P $PID 2>/dev/null; kill $PID 2>/dev/null; wait $PID 2>/dev/null

echo "===== markers"
grep -n -E "CQ_|QA: |Quest engine|Quest book|Found new data pack" "$LOG" | head -80
for f in "$RUN"/crash-reports/*.txt; do [ -f "$f" ] && { echo "=== $f"; head -100 "$f"; }; done
echo "===== errors and warnings"
grep -n -E "ERROR|WARN|Exception|Caused by|[Mm]ixin.*(fail|error)|Failed to|Couldn't|Unknown" "$LOG" | grep -v -E "Download|Task :" | head -80
echo "Result: $result"

fail=0
[ "$result" = ok ] || { echo "::error::server did not reach CQ_SMOKE_OK ($result)"; fail=1; }
grep -q -E "$CRASH" "$LOG" && { echo "::error::crash or mixin error"; fail=1; }
grep -q "CQ_SMOKE_RAN" "$LOG" || { echo "::error::the smoke function never ran"; fail=1; }
grep -q "CQ_SMOKE_RELOADED" "$LOG" || { echo "::error::/reload never finished"; fail=1; }
grep -q "Quest engine started" "$LOG" || { echo "::error::the quest engine never started"; fail=1; }
if grep -n "CQ_FAILED" "$LOG"; then echo "::error::a quest engine command failed"; fail=1; fi
oks=$(grep -c "CQ_OK " "$LOG"); loaded=$(grep -c "QA: loaded " "$LOG")
echo "Commands OK: $oks of $EXPECTED_OK; mixin targets loaded: $loaded of $TARGET_COUNT"
[ "$oks" -ge "$EXPECTED_OK" ] || { echo "::error::not every command check passed"; fail=1; }
[ "$loaded" -ge "$TARGET_COUNT" ] || { echo "::error::not every mixin target class loaded"; fail=1; }
# Errors, warnings and exceptions that mention the mod (its id, its logger name, its package or the smoke pack).
# (Mixin's "refmap could not be read" warning is expected in a dev environment, where names are not remapped.)
if grep -n -E "(ERROR|WARN|Exception|Caused by|Failed|Couldn't|Unknown|Errors in)" "$LOG" \
    | grep -v -E "JAVA_TOOL_OPTIONS|Reference map '$MOD.refmap.json' .* could not be read" \
    | grep -i -E "$MOD|Core RPG - Quests Engine|dev\.nightbeam\.corerpg|$NS"; then
  echo "::error::error or warning mentioning the mod"; fail=1
fi
exit $fail

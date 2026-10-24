#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")"
RES=src/main/resources
SRC=src/main/java
FAILED=0
WARNINGS=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

section() { printf '\n== %s ==\n' "$1"; }
fail() { printf '  ERROR: %s\n' "$1"; FAILED=$((FAILED + 1)); }
warn() { printf '  WARN:  %s\n' "$1"; WARNINGS=$((WARNINGS + 1)); }
ok() { printf '  ok:    %s\n' "$1"; }

section "Java compile (all lint warnings)"
cat > "$TMP/lint.gradle" <<'EOF'
allprojects {
    tasks.withType(JavaCompile).configureEach {
        options.compilerArgs += ['-Xlint:all,-processing,-serial,-classfile']
    }
}
EOF
if ./gradlew compileJava --rerun-tasks --init-script "$TMP/lint.gradle" > "$TMP/compile.log" 2>&1; then
    COUNT=$(grep -c 'warning:' "$TMP/compile.log" || true)
    if [ "$COUNT" -gt 0 ]; then
        grep 'warning:' "$TMP/compile.log" | sed 's#.*/src/main/java/#  #' | sort -u
        WARNINGS=$((WARNINGS + COUNT))
    else
        ok "no compiler warnings"
    fi
else
    grep -E 'error:' -A2 "$TMP/compile.log" | sed 's#.*/src/main/java/#  #' | head -40
    fail "compile failed"
fi

section "Shaders (glslangValidator)"
if command -v glslangValidator > /dev/null; then
    for f in "$RES"/assets/skysong/shaders/core/*.fsh "$RES"/assets/skysong/shaders/core/*.vsh; do
        stage=frag
        [[ "$f" == *.vsh ]] && stage=vert
        out=$(glslangValidator -S "$stage" "$f" 2>&1)
        if echo "$out" | grep -q 'ERROR'; then
            fail "$(basename "$f"): $(echo "$out" | grep ERROR | head -1)"
        fi
    done
    [ "$FAILED" -eq 0 ] && ok "all shaders compile"
else
    warn "glslangValidator not installed, shaders not checked"
fi

section "JSON resources"
python3 - "$RES" <<'EOF' || FAILED=$((FAILED + 1))
import json, pathlib, sys
bad = 0
for p in pathlib.Path(sys.argv[1]).rglob("*.json"):
    try:
        json.loads(p.read_text(encoding="utf-8-sig"))
    except Exception as e:
        print(f"  ERROR: {p}: {e}")
        bad += 1
print("  ok:    all JSON files parse" if bad == 0 else "")
sys.exit(1 if bad else 0)
EOF

section "Mod metadata, mixins, lang keys"
python3 - "$RES" "$SRC" <<'EOF' || FAILED=$((FAILED + 1))
import json, pathlib, re, sys
res, src = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
errors = []
mod = json.loads((res / "fabric.mod.json").read_text())
for side, entries in mod.get("entrypoints", {}).items():
    for cls in entries:
        if not (src / (cls.replace(".", "/") + ".java")).exists():
            errors.append(f"entrypoint {cls} not found")
for name in mod.get("mixins", []):
    cfg = json.loads((res / name).read_text())
    pkg = cfg["package"].replace(".", "/")
    for side in ("mixins", "client", "server"):
        for m in cfg.get(side, []):
            if not (src / pkg / (m + ".java")).exists():
                errors.append(f"mixin {m} listed in {name} but missing")
aw = mod.get("accessWidener")
if aw and not (res / aw).exists():
    errors.append(f"access widener {aw} missing")
lang = json.loads((res / "assets/skysong/lang/en_us.json").read_text())
used = set()
for f in src.rglob("*.java"):
    used |= set(re.findall(r'"(key\.skysong\.[a-z_]+)"', f.read_text()))
for key in sorted(used - lang.keys()):
    errors.append(f"lang key {key} has no translation")
for e in errors:
    print("  ERROR:", e)
print("  ok:    entrypoints, mixins and lang keys match" if not errors else "")
sys.exit(1 if errors else 0)
EOF

section "Share code ids"
python3 - "$RES" "$SRC" <<'EOF' || FAILED=$((FAILED + 1))
import pathlib, re, sys
res, src = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
settings = (src / "dev/infectedware/skysong/ModSettings.java").read_text()
config = (src / "dev/infectedware/skysong/ModConfig.java").read_text()
fields = re.findall(r"public static (?:boolean|int|float|String) (\w+) =", settings)
personal = set(re.findall(r'"(\w+)"', re.search(r"PERSONAL = Set\.of\(([^)]*)\)", config).group(1)))
ids = [l.strip() for l in (res / "assets/skysong/share_ids.txt").read_text().splitlines() if l.strip() and not l.startswith("#")]
missing = [f for f in fields if f not in ids and f not in personal]
dupes = sorted({i for i in ids if ids.count(i) > 1})
for m in missing:
    print("  ERROR: setting", m, "is not in share_ids.txt")
for d in dupes:
    print("  ERROR: share id", d, "is listed twice")
print("  ok:    every shared setting has a share id" if not missing and not dupes else "")
sys.exit(1 if missing or dupes else 0)
EOF

section "Secrets and files that must not be published"
LEAKS=$(git grep -nIE 'gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY|xox[baprs]-[A-Za-z0-9-]{10,}' -- . ':!inspect.sh' 2>/dev/null || true)
if [ -n "$LEAKS" ]; then
    echo "$LEAKS" | sed 's/^/  /'
    fail "possible secrets committed"
else
    ok "no tokens or private keys in tracked files"
fi
TRACKED=$(git ls-files | grep -E '^(build|run|libs|\.gradle|\.idea)/|\.jar$|spotify_token|skysong\.json$' | grep -v '^gradle/wrapper/gradle-wrapper\.jar$' || true)
if [ -n "$TRACKED" ]; then
    echo "$TRACKED" | sed 's/^/  /'
    fail "build output, game files or personal config are tracked by git"
else
    ok "no build output, jars or personal config tracked"
fi
MC=$(git ls-files | grep -iE 'assets/minecraft/' || true)
if [ -n "$MC" ]; then
    echo "$MC" | sed 's/^/  /'
    fail "Minecraft assets are tracked (Mojang owns them)"
else
    ok "no Minecraft assets tracked"
fi

section "Licenses"
[ -f LICENSE ] && grep -q "VerseFall-Development" LICENSE && ok "LICENSE keeps the original Versefall notice" || fail "LICENSE is missing the original Versefall copyright notice"
for f in "$RES"/assets/skysong/font/*.ttf; do
    case "$(basename "$f")" in
        exo-2*) [ -f "$RES/licenses/exo2-ofl.txt" ] || fail "Exo 2 license missing" ;;
        montserrat*) [ -f "$RES/licenses/montserrat-ofl.txt" ] || fail "Montserrat license missing" ;;
        aquire*) [ -f "$RES/licenses/aquire-license.txt" ] || fail "Aquire license missing" ;;
        *) warn "font $(basename "$f") has no known license" ;;
    esac
done
ok "font license files present"

section "Summary"
printf '  %d error(s), %d warning(s)\n' "$FAILED" "$WARNINGS"
[ "$FAILED" -eq 0 ]

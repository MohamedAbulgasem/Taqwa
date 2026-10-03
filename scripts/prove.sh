#!/usr/bin/env bash
# Turns the weekly monitor's new captures into proof (docs/MONITOR.md, "Prove"): runs the `prove` task
# (each capture a recipe of tools/timetables/official/monitor/recipes.tsv names, whose days the gate does
# not hold yet, pinned under archive/tables/pinned/<source>/<date>/ and its rows appended; the WHOLE gate
# run; every new row that breaks a promise removed again and reported, never loosened; the stamps written
# when green), then every check a release of the proof needs: ProofStamps.kt regenerated, the golden
# vector regenerated and required byte-identical (the engine did not change; if it moved, stop), the
# stamps checked, the tools' tests with the archive, the website's generator (CI=true), its build
# --check and its timetable tests. When a check fails, everything prove changed in this checkout and the
# pinned copies it wrote are put back, and the exit is 1.
#
#   scripts/prove.sh [--date yyyy-mm-dd] [--only <source>]...
#
# Writes into $PROVE_OUT (default $TAQWA_OFFICIAL/monitor/proof/, restricted: it quotes dates and
# minutes): proof.json (the task's report), notices-before.txt and notices-after.txt (the generator's
# notices on the committed proof and on the new one, for "cities newly proven"), and changed (true or
# false: whether this run added rows and every check passed, so the workflow knows whether to push). Exit 0 green
# (whether or not anything was added), 1 a check failed after rows were added (all put back), 2 prove
# could not run (the gate red or refused before anything was added). Nothing is committed or pushed
# here. TAQWA_GRADLE is the Gradle command (default ./gradlew), TAQWA_PYTHON the interpreter (python3).
set -uo pipefail

OFFICIAL="${TAQWA_OFFICIAL:-${HOME:-}/Desktop/Workspace/apps/Taqwa-official}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="${TAQWA_GRADLE:-./gradlew}"
PY="${TAQWA_PYTHON:-python3}"
OUT="${PROVE_OUT:-$OFFICIAL/monitor/proof}"
DATE=""
ONLY=()
while [ $# -gt 0 ]; do
    case "$1" in
        --date) DATE="${2:-}"; shift 2 ;;
        --only) ONLY+=("${2:-}"); shift 2 ;;
        *) echo "prove.sh: unknown option $1" >&2; exit 2 ;;
    esac
done
[ -d "$OFFICIAL/archive" ] || { echo "prove.sh: no archive/ under $OFFICIAL" >&2; exit 2; }
mkdir -p "$OUT"
rm -f "$OUT/proof.json" "$OUT/changed"
echo false > "$OUT/changed"
cd "$REPO" || exit 2
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
GOLDEN="shared/src/commonTest/kotlin/world/taqwa/app/prayer/engine/golden/GoldenVectorData.kt"
PROOF_STAMPS="shared/src/commonMain/kotlin/world/taqwa/app/prayer/engine/registry/data/ProofStamps.kt"

gradle() { "$GRADLE" --no-daemon -q -p tools/timetables "$@"; }

notices() {
    # The generator's notices (held cities, months shown alone, excused days) on this checkout's proof.
    CI=true gradle generate "-Pout=$TMP/timetables-$1.json" > "$TMP/generate-$1.log" 2>&1 || { cat "$TMP/generate-$1.log" >&2; return 1; }
    grep '::notice::' "$TMP/generate-$1.log" | sed 's/^.*::notice:://' > "$OUT/notices-$1.txt"
    grep -v '::notice::' "$TMP/generate-$1.log" | grep -v '^WARNING' | tail -1
}

put_back() {
    echo "prove.sh: $1; putting back what prove changed" >&2
    git checkout -- tools/timetables/official/gate tools/timetables/official/stamps "$PROOF_STAMPS" "$GOLDEN" 2>/dev/null
    git clean -fq -- tools/timetables/official/stamps 2>/dev/null
    if [ -f "$OUT/proof.json" ]; then
        # The pinned copies prove wrote are removed, and the report says why nothing it added stands.
        "$PY" - "$OUT/proof.json" "$OFFICIAL" "$1" <<'PY'
import json, os, sys
report = json.load(open(sys.argv[1], encoding="utf-8"))
for path in report.get("pinned") or []:
    full = os.path.join(sys.argv[2], path)
    if os.path.isfile(full):
        os.remove(full)
        try:
            os.rmdir(os.path.dirname(full))  # the dated folder, when this run's copies were all it held
        except OSError:
            pass
report["putBack"] = sys.argv[3]
report["changed"] = False
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(report, f, indent=2)
PY
    fi
    echo false > "$OUT/changed"
    exit 1
}

echo "== the generator on the committed proof"
notices before || exit 2

echo "== prove"
args=("-Pofficial=$OFFICIAL" "-Preport=$OUT/proof.json")
[ -n "$DATE" ] && args+=("-Pdate=$DATE")
if [ ${#ONLY[@]} -gt 0 ]; then args+=("-Ponly=$(IFS=,; echo "${ONLY[*]}")"); fi
gradle prove "${args[@]}"
code=$?
if [ $code -ne 0 ]; then
    echo "prove.sh: prove could not run (exit $code); nothing changed" >&2
    exit 2
fi
added="$("$PY" -c 'import json, sys; print("true" if json.load(open(sys.argv[1], encoding="utf-8")).get("changed") else "false")' "$OUT/proof.json" 2>/dev/null)"
if [ "$added" != true ]; then
    echo "Nothing new to prove: every capture's days are held (or every new row was left out)"
    cp "$OUT/notices-before.txt" "$OUT/notices-after.txt"
    exit 0
fi

echo "== ProofStamps.kt"
gradle generateProofStamps || put_back "generateProofStamps failed"

echo "== the golden vector (must not move: prove never changes the engine)"
gradle generateGoldenVector "-Pout=$TMP/GoldenVectorData.kt" || put_back "generateGoldenVector failed"
cmp -s "$TMP/GoldenVectorData.kt" "$GOLDEN" || put_back "the golden vector moved: the engine is not the one the gate files were proven with; stop and look"

echo "== checkStamps"
gradle checkStamps || put_back "checkStamps failed"

echo "== the tools' tests, with the archive"
gradle jvmTest "-Pofficial=$OFFICIAL" || put_back "the tools' tests failed"

echo "== the generator on the new proof"
notices after || put_back "the generator failed"
CI=true gradle generate > /dev/null 2>&1 || put_back "the generator failed writing _data/timetables.json"

echo "== the website"
"$PY" site/build.py --check || put_back "site/build.py --check failed"
"$PY" site/test_timetables.py || put_back "site/test_timetables.py failed"

echo true > "$OUT/changed"
echo "Proven: the checkout holds the new rows, stamps and ProofStamps.kt"
exit 0

#!/usr/bin/env bash
# Every commit the weekly monitor's bot (author taqwa-monitor) made since <since-ref> touched only the paths the
# bot may commit (tools/timetables/monitor/ci/bot-paths.txt, the list the publish job enforces), and none is a merge.
# Run at each release cut, from the release's last cut tag:
#
#   scripts/check-bot-commits.sh build-39
#
# Prints each bot commit it checked; exits 1 when one touched anything else (docs/MONITOR.md, "Publishing").
# It selects commits by author, which whoever holds a token can fake: it checks the honest pipeline's commits, and the
# real protection is that the push token never leaves the clean publish job.
set -euo pipefail
since="${1:?usage: scripts/check-bot-commits.sh <since-ref>}"
root="$(git rev-parse --show-toplevel)"
git rev-parse -q --verify "$since^{commit}" > /dev/null || { echo "check-bot-commits: no commit $since" >&2; exit 2; }
patterns="$(mktemp)"
trap 'rm -f "$patterns"' EXIT
grep -v -E '^[[:space:]]*(#|$)' "$root/tools/timetables/monitor/ci/bot-paths.txt" > "$patterns"
checked=0
bad=0
while IFS= read -r sha; do
    checked=$((checked + 1))
    if [ "$(git rev-list --parents -n 1 "$sha" | wc -w)" -gt 2 ]; then
        echo "BAD  $sha is a merge (the bot never merges)"
        bad=1
        continue
    fi
    outside="$(git diff-tree --no-commit-id --name-only -r --no-renames "$sha" | grep -v -x -E -f "$patterns" || true)"
    if [ -n "$outside" ]; then
        echo "BAD  $sha touches $(printf '%s' "$outside" | tr '\n' ' ')"
        bad=1
    else
        echo "ok   $sha $(git log -1 --format=%s "$sha" | cut -c1-80)"
    fi
done < <(git log --format=%H --author='^taqwa-monitor <' "$since..HEAD")
echo "check-bot-commits: $checked bot commit(s) since $since"
exit "$bad"

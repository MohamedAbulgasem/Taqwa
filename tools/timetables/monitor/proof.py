#!/usr/bin/env python3
"""The "Proof" section of the monitor's issue, and the public commit's message, from what
scripts/prove.sh wrote (docs/MONITOR.md, "Prove"): rows added, rows left out with the reason, and the
cities the generator newly publishes (its notices before and after).

    proof.py section <proof dir> [--pushed <state>]   # Markdown for the issue body (restricted repo)
    proof.py message <proof dir>                      # the public commit message: counts and names only

The commit message never carries a printed time, a minute or a date read from a table (ruling R69):
only counts, gate file names, entries and city slugs. The section lives in the private repository's
issue, like the report it sits beside (dates, counts and minutes, never a printed time).
"""
import json
import os
import re
import sys


def load(proof_dir):
    """proof.json (or None when prove did not write one) and the notices before and after."""
    def read(name):
        path = os.path.join(proof_dir, name)
        return open(path, encoding="utf-8").read() if os.path.isfile(path) else None

    text = read("proof.json")
    report = json.loads(text) if text else None
    before = (read("notices-before.txt") or "").splitlines()
    after = (read("notices-after.txt") or "").splitlines()
    return report, before, after


def held(notices):
    """City slug -> reason, from the generator's `<slug>: held — <reason>` notices."""
    out = {}
    for line in notices:
        m = re.match(r"^\s*([a-z0-9-]+): held — (.*)$", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def newly_proven(before, after):
    """Cities held before and published now, in order."""
    was, now = held(before), held(after)
    return sorted(slug for slug in was if slug not in now)


def newly_held(before, after):
    return sorted(slug for slug in held(after) if slug not in held(before))


def section(report, before, after, pushed=""):
    lines = ["## Proof", ""]
    if report is None:
        lines.append("Prove did not run (the run was partial, the monitor itself failed, or prove stopped before writing its report).")
        return "\n".join(lines) + "\n"
    if report.get("failure"):
        lines.append("Prove could not run, so nothing was added: " + report["failure"])
        return "\n".join(lines) + "\n"
    added = report.get("added") or []
    left = report.get("leftOut") or []
    if report.get("putBack"):
        lines.append("Prove added %d rows with the whole gate green, but a check after it failed (%s), so everything was put back "
                     "and nothing is published; the rows it would have added are listed below." % (len(added), report["putBack"]))
    else:
        lines.append("%d rows added, %d left out; the whole gate %s -> %s rows, %s -> %s place-days, green." % (
            len(added), len(left), report.get("rowsBefore"), report.get("rowsAfter"), report.get("placeDaysBefore"), report.get("placeDaysAfter")))
    if pushed:
        lines.append("")
        lines.append("Public repository: " + pushed)
    if added:
        lines += ["", "Added (split test):"]
        for a in added:
            who = a["entry"] + (" member " + a["member"] if a.get("member") else "")
            days = a.get("days") or []
            span = (days[0].split("..")[0] + ".." + days[-1].split("..")[-1]) if days else "-"
            lines.append("- %s: %s at %s, %d days %s (%s)" % (a["gate"], who, a["point"], a.get("dayCount", 0), span, a["capture"]))
    if left:
        lines += ["", "Left out (never loosened; the reason as the gate gave it):"]
        for item in left:
            lines.append("- %s: %s" % (item["row"], " ".join(item["reason"].split())))
    proven = newly_proven(before, after)
    held_now = newly_held(before, after)
    lines += ["", "Cities newly proven: " + (", ".join(proven) if proven else "none") + "."]
    if held_now:
        lines.append("Cities newly held: " + ", ".join(held_now) + ".")
    return "\n".join(lines) + "\n"


def message(report, before, after):
    """The public commit message: counts, gate files, entries and city slugs only (R69)."""
    added = report.get("added") or []
    left = report.get("leftOut") or []
    files = sorted({a["gate"] for a in added})
    entries = sorted({a["entry"].split("/")[0] for a in added})
    proven = newly_proven(before, after)
    head = "Gate: the weekly monitor's captures of %s proven (%d rows)" % (report.get("date", "?"), len(added))
    body = [
        head,
        "",
        "Written by scripts/prove.sh in the monitor's workflow, with the whole gate green: each capture a",
        "recipe of tools/timetables/official/monitor/recipes.tsv names, pinned in the archive and checked as",
        "split=test rows; stamps and ProofStamps.kt regenerated, the golden vector byte-identical, checkStamps,",
        "the tools' tests, the generator and the site's checks green.",
        "",
        "Gate files: " + (", ".join(files) or "none") + ".",
        "Entries: " + (", ".join(entries) or "none") + ".",
        "Whole gate: %s -> %s rows, %s -> %s place-days." % (report.get("rowsBefore"), report.get("rowsAfter"), report.get("placeDaysBefore"), report.get("placeDaysAfter")),
        "Rows left out (reported in the monitor's issue): %d." % len(left),
        "Cities newly proven: " + (", ".join(proven) if proven else "none") + ".",
    ]
    return "\n".join(body) + "\n"


def main(argv):
    if len(argv) < 3 or argv[1] not in ("section", "message"):
        sys.stderr.write(__doc__)
        return 2
    report, before, after = load(argv[2])
    if argv[1] == "section":
        pushed = argv[4] if len(argv) > 4 and argv[3] == "--pushed" else ""
        sys.stdout.write(section(report, before, after, pushed))
        return 0
    if report is None:
        sys.stderr.write("proof.py: no proof.json\n")
        return 2
    sys.stdout.write(message(report, before, after))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

#!/usr/bin/env python3
"""The "Proof" section of the monitor's issue, and the public commit's message, from what
scripts/prove.sh wrote (docs/MONITOR.md, "Prove"): rows added, rows left out with the reason, and the
cities the generator newly publishes (its notices before and after); and the checked timetables prove
demoted by itself (ruling R185: a capture of the authority's own site broke a promise).

    proof.py section <proof dir> [--pushed <state>]   # Markdown for the issue body (restricted repo)
    proof.py message <proof dir>                      # the public commit message: counts and names only
    proof.py attention <proof dir> [--pushed <state>] [--code <prove's exit>]
                                                      # fingerprint=, tier1=, summary= lines for $GITHUB_OUTPUT

The commit message never carries a printed time, a minute or a date read from a table (ruling R69):
only counts, gate file names, entries and city slugs, and it is refused (exit 3) when any line has a
clock time's shape. The section lives in the private repository's issue, like the report it sits
beside (dates, counts and minutes, never a printed time).

`attention` says whether prove's outcome needs the owner even on a green week: a row left out (tier 1
when the engine was early or an end late against the table, and always when it was a checked
timetable's own row, whatever the reason: a refusal or a mirror's capture demotes nothing, but its days
went unchecked), a checked timetable demoted (always tier 1), prove stopped or put back, a city newly held, or a public push that did not land. Its fingerprint ignores the run's date and every number, so
the workflow notifies when the set changes, not every week.
"""
import hashlib
import json
import os
import re
import sys

# A clock time's shape, h:mm or hh:mm, never a signed offset such as UTC+02:00 (ruling R69).
TIME_SHAPE = re.compile(r"(^|[^0-9+-])[0-2]?[0-9]:[0-5][0-9]([^0-9]|$)")


def load(proof_dir):
    """proof.json (or None when prove did not write one) and the notices before and after."""
    def read(name):
        path = os.path.join(proof_dir, name)
        if not os.path.isfile(path):
            return None
        with open(path, encoding="utf-8") as f:
            return f.read()

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
    demoted = report.get("demoted") or []
    if report.get("putBack"):
        lines.append("Prove added %d rows with the whole gate green, but a check after it failed (%s), so everything was put back "
                     "and nothing is published; the rows it would have added are listed below." % (len(added), report["putBack"]))
        if demoted:
            lines.append("The demotions below were put back with them: the next run writes them again.")
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
        unchecked = [item for item in left if item.get("checked")]
        if unchecked:
            lines += ["", "%d of these were a checked timetable's own row: the days they print are not checked until the row lands "
                      "(a row the reader refused, or a republisher's mirror, demotes nothing by itself, so a person must look)." % len(unchecked)]
    if demoted:
        lines += ["", "Demoted by the monitor itself (ruling R185): a capture of the authority's own site broke a promise, so the "
                  "`@demoted` line is in the gate file, the unit's city pages (the whole entry's, where no unit is named) are held, "
                  "a Ramadan build no longer counts the entry as covered under O4, and no release is cut until a person has looked. "
                  "Prove the failing days (refit the method, or record the authority's own misprint and add its corrected table) and "
                  "remove the line, or lower the entry's class in the registry:"]
        for d in demoted:
            lines.append("- %s in %s, since %s: %s" % (d["entry"], d["gate"], d.get("since", "?"), " ".join(d.get("reason", "").split())))
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
    demoted = report.get("demoted") or []
    proven = newly_proven(before, after)
    held_now = newly_held(before, after)
    head = "Gate: the weekly monitor's captures of %s proven (%d rows)" % (report.get("date", "?"), len(added))
    if demoted:
        head += ", %d demoted" % len(demoted)
    body = [
        head,
        "",
        "Written by scripts/prove.sh in the monitor's workflow, with the whole gate green: each capture a",
        "recipe of tools/timetables/official/monitor/recipes.tsv names, pinned in the archive and checked as",
        "split=test rows; stamps and ProofStamps.kt regenerated, the golden vector byte-identical, checkStamps,",
        "the tools' tests, the generator, the site's checks and the app's tests green.",
        "",
        "Gate files: " + (", ".join(files) or "none") + ".",
        "Entries: " + (", ".join(entries) or "none") + ".",
        "Whole gate: %s -> %s rows, %s -> %s place-days." % (report.get("rowsBefore"), report.get("rowsAfter"), report.get("placeDaysBefore"), report.get("placeDaysAfter")),
        "Rows left out (reported in the monitor's issue): %d." % len(left),
        "Cities newly proven: " + (", ".join(proven) if proven else "none") + ".",
    ]
    if demoted:
        # Names only (R69): the reason, with its dates and minutes, is in the gate file's line and the monitor's issue.
        body += [
            "Demoted by the monitor itself (ruling R185: a capture of the authority's own site broke a promise; the @demoted line",
            "is in the gate file and the stamp, and the pages are held until a person has looked): "
            + ", ".join(sorted(d["entry"] for d in demoted)) + ".",
            "Cities newly held: " + (", ".join(held_now) if held_now else "none") + ".",
        ]
    return "\n".join(body) + "\n"


def timed_lines(text):
    """The lines of [text] with a clock time's shape (what a public file or message never carries)."""
    return [line for line in text.splitlines() if TIME_SHAPE.search(line)]


def reason_class(reason):
    text = " ".join(reason.split())
    for words, name in ((" early", "early"), (" late ends", "late end"), ("over the late limit", "over the limit"),
                        ("out of order", "out of order"), ("unchecked", "unchecked"), ("never rewritten", "pinned copy differs"),
                        ("no recipe line reads it", "no recipe target"), ("refused", "refused"), ("red before prove", "red before")):
        if words in text:
            return name
    return "other"


def attention(report, before, after, pushed="", code=""):
    """(fingerprint, tier1, summary): what in prove's outcome needs the owner; the fingerprint is "" when nothing does."""
    items, tier1 = [], False
    if report is None:
        if code not in ("", "0"):
            items.append("prove: exit %s, no report" % code)
    else:
        if report.get("failure"):
            items.append("failure: " + re.sub(r"\d+", "#", report["failure"])[:200])
        if report.get("putBack"):
            items.append("put back: " + re.sub(r"\d+", "#", report["putBack"])[:200])
        for item in report.get("leftOut") or []:
            kind = reason_class(item.get("reason", ""))
            tier1 = tier1 or kind in ("early", "late end") or bool(item.get("checked"))
            items.append("left: %s | %s" % (item.get("row", ""), kind))
        for d in report.get("demoted") or []:
            tier1 = True
            items.append("demoted: %s" % d.get("entry", ""))
    held_now = newly_held(before, after)
    items += ["held: " + slug for slug in held_now]
    if pushed.startswith("not pushed"):
        items.append("push: " + re.sub(r"\([0-9a-f]{7,}\)", "", pushed))
    if not items:
        return "", False, ""
    fingerprint = hashlib.sha256("\n".join(sorted(items)).encode("utf-8")).hexdigest()[:16]
    left = report.get("leftOut") or [] if report else []
    parts = []
    if report and report.get("failure"):
        parts.append("prove could not run")
    if report and report.get("putBack"):
        parts.append("prove's rows were put back")
    if left:
        early = sum(1 for item in left if reason_class(item.get("reason", "")) in ("early", "late end"))
        own = sum(1 for item in left if item.get("checked"))
        parts.append("%d rows left out%s%s" % (len(left), " (%d with an early start or a late end)" % early if early else "",
                                               " (%d a checked timetable's own row: its days are unchecked)" % own if own else ""))
    if report and report.get("demoted"):
        parts.append("demoted by the monitor (ruling R185): " + ", ".join(sorted(d.get("entry", "") for d in report["demoted"])))
    if held_now:
        parts.append("cities newly held: " + ", ".join(held_now))
    if pushed.startswith("not pushed"):
        parts.append(pushed)
    if report is None and code not in ("", "0"):
        parts.append("prove exited %s without a report" % code)
    return fingerprint, tier1, "Proof: " + "; ".join(parts) + "."


def option(argv, name):
    return argv[argv.index(name) + 1] if name in argv[3:-1] else ""


def main(argv):
    if len(argv) < 3 or argv[1] not in ("section", "message", "attention"):
        sys.stderr.write(__doc__)
        return 2
    report, before, after = load(argv[2])
    if argv[1] == "section":
        sys.stdout.write(section(report, before, after, option(argv, "--pushed")))
        return 0
    if argv[1] == "attention":
        fingerprint, tier1, summary = attention(report, before, after, option(argv, "--pushed"), option(argv, "--code"))
        sys.stdout.write("fingerprint=%s\ntier1=%s\nsummary=%s\n" % (fingerprint, str(tier1).lower(), summary.replace("\n", " ")[:900]))
        return 0
    if report is None:
        sys.stderr.write("proof.py: no proof.json\n")
        return 2
    text = message(report, before, after)
    if timed_lines(text):
        sys.stderr.write("proof.py: the commit message would carry a clock time's shape; refused (ruling R69)\n")
        return 3
    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

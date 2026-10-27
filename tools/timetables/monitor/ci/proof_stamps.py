#!/usr/bin/env python3
# ProofStamps.kt from tools/timetables/official/stamps/*.json, byte for byte what the Kotlin generator writes
# (tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/GenerateProofStamps.kt; ProofStampsPortTest
# holds the two equal on the committed stamps and on stamps with every character a string literal must escape).
# The monitor's publish job runs this on its own clean runner, with the Python standard library alone, so the
# Kotlin it pushes is never the copy the proving job wrote (docs/MONITOR.md, "Publishing").
#
#   python3 proof_stamps.py <stamps dir>            the file's text on stdout
#   python3 proof_stamps.py <stamps dir> <out.kt>   written to <out.kt>
import json
import os
import re
import sys

UNITS_PER_FUNCTION = 40

HEADER = """// GENERATED FILE — do not edit by hand.
//
// Written by `./gradlew -p tools/timetables generateProofStamps` (GenerateProofStamps.kt) from
// every `tools/timetables/official/stamps/<entry>.json`. Re-run it after every stamp refresh (a
// `gate` run that changes a stamp) and commit the result: stamps are statistics only — place-days,
// dates, minute counts — never an official time, so this file is safe to publish (spec §5).
package world.taqwa.app.prayer.engine.registry.data

/**
 * One registry entry's proof, as the About-times screen shows it (spec §2.3, task 11): how many
 * place-days and places the gate has checked it on, the last date any row holds (`lastChecked`, the
 * stamp's `last`, null before any stamp exists: the end of the envelope, never a "checked through"
 * claim, since the days before it may have holes), the worst lateness seen per event over the whole entry
 * (`worstLateMinutes`, event keys as the gate writes them: "fajr", "sunrise", "dhuhr", "asr" or
 * "asrStandard"/"asrHanafi", "maghrib", "isha", "endOfEating", "imsak") and per authority unit
 * (`worstLateByUnit`, keyed by the unit's id; empty for an entry the gate checks without units),
 * and the late limits the gate held the entry to when the stamp was written ([ProofLateLimit],
 * ruling R41): the class's own (A 1, B 2, C 1 after the latest member, D 3), listed with a null
 * reason, and each recorded exception with its reason, each with the events it covered.
 * `checked` is the days any row compared, as the stamp's runs ("2026-09-25..2026-11-01", or one
 * date), and `checkedByUnit` a unit's own runs where they are not the entry's (ruling R115): About's
 * "checked through" date is the end of the run holding the day it is read on, so it never runs past a
 * day the place's own table was not checked on, as its city page's date never does.
 *
 * A unit or entry with no stamp file (an authority not yet gated, or a class with no official
 * days) has no row: [ProofStamps.of] returns null, and the screen reads that as "not yet compared"
 * (spec §2.3's without-data wording), never as zero.
 */
data class ProofStamp(
    val entryId: String,
    val places: Int,
    val placeDays: Int,
    val ramadanDays: Int,
    val lastChecked: String?,
    val worstLateMinutes: Map<String, Int>,
    val worstLateByUnit: Map<String, Map<String, Int>>,
    val lateLimits: List<ProofLateLimit>,
    val checked: List<String> = emptyList(),
    val checkedByUnit: Map<String, List<String>> = emptyMap(),
)

/**
 * One late limit the gate held this entry to and the events it covered (ruling R41): the class's
 * own with a null reason, or a recorded exception with its reason.
 */
data class ProofLateLimit(val minutes: Int, val reason: String?, val events: List<String>)


"""


def literal(s):
    """A Kotlin string literal: backslash, quote, dollar and control characters escaped (finding D9)."""
    out = []
    for ch in s:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("\\$")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ord(ch) < 0x20 or ch in "\u2028\u2029":
            out.append("\\u%04x" % ord(ch))
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


def as_int(v):
    """Kotlin's `as? Long ?: 0L`: a JSON integer, anything else 0 (wrapped to 32 bits as toInt() does)."""
    if isinstance(v, bool) or not isinstance(v, int):
        return 0
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= 1 << 31 else v


def as_map(v):
    return v if isinstance(v, dict) else {}


def worst_of(events):
    return {event: as_int(as_map(stats).get("worstLate")) for event, stats in events.items()}


def runs_of(v):
    if not isinstance(v, list):
        return []
    for x in v:
        if not isinstance(x, str):
            raise ValueError("a checked run is not a string")
    return list(v)


LATE_LIMIT = re.compile(r"^(.*): (\d+) min(?: \(.+\))?$")


def read_stamp(path):
    with open(path, encoding="utf-8") as f:
        root = json.load(f)
    if not isinstance(root, dict) or not isinstance(root.get("entry"), str):
        raise ValueError("%s: no entry" % path)
    units = as_map(root.get("units"))
    checked = runs_of(root.get("checked"))
    checked_by_unit = {}
    for unit, stats in units.items():
        runs = runs_of(as_map(stats).get("checked"))
        if runs and runs != checked:
            checked_by_unit[unit] = runs
    limits = []
    for label, value in as_map(root.get("lateLimits")).items():
        m = LATE_LIMIT.fullmatch(label)
        if not m:
            continue
        entry = as_map(value)
        reason = entry.get("reason") if isinstance(entry.get("reason"), str) else None
        events = entry.get("events")
        events = runs_of(events) if isinstance(events, list) else []
        minutes = int(m.group(2))
        if minutes > 9999:
            raise ValueError("a late limit of %d min" % minutes)
        limits.append((minutes, reason, events))
    last = root.get("last")
    return {
        "entry": root["entry"],
        "places": as_int(root.get("places")),
        "placeDays": as_int(root.get("placeDays")),
        "ramadanDays": as_int(root.get("ramadanDays")),
        "last": last if isinstance(last, str) else None,
        "worst": worst_of(as_map(root.get("events"))),
        "worstByUnit": {unit: worst_of(as_map(as_map(stats).get("events"))) for unit, stats in units.items()},
        "limits": limits,
        "checked": checked,
        "checkedByUnit": checked_by_unit,
    }


ENTRY_ID = re.compile(r"[a-z][a-z0-9]*([._-][a-z0-9]+)*")
KEYWORDS = {"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
            "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
            "when", "while"}
HELPER = re.compile(r".*(Units|Checked)[0-9]*")


def function_name(entry_id):
    if not ENTRY_ID.fullmatch(entry_id):
        raise ValueError("an entry id that is not lower-case letters, digits and . - _: %s" % literal(entry_id))
    words = [w for w in re.split(r"[._-]", entry_id) if w]
    name = words[0] + "".join(w[0].upper() + w[1:] for w in words[1:])
    if name in KEYWORDS or HELPER.fullmatch(name):
        raise ValueError("an entry id whose function name is a Kotlin keyword or a helper's: %s" % literal(entry_id))
    return name


def worst_literal(worst):
    return "mapOf(" + ", ".join("%s to %d" % (literal(k), worst[k]) for k in sorted(worst)) + ")"


def runs_literal(runs):
    return "emptyList()" if not runs else "listOf(" + ", ".join(literal(r) for r in runs) + ")"


def chunked(items, size):
    return [items[i:i + size] for i in range(0, len(items), size)]


def render(stamps):
    names = [function_name(s["entry"]) for s in stamps]
    if len(set(names)) != len(names):
        raise ValueError("two entry ids share a function name: %s" % names)
    o = [HEADER, "object ProofStamps {\n",
         "    /** One row per stamped entry (`tools/timetables/official/stamps/<id>.json`), sorted by id. */\n",
         "    val byEntry: Map<String, ProofStamp> = listOf(\n"]
    o += ["        %s(),\n" % n for n in names]
    o += ["    ).associateBy { it.entryId }\n\n",
          "    /** The stamp for [entryId], or null where the gate has not checked it (a class D unit still awaiting data). */\n",
          "    fun of(entryId: String): ProofStamp? = byEntry[entryId]\n"]
    for s, name in zip(stamps, names):
        chunks = chunked(sorted(s["worstByUnit"].items(), key=lambda kv: kv[0]), UNITS_PER_FUNCTION)
        o.append("\n")
        o.append("    private fun %s() = ProofStamp(\n" % name)
        o.append("        entryId = %s,\n" % literal(s["entry"]))
        o.append("        places = %d,\n" % s["places"])
        o.append("        placeDays = %d,\n" % s["placeDays"])
        o.append("        ramadanDays = %d,\n" % s["ramadanDays"])
        o.append("        lastChecked = %s,\n" % (literal(s["last"]) if s["last"] is not None else "null"))
        o.append("        worstLateMinutes = %s,\n" % worst_literal(s["worst"]))
        if not chunks:
            o.append("        worstLateByUnit = emptyMap(),\n")
        elif len(chunks) == 1:
            o.append("        worstLateByUnit = %sUnits(),\n" % name)
        else:
            o.append("        worstLateByUnit = %s,\n" % " + ".join("%sUnits%d()" % (name, i + 1) for i in range(len(chunks))))
        limits = sorted(s["limits"], key=lambda l: (l[0], l[2][0] if l[2] else ""))
        if not limits:
            o.append("        lateLimits = emptyList(),\n")
        else:
            o.append("        lateLimits = listOf(\n")
            for minutes, reason, events in limits:
                ev = ", ".join(literal(e) for e in sorted(events))
                o.append("            ProofLateLimit(minutes = %d, reason = %s, events = listOf(%s)),\n"
                         % (minutes, literal(reason) if reason is not None else "null", ev))
            o.append("        ),\n")
        o.append("        checked = %s,\n" % runs_literal(s["checked"]))
        run_chunks = chunked(sorted(s["checkedByUnit"].items(), key=lambda kv: kv[0]), UNITS_PER_FUNCTION)
        if len(run_chunks) == 1:
            o.append("        checkedByUnit = %sChecked(),\n" % name)
        elif len(run_chunks) > 1:
            o.append("        checkedByUnit = %s,\n" % " + ".join("%sChecked%d()" % (name, i + 1) for i in range(len(run_chunks))))
        o.append("    )\n")
        for i, chunk in enumerate(run_chunks):
            suffix = "" if len(run_chunks) == 1 else str(i + 1)
            o.append("\n")
            o.append("    private fun %sChecked%s(): Map<String, List<String>> = mapOf(\n" % (name, suffix))
            o += ["        %s to %s,\n" % (literal(u), runs_literal(r)) for u, r in chunk]
            o.append("    )\n")
        for i, chunk in enumerate(chunks):
            suffix = "" if len(chunks) == 1 else str(i + 1)
            o.append("\n")
            o.append("    private fun %sUnits%s(): Map<String, Map<String, Int>> = mapOf(\n" % (name, suffix))
            o += ["        %s to %s,\n" % (literal(u), worst_literal(w)) for u, w in chunk]
            o.append("    )\n")
    o.append("}\n")
    return "".join(o)


def generate(stamps_dir):
    files = sorted(f for f in os.listdir(stamps_dir) if f.endswith(".json"))
    stamps = [read_stamp(os.path.join(stamps_dir, f)) for f in files]
    stamps.sort(key=lambda s: s["entry"])
    return render(stamps)


if __name__ == "__main__":
    text = generate(sys.argv[1])
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    else:
        sys.stdout.write(text)

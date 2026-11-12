#!/usr/bin/env python3
"""The JDK's tz database version, checked before a monitor run fetches anything:

    python3 tools/timetables/monitor/tzdb.py [--java-home <dir>] [--minimum 2026e]

The gate, the surveys and the monitor read printed times on civil clocks through the JDK's own zone
rules (lib/tzdb.dat), so a JDK with older rules reads some tables an hour off: the cloud runner's
Temurin moved from 21.0.12 to 21.0.13 (tzdata 2026e) between the 26 Oct and 2 Nov 2026 runs and the
results changed (Canada's permanent offsets from 1 Nov 2026). Prints one line and exits 0 when the
version is MINIMUM or later, OLDER (4) when it is older (the line says why), 3 when no tzdb.dat can be
read (the Kotlin tasks' own check, gate/Tzdb.kt, still stands); any other code is this script failing,
never a refusal. The JDK is --java-home, else JAVA_HOME, else the java on PATH. The Kotlin tasks run on
Gradle's jvmToolchain(21) JDK, so JAVA_HOME must be that one; where the two differ, the Kotlin check is
the one that counts. MINIMUM must equal Tzdb.MINIMUM in gate/Tzdb.kt (a test holds the two together).
"""
import argparse
import os
import re
import shutil
import struct
import sys

MINIMUM = "2026e"
OLDER = 4  # the exit code of a refusal (1 is Python's own for an uncaught error, never read as one)
UNREAD = 3

_SHAPE = re.compile(r"(\d{4})([a-z]+)")


def order(version):
    """A version's sort key: `2026e` < `2026f` < `2027a`; ValueError for anything else."""
    m = _SHAPE.fullmatch(version.strip())
    if not m:
        raise ValueError(f"'{version}' is not a tz database version (yyyy and letters)")
    return int(m.group(1)), len(m.group(2)), m.group(2)


def at_least(version, minimum=MINIMUM):
    return order(version) >= order(minimum)


def read_version(path):
    """The version a tzdb.dat names (the JDK's TzdbZoneRulesCompiler layout: byte 1, UTF "TZDB",
    a short count, then that many UTF versions; the last is the rules' own)."""
    with open(path, "rb") as f:
        data = f.read(4096)

    def utf(at):
        (n,) = struct.unpack_from(">H", data, at)
        return data[at + 2:at + 2 + n].decode("utf-8"), at + 2 + n

    if not data or data[0] != 1:
        raise ValueError(f"{path} is not a tzdb.dat (format byte {data[:1].hex() or 'missing'})")
    magic, at = utf(1)
    if magic != "TZDB":
        raise ValueError(f"{path} is not a tzdb.dat (no TZDB mark)")
    (count,) = struct.unpack_from(">H", data, at)
    at += 2
    if count < 1:
        raise ValueError(f"{path} names no version")
    version = None
    for _ in range(count):
        version, at = utf(at)
    return version


def java_home(given=None):
    """The JDK folder: as given, else JAVA_HOME, else the java on PATH (its links followed), else None."""
    if given:
        return given
    if os.environ.get("JAVA_HOME"):
        return os.environ["JAVA_HOME"]
    java = shutil.which("java")
    if java:
        return os.path.dirname(os.path.dirname(os.path.realpath(java)))
    return None


def tzdb_path(home):
    """The tzdb.dat of the JDK at `home`: its lib/, else that of the java its bin/java links to
    (Homebrew's opt/openjdk@21 is links into libexec/openjdk.jdk/Contents/Home)."""
    path = os.path.join(home, "lib", "tzdb.dat")
    java = os.path.join(home, "bin", "java")
    if not os.path.isfile(path) and os.path.exists(java):
        linked = os.path.join(os.path.dirname(os.path.dirname(os.path.realpath(java))), "lib", "tzdb.dat")
        if os.path.isfile(linked):
            return linked
    return path


def check(home, minimum=MINIMUM):
    """(exit code, line) for the JDK at `home`."""
    if not home:
        return UNREAD, "no JDK found (JAVA_HOME unset and no java on PATH): the tz database version is not checked here"
    path = tzdb_path(home)
    try:
        version = read_version(path)
        ok = at_least(version, minimum)
    except (OSError, ValueError, struct.error, UnicodeDecodeError) as e:
        return UNREAD, f"cannot read the tz database version from {path} ({e}): it is not checked here"
    if ok:
        return 0, f"tz database {version} ({path}), at least {minimum}"
    return OLDER, (f"the JDK's tz database is {version}, older than {minimum} ({path}): printed times would be read on old clock "
                   f"rules (Canada's permanent offsets from 1 Nov 2026 among them); run on a JDK whose tzdb.dat is {minimum} or later, "
                   f"and make JAVA_HOME the JDK Gradle's toolchain runs on (gate/Tzdb.kt checks that one, and it is the one that counts)")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--java-home")
    ap.add_argument("--minimum", default=MINIMUM)
    a = ap.parse_args(argv)
    code, line = check(java_home(a.java_home), a.minimum)
    print(line)
    return code


if __name__ == "__main__":
    sys.exit(main())

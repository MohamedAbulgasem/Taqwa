#!/usr/bin/env bash
# Whether this release ships the Galaxy watch app to Google Play's Wear OS track (Mohamed's decision
# of 26 November 2026): only when the watch has changed. Play's Wear OS review takes days where the
# phone's takes hours, so a release whose watch is the last one's uploads the phone's AAB alone, and
# Play keeps serving the last watch build. scripts/release.sh asks it first.
#
#   scripts/watch-ships.sh         "ships: ..." and exit 0, or "stays: ..." and exit 1
#   scripts/watch-ships.sh --tag   the tag it compares with, wear-build-<N>, or nothing when there is none
#
# The tag is the wear-build-<N> with the highest N. wear-build-<N> means "Play's Wear OS track has
# build N": the Lead tags it, on the commit release.sh's PASS line names (the one its Wear AAB was
# built from), once that release's Wear AAB is submitted to the Wear OS track, and deletes it if that
# Wear release is abandoned or rejected with no replacement (docs/STORE-CHECKLIST.md, "Each watch
# release"). With none, the watch ships ("no watch release is tagged"). The tag it compares with is
# an error (exit 2), with or without --tag, when it is above androidApp's versionCode (no release is
# tagged before it is cut), when its commit's wearApp/build.gradle.kts does not say versionCode =
# WEAR_OFFSET + N (the bump wrote that on the commit that was built), or when build-<N> is there and
# its commit's watch (the three modules and the catalog) is not the tag's. A tag on a later commit
# would hide every watch change between the two, so each of these is a typing mistake to put right.
#
# It ships when a path under wearApp/, watchcore/ (the one module of this repository that wearApp
# depends on) or watchface/ (the face inside it) differs between that tag's commit and HEAD, between
# HEAD and the index, or between HEAD and the working tree, or is a new file git does not ignore.
# Not counted, because they never reach the release Wear AAB (the lead's ruling of 26 November 2026):
#   - every test or debug-only source set: src/test/, src/test<Name>/ (testDebug, testFixtures),
#     src/androidTest<Name>/, src/<name>Test/ (commonTest, androidHostTest, iosTest, androidTest) and
#     src/debug/, in each of the three modules. The release build compiles src/main and src/release
#     alone, and the debug build alone adds src/debug;
#   - watchcore/vectors/, the golden feeds and test vectors. Only the tests and the debug build's
#     sample feed read them (wearApp/build.gradle.kts, sampleFeedAssets), and release.sh's step 5
#     fails a store output that holds one.
# A change to wearApp/build.gradle.kts or watchface/build.gradle.kts counts unless every line it
# changes is a version line exactly as scripts/bump-version.sh writes it ("versionCode = <digits>" or
# 'versionName = "<text>"', indented, nothing else on the line), as many removed as added of each:
# the bump moves those with the phone's at every release, so whenever the watch next ships its code
# is WEAR_OFFSET + the phone's then (100058 after 100046, say), and Play only needs the code to rise.
#
# The version catalog, gradle/libs.versions.toml, counts by its entries (the lead's ruling of 26
# November 2026), read in any of TOML's forms (inline tables, dotted keys, [libraries.<alias>]
# sub-tables, quoted keys, single- or double-quoted strings). Each changed entry is one of three:
#   - the watch's: only the watch's release builds with it. It ships the watch, named on the
#     "ships:" line;
#   - shared: the watch's release builds with it, and another module's release does too. It is
#     named on the "note:" line;
#   - not the watch's: the watch's release does not build with it (the phone's own libraries, the
#     watch's test-only ones). It is named on an "aside:" line, with a change to the catalog's
#     comments or spacing, and weighed for nothing.
# "Builds with" is read from every build file each run, not kept by hand. An entry is used through
# its accessor (libs.wear.tiles, libs.versions.android.targetSdk, libs.bundles.x, libs.plugins.x;
# the longest alias a reference begins with, so libs.wear.protolayout.expression is that library,
# not wear-protolayout) or, in the watch's build files, by its name in quotes
# (libs.findLibrary("wear-tiles")). A version is used through every library and plugin that refers
# to it and every bundle that holds one of those; a library through every bundle that holds it.
# Comments are no use. Where the reading cannot be sure, it leans to shipping: in the watch's build
# files only a test or debug configuration call (testImplementation(...), debugImplementation(...)
# and the like, on one line or several) leaves a use out; in the others a test or debug source
# set's block does as well (commonTest.dependencies { }, getByName("androidHostTest") { }), so a
# watch library that only another module's tests name is the watch's (the phone's tests do not
# build the phone). The watch's three build files must be there, or it is an error (exit 2).
#
# What else the watch is built with beside its own modules does not ship it on its own, and a change
# there is named on a "note:" line, after the first, for the Lead to judge (TAQWA_RELEASE_WATCH=1
# ships it; release.sh repeats it in its phone-only PASS line): the catalog's shared entries, the
# root build.gradle.kts, settings.gradle.kts, gradle.properties, gradle/wrapper/, and the two scripts
# the Wear app's release build runs to make the face's token (tools/watchface/push-token.sh and the
# validator pins it sources, tools/watchface/lib-validators.sh).
#
# TAQWA_RELEASE_WATCH=1 makes it ship and TAQWA_RELEASE_WATCH=0 makes it stay, each saying it was
# forced, and =0 naming the watch changes it overrides; unset or empty, the tag decides. It exits 2,
# with the reason on stderr, on an error: the repository's root is not a git checkout's, there is no
# commit, a tag names no commit or fails a rule above, a git command fails, a watch build file is
# missing, or TAQWA_RELEASE_WATCH is anything else. It reads git and changes nothing.
# scripts/test-watch-ships.sh is its test.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/version-files.sh
# The pathspecs below are written for git's default reading of them ('*.gradle.kts' a glob that
# crosses directories); these four would change it, so a build file would go unread.
unset GIT_LITERAL_PATHSPECS GIT_GLOB_PATHSPECS GIT_NOGLOB_PATHSPECS GIT_ICASE_PATHSPECS

error() { echo "watch-ships.sh: $*" >&2; exit 2; }

TAG_ONLY=false
case "$#:${1-}" in
    0:) ;;
    1:--tag) TAG_ONLY=true ;;
    *) error "usage: scripts/watch-ships.sh [--tag]" ;;
esac

FORCED="${TAQWA_RELEASE_WATCH-}"
case "$FORCED" in
    "" | 0 | 1) ;;
    *) error "TAQWA_RELEASE_WATCH is 1 to ship the watch, 0 to keep it back, or unset, not '$FORCED'" ;;
esac

g() { git -c core.quotepath=off "$@"; }

WATCH=(wearApp watchcore watchface)
WATCH_BUILD_FILES=("$WEAR_GRADLE" watchcore/build.gradle.kts "$FACE_GRADLE")
CATALOG=gradle/libs.versions.toml
SHARED=("$CATALOG" build.gradle.kts settings.gradle.kts gradle.properties gradle/wrapper
    tools/watchface/push-token.sh tools/watchface/lib-validators.sh)

# paths <count>: "1 path" or "<count> paths".
paths() { if [ "$1" -eq 1 ]; then echo "1 path"; else echo "$1 paths"; fi; }
# described <lines>: "3 paths: a, b, c" or "12 paths: a, b, c and 9 more".
described() {
    local count shown
    count="$(grep -c . <<< "$1")"
    shown="$(head -3 <<< "$1" | awk 'NR > 1 { printf ", " } { printf "%s", $0 }')"
    if [ "$count" -le 3 ]; then echo "$(paths "$count"): $shown"
    else echo "$(paths "$count"): $shown and $((count - 3)) more"
    fi
}

# The root itself, not a directory above it that happens to be a checkout: a copy of the tree with no
# .git of its own would otherwise be compared with whatever repository holds it.
TOP="$(g rev-parse --show-toplevel 2> /dev/null)" || error "$PWD is not a git checkout"
[ "$(cd "$TOP" && pwd -P)" = "$(pwd -P)" ] || error "$PWD is not a git checkout's root (git sees $TOP)"
g rev-parse --verify -q 'HEAD^{commit}' > /dev/null || error "the checkout has no commit to compare"
SHALLOW="$(g rev-parse --is-shallow-repository)" || error "git cannot say whether this clone is shallow"

TAGS="$(g for-each-ref --format='%(refname:strip=2)' 'refs/tags/wear-build-*')" || error "git cannot list the wear-build tags"
TAG="$( { grep -E '^wear-build-[1-9][0-9]*$' <<< "$TAGS" || true; } | sort -t- -k3,3n | tail -1)"
if [ -n "$TAG" ]; then
    N="${TAG#wear-build-}"
    PHONE_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)$/\1/p' "$PHONE_GRADLE" 2> /dev/null || true)"
    [[ "$PHONE_CODE" =~ ^[0-9]+$ ]] || error "cannot read androidApp's versionCode from $PHONE_GRADLE, which $TAG is checked against"
    if [ "${#N}" -gt 15 ] || [ "$N" -gt "$PHONE_CODE" ]; then
        error "$TAG is above androidApp's versionCode, $PHONE_CODE: a watch release is tagged with the code it shipped at, so no wear-build tag is above the release being cut; delete the mistaken tag (git tag -d $TAG)"
    fi
    if ! REF="$(g rev-parse --verify -q "refs/tags/$TAG^{commit}")"; then
        if [ "$SHALLOW" = true ]; then error "$TAG names no commit this shallow clone holds (git fetch --unshallow --tags)"; fi
        error "$TAG names no commit"
    fi
    SHORT="$(g rev-parse --short "$REF")" || error "git cannot name $TAG's commit"
    SERVED=$((WEAR_OFFSET + N))
    # The tag sits on the commit its Wear AAB was built from, where the bump wrote that code.
    MOVE="put it on the commit that Wear AAB was built from, which release.sh's PASS line named (git tag -d $TAG, then git tag -a $TAG -m \"<V> Wear OS ($SERVED) store build, on Play's Wear OS track\" <that commit>)"
    TAGGED_GRADLE="$(g show "$REF:$WEAR_GRADLE" 2> /dev/null)" || error "$TAG's commit, $SHORT, has no $WEAR_GRADLE to read the code it was built at from; $MOVE"
    TAGGED_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\)$/\1/p' <<< "$TAGGED_GRADLE")"
    if [ "$TAGGED_CODE" != "$SERVED" ]; then
        error "$TAG is on $SHORT, whose $WEAR_GRADLE says versionCode = ${TAGGED_CODE:-<none>}, not $SERVED: the tag is not on the commit its Wear AAB was built from, and would hide the watch changes between that commit and this one; $MOVE"
    fi
    # build-<N>, when it is there, names the commit that was built: the tag's watch must be its.
    if BUILD_REF="$(g rev-parse --verify -q "refs/tags/build-$N^{commit}")"; then
        apart="$(g diff --name-only --no-renames --no-ext-diff "$BUILD_REF" "$REF" -- "${WATCH[@]}" "$CATALOG")" \
            || error "git could not compare $TAG ($SHORT) with build-$N"
        if [ -n "$apart" ]; then
            error "$TAG ($SHORT) and build-$N ($(g rev-parse --short "$BUILD_REF")) hold different watches, $(described "$apart"): one of them is not on the commit that was built; if it is $TAG, $MOVE"
        fi
    fi
fi
if $TAG_ONLY; then
    [ -z "$TAG" ] || echo "$TAG"
    exit 0
fi

# never_shipped <path>: whether a path under the watch's modules never reaches the release Wear AAB:
# a test or debug-only source set, or watchcore/vectors/.
never_shipped() {
    case "$1" in watchcore/vectors/*) return 0 ;; esac
    case "$1" in wearApp/src/*/* | watchcore/src/*/* | watchface/src/*/*) ;; *) return 1 ;; esac
    local set="${1#*/src/}"
    set="${set%%/*}"
    case "$set" in
        debug | test | test[[:upper:]]* | androidTest[[:upper:]]* | *Test) return 0 ;;
    esac
    return 1
}
# moved_only <git diff arguments>: whether that diff of one file changes nothing, or only version
# lines exactly as bump-version.sh writes them, as many removed as added of each kind. A change with
# no lines (its mode alone) counts. A git failure is an error.
moved_only() {
    local quiet=0 lines
    g diff --quiet --no-ext-diff "$@" || quiet=$?
    case "$quiet" in 0) return 0 ;; 1) ;; *) error "git diff $* failed" ;; esac
    lines="$(g diff -U0 --no-renames --no-ext-diff --no-textconv --no-color "$@")" || error "git diff $* failed"
    awk '
        /^diff --git / { hunk = 0; next }
        /^@@/ { hunk = 1; next }
        !hunk || !/^[-+]/ { next }
        { any = 1; sign = substr($0, 1, 1); rest = substr($0, 2) }
        rest ~ /^[[:space:]]*versionCode = [0-9]+$/ { count[sign "code"]++; next }
        rest ~ /^[[:space:]]*versionName = "[^"]+"$/ { count[sign "name"]++; next }
        { other = 1 }
        END { exit !(any && !other && count["-code"] == count["+code"] && count["-name"] == count["+name"]) }
    ' <<< "$lines"
}
# counts <path>: whether a change to this watch path ships the watch.
counts() {
    case "$1" in "$WEAR_GRADLE" | "$FACE_GRADLE") ;; *) return 0 ;; esac
    local new
    new="$(g ls-files --others --exclude-standard -- "$1")" || error "git ls-files failed on $1"
    [ -z "$new" ] || return 0
    ! { moved_only "$REF" HEAD -- "$1" && moved_only --cached HEAD -- "$1" && moved_only HEAD -- "$1"; }
}
# changed <paths...>: every path under them that differs between the tag's commit and HEAD, between
# HEAD and the index, or between HEAD and the working tree, or is new and not ignored; one a line,
# sorted. It fails (status 1) when a git command does.
changed() {
    local committed staged unstaged new
    committed="$(g diff --name-only --no-renames --no-ext-diff "$REF" HEAD -- "$@")" || return 1
    staged="$(g diff --name-only --no-renames --no-ext-diff --cached HEAD -- "$@")" || return 1
    unstaged="$(g diff --name-only --no-renames --no-ext-diff HEAD -- "$@")" || return 1
    new="$(g ls-files --others --exclude-standard -- "$@")" || return 1
    printf '%s\n' "$committed" "$staged" "$unstaged" "$new" | { grep -v '^$' || true; } | sort -u
}
# catalog_text <commit|:index|worktree>: the catalog's text there, nothing when it has none; status 1
# when git fails.
catalog_text() {
    local listed
    case "$1" in
        worktree) if [ -f "$CATALOG" ]; then cat "$CATALOG"; fi ;;
        :index)
            listed="$(g ls-files --cached -- "$CATALOG")" || return 1
            if [ -n "$listed" ]; then g show ":$CATALOG" || return 1; fi ;;
        *)
            listed="$(g ls-tree --name-only "$1" -- "$CATALOG")" || return 1
            if [ -n "$listed" ]; then g show "$1:$CATALOG" || return 1; fi ;;
    esac
}
# catalog_entries: the catalog on stdin as "<section>\t<alias>\t<its text>" lines, every line of an
# entry joined in order, whichever TOML form spells it: "wear-tiles = { module = ..., version.ref =
# ... }", the dotted "wear-tiles.module = ..." and "wear-tiles.version.ref = ...", or a
# [libraries.wear-tiles] sub-table; keys may be quoted. What belongs to no entry is keyed "?", and
# the comment lines, all of them, are one entry "#\t#".
catalog_entries() {
    awk -v q="'" '
        function trim(s) { sub(/^[[:space:]]+/, "", s); sub(/[[:space:]]+$/, "", s); return s }
        # unquote(k): a TOML key with its quotes and the spaces around its dots taken out.
        function unquote(k,   out, c, i, n, open) {
            out = ""; open = ""; n = length(k)
            for (i = 1; i <= n; i++) {
                c = substr(k, i, 1)
                if (open != "") { if (c == open) open = ""; else out = out c; continue }
                if (c == "\"" || c == q) { open = c; continue }
                if (c ~ /[[:space:]]/) continue
                out = out c
            }
            return out
        }
        # alias(k): the alias a key spells, without the parts of an entry that a dotted key or a
        # sub-table spells out (module, group, name, id, version, version.ref, strictly and the like).
        function alias(k) {
            while (match(k, /\.(module|group|name|id|version|ref|strictly|require|prefer|reject|rejectAll)$/)) k = substr(k, 1, RSTART - 1)
            return k
        }
        function add(s, k, piece,   e) {
            e = s "\t" k
            if (!(e in text)) { order[++n] = e; text[e] = piece } else text[e] = text[e] " " piece
            cur = e
        }
        BEGIN {
            seg = "([A-Za-z0-9_-]+|\"[^\"]*\"|" q "[^" q "]*" q ")"
            keyline = "^[[:space:]]*" seg "([[:space:]]*\\.[[:space:]]*" seg ")*[[:space:]]*="
        }
        { sub(/\r$/, "") }
        /^[[:space:]]*\[/ {
            h = $0; sub(/^[[:space:]]*\[+/, "", h); sub(/\]+[[:space:]]*(#.*)?$/, "", h); h = unquote(h)
            i = index(h, ".")
            if (i) { section = substr(h, 1, i - 1); table = alias(substr(h, i + 1)) } else { section = h; table = "" }
            cur = ""
            if (table != "") add(section, table, "")
            next
        }
        /^[[:space:]]*#/ { comments = comments " " trim($0); next }
        /^[[:space:]]*$/ { next }
        match($0, keyline) {
            k = unquote(substr($0, RSTART, RLENGTH - 1)); value = trim(substr($0, RSTART + RLENGTH))
            if (table != "") add(section, table, k " = " value)
            else {
                a = alias(k)
                add(section, a, (a == k) ? value : substr(k, length(a) + 2) " = " value)
            }
            next
        }
        {
            t = trim($0)
            if (cur != "") text[cur] = text[cur] " " t; else orphans[section] = orphans[section] " " t
        }
        END {
            for (i = 1; i <= n; i++) { e = order[i]; t = text[e]; gsub(/\t/, " ", t); print e "\t" t }
            for (s in orphans) { t = orphans[s]; gsub(/\t/, " ", t); print s "\t?\t" t }
            if (comments != "") { gsub(/\t/, " ", comments); print "#\t#\t" comments }
        }
    '
}
# catalog_keys: "<kind>\t<alias>" for every catalog entry that differs between the tag's commit,
# HEAD, the index and the working tree, the kind one of watch, shared or aside (the header says how
# each is read from the build files), and "comments\t#" when its comments differ. Status 3 when a
# watch build file is missing, 1 when git fails.
catalog_keys() {
    local scratch state text build_files f need found
    scratch="$(mktemp -d "${TMPDIR:-/tmp}/watch-ships.XXXXXX")" || return 1
    for state in "$REF" HEAD :index worktree; do
        text="$(catalog_text "$state")" || { rm -rf "$scratch"; return 1; }
        catalog_entries <<< "$text" | awk -v s="$state" '{ print s "\t" $0 }'
    done > "$scratch/states"
    # The keys whose entry is not the same, or not there, in every one of the four.
    awk -F'\t' -v ref="$REF" '
        NF >= 3 { k = $2 "\t" $3; keys[k] = 1; there[k, $1] = 1; text[k, $1] = $4 }
        END {
            n = split(ref " HEAD :index worktree", states, " ")
            for (k in keys) {
                d = 0
                for (i = 2; i <= n; i++) {
                    s = states[i]
                    if (((k, s) in there) != ((k, ref) in there) || text[k, s] != text[k, ref]) d = 1
                }
                if (d) print k
            }
        }
    ' "$scratch/states" | sort > "$scratch/changed"
    # The entries the build files are read against: the working tree's catalog, as release.sh builds it.
    catalog_text worktree | catalog_entries > "$scratch/entries"
    build_files="$(g ls-files --cached --others --exclude-standard -- '*.gradle.kts' '*.gradle')" || { rm -rf "$scratch"; return 1; }
    local -a files=()
    while IFS= read -r f; do
        if [ -n "$f" ] && [ -f "$f" ]; then files+=("$f"); fi
    done <<< "$build_files"
    for need in "${WATCH_BUILD_FILES[@]}"; do
        found=false
        for f in ${files[@]+"${files[@]}"}; do
            if [ "$f" = "$need" ]; then found=true; fi
        done
        $found || { rm -rf "$scratch"; return 3; }
    done
    awk -v entries="$scratch/entries" -v changed="$scratch/changed" -v q="'" '
        function norm(k) { gsub(/[-_.]/, ".", k); return k }
        function accessor(s, k) {
            if (s == "libraries") return norm(k)
            if (s == "versions" || s == "plugins" || s == "bundles") return s "." norm(k)
            return ""
        }
        # strings(t, out): every quoted string in the text of a catalog entry, a key of out.
        function strings(t, out) {
            while (match(t, qre)) { out[substr(t, RSTART + 1, RLENGTH - 2)] = 1; t = substr(t, RSTART + RLENGTH) }
        }
        # refs(t, out): the versions an entry refers to (version.ref = "x", version = { ref = "x" }).
        function refs(t, out,   m) {
            while (match(t, refre)) {
                m = substr(t, RSTART, RLENGTH); sub(/^[^=]*=[[:space:]]*/, "", m)
                out[substr(m, 2, length(m) - 2)] = 1; t = substr(t, RSTART + RLENGTH)
            }
        }
        # A test or debug configuration call: testImplementation, debugApi, androidTestRuntimeOnly...
        function testcall(n) { return n ~ /^(test|androidTest|debug)[A-Za-z0-9]*(Implementation|Api|CompileOnly|RuntimeOnly)$/ }
        # A test or debug block: a test source set (commonTest, androidHostTest, testDebug, test), the
        # debug build type, a Test task (tasks.withType<Test>()).
        function testblock(h) { return h ~ /(^|[^A-Za-z0-9_])([a-z][A-Za-z0-9]*Test|Test|test|test[A-Z][A-Za-z0-9]*|androidTest[A-Za-z0-9]*|debug|debug[A-Z][A-Za-z0-9]*)([^A-Za-z0-9_]|$)/ }
        # use(a): a use of the entry a, here.
        function use(a) {
            if (watchfile) { if (!pt[pd]) watchrelease[a] = 1 }
            else if (!pt[pd] && !bt[bd]) elsewhere[a] = 1
        }
        # reference(tok): libs.<tok>, as the longest alias it begins with.
        function reference(tok) {
            while (tok != "" && !(tok in known)) { if (!sub(/\.[^.]*$/, "", tok)) tok = "" }
            if (tok != "") use(tok)
        }
        # named(str): a string in a watch build file that names an entry (findLibrary("wear-tiles")).
        function named(str,   i, m, parts) {
            if (!watchfile || !((str = norm(str)) in byname)) return
            m = split(byname[str], parts, SUBSEP)
            for (i = 1; i <= m; i++) if (parts[i] != "") use(parts[i])
        }
        # libs_at(s, i): the length of a libs.<x>... reference starting at i, or 0.
        function libs_at(s, i) {
            if (i > 1 && substr(s, i - 1, 1) ~ /[A-Za-z0-9_.]/) return 0
            if (!match(substr(s, i), /^libs(\.[A-Za-z0-9_]+)+/)) return 0
            return RLENGTH
        }
        # scan(s): one line of a build file, its comments, strings, calls and blocks followed across
        # lines (block comments nest, as in Kotlin; raw strings span lines).
        function scan(s,   i, n, c, c2, len, word) {
            n = length(s); i = 1
            if (pd == 0) head = ""
            while (i <= n) {
                c = substr(s, i, 1); c2 = substr(s, i, 2)
                if (depth > 0) {
                    if (c2 == "/*") { depth++; i += 2 } else if (c2 == "*/") { depth--; i += 2 } else i++
                    continue
                }
                if (str != "") {
                    if (str == "\"" && c == "\\") { buf = buf substr(s, i, 2); i += 2; continue }
                    if ((str == "\"" && c == "\"") || (str == "\"\"\"" && substr(s, i, 3) == "\"\"\"")) {
                        i += length(str); str = ""; named(buf); last = buf; head = head "\"" buf "\""; continue
                    }
                    if (c == "l" && (len = libs_at(s, i))) { reference(substr(s, i + 5, len - 5)); buf = buf substr(s, i, len); i += len; continue }
                    buf = buf c; i++; continue
                }
                if (c2 == "//") break
                if (c2 == "/*") { depth = 1; i += 2; continue }
                if (substr(s, i, 3) == "\"\"\"") { str = "\"\"\""; buf = ""; i += 3; continue }
                if (c == "\"") { str = "\""; buf = ""; i++; continue }
                if (c == q) { if (match(substr(s, i), charre)) i += RLENGTH; else i++; last = ""; continue }
                if (c ~ /[A-Za-z_]/) {
                    if (c == "l" && (len = libs_at(s, i))) { reference(substr(s, i + 5, len - 5)); head = head substr(s, i, len); i += len; last = ""; continue }
                    match(substr(s, i), /^[A-Za-z0-9_]+/); word = substr(s, i, RLENGTH)
                    head = head word; i += RLENGTH; last = word; continue
                }
                if (c == "(") { pd++; pt[pd] = pt[pd - 1] || testcall(last); last = ""; head = head c; i++; continue }
                if (c == ")") { if (pd > 0) pd--; last = ""; head = head c; i++; continue }
                if (c == "{") { bd++; bt[bd] = bt[bd - 1] || testblock(head); head = ""; last = ""; i++; continue }
                if (c == "}") { if (bd > 0) bd--; head = ""; last = ""; i++; continue }
                if (c == ";") { head = ""; last = ""; i++; continue }
                if (c !~ /[[:space:]]/) last = ""
                head = head c; i++
            }
            if (str == "\"") str = ""
            if (str == "\"\"\"") buf = buf "\n"
        }
        BEGIN {
            qre = "\"[^\"]*\"|" q "[^" q "]*" q
            refre = "(^|[^A-Za-z0-9_-])ref[[:space:]]*=[[:space:]]*(\"[^\"]*\"|" q "[^" q "]*" q ")"
            charre = "^" q "(\\\\.|[^" q "\\\\])" q
            while ((getline line < entries) > 0) {
                if (split(line, f, "\t") < 3 || f[2] == "?") continue
                a = accessor(f[1], f[2])
                if (a == "") continue
                known[a] = 1; sec[a] = f[1]; text[a] = f[3]
                byname[norm(f[2])] = byname[norm(f[2])] SUBSEP a
            }
            close(entries)
            pt[0] = 0; bt[0] = 0
        }
        FNR == 1 { depth = 0; pd = 0; bd = 0; str = ""; head = ""; last = ""; watchfile = (FILENAME ~ /^(wearApp|watchcore|watchface)\//) }
        { scan($0) }
        END {
            while ((getline line < changed) > 0) {
                split(line, f, "\t"); s = f[1]; k = f[2]
                if (s == "#") { print "comments\t#"; continue }
                a = accessor(s, k)
                # What cannot be told is kept on the note.
                if (k == "?" || a == "") { print "shared\t" k; continue }
                split("", group); group[a] = 1
                if (s == "versions")
                    for (b in known) if (sec[b] == "libraries" || sec[b] == "plugins") {
                        split("", r); refs(text[b], r)
                        if (k in r) group[b] = 1
                    }
                for (b in known) if (sec[b] == "bundles") {
                    split("", m); strings(text[b], m)
                    for (x in m) if (norm(x) in group) group[b] = 1
                }
                w = 0; o = 0
                for (b in group) { if (b in watchrelease) w = 1; if (b in elsewhere) o = 1 }
                print (w ? (o ? "shared" : "watch") : "aside") "\t" k
            }
        }
    ' ${files[@]+"${files[@]}"}
    rm -rf "$scratch"
}

if [ -z "$TAG" ]; then
    why="no watch release is tagged (no wear-build-<N>)"
    if [ "$SHALLOW" = true ]; then why="no watch release is tagged (no wear-build-<N>; this clone is shallow, and its tags may be unfetched: git fetch --unshallow --tags)"; fi
    case "$FORCED" in
        1) echo "ships: forced by TAQWA_RELEASE_WATCH=1 ($why, so it would ship anyway)"; exit 0 ;;
        0) echo "stays: forced by TAQWA_RELEASE_WATCH=0, over \"$why\": Play's Wear OS track keeps serving the last watch build it took, which no wear-build tag names"; exit 1 ;;
        *) echo "ships: $why, so the watch goes to Play's Wear OS track"; exit 0 ;;
    esac
fi

watch_list="$(changed "${WATCH[@]}")" || error "git could not compare the watch's modules with $TAG ($SHORT)"
watch_changes=""
versions_moved=false
not_carried=0
while IFS= read -r path; do
    [ -n "$path" ] || continue
    if never_shipped "$path"; then not_carried=$((not_carried + 1))
    elif counts "$path"; then watch_changes+="$path"$'\n'
    else versions_moved=true
    fi
done <<< "$watch_list"

shared_changes="$(changed "${SHARED[@]}")" || error "git could not compare what the watch is built with against $TAG ($SHORT)"
# The catalog by its entries: the watch's ship it, the shared stay on the note, the rest aside.
aside=""
target_sdk=false
if grep -qxF "$CATALOG" <<< "$shared_changes"; then
    keys_status=0
    keys="$(catalog_keys)" || keys_status=$?
    case "$keys_status" in
        0) ;;
        3) error "the watch's build files (${WATCH_BUILD_FILES[*]}) are not all in this checkout, so which of $CATALOG's entries the watch builds with cannot be read" ;;
        *) error "$CATALOG's entries could not be read at $TAG ($SHORT), HEAD, the index or the working tree" ;;
    esac
    # kinds <kind>: the aliases of that kind, sorted, joined with ", ".
    kinds() { awk -F'\t' -v kind="$1" '$1 == kind && $2 != "?" { print $2 }' <<< "$keys" | sort -u | awk 'NR > 1 { printf ", " } { printf "%s", $0 }'; }
    watch_keys="$(kinds watch)"
    shared_keys="$(kinds shared)"
    aside_keys="$(kinds aside)"
    shared_unknown="$(awk -F'\t' '$1 == "shared" && $2 == "?"' <<< "$keys")"
    comments="$(awk -F'\t' '$1 == "comments"' <<< "$keys")"
    shared_changes="$(grep -vxF "$CATALOG" <<< "$shared_changes" || true)"
    if [ -n "$watch_keys" ]; then watch_changes+="$CATALOG ($watch_keys)"$'\n'; fi
    if [ -n "$shared_keys" ]; then
        shared_changes+=$'\n'"$CATALOG ($shared_keys)"
        case ", $shared_keys, " in *", android-targetSdk, "*) target_sdk=true ;; esac
    elif [ -n "$shared_unknown" ]; then
        # A line no entry holds: what it changes cannot be told.
        shared_changes+=$'\n'"$CATALOG"
    fi
    # Nothing else changed in it: its comments, or its spacing.
    if [ -z "$watch_keys$shared_keys$aside_keys$shared_unknown" ]; then comments=yes; fi
    aside="$aside_keys"
    if [ -n "$comments" ]; then aside="${aside:+$aside, }its comments or spacing"; fi
    shared_changes="$(grep -v '^$' <<< "$shared_changes" | sort || true)"
fi
watch_changes="$(grep -v '^$' <<< "$watch_changes" | sort || true)"

SINCE="since $TAG ($SHORT)"
if [ -n "$watch_changes" ]; then
    what="the watch has changed $SINCE, $(described "$watch_changes")"
else
    what="the watch has not changed $SINCE"
    not_carried_words="$(paths "$not_carried") its release build never carries (tests, debug code, vectors)"
    if $versions_moved && [ "$not_carried" -gt 0 ]; then
        what+=" but for its version lines and $not_carried_words"
    elif $versions_moved; then
        what+=" but for its version lines"
    elif [ "$not_carried" -gt 0 ]; then
        what+=" but for $not_carried_words"
    fi
fi
case "$FORCED" in
    1) echo "ships: forced by TAQWA_RELEASE_WATCH=1; $what"; status=0 ;;
    0)
        if [ -n "$watch_changes" ]; then
            echo "stays: forced by TAQWA_RELEASE_WATCH=0, over its changes $SINCE, $(described "$watch_changes"); Play's Wear OS track keeps serving $SERVED"
        else
            echo "stays: forced by TAQWA_RELEASE_WATCH=0; $what, so Play's Wear OS track keeps serving $SERVED"
        fi
        status=1 ;;
    *)
        if [ -n "$watch_changes" ]; then
            echo "ships: $what"; status=0
        else
            echo "stays: $what, so Play's Wear OS track keeps serving $SERVED"; status=1
        fi ;;
esac
if [ -n "$shared_changes" ]; then
    if [ "$status" -eq 0 ]; then
        carried="and the Wear AAB this release ships is built with it"
    else
        carried="and the Wear AAB Play keeps serving, $SERVED, was built without it (TAQWA_RELEASE_WATCH=1 ships it)"
    fi
    sdk=""
    if $target_sdk; then sdk="; android-targetSdk is also the target SDK in the Wear app's and the face's manifests"; fi
    echo "note: what the watch is built with beside its own modules has changed $SINCE, $(described "$shared_changes"); it does not ship the watch on its own, $carried$sdk"
fi
if [ -n "$aside" ]; then
    echo "aside: $CATALOG has changed $SINCE where the watch's release build does not read it ($aside), which is weighed for nothing"
fi
exit "$status"

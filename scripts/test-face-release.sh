#!/usr/bin/env bash
# Tests the face's release tools (plan 5 Task 2) with keys made in a scratch directory and the
# throwaway test key (~/.android/taqwa-face-debug.keystore) only. It never makes, reads or uses
# Mohamed's face key, and never writes to ~/keys: where a default would point there, it runs under
# a scratch HOME.
#   - make-face-key.sh makes a key in a scratch directory and prints its certificate's SHA-256, the
#     digest apksigner reads from what the key signs, with its two warnings; it never overwrites a
#     key, and a password from the environment never makes the key in ~/keys;
#   - release-face.sh signs the release face with the test key and holds it to the pin; a face
#     signed by another key than the pin's is deleted; the password piped on stdin signs (the scratch
#     key, pinned to what make-face-key.sh printed) with no prompt printed; on a pseudo-terminal, as
#     Mohamed runs it, the password is asked for once, never echoed, and nothing asks for it again
#     once it is typed (a prompt left on the screen sends what is typed at it to the shell and its
#     history), and a terminal whose output is redirected, where apksigner would echo it, is
#     refused; a password from the environment is refused for a key in ~/keys, and with no pin
#     nothing is signed;
#   - push-token.sh writes the token of the signed face and nothing prints it; an unsigned face gets
#     no token, and a token file left from before is emptied;
#   - face-art.py --check compares decoded pixels, mode and size: it passes on the committed
#     pictures, whose bytes this Mac's PNG encoder writes differently, fails on one changed pixel, a
#     changed mode, a changed size or a missing picture, and writes nothing; an unknown argument
#     (--help among them) exits 2 and rewrites nothing; a plain run leaves a picture whose pixels
#     have not changed byte for byte as it was;
#   - get-system-fonts.sh --check refuses a missing or changed font without a device, and passes on
#     the pinned one; face-gate.sh refuses to start without the pinned fonts, and a stale picture
#     (one pixel of arch_mark.png, in a scratch copy of the working tree, never in the repository)
#     fails it, every other check still run, leaving the repository's built faces as they were.
# It builds :watchface's release with Gradle in the repository (release-face.sh) and both faces in
# that scratch copy (the gate), and fetches Google's pinned Push validator into
# ~/.cache/taqwa-watchface-tools once. It refuses to start (exit 2) while build/face-release/ holds
# an APK, which may be a face Mohamed signed, and removes the one it signs itself when it ends.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

TEST_KEY="$HOME/.android/taqwa-face-debug.keystore"
OUT_DIR=build/face-release
SIGNED="$OUT_DIR/watchface-release-signed.apk"
UNSIGNED=watchface/build/outputs/apk/release/watchface-release-unsigned.apk
DRAWABLE=watchface/src/main/res/drawable
FONT_CACHE="$HOME/.cache/taqwa-watchface-tools/fonts"

if compgen -G "$OUT_DIR/*.apk" > /dev/null; then
  echo "$OUT_DIR holds an APK, which may be a face signed for a release: move it away first" >&2
  exit 2
fi
if [ ! -f "$TEST_KEY" ]; then
  echo "the test key $TEST_KEY is missing: run scripts/face-debug-key.sh" >&2
  exit 2
fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-face-release-test.XXXXXX")
cleanup() { rm -rf "$WORK" "$OUT_DIR"; }
trap cleanup EXIT

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }
indent() { sed 's/^/    /'; }
no_apk_in_out_dir() { ! compgen -G "$OUT_DIR/*.apk" > /dev/null; }

STATUS_BEFORE=$(git status --porcelain)

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
[ -n "$APKSIGNER" ] || { echo "no apksigner under $SDK/build-tools" >&2; exit 2; }
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

# A certificate's SHA-256 as apksigner reads it from a signed APK, one line a distinct signer.
cert_of_apk() {
  "$APKSIGNER" verify --print-certs "$1" 2>/dev/null \
    | sed -n 's/.*certificate SHA-256 digest: \([0-9a-f]\{64\}\)$/\1/p' | sort -u
}
# A key's certificate's SHA-256 as keytool exports it (DER): keystore, alias, the password's variable.
cert_of_key() {
  "$KEYTOOL" -exportcert -keystore "$1" -alias "$2" -storepass:env "$3" 2>/dev/null \
    | shasum -a 256 | cut -d' ' -f1
}
# How many lines of a transcript end in a password prompt ("…password…:").
prompts() { tr '\r' '\n' < "$1" | grep -ciE 'password[^:]*:[[:space:]]*$' || true; }
# on_a_terminal <prefix> <tty|pipe> <password variable> <command...>: runs the command as from
# Mohamed's Terminal: a pseudo-terminal is its controlling terminal, stdin and stderr, and its stdout
# too, unless "pipe" sends stdout down a pipe. Once a line ending in a password prompt shows, on the
# terminal or down the pipe, it types the variable's password and Return, once, as a person would;
# with no prompt it types nothing. What the terminal showed before and after the typing goes to
# <prefix>.before and <prefix>.after, and the pipe's to <prefix>.out. It exits as the command did
# (124 if the command ran past 10 minutes, and is killed).
on_a_terminal() {
  python3 -I -c '
import fcntl, os, pty, re, select, subprocess, sys, termios, time
prefix, mode, var, argv = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
password = os.environ[var].encode()
master, slave = pty.openpty()
r_out, w_out = os.pipe()
def controlling_terminal():
    os.setsid()
    fcntl.ioctl(0, termios.TIOCSCTTY, 0)
p = subprocess.Popen(argv, stdin=slave, stdout=slave if mode == "tty" else w_out, stderr=slave,
                     preexec_fn=controlling_terminal)
os.close(slave); os.close(w_out)
prompt = re.compile(rb"(?i)password[^\r\n:]*:[ \t]*$")
before, after, out = bytearray(), bytearray(), bytearray()
typed, fds, deadline, drain_until = False, [master, r_out], time.time() + 600, None
while True:
    ready = select.select(fds, [], [], 0.5)[0] if fds else (time.sleep(0.5) or [])
    for f in ready:
        try:
            data = os.read(f, 4096)
        except OSError:
            data = b""
        if not data:
            fds.remove(f)
        elif f == r_out:
            out.extend(data)
        else:
            (after if typed else before).extend(data)
    if not typed and (prompt.search(bytes(before)) or prompt.search(bytes(out))):
        time.sleep(0.3)
        os.write(master, password + b"\n")
        typed = True
    if p.poll() is not None and not ready:
        drain_until = drain_until or time.time() + 2
        if time.time() > drain_until:
            break
    if time.time() > deadline:
        p.kill(); p.wait()
        break
for suffix, data in (("before", before), ("after", after), ("out", out)):
    open(prefix + "." + suffix, "wb").write(bytes(data))
code = 124 if time.time() > deadline else p.returncode
sys.exit(code if code >= 0 else 128 - code)
' "$@"
}
# The (name, CRC-32) of every entry outside META-INF/, one a line, sorted.
entries() {
  python3 -I -c '
import sys, zipfile
for i in sorted(zipfile.ZipFile(sys.argv[1]).infolist(), key=lambda i: i.filename):
    if not i.filename.startswith("META-INF/"):
        print(i.filename, "%08x" % i.CRC)
' "$1"
}

export TAQWA_TEST_FACE_PW=android     # the test key's well-known password (scripts/face-debug-key.sh)
TEST_CERT=$(cert_of_key "$TEST_KEY" taqwaface TAQWA_TEST_FACE_PW)
printf '%s\n' "$TEST_CERT" > "$WORK/test-cert.sha256"
SCRATCH_PW="scratch-$(python3 -I -c 'import secrets; print(secrets.token_hex(12))')"
export TAQWA_TEST_SCRATCH_PW="$SCRATCH_PW"

# ---- aScratchFaceKeyIsMadeAndNeverOverwritten
KEYS="$WORK/keys"
status=0
out=$(TAQWA_FACE_PASSWORD="$SCRATCH_PW" bash scripts/make-face-key.sh "$KEYS" 2>&1) || status=$?
SCRATCH_KEY="$KEYS/taqwa-face.jks"
if [ "$status" -eq 0 ] && [ -f "$SCRATCH_KEY" ]; then
  pass "make-face-key.sh makes $(basename "$SCRATCH_KEY") in a scratch directory"
else
  fail "make-face-key.sh <scratch> exits $status and makes no taqwa-face.jks:"; echo "$out" | indent
fi
printed=$(printf '%s\n' "$out" | grep -oE '(^|[^0-9a-f])[0-9a-f]{64}($|[^0-9a-f])' | grep -oE '[0-9a-f]{64}' | sort -u || true)
SCRATCH_CERT=""
if [ -f "$SCRATCH_KEY" ]; then SCRATCH_CERT=$(cert_of_key "$SCRATCH_KEY" taqwa-face TAQWA_TEST_SCRATCH_PW); fi
if [ "$(printf '%s\n' "$printed" | grep -c .)" -eq 1 ] && [ "$printed" = "$SCRATCH_CERT" ]; then
  pass "it prints one 64-hex SHA-256, the certificate's"
else
  fail "it prints '${printed:-no SHA-256}', not the one certificate SHA-256 ${SCRATCH_CERT:-(no key)}"
fi
if [ -f "$SCRATCH_KEY" ]; then
  listing=$("$KEYTOOL" -list -v -keystore "$SCRATCH_KEY" -storepass:env TAQWA_TEST_SCRATCH_PW 2>/dev/null || true)
  days=$("$KEYTOOL" -exportcert -rfc -keystore "$SCRATCH_KEY" -alias taqwa-face -storepass:env TAQWA_TEST_SCRATCH_PW 2>/dev/null \
    | openssl x509 -noout -startdate -enddate 2>/dev/null | python3 -I -c '
import sys, datetime
d = dict(line.strip().split("=", 1) for line in sys.stdin if "=" in line)
f = lambda s: datetime.datetime.strptime(s.replace("  ", " "), "%b %d %H:%M:%S %Y %Z")
print((f(d["notAfter"]) - f(d["notBefore"])).days)' 2>/dev/null || true)
  grep -q '^Alias name: taqwa-face$' <<< "$listing" \
    && grep -q 'Owner: CN=Taqwa watch face, O=Mohamed Abulgasem' <<< "$listing" \
    && grep -q '4096-bit RSA' <<< "$listing" && [ "$days" = 10000 ] \
    && pass "the key is alias taqwa-face, RSA 4096, CN=Taqwa watch face, O=Mohamed Abulgasem, valid 10000 days" \
    || fail "the key is not alias taqwa-face, RSA 4096, the face's name, 10000 days (validity ${days:-unread}):"$'\n'"$(grep -E 'Alias name|Owner|RSA' <<< "$listing" | indent)"
fi
grep -qi 'back up' <<< "$out" && grep -qi 'two places' <<< "$out" \
  && pass "it says to back up the key and its password in two places" \
  || fail "it does not say to back up the key and its password in two places"
grep -qi 'nobody can reset' <<< "$out" && grep -qi 'style, colour and slots' <<< "$out" \
  && pass "it says nobody can reset a lost face key, and what a new one costs every wearer" \
  || fail "it does not warn that a lost face key cannot be reset, losing each wearer's style, colour and slots"
grep -qF "$SCRATCH_PW" <<< "$out" && fail "it prints the password" || pass "it never prints the password"
left=$(ls -A "$KEYS" 2>/dev/null | tr '\n' ' ' || true)
[ "$left" = "taqwa-face.jks " ] && pass "it writes the key and nothing else (the password goes nowhere)" \
  || fail "the key's directory holds '$left', not taqwa-face.jks alone"
if [ -f "$SCRATCH_KEY" ]; then
  before=$(shasum -a 256 < "$SCRATCH_KEY")
  status=0
  out2=$(TAQWA_FACE_PASSWORD="$SCRATCH_PW" bash scripts/make-face-key.sh "$KEYS" 2>&1) || status=$?
  [ "$status" -eq 1 ] && [ "$(shasum -a 256 < "$SCRATCH_KEY")" = "$before" ] \
    && pass "a second run exits 1 and leaves the key as it was" \
    || { fail "a second run exits $status, or changes the key:"; echo "$out2" | indent; }
fi

# ---- aPasswordFromTheEnvironmentNeverMakesTheRealKey (under a scratch HOME, so ~/keys is scratch)
FAKE_HOME="$WORK/home"
mkdir -p "$FAKE_HOME"
status=0
out=$(HOME="$FAKE_HOME" TAQWA_FACE_PASSWORD="$SCRATCH_PW" bash scripts/make-face-key.sh 2>&1) || status=$?
[ "$status" -eq 1 ] && [ ! -e "$FAKE_HOME/keys/taqwa-face.jks" ] \
  && pass "TAQWA_FACE_PASSWORD never makes the key in ~/keys (exit 1, nothing written)" \
  || { fail "TAQWA_FACE_PASSWORD with the default directory exits $status and leaves $(ls -A "$FAKE_HOME/keys" 2>/dev/null | tr '\n' ' '):"; echo "$out" | indent; }
# ~/keys named outright, before it exists, and a directory inside it
for dir in "$FAKE_HOME/keys" "$FAKE_HOME/keys/spare"; do
  status=0
  out=$(HOME="$FAKE_HOME" TAQWA_FACE_PASSWORD="$SCRATCH_PW" bash scripts/make-face-key.sh "$dir" 2>&1) || status=$?
  [ "$status" -eq 1 ] && [ ! -e "$FAKE_HOME/keys" ] \
    && pass "TAQWA_FACE_PASSWORD never makes a key in ${dir#"$FAKE_HOME"/} under ~ (exit 1, nothing written)" \
    || { fail "TAQWA_FACE_PASSWORD with $dir exits $status and leaves $(find "$FAKE_HOME/keys" 2>/dev/null | tr '\n' ' '):"; echo "$out" | indent; }
  rm -rf "$FAKE_HOME/keys"
done

# ---- aMissingPinSignsNothing
status=0
out=$(TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
  TAQWA_FACE_CERT_FILE="$WORK/no-such-pin.sha256" bash scripts/release-face.sh 2>&1) || status=$?
[ "$status" -eq 1 ] && no_apk_in_out_dir \
  && pass "with no pin, release-face.sh exits 1 and signs nothing" \
  || { fail "with no pin, release-face.sh exits $status or leaves an APK:"; echo "$out" | indent; }

# ---- thePasswordEnvironmentIsForATestKeyAlone (a key in ~/keys, under a scratch HOME)
mkdir -p "$FAKE_HOME/keys/old"
cp "$TEST_KEY" "$FAKE_HOME/keys/old/taqwa-face.jks"
for ks in "" "$FAKE_HOME/keys/taqwa-face.jks" "$FAKE_HOME/keys/old/taqwa-face.jks"; do
  status=0
  out=$(HOME="$FAKE_HOME" TAQWA_FACE_KS="$ks" TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
    TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" bash scripts/release-face.sh 2>&1) || status=$?
  [ "$status" -eq 1 ] && no_apk_in_out_dir && grep -q TAQWA_FACE_KS_PASS_ENV <<< "$out" \
    && pass "TAQWA_FACE_KS_PASS_ENV is refused for ${ks:-the default key} (a key in ~/keys)" \
    || { fail "TAQWA_FACE_KS_PASS_ENV for ${ks:-the default key} exits $status:"; echo "$out" | indent; }
done

# ---- theTestKeySignsTheReleaseFace
status=0
out=$(TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
  TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" bash scripts/release-face.sh 2>&1) || status=$?
if [ "$status" -eq 0 ] && [ -f "$SIGNED" ]; then
  pass "release-face.sh signs $SIGNED with the test key"
else
  fail "release-face.sh with the test key exits $status, and makes ${SIGNED} $( [ -f "$SIGNED" ] && echo yes || echo no):"; echo "$out" | indent
fi
printed_out="$out"
if [ -f "$SIGNED" ]; then
  "$APKSIGNER" verify "$SIGNED" > /dev/null 2>&1 && [ "$(cert_of_apk "$SIGNED")" = "$TEST_CERT" ] \
    && pass "the signed face verifies, signed by the test key alone" \
    || fail "the signed face does not verify as the test key's: $(cert_of_apk "$SIGNED" | tr '\n' ' ')"
  [ -f "$UNSIGNED" ] && [ "$(entries "$SIGNED")" = "$(entries "$UNSIGNED")" ] \
    && pass "the signed face holds the release build's entries, CRC for CRC" \
    || fail "the signed face's entries differ from $UNSIGNED's"
  status=0
  tok_out=$(bash tools/watchface/push-token.sh "$SIGNED" "$WORK/token" 2>&1) || status=$?
  token=$(cat "$WORK/token" 2>/dev/null || true)
  if [ "$status" -eq 0 ] && [ -n "$token" ] && [ "$(wc -l < "$WORK/token" | tr -d ' ')" = 0 ] \
    && grep -qE '^[A-Za-z0-9+/=:_-]+$' <<< "$token"; then
    pass "push-token.sh writes the signed face's token alone, with no newline"
  else
    fail "push-token.sh on the signed face exits $status, with a token file of $(wc -c < "$WORK/token" 2>/dev/null || echo no) bytes:"
    echo "$tok_out" | indent
  fi
  if [ -n "$token" ] && grep -qF -- "$token" <<< "$printed_out$tok_out"; then
    fail "the token is printed"
  else
    pass "nothing printed contains the token"
  fi
fi

# ---- aFaceSignedByAnotherKeyThanThePinIsDeleted
printf '%064d\n' 0 > "$WORK/zeros.sha256"
status=0
out=$(TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_FACE_PW \
  TAQWA_FACE_CERT_FILE="$WORK/zeros.sha256" bash scripts/release-face.sh 2>&1) || status=$?
[ "$status" -eq 1 ] && no_apk_in_out_dir \
  && pass "signed by a key other than the pin's, release-face.sh exits 1 and $OUT_DIR holds no APK" \
  || { fail "with a pin of zeros, release-face.sh exits $status, leaving $(ls -A "$OUT_DIR" 2>/dev/null | tr '\n' ' '):"; echo "$out" | indent; }

# ---- aWrongPasswordSignsNothing
export TAQWA_TEST_WRONG_PW=not-the-password
status=0
out=$(TAQWA_FACE_KS="$TEST_KEY" TAQWA_FACE_ALIAS=taqwaface TAQWA_FACE_KS_PASS_ENV=TAQWA_TEST_WRONG_PW \
  TAQWA_FACE_CERT_FILE="$WORK/test-cert.sha256" bash scripts/release-face.sh 2>&1) || status=$?
[ "$status" -eq 1 ] && no_apk_in_out_dir \
  && pass "with a wrong password, release-face.sh exits 1 and $OUT_DIR holds no APK" \
  || { fail "with a wrong password, release-face.sh exits $status, leaving $(ls -A "$OUT_DIR" 2>/dev/null | tr '\n' ' '):"; echo "$out" | indent; }

# ---- thePasswordTypedOnStdinSigns (the scratch key, pinned to what make-face-key.sh printed)
if [ -f "$SCRATCH_KEY" ] && [ -n "$SCRATCH_CERT" ]; then
  printf '%s\n' "${printed:-}" > "$WORK/scratch-cert.sha256"
  status=0
  out=$(printf '%s\n' "$SCRATCH_PW" | TAQWA_FACE_KS="$SCRATCH_KEY" TAQWA_FACE_CERT_FILE="$WORK/scratch-cert.sha256" \
    bash scripts/release-face.sh 2>&1) || status=$?
  [ "$status" -eq 0 ] && [ "$(cert_of_apk "$SIGNED" 2>/dev/null)" = "$SCRATCH_CERT" ] \
    && pass "the password on stdin signs with a scratch key (alias taqwa-face), to the SHA-256 make-face-key.sh printed" \
    || { fail "the password on stdin, scratch key, exits $status:"; echo "$out" | indent; }
  grep -qF "$SCRATCH_PW" <<< "$out" && fail "release-face.sh prints the password" || pass "release-face.sh never prints the password"
  [ "$(prompts <(printf '%s\n' "$out"))" -eq 0 ] \
    && pass "a piped password is read with no prompt printed" \
    || { fail "a piped password still prints a password prompt nobody answers:"; echo "$out" | indent; }

  # ---- aTerminalWhoseOutputIsRedirectedIsRefused (apksigner would prompt down the pipe, and echo)
  rm -rf "$OUT_DIR"
  status=0
  on_a_terminal "$WORK/redirected" pipe TAQWA_TEST_SCRATCH_PW env TAQWA_FACE_KS="$SCRATCH_KEY" \
    TAQWA_FACE_CERT_FILE="$WORK/scratch-cert.sha256" bash scripts/release-face.sh || status=$?
  [ "$status" -eq 1 ] && no_apk_in_out_dir && grep -qi 'terminal' "$WORK/redirected.before" \
    && pass "on a terminal whose output is redirected, release-face.sh refuses (exit 1), saying why, and signs nothing" \
    || { fail "on a terminal whose output is redirected, release-face.sh exits $status:"; cat "$WORK/redirected".{before,after,out} | indent; }
  cat "$WORK/redirected".{before,after,out} | grep -qF "$SCRATCH_PW" \
    && fail "on a terminal whose output is redirected, the password is echoed" \
    || pass "on a terminal whose output is redirected, the password is never echoed"

  # ---- thePasswordTypedOnATerminalIsAskedForOnceAndNeverEchoed (as Mohamed runs it)
  status=0
  on_a_terminal "$WORK/terminal" tty TAQWA_TEST_SCRATCH_PW env TAQWA_FACE_KS="$SCRATCH_KEY" \
    TAQWA_FACE_CERT_FILE="$WORK/scratch-cert.sha256" bash scripts/release-face.sh || status=$?
  [ "$status" -eq 0 ] && [ "$(cert_of_apk "$SIGNED" 2>/dev/null)" = "$SCRATCH_CERT" ] \
    && pass "the password typed on a terminal signs with the scratch key, to the pin" \
    || { fail "the password typed on a terminal exits $status:"; cat "$WORK/terminal".{before,after} | indent; }
  [ "$(prompts "$WORK/terminal.before")" -eq 1 ] && [ "$(prompts "$WORK/terminal.after")" -eq 0 ] \
    && pass "on a terminal the password is asked for once, and nothing asks for it again once it is typed" \
    || { fail "on a terminal: $(prompts "$WORK/terminal.before") password prompts before the typing, $(prompts "$WORK/terminal.after") after (what is typed at one left on the screen goes to the shell):"; cat "$WORK/terminal".{before,after} | indent; }
  cat "$WORK/terminal".{before,after} | grep -qF "$SCRATCH_PW" \
    && fail "on a terminal the password is echoed" \
    || pass "on a terminal the password is never echoed"
fi
rm -rf "$OUT_DIR"

# ---- theUnsignedFaceHasNoToken
printf 'a token from before\n' > "$WORK/token2"
status=0
out=$(bash tools/watchface/push-token.sh "$ROOT/$UNSIGNED" "$WORK/token2" 2>&1) || status=$?
[ "$status" -eq 1 ] && [ -f "$WORK/token2" ] && [ ! -s "$WORK/token2" ] \
  && pass "push-token.sh on the unsigned face exits 1, with an empty token file" \
  || { fail "push-token.sh on the unsigned face exits $status, with $(wc -c < "$WORK/token2" 2>/dev/null || echo no) bytes of token:"; echo "$out" | indent; }
status=0
bash tools/watchface/push-token.sh > /dev/null 2>&1 || status=$?
[ "$status" -eq 2 ] && pass "push-token.sh with no arguments exits 2" || fail "push-token.sh with no arguments exits $status, not 2"

# ---- faceArtCheckSeesAChangedPicture (a scratch copy: the working tree's face-art.py, the committed pictures)
ART="$WORK/art"
fresh_art() {
  rm -rf "$ART"; mkdir -p "$ART/tools"
  cp tools/face-art.py "$ART/tools/"
  git archive HEAD "$DRAWABLE" | tar -x -C "$ART"
}
art() { (cd "$ART" && python3 -I tools/face-art.py "$@"); }
sums() { (cd "$ART/$DRAWABLE" && shasum -a 256 *.png); }
# pixel_edit <file> <python expression on im, an RGBA image, giving the picture to save>
pixel_edit() {
  python3 -I -c '
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGBA")
im = eval(sys.argv[2])
im.save(sys.argv[1])' "$ART/$DRAWABLE/$1" "$2"
}
fresh_art
status=0
out=$(art --check 2>&1) || status=$?
[ "$status" -eq 0 ] && pass "face-art.py --check passes on the committed pictures" \
  || { fail "face-art.py --check exits $status on the committed pictures:"; echo "$out" | indent; }
before=$(sums)
python3 -I -c '
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGBA")
r, g, b, a = im.getpixel((24, 30))
im.putpixel((24, 30), (r, g, b, (a + 128) % 256))
im.save(sys.argv[1])' "$ART/$DRAWABLE/arch_mark.png"
changed=$(sums)
status=0
out=$(art --check 2>&1) || status=$?
[ "$status" -eq 1 ] && grep -q arch_mark.png <<< "$out" && [ "$(sums)" = "$changed" ] \
  && pass "one changed pixel of arch_mark.png makes --check exit 1, naming it, and nothing is written" \
  || { fail "one changed pixel: --check exits $status, or writes:"; echo "$out" | indent; }
for case in "glow.png:mode:im.convert('LA')" "moon_pointer.png:size:im.crop((0, 0, im.width - 1, im.height))"; do
  fresh_art
  file=${case%%:*}; rest=${case#*:}; what=${rest%%:*}; expr=${rest#*:}
  pixel_edit "$file" "$expr"
  status=0
  out=$(art --check 2>&1) || status=$?
  [ "$status" -eq 1 ] && grep -q "$file" <<< "$out" \
    && pass "a changed $what in $file makes --check exit 1, naming it" \
    || { fail "a changed $what in $file: --check exits $status:"; echo "$out" | indent; }
done
fresh_art
rm "$ART/$DRAWABLE/hand_second.png"
status=0
out=$(art --check 2>&1) || status=$?
[ "$status" -eq 1 ] && grep -q hand_second.png <<< "$out" && [ ! -e "$ART/$DRAWABLE/hand_second.png" ] \
  && pass "a missing hand_second.png makes --check exit 1, naming it, and writes nothing" \
  || { fail "a missing picture: --check exits $status:"; echo "$out" | indent; }

# ---- faceArtLeavesAPictureWhosePixelsAreUnchangedAlone (a plain run in the scratch copy)
fresh_art
committed=$(sums)
status=0
out=$(art 2>&1) || status=$?
[ "$status" -eq 0 ] && [ "$(sums)" = "$committed" ] \
  && pass "a plain face-art.py run leaves every picture byte for byte as committed" \
  || { fail "a plain run exits $status, or re-encodes:"; diff <(echo "$committed") <(sums) | indent || true; }
pixel_edit arch_mark.png "im.point(lambda v: v // 2)"
status=0
out=$(art 2>&1) || status=$?
[ "$status" -eq 0 ] && grep -q arch_mark.png <<< "$out" && art --check > /dev/null 2>&1 \
  && pass "a plain run draws a changed picture again, and --check then passes" \
  || { fail "a plain run over a changed arch_mark.png exits $status:"; echo "$out" | indent; }

# ---- faceArtRefusesAnUnknownArgument (in the repository itself: nothing may be rewritten)
if [ -n "$(git status --porcelain -- "$DRAWABLE")" ]; then
  fail "$DRAWABLE has local changes, so face-art.py's arguments are not tried in the repository"
else
  for arg in --help --chek "--check extra"; do
    status=0
    # shellcheck disable=SC2086
    out=$(python3 -I tools/face-art.py $arg 2>&1) || status=$?
    if [ "$status" -eq 2 ] && grep -q -- '--check' <<< "$out" && [ -z "$(git status --porcelain -- "$DRAWABLE")" ]; then
      pass "face-art.py $arg prints the usage, exits 2 and rewrites nothing"
    else
      fail "face-art.py $arg exits $status, or rewrites $(git status --porcelain -- "$DRAWABLE" | tr '\n' ' '):"; echo "$out" | indent
      # it was clean before this run, so what the run rewrote is put back as committed
      git checkout HEAD -- "$DRAWABLE"
    fi
  done
  status=0
  out=$(python3 -I tools/face-art.py --check 2>&1) || status=$?
  [ "$status" -eq 0 ] && [ -z "$(git status --porcelain -- "$DRAWABLE")" ] \
    && pass "face-art.py --check passes in the repository and leaves git status clean" \
    || { fail "face-art.py --check in the repository exits $status:"; echo "$out" | indent; git checkout HEAD -- "$DRAWABLE"; }
fi

# ---- theFitToolsFontsArePinned (no device: ANDROID_HOME points nowhere)
fonts_check() { TAQWA_SYSTEM_FONTS="$1" ANDROID_HOME=/nonexistent bash tools/watchface/get-system-fonts.sh --check 2>&1; }
mkdir -p "$WORK/fonts-none" "$WORK/fonts-changed" "$WORK/fonts-ok"
status=0
out=$(fonts_check "$WORK/fonts-none") || status=$?
[ "$status" -eq 1 ] && grep -q NotoSansBengaliUI-VF.ttf <<< "$out" \
  && pass "get-system-fonts.sh --check refuses a missing NotoSansBengaliUI-VF.ttf" \
  || { fail "get-system-fonts.sh --check on an empty cache exits $status:"; echo "$out" | indent; }
printf 'not the watch font\n' > "$WORK/fonts-changed/NotoSansBengaliUI-VF.ttf"
status=0
out=$(fonts_check "$WORK/fonts-changed") || status=$?
[ "$status" -eq 1 ] && grep -qi 'changed' <<< "$out" \
  && pass "get-system-fonts.sh --check refuses a changed NotoSansBengaliUI-VF.ttf" \
  || { fail "get-system-fonts.sh --check on a changed font exits $status:"; echo "$out" | indent; }
if [ -f "$FONT_CACHE/NotoSansBengaliUI-VF.ttf" ]; then
  cp "$FONT_CACHE/NotoSansBengaliUI-VF.ttf" "$WORK/fonts-ok/"
  status=0
  out=$(fonts_check "$WORK/fonts-ok") || status=$?
  [ "$status" -eq 0 ] && pass "get-system-fonts.sh --check passes on the pinned font, with no device" \
    || { fail "get-system-fonts.sh --check on the cached font exits $status:"; echo "$out" | indent; }
else
  fail "$FONT_CACHE holds no NotoSansBengaliUI-VF.ttf: run tools/watchface/get-system-fonts.sh with emulator-5558 up"
fi
status=0
out=$(TAQWA_SYSTEM_FONTS="$WORK/fonts-changed" bash scripts/face-gate.sh 2>&1) || status=$?
[ "$status" -eq 1 ] && ! grep -q 'face-arch.py' <<< "$out" \
  && pass "face-gate.sh refuses to start with a changed font" \
  || { fail "face-gate.sh with a changed font exits $status, or runs on:"; echo "$out" | tail -5 | indent; }

# ---- aStalePictureFailsTheGate (in a scratch copy of the working tree: one pixel of arch_mark.png)
# Never in the repository itself: the gate builds both faces (validate.sh), so there it would leave
# watchface/build/outputs built from the changed picture, which validate.sh --no-build reads
# without building, and the tracked picture would stay changed for as long as the gate ran. The
# copy holds the files git would commit, as they are on disk, and local.properties (the SDK's place)
# and no other ignored file, in a git repository of its own, so the gate reads its tree as it reads
# the repository's. Gradle builds there.
if [ -f "$FONT_CACHE/NotoSansBengaliUI-VF.ttf" ]; then
  face_outputs() { (cd watchface/build/outputs 2>/dev/null && find . -name '*.apk' -type f | sort | while read -r f; do shasum -a 256 "$f"; done) || true; }
  outputs_before=$(face_outputs)
  picture_before=$(shasum -a 256 < "$DRAWABLE/arch_mark.png")
  GATE="$WORK/gate"
  mkdir -p "$GATE"
  git ls-files -z --cached --others --exclude-standard \
    | while IFS= read -r -d '' f; do if [ -e "$f" ] || [ -L "$f" ]; then printf '%s\0' "$f"; fi; done \
    | COPYFILE_DISABLE=1 tar --null --no-recursion -T - -cf - | tar -xf - -C "$GATE"
  if [ -f local.properties ]; then cp local.properties "$GATE/"; fi
  git -C "$GATE" init -q
  git -C "$GATE" add -A
  git -C "$GATE" -c user.name=scratch -c user.email=scratch@invalid -c commit.gpgsign=false \
    -c core.hooksPath=/dev/null commit -q -m "the working tree, for the stale-picture gate"
  python3 -I -c '
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGBA")
r, g, b, a = im.getpixel((24, 30))
im.putpixel((24, 30), (r, g, b, (a + 128) % 256))
im.save(sys.argv[1])' "$GATE/$DRAWABLE/arch_mark.png"
  status=0
  out=$(bash "$GATE/scripts/face-gate.sh" 2>&1) || status=$?
  [ "$status" -eq 1 ] && grep -qx 'face-gate.sh: 1 FAILED: face-art.py --check' <<< "$out" \
    && grep -q '^face-gate.sh: 11 passed: .*validate.sh$' <<< "$out" \
    && [ "$(git -C "$GATE" status --porcelain)" = " M $DRAWABLE/arch_mark.png" ] \
    && pass "a stale picture fails the gate, which runs every other check and exits 1" \
    || { fail "face-gate.sh with a stale arch_mark.png exits $status:"; echo "$out" | grep -E 'face-gate|FAIL|stale' | indent; git -C "$GATE" status --porcelain | indent; }
  rm -rf "$GATE"
  [ "$(face_outputs)" = "$outputs_before" ] && [ "$(shasum -a 256 < "$DRAWABLE/arch_mark.png")" = "$picture_before" ] \
    && pass "the stale-picture gate leaves the repository's picture and built faces as they were" \
    || { fail "the stale-picture gate changed the repository's arch_mark.png or rebuilt its faces:"; diff <(echo "$outputs_before") <(face_outputs) | indent || true; }
fi

# ---- nothing tracked changed
[ "$(git status --porcelain)" = "$STATUS_BEFORE" ] && pass "git status is as it was" \
  || { fail "git status changed:"; git status --porcelain | indent; }

if [ "$FAILS" -gt 0 ]; then
  echo "test-face-release.sh: $FAILS FAILED"
  exit 1
fi
echo "test-face-release.sh: PASS"

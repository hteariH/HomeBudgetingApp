#!/usr/bin/env bash
#
# Registers this deployment with Monobank as a company, which is what the corporate API
# needs before any customer can approve anything.
#
# A shell script rather than something in the application because it is run once, by the
# operator, on a machine that is not the server - and because the private key it makes must
# never reach the repository or a build. Everything here is openssl and curl; on Windows,
# Git Bash has both.
#
#   ./scripts/monobank-register.sh keygen           # make the signing key
#   ./scripts/monobank-register.sh register         # send the application
#   ./scripts/monobank-register.sh status           # ask whether it was approved, get the key id
#
# See docs/monobank-corporate.md for what to do with the answer.

set -euo pipefail

API="${MONO_API:-https://api.monobank.ua}"
KEY_DIR="${MONO_KEY_DIR:-.}"
PRIV="$KEY_DIR/monobank-priv.pem"
PUB="$KEY_DIR/monobank-pub.pem"

die() { echo "error: $*" >&2; exit 1; }

# Monobank signs even the registration endpoints, with the key being registered - the
# signature is the proof that whoever sent the public key holds the private one.
sign() {
    local path="$1" time="$2"
    printf '%s%s' "$time" "$path" \
        | openssl dgst -sha256 -sign "$PRIV" \
        | openssl base64 -A
}

post() {
    local path="$1" body="$2" time file
    time=$(date +%s)
    # Through a file rather than an argument: on Windows a command-line argument is
    # re-encoded on its way into curl, and a Cyrillic company name arrives as question marks.
    file=$(mktemp)
    printf '%s' "$body" > "$file"
    curl -sS -w '\n%{http_code}' \
        -X POST "$API$path" \
        -H "X-Time: $time" \
        -H "X-Sign: $(sign "$path" "$time")" \
        -H 'Content-Type: application/json; charset=utf-8' \
        --data-binary "@$file"
    rm -f "$file"
}

# The response's last line is the status code; everything before it is the body.
report() {
    local response="$1" code body
    code=$(printf '%s' "$response" | tail -n1)
    body=$(printf '%s' "$response" | sed '$d')
    echo "HTTP $code"
    echo "$body"
    [ "$code" = "200" ] || return 1
}

cmd_keygen() {
    [ -f "$PRIV" ] && die "$PRIV already exists - registration is tied to it, so refusing to overwrite"
    # secp256k1, which is what Monobank checks the signature against. Not the same curve as
    # the prime256v1 that most tutorials reach for, and the wrong one fails as a flat 403.
    openssl ecparam -name secp256k1 -genkey -noout -out "$PRIV"
    chmod 600 "$PRIV"
    openssl ec -in "$PRIV" -pubout -out "$PUB" 2>/dev/null
    echo "wrote $PRIV and $PUB"
    echo
    echo "Keep $PRIV out of the repository. It is the credential the whole registration"
    echo "rests on, and Monobank has no way to rotate it but a new application."
}

cmd_register() {
    [ -f "$PRIV" ] || die "no key yet - run '$0 keygen' first"
    : "${MONO_COMPANY_NAME:?set MONO_COMPANY_NAME}"
    : "${MONO_DESCRIPTION:?set MONO_DESCRIPTION - what the service does and why it needs statements}"
    : "${MONO_CONTACT:?set MONO_CONTACT - the contact name}"
    : "${MONO_PHONE:?set MONO_PHONE, e.g. 380671234567}"
    : "${MONO_EMAIL:?set MONO_EMAIL}"
    : "${MONO_LOGO:?set MONO_LOGO - path to a PNG the bank will show customers}"
    [ -f "$MONO_LOGO" ] || die "no such file: $MONO_LOGO"
    # Not in Monobank's published spec, but enforced: a longer description is a 400.
    # Characters, not bytes - a Cyrillic description gets the same 100 as a Latin one.
    [ "$(printf '%s' "$MONO_DESCRIPTION" | wc -m)" -le 100 ] \
        || die "MONO_DESCRIPTION is over Monobank's 100-character limit"

    local pubkey logo body
    pubkey=$(openssl base64 -A -in "$PUB")
    logo=$(openssl base64 -A -in "$MONO_LOGO")
    # Values are interpolated raw, so keep quotes and backslashes out of them.
    body=$(cat <<JSON
{"pubkey":"$pubkey",
 "name":"$MONO_COMPANY_NAME",
 "description":"$MONO_DESCRIPTION",
 "contactPerson":"$MONO_CONTACT",
 "phone":"$MONO_PHONE",
 "email":"$MONO_EMAIL",
 "logo":"$logo"}
JSON
)
    report "$(post /personal/auth/registration "$body")"
    echo
    echo "A human at the bank reads this. Poll '$0 status' - it answers New, Declined or"
    echo "Approved, and Approved carries the keyId."
}

cmd_status() {
    [ -f "$PUB" ] || die "no key yet - run '$0 keygen' first"
    local body
    body="{\"pubkey\":\"$(openssl base64 -A -in "$PUB")\"}"
    report "$(post /personal/auth/registration/status "$body")"
}

case "${1:-}" in
    keygen)   cmd_keygen ;;
    register) cmd_register ;;
    status)   cmd_status ;;
    *)        sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac

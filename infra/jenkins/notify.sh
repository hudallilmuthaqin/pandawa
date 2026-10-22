#!/usr/bin/env bash
# Sends a pipeline notification by email (SMTP) and WhatsApp (Fonnte). Called from the Jenkinsfile.
#   NOTIFY_ENV      path of the secrets file (Jenkins credential "pandawa-notify-env", see notify.env.example)
#   NOTIFY_SUBJECT  one-line summary (email subject, first line of the WhatsApp message)
#   NOTIFY_BODY     message text
# A channel whose settings are empty is skipped. Never fails the build: problems are only logged.
set -u

trim() {
    local s="$1"
    s="${s#"${s%%[![:space:]]*}"}"
    printf '%s' "${s%"${s##*[![:space:]]}"}"
}

# Plain KEY=VALUE parsing instead of sourcing, so values may contain spaces (e.g. "a@x.com, b@y.com").
while IFS='=' read -r key value || [ -n "$key" ]; do
    key="$(trim "$key")"
    case "$key" in '' | \#*) continue ;; esac
    value="${value%$'\r'}"
    value="${value#\"}"
    value="${value%\"}"
    export "$key=$value"
done < "$NOTIFY_ENV"
# Google shows App Passwords in groups of four ("abcd efgh ijkl mnop"); the spaces are not part of it.
SMTP_PASSWORD="${SMTP_PASSWORD:-}"
SMTP_PASSWORD="${SMTP_PASSWORD// /}"

body="$NOTIFY_BODY"
if [ -n "${APP_URL:-}" ]; then
    body="$body
Aplikasi: $APP_URL"
fi

send_email() {
    if [ -z "${SMTP_URL:-}" ] || [ -z "${MAIL_TO:-}" ]; then
        echo "notify: email not configured, skipped"
        return
    fi
    local rcpts=() to
    IFS=',' read -ra to <<< "$MAIL_TO"
    for r in "${to[@]}"; do
        r="$(trim "$r")"
        [ -n "$r" ] && rcpts+=(--mail-rcpt "$r")
    done
    # RFC 2047 encoded subject so emoji and non-ASCII survive every mail client.
    local subject="=?UTF-8?B?$(printf '%s' "$NOTIFY_SUBJECT" | base64 -w0)?="
    local mail
    mail="$(mktemp)"
    printf 'From: PB Pandawa CI <%s>\r\nTo: %s\r\nSubject: %s\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n%s\r\n' \
        "${MAIL_FROM:-$SMTP_USER}" "$MAIL_TO" "$subject" "$body" > "$mail"
    if curl -sS --max-time 30 --ssl-reqd --url "$SMTP_URL" --user "$SMTP_USER:$SMTP_PASSWORD" \
        --mail-from "${MAIL_FROM:-$SMTP_USER}" "${rcpts[@]}" --upload-file "$mail"; then
        echo "notify: email sent to $MAIL_TO"
    else
        echo "notify: email FAILED"
    fi
    rm -f "$mail"
}

send_whatsapp() {
    if [ -z "${FONNTE_TOKEN:-}" ] || [ -z "${WA_TARGET:-}" ]; then
        echo "notify: WhatsApp not configured, skipped"
        return
    fi
    local res
    res="$(curl -sS --max-time 30 -X POST https://api.fonnte.com/send \
        -H "Authorization: $FONNTE_TOKEN" \
        --form-string "target=$WA_TARGET" \
        --form-string "countryCode=62" \
        --form-string "message=*$NOTIFY_SUBJECT*
$body")"
    # Fonnte answers HTTP 200 even on errors; the JSON "status" says whether it was queued.
    if echo "$res" | grep -q '"status": *true'; then
        echo "notify: WhatsApp sent to $WA_TARGET"
    else
        echo "notify: WhatsApp FAILED: $res"
    fi
}

send_email
send_whatsapp
exit 0

#!/bin/bash
# ==============================================================================
# MediSure HIMS — encrypted backup (PBI34)
#
# Writes one dated folder with the database and the claim documents, each compressed and
# encrypted with AES-256 (PBKDF2, 200,000 iterations) under a backup passphrase. Nothing is ever
# written to disk unencrypted: mysqldump streams straight through gzip into openssl. Every backup is
# test-decrypted right after it is written, and backups older than KEEP_DAYS are removed.
#
# Settings (environment variables, all optional):
#   HIMS_DB               database name                       (default hims_db)
#   HIMS_BACKUP_DIR       where backups go                    (default ~/HIMS-backups/daily)
#   HIMS_MYSQL_CNF        MySQL login file, chmod 600         (default ~/HIMS-backups/.mysql.cnf)
#   HIMS_BACKUP_PASSFILE  backup passphrase file, chmod 600   (default ~/HIMS-backups/.backup-passphrase)
#   HIMS_UPLOADS          claim documents folder              (default <repo>/uploads)
#   KEEP_DAYS             how many days of backups to keep    (default 14)
#
# The medical fields inside the dump are also encrypted with the app's own key, so reading them
# after a restore needs BOTH the backup passphrase and app.encryption.key. Keep both in a password
# manager, away from this machine.
# ==============================================================================
set -euo pipefail
umask 077

REPO_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
DB="${HIMS_DB:-hims_db}"
BACKUP_DIR="${HIMS_BACKUP_DIR:-$HOME/HIMS-backups/daily}"
MYSQL_CNF="${HIMS_MYSQL_CNF:-$HOME/HIMS-backups/.mysql.cnf}"
PASSFILE="${HIMS_BACKUP_PASSFILE:-$HOME/HIMS-backups/.backup-passphrase}"
UPLOADS="${HIMS_UPLOADS:-$REPO_DIR/uploads}"
KEEP_DAYS="${KEEP_DAYS:-14}"
ENC=(openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "file:$PASSFILE")

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }
fail() { log "BACKUP FAILED: $*"; exit 1; }

for f in "$MYSQL_CNF" "$PASSFILE"; do
    [ -r "$f" ] || fail "$f is missing (see docs/deployment.md, section 3)"
done
[ -s "$PASSFILE" ] || fail "the backup passphrase file $PASSFILE is empty"
command -v mysqldump >/dev/null || fail "mysqldump is not on the PATH"

STAMP="$(date '+%Y-%m-%d_%H%M')"
TARGET="$BACKUP_DIR/$STAMP"
PARTIAL="$TARGET.partial"
mkdir -p "$PARTIAL"
trap 'rm -rf "$PARTIAL"' EXIT

log "Backing up database $DB"
mysqldump --defaults-extra-file="$MYSQL_CNF" --single-transaction --routines --triggers \
          --no-tablespaces "$DB" | gzip -9 | "${ENC[@]}" > "$PARTIAL/$DB.sql.gz.enc"

if [ -d "$UPLOADS" ]; then
    log "Backing up claim documents from $UPLOADS"
    tar -C "$(dirname "$UPLOADS")" -czf - "$(basename "$UPLOADS")" | "${ENC[@]}" > "$PARTIAL/uploads.tar.gz.enc"
else
    log "No uploads folder at $UPLOADS; skipping documents"
fi

# Prove the backup can be read back before trusting it.
"${ENC[@]}" -d -in "$PARTIAL/$DB.sql.gz.enc" | gzip -t || fail "database backup did not decrypt cleanly"
"${ENC[@]}" -d -in "$PARTIAL/$DB.sql.gz.enc" | gunzip | tail -1 | grep -q "Dump completed" \
    || fail "database dump is incomplete"
if [ -f "$PARTIAL/uploads.tar.gz.enc" ]; then
    "${ENC[@]}" -d -in "$PARTIAL/uploads.tar.gz.enc" | tar -tzf - >/dev/null || fail "documents backup did not decrypt cleanly"
fi

(cd "$PARTIAL" && shasum -a 256 ./*.enc > SHA256SUMS)
mv "$PARTIAL" "$TARGET"
trap - EXIT
log "Backup written and verified: $TARGET ($(du -sh "$TARGET" | cut -f1))"

# Keep the last KEEP_DAYS days.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$KEEP_DAYS" -print -exec rm -rf {} + \
    | sed "s/^/$(date '+%Y-%m-%d %H:%M:%S') Removed old backup /"

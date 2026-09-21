#!/bin/bash
# ==============================================================================
# MediSure HIMS — restore an encrypted backup (PBI34)
#
#   scripts/backup/hims-restore.sh <backup-folder> [target-database]
#
# Decrypts the backup and loads it into the target database (default hims_restore_check), and
# unpacks the claim documents into <backup-folder>-restored/. By default it never touches the live
# database or the live uploads folder: to restore over hims_db you must also pass
# --overwrite-live, and stop the app first.
#
# Uses the same settings as hims-backup.sh (HIMS_MYSQL_CNF, HIMS_BACKUP_PASSFILE).
# ==============================================================================
set -euo pipefail
umask 077

usage() { echo "usage: $0 <backup-folder> [target-database] [--overwrite-live]"; exit 2; }
[ $# -ge 1 ] || usage
SOURCE="${1%/}"
TARGET_DB="${2:-hims_restore_check}"
OVERWRITE_LIVE="${3:-}"
LIVE_DB="${HIMS_DB:-hims_db}"
MYSQL_CNF="${HIMS_MYSQL_CNF:-$HOME/HIMS-backups/.mysql.cnf}"
PASSFILE="${HIMS_BACKUP_PASSFILE:-$HOME/HIMS-backups/.backup-passphrase}"
DEC=(openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass "file:$PASSFILE")

[ -d "$SOURCE" ] || { echo "No backup folder at $SOURCE"; exit 1; }
DUMP="$(ls "$SOURCE"/*.sql.gz.enc 2>/dev/null | head -1)"
[ -n "$DUMP" ] || { echo "No database backup (*.sql.gz.enc) in $SOURCE"; exit 1; }
[[ "$TARGET_DB" =~ ^[A-Za-z0-9_]+$ ]] || { echo "Invalid database name: $TARGET_DB"; exit 1; }
if [ "$TARGET_DB" = "$LIVE_DB" ] && [ "$OVERWRITE_LIVE" != "--overwrite-live" ]; then
    echo "Refusing to overwrite the live database $LIVE_DB. Stop the app and add --overwrite-live if you really mean it."
    exit 1
fi

echo "Checking the backup against its checksums"
(cd "$SOURCE" && shasum -a 256 -c SHA256SUMS)

echo "Restoring $DUMP into database $TARGET_DB"
mysql --defaults-extra-file="$MYSQL_CNF" -e "DROP DATABASE IF EXISTS \`$TARGET_DB\`; CREATE DATABASE \`$TARGET_DB\`;"
"${DEC[@]}" -in "$DUMP" | gunzip | mysql --defaults-extra-file="$MYSQL_CNF" "$TARGET_DB"

if [ -f "$SOURCE/uploads.tar.gz.enc" ]; then
    OUT="$SOURCE-restored"
    mkdir -p "$OUT"
    "${DEC[@]}" -in "$SOURCE/uploads.tar.gz.enc" | tar -xzf - -C "$OUT"
    echo "Claim documents unpacked into $OUT/uploads"
fi

echo "Restored row counts:"
mysql --defaults-extra-file="$MYSQL_CNF" -N "$TARGET_DB" -e "
    SELECT 'users', COUNT(*) FROM users UNION ALL
    SELECT 'policies', COUNT(*) FROM policies UNION ALL
    SELECT 'claims', COUNT(*) FROM claims UNION ALL
    SELECT 'payments', COUNT(*) FROM payments UNION ALL
    SELECT 'claim_documents', COUNT(*) FROM claim_documents"

#!/bin/bash
# ==============================================================================
# MediSure HIMS — schedule the nightly encrypted backup on macOS (PBI34)
#
#   scripts/backup/install-mac-schedule.sh [hour]        (default 2, i.e. 02:00 every night)
#
# macOS does not let background jobs read ~/Desktop or ~/Documents, so the backup script is copied
# to ~/HIMS-backups/bin and the claim documents must live outside those folders (set
# app.upload-dir, e.g. ~/HIMS-data/uploads). A Mac that is asleep at the set time runs the backup
# when it wakes. Output goes to ~/HIMS-backups/backup.log. Run it again after changing the script.
# ==============================================================================
set -euo pipefail

HOUR="${1:-2}"
HERE="$(cd "$(dirname "$0")" && pwd)"
BIN="$HOME/HIMS-backups/bin"
UPLOADS="${HIMS_UPLOADS:-$HOME/HIMS-data/uploads}"
LABEL="lk.medisure.hims-backup"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
MYSQL_BIN="$(dirname "$(command -v mysqldump || echo /usr/local/mysql/bin/mysqldump)")"

case "$UPLOADS" in
    "$HOME/Desktop"*|"$HOME/Documents"*|"$HOME/Downloads"*)
        echo "The uploads folder $UPLOADS is in a folder macOS hides from background jobs."
        echo "Move it (e.g. to ~/HIMS-data/uploads), set app.upload-dir to match, and run this again."
        exit 1;;
esac
for f in "$HOME/HIMS-backups/.mysql.cnf" "$HOME/HIMS-backups/.backup-passphrase"; do
    [ -s "$f" ] || { echo "Create $f first (see docs/deployment.md, section 3)."; exit 1; }
done

mkdir -p "$BIN"
chmod 700 "$HOME/HIMS-backups"
install -m 700 "$HERE/hims-backup.sh" "$BIN/hims-backup.sh"
install -m 700 "$HERE/hims-restore.sh" "$BIN/hims-restore.sh"

cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>$LABEL</string>
    <key>ProgramArguments</key>
    <array><string>/bin/bash</string><string>$BIN/hims-backup.sh</string></array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PATH</key><string>$MYSQL_BIN:/usr/bin:/bin:/usr/sbin:/sbin</string>
        <key>HIMS_UPLOADS</key><string>$UPLOADS</string>
    </dict>
    <key>StartCalendarInterval</key>
    <dict><key>Hour</key><integer>$HOUR</integer><key>Minute</key><integer>0</integer></dict>
    <key>StandardOutPath</key><string>$HOME/HIMS-backups/backup.log</string>
    <key>StandardErrorPath</key><string>$HOME/HIMS-backups/backup.log</string>
</dict>
</plist>
EOF
plutil -lint "$PLIST" >/dev/null
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Nightly backup scheduled for $(printf '%02d' "$HOUR"):00. Run one now with:"
echo "  launchctl kickstart gui/$(id -u)/$LABEL && tail -f ~/HIMS-backups/backup.log"

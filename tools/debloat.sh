#!/usr/bin/env bash
# Disables realme/ColorOS bloat (reversible) and silences noisy apps over adb.
#   tools/debloat.sh apply [serial]    disable-user every package in realme-debloat.txt, revoke notifications
#   tools/debloat.sh restore [serial]  re-enable them
set -u
cd "$(dirname "$0")"
mode=${1:-}; serial=${2:-}
adb_() { if [ -n "$serial" ]; then adb -s "$serial" "$@"; else adb "$@"; fi; }

# Apps that may stay installed but should never post notifications.
QUIET="com.google.android.youtube com.google.android.apps.photos com.google.android.apps.maps
com.google.android.googlequicksearchbox com.google.android.keep com.android.vending com.oplus.themestore
com.coloros.phonemanager com.heytap.usercenter com.coloros.gallery3d com.google.android.apps.wellbeing
com.realme.wellbeing com.coloros.backuprestore com.coloros.note com.google.android.apps.nbu.files
com.coloros.filemanager com.oplus.ota com.oplus.romupdate com.coloros.oshare com.oplus.cast
com.google.android.apps.restore com.heytap.colorfulengine com.oplus.wallpapers com.heytap.market
com.nearme.instant.platform com.oplus.sau com.oplus.pay"

case "$mode" in
  apply)
    while read -r p; do [ -n "$p" ] && echo "$p: $(adb_ shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r')"; done < realme-debloat.txt
    for p in $QUIET; do
      adb_ shell pm revoke "$p" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
      adb_ shell cmd appops set "$p" POST_NOTIFICATION ignore >/dev/null 2>&1
    done
    echo "Notifications silenced for $(echo $QUIET | wc -w | tr -d ' ') apps." ;;
  restore)
    while read -r p; do [ -n "$p" ] && echo "$p: $(adb_ shell pm enable "$p" 2>&1 | tr -d '\r')"; done < realme-debloat.txt
    for p in $QUIET; do adb_ shell cmd appops set "$p" POST_NOTIFICATION allow >/dev/null 2>&1; done ;;
  *) echo "usage: $0 apply|restore [adb-serial]"; exit 1 ;;
esac

#!/usr/bin/env bash
# Publishes release wgbg-v<versionName> on cyclistmass/sharable: the debug APK built by CI for the pushed
# commit, plus the gameplay video from tools/record_showcase.sh. Asset names never change, so
#   https://github.com/cyclistmass/sharable/releases/latest/download/working-german-board-game.apk
# always serves the newest version. Run from the project root after CI is green for the pushed commit.
# The GitHub token comes from Git Credential Manager for one command at a time; it is never printed or saved.
# Usage: tools/publish_release.sh [path to gameplay mp4]
set -euo pipefail
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
VIDEO="${1:-$TEMP/wgbg-showcase/working-german-board-game-gameplay.mp4}"
REPO=cyclistmass/sharable
SHARABLE="${SHARABLE:-../sharable}"
WORK="$(mktemp -d)"
tok() { printf "protocol=https\nhost=github.com\npath=%s.git\n\n" "$REPO" | git credential fill | sed -n 's/^password=//p'; }
api() { curl -s -m 60 -H "Authorization: Bearer $(tok)" "$@"; }

SHA=$(git -C "$SHARABLE" rev-parse origin/main)
RUN=$(api "https://api.github.com/repos/$REPO/actions/runs?head_sha=$SHA" | py -c "
import json,sys
r=[w for w in json.load(sys.stdin)['workflow_runs'] if 'working' in w['name']]
print(r[0]['id'] if r and r[0]['conclusion']=='success' else '')")
[ -n "$RUN" ] || { echo "CI is not green for ${SHA:0:7}; not publishing"; exit 1; }
ART=$(api "https://api.github.com/repos/$REPO/actions/runs/$RUN/artifacts" | py -c "
import json,sys
print([a['id'] for a in json.load(sys.stdin)['artifacts'] if a['name'].endswith('debug-apk')][0])")
curl -sL -m 300 -H "Authorization: Bearer $(tok)" -o "$WORK/apk.zip" "https://api.github.com/repos/$REPO/actions/artifacts/$ART/zip"
(cd "$WORK" && unzip -q apk.zip && mv app-debug.apk working-german-board-game.apk)

py -c "
import json,sys
print(json.dumps({'tag_name':'wgbg-v$VERSION','target_commitish':'$SHA','name':'Working German Board Game $VERSION','make_latest':'true',
 'body':'''Android build $VERSION of Working German Board Game (debug-signed, for testing).

**Install:** download working-german-board-game.apk below on an Android phone (Android 8+), open it, and allow installing from this source when asked. It updates any earlier version.

Always-latest link: https://github.com/$REPO/releases/latest/download/working-german-board-game.apk

Built by CI from commit $SHA; source in [working-german-board-game/](https://github.com/$REPO/tree/main/working-german-board-game). GPL-3.0.'''}))" > "$WORK/release.json"
ID=$(api -X POST "https://api.github.com/repos/$REPO/releases" -d @"$WORK/release.json" | py -c "import json,sys;d=json.load(sys.stdin);print(d.get('id') or d)")
case "$ID" in *[!0-9]*) echo "release not created: $ID"; exit 1;; esac
up() { api -H "Content-Type: $2" --data-binary @"$1" "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=$(basename "$1")" |
  py -c "import json,sys;d=json.load(sys.stdin);print(d.get('browser_download_url') or d)"; }
up "$WORK/working-german-board-game.apk" application/vnd.android.package-archive
if [ -f "$VIDEO" ]; then cp "$VIDEO" "$WORK/working-german-board-game-gameplay.mp4"; up "$WORK/working-german-board-game-gameplay.mp4" video/mp4; fi
rm -rf "$WORK"
echo "published wgbg-v$VERSION from ${SHA:0:7}"

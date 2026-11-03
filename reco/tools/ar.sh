#!/bin/bash
# ar.sh <cmd> [arg] — drive the QA build's in-process DebugBridge (see app/src/qa).
#   fg | tree | click text:Foo|desc:Foo|id:name | pref <key> | back | start .ui.X | scroll <dy> | shot | crash | crash-clear
# shot pulls the PNG to ./qa_shot.png. Never touches other apps (all actions are in-process).
cmd=$1; arg=$2
r=$(adb shell am broadcast -n ${PKG:-com.jcapps.adbremote.qa}/com.jcapps.adbremote.qa.DebugBridge --es cmd "'$cmd'" --es arg "'$arg'" 2>&1)
data=$(printf '%s' "$r" | sed -n '/data="/,$p' | sed '1s/.*data="//; $s/"$//')
[ -z "$data" ] && { echo "NO RESPONSE (app not running?)"; printf '%s\n' "$r" | tail -2; exit 1; }
printf '%s\n' "$data"
if [ "$cmd" = shot ]; then adb shell cat /sdcard/Android/data/${PKG:-com.jcapps.adbremote.qa}/files/qa_shot.png > qa_shot.png; fi

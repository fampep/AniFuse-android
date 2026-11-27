# Screenshots

Raw captures live in `raw/`. The PNGs next to this file are the same shots
composited onto Google's Pixel 8 device art (with a generated shadow).

These are for the project README only. Google's device art must not be used in
Play Store listings. Bezel files download when the frame script runs; they are
not stored in the repo.

## Launch

Night mode, then the public catalog. The `browse` extra opens without forcing
sign-in.

```bash
adb shell cmd uimode night yes
adb shell am start -n site.anikitty.app/.MainActivity --ez browse true
```

Wait until poster art has painted. Dismiss any welcome card and the update
banner before capturing Home.

## Status bar

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide
adb shell screencap -p /sdcard/anifuse-home.png
adb pull /sdcard/anifuse-home.png docs/screenshots/raw/home.png
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

Use `adb shell screencap -p /sdcard/...` + `adb pull` on Windows — PowerShell
redirects corrupt PNG bytes.

Suggested taps after Home:

| File | How to reach it |
| --- | --- |
| `raw/home.png` | Home tab, scrolled to the top |
| `raw/title.png` | Open a title from Spotlight / Trending |
| `raw/player.png` | Watch Now on that title |
| `raw/library.png` | Library tab on the floating dock |
| `raw/profile.png` | Profile avatar in the Home bar, or Profile tab |

## Frame

```bash
python3 scripts/frame_screenshots.py
```

| File | Screen |
| --- | --- |
| `home.png` | Home — glass bar, spotlight, floating dock |
| `title.png` | Title page — glass actions, episodes |
| `player.png` | Media3 player — Source / Quality / Audio card |
| `library.png` | Library — week goal, AniList lists |
| `profile.png` | Profile — AniList card, stats, settings hub |

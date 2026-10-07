# 🧠 GoodBrainRot

**An offline Android play-and-learn app for toddlers and young kids — in 5 languages.**

Instruments, picture quizzes, mini-games and drawing, wrapped in a colorful hub UI. Everything runs on the device: **no ads, no tracking, no account, no network** (the only permission is `VIBRATE`). Built in Kotlin + Jetpack Compose.

*(Yes, the name is a joke — this is the good kind of brain rot.)*

## Activities

- 🎵 **Music** — piano, xylophone, guitar, and two MPC-style pads (hip-hop / electro, Sonic Pi CC0 drum kits), all playable by tiny fingers.
- 🖼️ **Recognize** — guess the animal (~200 of them), vehicle or dinosaur from a photo; names in FR/EN/IT/ES/RU from Wikidata, photos from Wikipedia/Commons (fully credited in [`CREDITS-ASSETS.md`](CREDITS-ASSETS.md)).
- 🔤 **Guess** — letters, numbers, colors, shapes, counting.
- 👂 **Listen** — animal cries and vehicle sounds quizzes.
- 🎮 **Games** — bubble popping, star rain, a cute shmup, a 3D race, a zen gardener.
- 🎨 **Create** — finger drawing.
- 👨‍👩‍👧 **Parent corner** (behind a 3-second gate): screen-time view, per-category mastery, "classic → exotic" spaced introduction of new items.
- 👶 **Baby mode** pins the screen so random taps can't leave the app.
- Multiple child profiles, all local.

## Building

```bash
git clone https://github.com/hypedigger/GoodBrainRot.git
cd GoodBrainRot
# open in Android Studio, or:
gradlew.bat assembleDebug
```

Content is **data-driven**: `app/src/main/assets/content/*.json` + images per category.
Optional content scripts (PowerShell):

- `scripts/fetch_content.ps1` — refresh Wikidata names and Wikipedia images (`-Rank` orders animals by popularity).
- `scripts/fetch_samples.ps1` — download the CC0 MPC drum samples.
- `tools/content-admin/serve.ps1` — local web admin to spot missing images/translations.

A few animal-cry sounds of the author's personal build are not redistributable and are therefore **not** in this repository — the app falls back to text-to-speech; see [`CREDITS.md`](CREDITS.md).

## Licenses & credits

Code: [MIT](LICENSE). Asset licensing is documented in [`CREDITS.md`](CREDITS.md) (tooling, fonts, samples) and [`CREDITS-ASSETS.md`](CREDITS-ASSETS.md) (per-image Wikimedia Commons attribution — author and license for every photo). If you ship an APK built from this repository, keep that attribution visible.

## Support

If GoodBrainRot bought you a quiet car ride:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/hypedigger)

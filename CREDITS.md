# Crédits et licences

## Outillage (100 % open source / gratuit)
- **Kotlin** — Apache 2.0 (JetBrains)
- **Jetpack Compose / AndroidX** — Apache 2.0 (Google)
- **Gradle** 8.10.2 — Apache 2.0
- **Android Gradle Plugin** 8.7.3 — Apache 2.0
- **Eclipse Temurin JDK 17** — GPLv2 + Classpath Exception (Adoptium)
- **Android Studio** — gratuit, basé sur IntelliJ Community (Apache 2.0)

## Contenus de l'application
- **Logo et icône** : vector drawable original créé pour ce projet (cerveau
  cartoon pastel). Le cerveau zombie de l'easter egg est aussi un vector original.
- **Police « Creepster »** (mode zombie « BrainRot ») : Google Fonts, licence
  SIL Open Font License 1.1 — `app/src/main/res/font/creepster.ttf`.
- **Sons du piano, victoire, erreur** : synthétisés programmatiquement par
  l'application (`SoundEngine`), aucun fichier audio tiers.
- **Voix** : synthèse vocale Android (moteur TTS de l'appareil).
- **Emojis** : police emoji système Android (fallback des images d'animaux).

## Contenu « Reconnais » (animaux ~200, dinosaures, véhicules)
- **Noms multilingues** (FR/EN/IT/ES/RU) : libellés **Wikidata** (CC0).
- **Images** : photo principale de l'article **Wikipédia** correspondant
  (fichiers Wikimedia Commons, licences libres). Récupérées par
  `scripts/fetch_content.ps1`. Quelques libellés Wikidata sont des noms
  scientifiques (ex. « Bos taurus ») — à nettoyer au besoin.
- **Silhouettes de dinosaures** : vector drawables originaux créés pour ce projet
  (repli hors-ligne quand la photo n'est pas téléchargée).
- **Samples MPC** : kit de batterie Sonic Pi (CC0), via `scripts/fetch_samples.ps1`.

## Photos et cris d'animaux (optionnels)
Téléchargés par `scripts/download_assets.ps1` depuis Wikipédia / Wikimedia
Commons (licences libres : CC0, CC-BY, CC-BY-SA selon les fichiers).
Le détail fichier par fichier (source, auteur, licence) est généré dans
**`CREDITS-ASSETS.md`**.

L'attribution fichier par fichier (source Commons, auteur, licence) est dans
**`CREDITS-ASSETS.md`**. Si vous distribuez un APK construit depuis ce dépôt,
reportez cette attribution de manière visible (écran de crédits ou notice).

## Sons non inclus dans le dépôt
Quelques cris d'animaux (chat, vache, chèvre, poule, cheval, mouton, coq) du
build personnel proviennent de [LaSonothèque](https://lasonotheque.org/)
(libres d'utilisation, mais leur redistribution en l'état n'est pas permise) :
ils ne sont **pas** dans ce dépôt. L'app se rabat sur la synthèse vocale quand
un cri manque ; vous pouvez placer vos propres sons dans
`app/src/main/assets/sounds/`.

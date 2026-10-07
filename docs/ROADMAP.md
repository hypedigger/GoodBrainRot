# GoodBrainRot — Roadmap & idées

> Vision : une app d'éveil que **les parents ont envie de faire tester à leurs
> enfants**, qui devient un petit **réseau social de jeux éducatifs familiaux**,
> avec un apprentissage qui s'adapte vraiment à chaque enfant.

Statut : l'app est fonctionnelle (15+ activités, 5 langues, contenu ~200 animaux
data‑driven, jeux d'arcade, MPC, etc.). Cette roadmap liste la suite pour
« faire pro ».

---

## A. Socle « apprentissage adaptatif » (cœur produit — à faire en premier)

Tout le reste (dashboard, niveaux, monétisation, social) dépend d'une **couche
de données** propre. À construire :

1. **Profils enfants** (multi‑enfants par appareil) : prénom, avatar, âge, langue
   préférée. Sélecteur de profil au lancement.
2. **Modèle de maîtrise par item** (répétition espacée, type Leitner) :
   pour chaque (enfant, item) → vu, justes, erreurs, série, boîte 0‑5, dernière vue.
   - Sélection : pondérer par (faible maîtrise + items « dus »), n'introduire que
     quelques **nouveaux items à la fois**, espacer les items maîtrisés.
   - Difficulté adaptative : **nombre de choix** qui monte avec le niveau global.
3. **Séquençage classique → exotique** : champ `rank` (popularité) sur chaque item.
   - Automatisable : le script de contenu peut lire les **pages vues Wikipédia**
     (API pageviews) pour classer les animaux du plus connu au plus rare.
4. **Niveau & XP de l'enfant** : XP par bonne réponse, paliers, petite animation
   de montée de niveau.
5. **Tracking du temps & des sessions** : temps par activité/jour, items appris.

> Reco technique : passer de SharedPreferences à **Room** (SQLite) pour ces
> données relationnelles (enfants × items × événements), tout en restant 100% local
> dans un premier temps (sync cloud plus tard, cf. C).

## B. Dashboard parents

- Écran derrière la **barrière parents** (appui long 3 s déjà en place).
- Temps d'écran par jour/activité, courbe d'évolution, items maîtrisés par catégorie,
  points forts / à retravailler, par enfant.
- Graphiques simples dessinés en Compose (barres / courbe) — pas de lib externe.
- Rappels santé : cohérent avec l'écran d'avertissement (< 3 ans = temps court).

## C. Outils de contenu

- **Outil web d'admin** (page locale) : tableau de tout le contenu par catégorie
  (image / nom × 5 langues / son présent ou non), **repérage des manques**,
  **édition des traductions**, export du JSON corrigé. Statique (HTML+JS) servi
  par un mini serveur local. Permet de **corriger les noms scientifiques** issus
  de Wikidata.
- Nettoyage des libellés Wikidata (ex. « Bos taurus » → « vache »).
- **Packs de contenu** : structurer le contenu en packs activables (base gratuit,
  packs thématiques) — prépare la monétisation D.

## D. Modèles économiques (⚠️ à décider plus tard, app à polir avant)

Options à évaluer (non exclusives) :
- **Freemium** : nb de jeux/sessions par jour limité sans abonnement.
- **Progression premium** : dashboard parents & suivi détaillé réservés aux abonnés.
- **Packs de contenu** déblocables : packs d'animaux/véhicules thématiques.
  - ⚠️ **Packs sous licence** (Disney, animés, séries de jeux) = **droits IP à
    négocier/acheter** — coûteux et juridiquement lourd. À garder pour plus tard
    avec des licences en règle (sinon retrait du store + risque légal).
- **Packs gratuits financés par des éditeurs** (sponsoring) — attention : pub/
  sponsoring dans une app enfants = très encadré (cf. E légal).
- **Abonnement famille** (plusieurs profils enfants) — modèle le plus naturel.
- Outils : **RevenueCat** (abonnements multiplateforme), Google Play Billing.

## E. Comptes, famille, cloud

- **Comptes famille** : un compte parent → plusieurs profils enfants.
- **Sync multi‑appareils** (tablette + téléphones de la famille).
- Backend léger : **Firebase** ou **Supabase** (auth, base, stockage des assets/packs).
- Le modèle de données A est déjà pensé pour ça (enfant = entité).

## F. Engagement & « réseau social familial » (à la Duolingo, version éthique)

⚠️ **Tension à assumer** : rendre une app **addictive pour des < 3 ans** contredit
notre propre écran d'avertissement (limiter les écrans) et la **politique Google
Play Families**. Reco : déplacer le « FOMO » **côté parents**, pas côté tout‑petits.

- **Streaks / objectif du jour** côté parent (« 3 sessions de 5 min cette semaine »).
- **Badges & jalons** d'apprentissage (1er mot, 10 animaux reconnus…).
- **Groupes de familles** : comparer la progression, petits **classements
  bienveillants** entre familles amies, défis hebdo doux.
- **Partage** des progrès (carte « ton enfant a appris X animaux ce mois‑ci »).
- Notifications **destinées aux parents**, jamais à l'enfant.

## G. « Faire pro » — qualité & conformité

- **Design system** unifié (couleurs, typographies, composants, animations).
- **Onboarding** (choix langue, création profil enfant, âge).
- **Accessibilité** : contrastes, tailles, descriptions, daltonisme.
- **Légal enfants (crucial)** : COPPA (US), RGPD + protection des mineurs (UE),
  **Google Play “Designed for Families”**, **pas de pub comportementale ni de
  tracking** pour les enfants, achats derrière barrière parentale, politique de
  confidentialité claire.
- **Technique** : release signé (AAB), ProGuard, **analytics respectueux de la vie
  privée**, **crash reporting**, tests, **CI** (build auto), capture/screenshots store.
- Fiche Play Store : icône, captures, vidéo, descriptions localisées.

---

## Ordre conseillé
1. **A** (profils + maîtrise + niveaux + temps) — débloque tout le reste.
2. **B** (dashboard parents) — valeur perçue immédiate pour les parents.
3. **rank classique→exotique** (extension du script + tri) — peu coûteux, gros effet.
4. **C** (outil d'admin web + nettoyage noms).
5. Puis **E/D/F** quand l'app est polie.

## Petites features prêtes à coder tout de suite
- **Switch photo / emoji** dans les options des jeux d'animaux (défaut : photo).
- Tri **classique → exotique** basé sur `rank` (une fois le rank récupéré).

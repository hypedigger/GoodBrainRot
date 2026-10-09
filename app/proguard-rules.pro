# Règles R8 pour le build release (minify du code seulement, pas des ressources).

# L'app charge beaucoup de ressources par NOM (Resources.getIdentifier) : R8 ne retire pas
# les ressources (shrinkResources off) mais on garde aussi R et BuildConfig par sûreté.
-keep class com.goodbrainrot.app.BuildConfig { *; }
-keepclassmembers class **.R$* { public static <fields>; }

# org.json est dans la plateforme Android ; rien à garder côté app, mais on protège les
# classes de données sérialisées/désérialisées à la main si des noms de champs sont lus par réflexion.
-keepattributes *Annotation*, Signature

# Compose / coroutines : les règles par défaut des artefacts AndroidX suffisent (consumer rules).

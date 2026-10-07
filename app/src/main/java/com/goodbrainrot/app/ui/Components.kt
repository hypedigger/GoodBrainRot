package com.goodbrainrot.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.data.Animal
import com.goodbrainrot.app.data.ContentItem
import com.goodbrainrot.app.data.loadAnimalImage
import com.goodbrainrot.app.data.loadContentImage

/** Résout une icône IA dessinée « ic_<clé> » (0 si absente). */
fun contentIconRes(context: android.content.Context, key: String): Int =
    context.resources.getIdentifier("ic_$key", "drawable", context.packageName)

/** Avatar de profil : les emoji stockés en base sont rendus avec l'icône IA correspondante. */
private val AVATAR_ICONS = mapOf(
    "🐣" to "ic_chick", "🦊" to "ic_fox", "🐱" to "ic_cat", "🐯" to "ic_tiger",
    "🐰" to "ic_rabbit", "🐻" to "ic_bear", "🦁" to "ic_lion", "🐸" to "ic_frog",
    "🐵" to "ic_monkey", "🐧" to "ic_penguin", "🦄" to "ic_unicorn", "🐨" to "ic_koala"
)

@Composable
fun AvatarIcon(avatar: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val res = remember(avatar) {
        AVATAR_ICONS[avatar]?.let {
            context.resources.getIdentifier(it, "drawable", context.packageName)
        } ?: 0
    }
    if (res != 0) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = modifier.then(Modifier.size(size)),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(text = avatar, fontSize = (size.value * 0.72f).sp, modifier = modifier)
    }
}

/**
 * Visuel d'un animal : photo depuis les assets si disponible,
 * sinon icône IA « mignonne » (ic_<clé>), sinon fallback emoji.
 */
@Composable
fun AnimalVisual(animal: Animal, modifier: Modifier = Modifier, emojiSize: Int = 52) {
    val context = LocalContext.current
    val usePhotos = Settings.useContentPhotos.value
    val bitmap = remember(animal.key, usePhotos) { if (usePhotos) loadAnimalImage(context, animal.key) else null }
    val iconId = remember(animal.key) { contentIconRes(context, animal.key) }
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = stringResource(animal.nameRes),
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        iconId != 0 -> Image(
            painter = painterResource(iconId),
            contentDescription = stringResource(animal.nameRes),
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = animal.emoji, fontSize = emojiSize.sp)
        }
    }
}

/**
 * Visuel d'un item de contenu (animal/véhicule/dino) : image d'asset si présente,
 * sinon dessin vectoriel, sinon emoji, sinon le nom en texte.
 */
@Composable
fun ContentVisual(item: ContentItem, modifier: Modifier = Modifier, emojiSize: Int = 56) {
    val context = LocalContext.current
    val usePhotos = Settings.useContentPhotos.value
    val bitmap = remember(item.key, item.folder, usePhotos) {
        if (usePhotos) loadContentImage(context, item.folder, item.key) else null
    }
    val drawableId = remember(item.key) { item.drawableRes(context) }
    val iconId = remember(item.key) { contentIconRes(context, item.key) }
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = item.displayName(context),
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        drawableId != 0 -> Image(
            painter = painterResource(drawableId),
            contentDescription = item.displayName(context),
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        iconId != 0 -> Image(
            painter = painterResource(iconId),
            contentDescription = item.displayName(context),
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        item.emoji != null -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = item.emoji, fontSize = emojiSize.sp)
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = item.displayName(context),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            )
        }
    }
}

/** Sélecteur numérique simple « − valeur + ». */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            FilledTonalButton(
                onClick = { onChange((value - step).coerceIn(range)) },
                enabled = value > range.first
            ) {
                Text(text = "−", fontSize = 24.sp)
            }
            Text(text = "$value", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            FilledTonalButton(
                onClick = { onChange((value + step).coerceIn(range)) },
                enabled = value < range.last
            ) {
                Text(text = "+", fontSize = 24.sp)
            }
        }
    }
}

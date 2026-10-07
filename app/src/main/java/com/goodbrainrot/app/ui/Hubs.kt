package com.goodbrainrot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.R
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow

/** Une tuile de menu : un emoji (ou une icône dessinée), un libellé, une destination. */
data class MenuEntry(val route: String, val emoji: String, val labelRes: Int, val iconRes: Int? = null)

/** Hubs de premier niveau (page d'accueil). */
val TOP_HUBS = listOf(
    MenuEntry(Routes.HUB_MUSIC, "🎵", R.string.hub_music, iconRes = R.drawable.ic_hub_music),
    MenuEntry(Routes.HUB_GUESS, "🦁🚗🦖", R.string.hub_guess, iconRes = R.drawable.ic_hub_recognize),
    MenuEntry(Routes.HUB_DEVINE, "🔤", R.string.hub_devine, iconRes = R.drawable.ic_hub_guess),
    MenuEntry(Routes.HUB_LISTEN, "🔊", R.string.hub_listen, iconRes = R.drawable.ic_hub_listen),
    MenuEntry(Routes.HUB_GAMES, "🎮", R.string.hub_games, iconRes = R.drawable.ic_hub_games),
    MenuEntry(Routes.HUB_CREATE, "🖍️", R.string.hub_create, iconRes = R.drawable.ic_hub_create)
)

/** Contenu de chaque hub. */
fun hubChildren(hubRoute: String): List<MenuEntry> = when (hubRoute) {
    Routes.HUB_MUSIC -> listOf(
        MenuEntry(Routes.PIANO, "🎹", R.string.activity_piano, iconRes = R.drawable.ic_piano),
        MenuEntry(Routes.XYLOPHONE, "🎶", R.string.activity_xylophone, iconRes = R.drawable.ic_notes),
        MenuEntry(Routes.GUITAR, "🎸", R.string.activity_guitar, iconRes = R.drawable.ic_guitar),
        MenuEntry(Routes.MPC_HIPHOP, "🎛️", R.string.activity_mpc_hiphop, iconRes = R.drawable.ic_mpc),
        MenuEntry(Routes.MPC_ELECTRO, "🎚️", R.string.activity_mpc_electro, iconRes = R.drawable.ic_mpc)
    )
    Routes.HUB_GUESS -> listOf(
        MenuEntry(Routes.QUIZ_IMG_ANIMAL, "🦁", R.string.activity_guess_animal, iconRes = R.drawable.ic_lion),
        MenuEntry(Routes.QUIZ_IMG_VEHICLE, "🚗", R.string.activity_guess_vehicle, iconRes = R.drawable.ic_car),
        MenuEntry(Routes.QUIZ_IMG_DINO, "🦖", R.string.activity_guess_dino, iconRes = R.drawable.ic_dino),
        MenuEntry(Routes.QUIZ_SOUND, "🔊", R.string.activity_sound_quiz, iconRes = R.drawable.ic_hub_listen),
        MenuEntry(Routes.QUIZ_IMG_MIXED, "🎲", R.string.activity_guess_mixed, iconRes = R.drawable.ic_dice),
        MenuEntry(Routes.QUIZ_MAMA, "🐥", R.string.activity_mama, iconRes = R.drawable.ic_chick)
    )
    Routes.HUB_DEVINE -> listOf(
        MenuEntry(Routes.QUIZ_LETTER, "🔤", R.string.activity_guess_letter, iconRes = R.drawable.ic_hub_guess),
        MenuEntry(Routes.QUIZ_NUMBER, "🔢", R.string.activity_guess_number, iconRes = R.drawable.ic_numbers),
        MenuEntry(Routes.QUIZ_COLOR, "🎨", R.string.activity_color, iconRes = R.drawable.ic_palette),
        MenuEntry(Routes.QUIZ_SHAPE, "🔷", R.string.activity_shape, iconRes = R.drawable.ic_shape),
        MenuEntry(Routes.QUIZ_COUNT, "🧮", R.string.activity_count, iconRes = R.drawable.ic_abacus)
    )
    Routes.HUB_LISTEN -> listOf(
        MenuEntry(Routes.ANIMAL_SOUNDS, "🐮", R.string.activity_animal_sounds, iconRes = R.drawable.ic_cow),
        MenuEntry(Routes.VEHICLES, "🚒", R.string.activity_vehicles, iconRes = R.drawable.ic_firetruck)
    )
    Routes.HUB_GAMES -> listOf(
        MenuEntry(Routes.SHMUP, "🚀", R.string.activity_shmup, iconRes = R.drawable.ic_shmup),
        MenuEntry(Routes.VROUM, "🚗", R.string.activity_vroum, iconRes = R.drawable.ic_vroum),
        MenuEntry(Routes.GARDEN, "✂️", R.string.activity_garden, iconRes = R.drawable.ic_garden),
        MenuEntry(Routes.BUBBLES, "💦", R.string.activity_bubbles, iconRes = R.drawable.ic_bubbles),
        MenuEntry(Routes.STARS, "⭐", R.string.activity_stars, iconRes = R.drawable.ic_star)
    )
    Routes.HUB_CREATE -> listOf(
        MenuEntry(Routes.DRAWING, "🖍️", R.string.activity_drawing, iconRes = R.drawable.ic_hub_create)
    )
    else -> emptyList()
}

fun hubTitleRes(hubRoute: String): Int = TOP_HUBS.firstOrNull { it.route == hubRoute }?.labelRes
    ?: R.string.app_name

/** Sous-menu d'un hub : titre, flèche retour, grille des activités. */
@Composable
fun HubScreen(hubRoute: String, onOpenActivity: (String) -> Unit, onBack: () -> Unit) {
    val children = hubChildren(hubRoute)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
            Text(
                text = stringResource(hubTitleRes(hubRoute)),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = InkColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(children) { index, entry ->
                MenuCard(entry, PastelRainbow[index % PastelRainbow.size]) { onOpenActivity(entry.route) }
            }
        }
    }
}

/** Carte d'activité/hub réutilisable. */
@Composable
fun MenuCard(entry: MenuEntry, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1.05f),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icône dessinée si disponible, sinon emoji (taille réduite si plusieurs)
            if (entry.iconRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(entry.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp)
                )
            } else {
                Text(text = entry.emoji, fontSize = if (entry.emoji.length > 3) 34.sp else 52.sp)
            }
            Text(
                text = stringResource(entry.labelRes),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = InkColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

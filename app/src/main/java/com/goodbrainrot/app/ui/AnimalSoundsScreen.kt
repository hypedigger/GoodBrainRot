package com.goodbrainrot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.data.AnimalSoundPlayer
import com.goodbrainrot.app.data.Animals
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GRID_SIZE = 20

@Composable
fun AnimalSoundsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 20 animaux affichés, tirés du pool de 24
    val tiles = remember {
        mutableStateListOf(*Animals.ALL.shuffled().take(GRID_SIZE).map { it.key }.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose { AnimalSoundPlayer.stop() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tiles) { index, key ->
                val animal = Animals.byKey(key) ?: return@itemsIndexed
                Card(
                    onClick = {
                        AnimalSoundPlayer.play(context, animal, Settings.sayAnimalName.value)
                        // Après le son, la case affiche un autre animal
                        scope.launch {
                            delay(900)
                            val candidates = Animals.ALL.filter { it.key !in tiles }
                            if (candidates.isNotEmpty()) {
                                tiles[index] = candidates.random().key
                            }
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = PastelRainbow[index % PastelRainbow.size]
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimalVisual(
                            animal = animal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.15f),
                            emojiSize = 40
                        )
                        Text(
                            text = stringResource(animal.nameRes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

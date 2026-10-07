package com.goodbrainrot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.goodbrainrot.app.Haptics
import com.goodbrainrot.app.Settings
import com.goodbrainrot.app.audio.Tts
import com.goodbrainrot.app.data.VehicleSounds
import com.goodbrainrot.app.data.Vehicles
import androidx.compose.runtime.DisposableEffect
import com.goodbrainrot.app.ui.theme.InkColor
import com.goodbrainrot.app.ui.theme.PastelRainbow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Imagier des véhicules : grille 3×4, sons synthétisés (sirènes, klaxons,
 * sifflet du train…), et le nom est prononcé après le son si l'option est active.
 */
@Composable
fun VehiclesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { VehicleSounds.stop() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BackButton(onBack = onBack)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(Vehicles.ALL) { index, vehicle ->
                val name = stringResource(vehicle.nameRes)
                Card(
                    onClick = {
                        Haptics.tap(context)
                        // Son réel aléatoire si présent, sinon synthèse
                        VehicleSounds.play(context, vehicle.key) { vehicle.playSound() }
                        if (Settings.sayAnimalName.value) {
                            scope.launch {
                                delay(2100)
                                Tts.speak(name)
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
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = vehicle.emoji, fontSize = 52.sp)
                        Text(
                            text = name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

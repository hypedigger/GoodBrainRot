package com.goodbrainrot.app.data

import androidx.annotation.StringRes
import com.goodbrainrot.app.R
import com.goodbrainrot.app.audio.SoundEngine
import kotlinx.coroutines.delay

/**
 * Imagier des véhicules. Tous les sons sont synthétisés (sirènes, klaxons,
 * sifflets…) : aucun fichier audio nécessaire.
 */
data class Vehicle(
    val key: String,
    val emoji: String,
    @StringRes val nameRes: Int,
    val playSound: () -> Unit
)

object Vehicles {
    val ALL = listOf(
        Vehicle("car", "🚗", R.string.vehicle_car) {
            // Tut-tuut de klaxon
            SoundEngine.sequence {
                SoundEngine.playSweep(440.0, 440.0, 180, 0.8f)
                delay(250)
                SoundEngine.playSweep(440.0, 440.0, 420, 0.8f)
            }
        },
        Vehicle("fire_truck", "🚒", R.string.vehicle_fire_truck) {
            // Pin-pon lent des pompiers
            SoundEngine.sequence {
                repeat(2) {
                    SoundEngine.playSweep(435.0, 435.0, 380, 0.8f)
                    delay(420)
                    SoundEngine.playSweep(580.0, 580.0, 380, 0.8f)
                    delay(420)
                }
            }
        },
        Vehicle("police", "🚓", R.string.vehicle_police) {
            // Sirène rapide
            SoundEngine.sequence {
                repeat(4) {
                    SoundEngine.playSweep(600.0, 800.0, 180, 0.7f)
                    delay(190)
                    SoundEngine.playSweep(800.0, 600.0, 180, 0.7f)
                    delay(190)
                }
            }
        },
        Vehicle("ambulance", "🚑", R.string.vehicle_ambulance) {
            // Sirène modulée montante/descendante
            SoundEngine.sequence {
                repeat(2) {
                    SoundEngine.playSweep(650.0, 950.0, 500, 0.7f)
                    delay(520)
                    SoundEngine.playSweep(950.0, 650.0, 500, 0.7f)
                    delay(520)
                }
            }
        },
        Vehicle("train", "🚂", R.string.vehicle_train) {
            // Sifflet à deux tons : tchou-tchouuu
            SoundEngine.sequence {
                SoundEngine.playSweep(440.0, 440.0, 300, 0.7f)
                SoundEngine.playSweep(554.0, 554.0, 300, 0.5f)
                delay(420)
                SoundEngine.playSweep(440.0, 440.0, 800, 0.7f)
                SoundEngine.playSweep(554.0, 554.0, 800, 0.5f)
            }
        },
        Vehicle("plane", "✈️", R.string.vehicle_plane) {
            // Montée en régime au décollage
            SoundEngine.playSweep(180.0, 750.0, 1400, 0.7f)
        },
        Vehicle("helicopter", "🚁", R.string.vehicle_helicopter) {
            // Battements du rotor
            SoundEngine.sequence {
                repeat(9) {
                    SoundEngine.playSweep(95.0, 80.0, 70, 0.9f)
                    delay(125)
                }
            }
        },
        Vehicle("bike", "🚲", R.string.vehicle_bike) {
            // Dring-dring de sonnette
            SoundEngine.sequence {
                SoundEngine.playXylo(1318.5, 250, 0.8f)
                delay(140)
                SoundEngine.playXylo(1318.5, 400, 0.8f)
            }
        },
        Vehicle("bus", "🚌", R.string.vehicle_bus) {
            // Klaxon grave
            SoundEngine.playSweep(220.0, 220.0, 600, 0.9f)
        },
        Vehicle("tractor", "🚜", R.string.vehicle_tractor) {
            // Teuf-teuf du moteur
            SoundEngine.sequence {
                repeat(7) {
                    SoundEngine.playSweep(110.0, 85.0, 90, 0.9f)
                    delay(160)
                }
            }
        },
        Vehicle("boat", "🚢", R.string.vehicle_boat) {
            // Corne de brume
            SoundEngine.playSweep(130.0, 125.0, 1100, 0.9f)
        },
        Vehicle("rocket", "🚀", R.string.vehicle_rocket) {
            // Décollage !
            SoundEngine.sequence {
                SoundEngine.playSweep(80.0, 200.0, 600, 0.9f)
                delay(550)
                SoundEngine.playSweep(200.0, 1400.0, 1100, 0.7f)
            }
        }
    )
}

/**
 * Paires maman/bébé du jeu « Maman et bébé ». Les clés référencent le pool
 * d'animaux ; la photo du bébé est cherchée dans assets/babies/<key>.*,
 * sinon fallback : petit emoji + biberon.
 */
object MamaPairs {
    val KEYS = listOf("cat", "dog", "hen", "cow", "horse", "sheep", "duck", "frog")
}

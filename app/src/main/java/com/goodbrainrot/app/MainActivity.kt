package com.goodbrainrot.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.goodbrainrot.app.audio.Haptics
import com.goodbrainrot.app.audio.Sfx
import com.goodbrainrot.app.audio.Tts
import com.goodbrainrot.app.data.HighScores
import com.goodbrainrot.app.data.Profiles
import com.goodbrainrot.app.data.Stats
import com.goodbrainrot.app.ui.AppNavHost
import com.goodbrainrot.app.ui.theme.GoodBrainRotTheme

class MainActivity : AppCompatActivity() {

    private var resumeTs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        HighScores.init(this)
        Profiles.init(this)
        Stats.init(this)
        Tts.init(this)
        Haptics.init(this)
        Sfx.init(this)
        setContent {
            GoodBrainRotTheme {
                // safeDrawingPadding : le contenu ne chevauche jamais les barres
                // système quand elles sont visibles (hors baby mode), et occupe
                // tout l'écran quand elles sont masquées (baby mode).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                ) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTs = System.currentTimeMillis()
        applyBabyMode(Settings.babyMode.value)
    }

    override fun onPause() {
        super.onPause()
        // Coupe TOUT l'audio quand l'app passe en arrière-plan (musique + sons).
        com.goodbrainrot.app.audio.GameMusic.pause()
        com.goodbrainrot.app.audio.SoundEngine.stopAll()
        if (resumeTs > 0) {
            Stats.addTime((System.currentTimeMillis() - resumeTs) / 1000)
            resumeTs = 0
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive(forceImmersive || Settings.babyMode.value)
    }

    override fun onDestroy() {
        if (isFinishing) Tts.shutdown()
        super.onDestroy()
    }
}

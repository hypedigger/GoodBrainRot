package com.goodbrainrot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.goodbrainrot.app.R
import com.goodbrainrot.app.openKofi
import com.goodbrainrot.app.ui.theme.InkColor

/**
 * Rappel de soutien (Ko-fi), affiché sur l'accueil au 10e lancement puis tous les 100 lancements.
 * Se ferme d'un tap (« Plus tard » ou à côté de la carte) ; [onClose] reçoit l'état de la case
 * « Ne plus me le demander ».
 */
@Composable
fun SupportDialog(onClose: (optOut: Boolean) -> Unit) {
    val context = LocalContext.current
    var optOut by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onClose(optOut) }) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    stringResource(R.string.support_title),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkColor
                )
                Text(
                    stringResource(R.string.support_body),
                    fontSize = 16.sp, lineHeight = 22.sp, color = InkColor,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { optOut = !optOut }
                        .padding(vertical = 10.dp)
                ) {
                    Checkbox(checked = optOut, onCheckedChange = null)
                    Text(
                        stringResource(R.string.support_dont_ask),
                        fontSize = 14.sp, color = InkColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Button(
                    onClick = { openKofi(context); onClose(optOut) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.support_kofi), fontSize = 16.sp)
                }
                TextButton(
                    onClick = { onClose(optOut) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.support_later), fontSize = 15.sp, color = InkColor)
                }
            }
        }
    }
}

/** Bouton « Soutenir sur Ko-fi » des Options : ouvre la page de don dans le navigateur. */
@Composable
fun SupportButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE8A0C0))
            .clickableNoRipple { openKofi(context) }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.support_kofi),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

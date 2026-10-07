package com.goodbrainrot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.goodbrainrot.app.R
import com.goodbrainrot.app.data.Profiles
import com.goodbrainrot.app.ui.theme.InkColor

/** Barre du profil enfant courant (avatar, prénom, niveau + barre d'XP). */
@Composable
fun ProfileBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val v by Profiles.version
    val p = remember(v) { Profiles.current() }
    val lvl = Profiles.level(p.xp)
    val frac = Profiles.xpInLevel(p.xp).toFloat() / Profiles.xpPerLevel()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEDE3F2))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarIcon(p.avatar, size = 34.dp)
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Bold, color = InkColor, fontSize = 15.sp)
            Text(stringResource(R.string.profile_level_short) + " $lvl", color = InkColor, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                color = Color(0xFFE8A0C0),
                trackColor = Color(0x33000000)
            )
        }
        Text(" ▾", color = InkColor, fontSize = 14.sp)
    }
}

/** Dialog « Qui joue ? » : sélection d'un profil + création. */
@Composable
fun ProfileDialog(onDismiss: () -> Unit) {
    val v by Profiles.version
    val children = remember(v) { Profiles.list.toList() }
    var newName by remember { mutableStateOf("") }
    var newAvatar by remember { mutableStateOf(Profiles.AVATARS.first()) }
    var pendingDelete by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.profile_who_plays),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkColor
                )
                Box(modifier = Modifier.size(12.dp))
                children.forEach { child ->
                    val sel = child.id == Profiles.currentId.value
                    val confirming = child.id == pendingDelete
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    confirming -> Color(0xFFE89AA0).copy(alpha = 0.3f)
                                    sel -> Color(0xFFE8A0C0).copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable(enabled = !confirming) { Profiles.select(child.id); onDismiss() }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarIcon(child.avatar, size = 34.dp)
                        Text(
                            child.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkColor,
                            modifier = Modifier.padding(start = 10.dp).weight(1f)
                        )
                        if (confirming) {
                            Text(
                                stringResource(R.string.profile_delete_confirm),
                                fontSize = 14.sp, color = Color(0xFFB23A4A), fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .clip(CircleShape)
                                    .clickable { Profiles.delete(child.id); pendingDelete = "" }
                                    .padding(6.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .clip(CircleShape)
                                    .clickable { pendingDelete = "" }
                                    .padding(6.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_cross),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.profile_level_short) + " " + Profiles.level(child.xp),
                                fontSize = 13.sp, color = InkColor
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(CircleShape)
                                    .clickable { pendingDelete = child.id }
                                    .padding(6.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.profile_add), fontWeight = FontWeight.Bold, color = InkColor)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_name_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Profiles.AVATARS.forEach { a ->
                        val sel = a == newAvatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (sel) Color(0xFFE8A0C0) else Color(0x22000000))
                                .clickable { newAvatar = a },
                            contentAlignment = Alignment.Center
                        ) { AvatarIcon(a, size = 32.dp) }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF9BD3A0))
                        .clickable {
                            if (newName.isNotBlank()) { Profiles.add(newName.trim(), newAvatar); onDismiss() }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(20.dp)
                        )
                        Text(stringResource(R.string.profile_add), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

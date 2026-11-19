package com.hydro.reedance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Local builds have no verified entitlement provider; never imply that a purchase succeeded. */
@Composable internal fun ProfileMembershipCard(onUpgrade: () -> Unit, clickableCard: Boolean = false) {
    val gold = Color(0xFFFFDF80)
    val ink = Color(0xFF42310B)
    Surface(Modifier.fillMaxWidth().then(if(clickableCard) Modifier.clickable(onClick = onUpgrade) else Modifier),
        shape = RoundedCornerShape(22.dp), color = gold, contentColor = ink) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(UiIcons.CrownFilled, null, Modifier.size(32.dp), tint = ink)
                Text(tr("عضویت پریمیوم", "Premium membership"), style = MaterialTheme.typography.titleLarge)
            }
            Text(tr("با پریمیوم، فراتر یاد بگیر.", "Take your learning further with Premium."))
            PressButton(onUpgrade, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = gold)) {
                Text(tr("تهیهٔ پریمیوم", "Get Premium"))
            }
            Text(tr("خرید اشتراک هنوز فعال نیست.", "Membership checkout is not available yet."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun ProfileStarterCard(starter: () -> Unit) {
    Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("اولین قدمت را بردار", "A little start. A whole new language."), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            StarterArtwork(Modifier.size(88.dp))
        }
        Text(tr("از حروف و صداها تا اولین سطر؛ انگلیسی، اسپانیایی و کره‌ای.", "Letters, sounds and your very first line. English, Spanish and Korean."))
        PressButton(starter, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
            Text(tr("ورود به استارتر", "Open Starter"))
        }
    }
}

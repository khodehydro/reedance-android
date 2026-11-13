package com.hydro.reedance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.R
import com.hydro.reedance.data.*
import com.google.gson.JsonObject

fun cover(id: String) = when(id) {
    "despacito" -> R.drawable.despacito; "spring-day" -> R.drawable.spring_day; "flowers" -> R.drawable.flowers
    "someone-like-you" -> R.drawable.someone_like_you; "blinding-lights" -> R.drawable.blinding_lights; else -> R.drawable.midnight_avenue
}
@Composable fun CatalogScreen(model: AppModel, home: Boolean, openSong: (String) -> Unit, starter: () -> Unit) {
    val catalog by model.catalog.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableIntStateOf(0) }
    var access by rememberSaveable { mutableIntStateOf(0) }
    val fa = LocalFa.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if(home) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Blue, Deep))).padding(26.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tr("یادگیری با ریتم تو", "YOUR RHYTHM. YOUR WORDS."), color = Color(0xFFCADFFF), style = MaterialTheme.typography.labelMedium)
                    Text(tr("هر آهنگ،\nیک قدم جلوتر.", "Every song.\nOne step further."), color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    Text(tr("گوش بده، کشف کن و زبان را زندگی کن.", "Listen closer. Discover more. Make the words yours."), color = Color(0xFFE1ECFF))
                    Button({ openSong("midnight-avenue") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue)) { Text(tr("شروع یادگیری", "Start learning")); Icon(Icons.Rounded.PlayArrow, null) }
                }
            }
            Panel(tone = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tr("اولین قدمت را بردار", "A little start. A whole new language."), style = MaterialTheme.typography.titleLarge)
                Text(tr("از حروف و صداها تا اولین سطر؛ انگلیسی، اسپانیایی و کره‌ای.", "Letters, sounds and your very first line. English, Spanish and Korean."))
                TextButton(starter) { Text(tr("ورود به استارتر", "Open Starter")) }
            }
        }
        Heading(if(home) tr("آهنگ بعدی تو", "Find your next song") else tr("کشف موسیقی", "Discover music"), tr("موسیقی را بشنو، کلمه‌ها را کشف کن.", "Good music. Words that stay with you."))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(tr("جست‌وجوی آهنگ یا هنرمند", "Search song or artist")) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        ChoiceRow(listOf(tr("همه زبان‌ها", "All languages"), "English", "Español", "한국어"), language) { language = it }
        ChoiceRow(listOf(tr("همه", "All"), tr("رایگان", "Free"), tr("پولی", "Premium"), tr("قابل تجربه", "Playable demo")), access) { access = it }
        val filtered = catalog.filter { s -> (language == 0 || s.s("language") == listOf("", "en", "es", "ko")[language]) && (query.isBlank() || (s.s("title") + s.s("artist")).contains(query, true)) && when(access) { 1 -> s.s("access") == "free"; 2 -> s.s("access") == "paid"; 3 -> s.s("id") == "midnight-avenue"; else -> true } }
        filtered.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            pair.forEach { song -> SongTile(song, Modifier.weight(1f)) { openSong(song.s("id")) } }
            if(pair.size == 1) Spacer(Modifier.weight(1f))
        } }
        if(filtered.isEmpty()) Panel { Text(tr("آهنگی پیدا نشد؛ فیلترها را تغییر بده.", "No songs found. Try another filter.")) }
        Text(tr("فقط Midnight Avenue دموی محلی دارد. بقیهٔ عنوان‌ها پیش‌نمایش کاتالوگ‌اند، نه محتوای قابل خرید یا دانلود.", "Only Midnight Avenue has a local demo. Other titles are catalog previews, not purchasable or downloadable content."), style = MaterialTheme.typography.bodySmall)
        Text("developed with ❤️ by HYDRO team", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
}
@Composable private fun SongTile(song: JsonObject, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Image(painterResource(cover(song.s("id"))), song.s("title"), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .9f)) {
                Icon(if(song.s("access") == "free") Icons.Rounded.LockOpen else Icons.Rounded.Lock, tr("ردهٔ دسترسی", "Access tier"), Modifier.padding(6.dp).size(18.dp), tint = Deep)
            }
        }
        Foreign(song.s("title"))
        Text(song.s("artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${when(song.s("language")) { "es" -> "🇪🇸"; "ko" -> "🇰🇷"; else -> "🇬🇧" }}  ${song.s("level")}  ·  ${song.s("duration")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

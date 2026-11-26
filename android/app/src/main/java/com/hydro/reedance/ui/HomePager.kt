package com.hydro.reedance.ui

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hydro.reedance.R
import java.time.Instant
import java.time.ZoneId

internal data class PersianMonth(val year: Int, val month: Int, val today: Int, val offset: Int, val days: List<Long>)
internal fun currentPersianMonth(now: Long = System.currentTimeMillis()): PersianMonth {
    val zone = ZoneId.systemDefault()
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone.id), ULocale("fa_IR@calendar=persian"))
    calendar.timeInMillis = now
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val today = calendar.get(Calendar.DAY_OF_MONTH)
    val count = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 12)
    val offset = calendar.get(Calendar.DAY_OF_WEEK) % 7 // Saturday first.
    val days = (1..count).map { day ->
        calendar.set(Calendar.DAY_OF_MONTH, day)
        Instant.ofEpochMilli(calendar.timeInMillis).atZone(zone).toLocalDate().toEpochDay()
    }
    return PersianMonth(year, month, today, offset, days)
}

@Composable internal fun HomePager(openDialogues: (Int) -> Unit) {
    val pages = remember {
        dialogueCategories.mapIndexedNotNull { index, category ->
            val artwork = when(category.artwork) {
                R.drawable.dialogue_art_daily -> R.drawable.reetalk_home_cafe
                R.drawable.dialogue_art_travel -> R.drawable.reetalk_home_travel
                else -> null
            }
            artwork?.let { index to it }
        }
    }
    if(pages.isEmpty()) return
    val pager = rememberPagerState { pages.size }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).aspectRatio(.84f),
            pageSpacing = 12.dp, key = { pages[it].first }) { page ->
            val (index, artwork) = pages[page]
            val category = dialogueCategories[index]
            val title = tr(category.fa, category.en)
            val label = if(category.premium) tr("$title، پریمیوم", "$title, Premium") else title
            Surface(onClick = { openDialogues(index) }, modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
                shape = HeroShape, color = colors.surfaceContainer) {
                Box {
                    DialogueCover(artwork, Modifier.matchParentSize())
                    Surface(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(44.dp),
                        shape = CircleShape, color = Color.Black.copy(alpha = .54f), contentColor = Color.White) {
                        Box(contentAlignment = Alignment.Center) {
                            if(category.premium) AppIcon(UiIcons.CrownFilled, null, Modifier.size(20.dp))
                            else Text("+", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
        if(pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
            repeat(pages.size) { index ->
                val selected = pager.currentPage == index
                Box(Modifier.size(width = if(selected) 15.dp else 5.dp, height = 5.dp).background(
                    if(selected) colors.onSurface else colors.outlineVariant, CircleShape))
            }
        }
    }
}

@Composable internal fun WelcomeBackdrop(animate: Boolean, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Brush.linearGradient(listOf(Blue, Deep)))) {
        BannerGradient(animate, Modifier.matchParentSize())
        content()
    }
}

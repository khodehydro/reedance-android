package com.hydro.reedance.ui

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.ACTIVE_DAY_MILLIS
import com.hydro.reedance.data.AppModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@Composable fun HomePager(model: AppModel, openSong: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(420.dp).onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = bounds.width > 0f && bounds.height > 0f
            }, pageSpacing = 12.dp,
            key = { if(it == 0) "welcome" else "activity" }) { page ->
            if(page == 0) WelcomeBanner(openSong, animate = visible && pager.currentPage == 0) else ActivityCalendar(model)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(tr("شروع یادگیری", "Welcome"), tr("فعالیت این ماه", "This month's activity")).forEachIndexed { index, label ->
                PressTextButton({ scope.launch { if(reduced) pager.scrollToPage(index) else pager.animateScrollToPage(index) } }) {
                    Text((if(pager.currentPage == index) "● " else "○ ") + label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable private fun WelcomeBanner(openSong: (String) -> Unit, animate: Boolean) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Blue, Deep)))) {
        BannerGradient(animate, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("یادگیری با ریتم تو", "YOUR RHYTHM. YOUR WORDS."), color = Color(0xFFCADFFF), style = MaterialTheme.typography.labelMedium)
            Text(tr("هر آهنگ،\nیک قدم جلوتر.", "Every song.\nOne step further."), color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(tr("گوش بده، کشف کن و زبان را زندگی کن.", "Listen closer. Discover more. Make the words yours."), color = Color(0xFFE1ECFF))
            PressButton({ openSong("midnight-avenue") }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Blue)) {
                Text(tr("شروع یادگیری", "Start learning")); AppIcon(UiIcons.Play, null)
            }
        }
    }
}

@Composable private fun ActivityCalendar(model: AppModel) {
    val ledger by model.activityDays.collectAsStateWithLifecycle()
    val initialMonth = remember { currentPersianMonth() }
    val month by produceState(initialMonth) {
        while(true) { value = currentPersianMonth(); delay(30_000) }
    }
    val names = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val english = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading(tr("فعالیت این ماه", "This month's activity"), "${if(LocalFa.current) names[month.month] else english[month.month]} ${month.year}")
            Row(Modifier.fillMaxWidth()) {
                listOf(tr("ش", "Sa"), tr("ی", "Su"), tr("د", "Mo"), tr("س", "Tu"), tr("چ", "We"), tr("پ", "Th"), tr("ج", "Fr")).forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
            val rows = (month.offset + month.days.size + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { column ->
                        val index = row * 7 + column - month.offset
                        if(index !in month.days.indices) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else {
                            val day = index + 1
                            val active = (ledger[month.days[index]] ?: 0L) >= ACTIVE_DAY_MILLIS
                            val future = day > month.today
                            val status = when { future -> tr("آینده", "Upcoming"); active -> tr("فعال", "Active"); day < month.today -> tr("بدون فعالیت کافی", "Not enough activity"); else -> tr("در انتظار فعالیت", "Activity pending") }
                            val color = when { future -> MaterialTheme.colorScheme.surfaceVariant; active -> Color(0xFF16834B); day < month.today -> Color(0xFFB83B45); else -> MaterialTheme.colorScheme.surface }
                            Box(Modifier.weight(1f).aspectRatio(1f).background(color, RoundedCornerShape(4.dp))
                                .semantics(mergeDescendants = true) { contentDescription = "$day ${names[month.month]} ${month.year}: $status" }, contentAlignment = Alignment.Center) {
                                Text("$day", style = MaterialTheme.typography.labelSmall, color = if(!future && (active || day < month.today)) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            Text(tr("سبز: فعال · قرمز: بدون فعالیت کافی · خاکستری: آینده", "Green: active · Red: not enough activity · Grey: upcoming"), style = MaterialTheme.typography.labelSmall)
            Text(tr("۲ دقیقه استفاده در برنامه یا تکمیل یک تمرین یا مرور، روزت را سبز می‌کند.", "Use the app for 2 minutes or complete a practice or review to turn your day green."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

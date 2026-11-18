package com.hydro.reedance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hydro.reedance.R
import com.hydro.reedance.data.AppModel
import kotlinx.coroutines.launch

private data class IntroPage(val art: Int, val title: String, val detail: String)

@Composable internal fun Setup(model: AppModel) {
    val pages = listOf(
        IntroPage(R.drawable.uicon_lyrics,
            tr("با آهنگ زبان یاد بگیر", "Learn with music"),
            tr("هم‌زمان با پخش آهنگ، متن و ترجمه را دنبال کن.", "Follow the lyrics and translation as the song plays.")),
        IntroPage(R.drawable.uicon_review,
            tr("واژه‌ها را تمرین کن", "Make new words stick"),
            tr("واژه‌های تازه را ذخیره کن، مرور کن و خودت را بسنج.", "Save new words, review them, and test yourself.")),
        IntroPage(R.drawable.uicon_globe,
            tr("فرهنگ پشت واژه‌ها", "Discover the culture behind the words"),
            tr("شناخت فرهنگ و زندگی مردم کمک می‌کند اصطلاح‌ها را بهتر بفهمی.", "Knowing how people live and speak helps you understand their expressions.")),
        IntroPage(R.drawable.uicon_original,
            tr("معنیِ واقعیِ ترانه", "Understand what the lyrics really say"),
            tr("بعضی ترانه‌ها عبارت‌های رکیک دارند. معنی و لحن اصلی را یاد بگیر تا بدانی کجا نباید از آن‌ها استفاده کنی.", "Some songs use crude language. Learn what it means and how it sounds so you know when not to use it."))
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("آشنایی با ری‌دنس", "Meet Reedance"), style = MaterialTheme.typography.titleMedium)
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages[index]
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val artSize = (maxHeight * .48f).coerceIn(72.dp, 220.dp)
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(artSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(page.art), null, Modifier.size(artSize * .48f),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(page.detail, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pages.size) { index ->
                val active = index == pager.currentPage
                Box(Modifier.size(width = if(active) 20.dp else 8.dp, height = 8.dp).background(
                    if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.height(16.dp))
        Action(tr(if(last) "شروع یادگیری" else "بعدی", if(last) "Start learning" else "Next"),
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp),
            enabled = !pager.isScrollInProgress) {
            if(last) model.completePermissionSetup()
            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

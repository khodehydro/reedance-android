package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.LessonAccess
import com.hydro.reedance.domain.lineLearning
import kotlinx.coroutines.launch

@Composable internal fun LineLearningSheet(model: AppModel, lesson: JsonObject, line: JsonObject, access: LessonAccess, dismiss: () -> Unit) {
    val learning = remember(lesson, line, access) { lineLearning(lesson, line, access) }
    val labels = listOf(tr("گرامر", "Grammar"), tr("نکات", "Notes"), tr("لغات", "Words"))
    val first = when {
        learning.grammar.isNotEmpty() -> 0
        learning.phrases.isNotEmpty() -> 1
        else -> 2
    }
    val pager = rememberPagerState(initialPage = first) { labels.size }
    val scope = rememberCoroutineScope()
    ReedanceBottomSheet(dismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("یادگیری این خط", "Learn this line"), style = MaterialTheme.typography.titleLarge)
            Foreign(line.s("text"), large = true)
            PersianText(line.tr("translation", LearningLanguage.persian),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpandedPillTabs(labels.map { PillOption(it) }, pager.currentPage) { target ->
                scope.launch { pager.animateScrollToPage(target) }
            }
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    when(page) {
                        0 -> items(learning.grammar) { note -> LearningNote(note) { SaveAction(model, "grammar", note.s("id")) } }
                        1 -> items(learning.phrases) { (type, phrase) -> Panel {
                            PhraseLearningContent(phrase)
                            SaveAction(model, type, phrase.s("id"))
                        } }
                        2 -> items(learning.words) { word -> Panel {
                            WordLearningContent(word)
                            if(word.s("lexemeId").isNotBlank()) SaveAction(model, "vocabulary", word.s("lexemeId"))
                        } }
                    }
                    val empty = when(page) {
                        0 -> learning.grammar.isEmpty()
                        1 -> learning.phrases.isEmpty()
                        else -> learning.words.isEmpty()
                    }
                    if(empty) item { Text(tr("برای این خط در این بخش محتوایی در دسترس نیست.", "Nothing is available for this line in this section."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

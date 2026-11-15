package com.hydro.reedance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StarterScreen(model: AppModel, code: String) {
    val courses by model.starters.collectAsStateWithLifecycle()
    val progress by model.steps.collectAsStateWithLifecycle()
    var step by rememberSaveable(code) { mutableIntStateOf(0) }
    val course = courses.obj(code)
    val fa = LearningLanguage.persian
    val names = listOf(tr("حروف", "Alphabet"), tr("صداها", "Sounds"), tr("گرامر", "Grammar"), tr("واژه‌ها", "Words"), tr("اولین سطر", "First line"))
    val done = progress.count { it.targetLanguage == code }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "controls") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Heading(tr("استارتر", "A small start"), course.tr("intro", fa)) }
            StarterArtwork(Modifier.size(96.dp))
        }
        LinearProgressIndicator(progress = { done / 5f }, modifier = Modifier.fillMaxWidth())
        Text(tr("$done از ۵ قدم", "$done of 5 steps"))
        ChoiceRow(names.mapIndexed { i, text -> if(progress.any { it.targetLanguage == code && it.stepId == i }) "✓ $text" else text }, step, listOf(UiIcons.Original to UiIcons.OriginalFilled, UiIcons.Volume to UiIcons.VolumeFilled, UiIcons.Notes to UiIcons.NotesFilled, UiIcons.Words to UiIcons.WordsFilled, UiIcons.Lines to UiIcons.LinesFilled)) { step = it }
        } }
        when(step) {
            0 -> {
                item(key = "alphabet-heading") { Heading(course.tr("alphabet", fa), course.tr("alphabetTip", fa)) }
                items(course.a("alphabetGroups"), contentType = { "alphabet" }) { group ->
                    Panel {
                        Text(group.tr("name", fa), style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.a("letters").forEach { letter ->
                                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) {
                                    Text(letter.s("glyph"), style = MaterialTheme.typography.titleLarge)
                                    Text(letter.s("name"), style = MaterialTheme.typography.bodySmall)
                                    if(fa) Text(letter.s("faHint"), style = MaterialTheme.typography.bodySmall)
                                } }
                            }
                        }
                    }
                }
            }
            1 -> items(course.a("sounds"), contentType = { "sound" }) { sound -> Panel {
                Foreign(sound.s("symbol"), large = true); Foreign(sound.s("example")); Foreign(sound.s("reading"))
                if(fa) Text(sound.s("faReading"))
                Text(sound.tr("tip", fa))
            } }
            2 -> items(course.a("grammar"), contentType = { "grammar" }) { note -> LearningNote(note) }
            3 -> {
                item(key = "words-intro") { Text(course.tr("wordsIntro", fa)) }
                items(course.a("words"), contentType = { "word" }) { word -> Panel {
                    Foreign(word.s("text").ifEmpty { word.s("word") }, large = true)
                    Text(word.tr("translation", fa).ifEmpty { word.s(if(fa) "fa" else "en") })
                    Foreign(word.s("reading")); if(fa) Text(word.s("faReading"))
                    if(word.s("article").isNotEmpty()) Text("${word.s("article")} · ${word.tr("gender", fa)} · ${word.s("plural")}")
                    Text(word.tr("part", fa), style = MaterialTheme.typography.labelMedium)
                    Foreign(word.s("example"))
                    Text(word.tr("example", fa))
                } }
                items(course.a("vocabularyTables"), contentType = { "table" }) { Panel { LearningTable(it) } }
            }
            4 -> item(key = "practice") { Panel {
                val practice = course.obj("practice")
                Foreign(practice.s("line"), large = true)
                if(fa) Text(practice.s("faReading"))
                Text(practice.tr("translation", fa))
                practice.a("pieces").forEach { part -> Foreign(part.s("text")); Text(part.s(if(fa) "fa" else "en")); HorizontalDivider() }
                Text(practice.tr("tip", fa))
            } }
        }
        item(key = "completion") { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Action(if(progress.any { it.targetLanguage == code && it.stepId == step }) tr("انجام‌شده ✓ (لغو)", "Completed ✓ (undo)") else tr("این قدم را یاد گرفتم", "Mark this step complete"), Modifier.fillMaxWidth()) { model.step(code, step) }
        if(step < 4) PressTextButton({ step++ }) { Text(tr("قدم بعدی", "Next step")) }
        Text(tr("راهنمای فارسی تقریب تلفظ است. پخش صدا به صدای آفلاین نصب‌شدهٔ دستگاه بستگی دارد.", "Pronunciation hints are approximate. Audio requires an installed offline device voice."), style = MaterialTheme.typography.bodySmall)
        } }
    }
}

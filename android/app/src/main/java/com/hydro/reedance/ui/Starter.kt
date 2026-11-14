package com.hydro.reedance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.reedance.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StarterScreen(model: AppModel) {
    val courses by model.starters.collectAsStateWithLifecycle()
    val progress by model.steps.collectAsStateWithLifecycle()
    var language by rememberSaveable { mutableIntStateOf(0) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val code = listOf("en", "es", "ko")[language]
    val course = courses.obj(code)
    val fa = LocalFa.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalOverlayBottomInset.current), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Heading(tr("استارتر", "A small start"), course.tr("intro", fa))
        ChoiceRow(listOf("English", "Español", "한국어"), language) { language = it; step = 0 }
        val names = listOf(tr("حروف", "Alphabet"), tr("صداها", "Sounds"), tr("گرامر", "Grammar"), tr("واژه‌ها", "Words"), tr("اولین سطر", "First line"))
        val done = progress.count { it.targetLanguage == code }
        LinearProgressIndicator(progress = { done / 5f }, modifier = Modifier.fillMaxWidth())
        Text(tr("$done از ۵ قدم", "$done of 5 steps"))
        ChoiceRow(names.mapIndexed { i, text -> if(progress.any { it.targetLanguage == code && it.stepId == i }) "✓ $text" else text }, step) { step = it }
        when(step) {
            0 -> {
                Heading(course.tr("alphabet", fa), course.tr("alphabetTip", fa))
                course.a("alphabetGroups").forEach { group ->
                    Panel {
                        Text(group.tr("name", fa), style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.a("letters").forEach { letter ->
                                PressOutlinedButton({ model.speak(letter.s("glyph"), course.s("locale")) }) { Column {
                                    Text(letter.s("glyph"), style = MaterialTheme.typography.titleLarge)
                                    Text(letter.s("name"), style = MaterialTheme.typography.bodySmall)
                                    if(fa) Text(letter.s("faHint"), style = MaterialTheme.typography.bodySmall)
                                } }
                            }
                        }
                    }
                }
            }
            1 -> course.a("sounds").forEach { sound -> Panel {
                Foreign(sound.s("symbol"), large = true); Foreign(sound.s("example")); Foreign(sound.s("reading"))
                if(fa) Text(sound.s("faReading"))
                Text(sound.tr("tip", fa))
                PressTextButton({ model.speak(sound.s("example"), course.s("locale")) }) { Text(tr("شنیدن تلفظ", "Hear pronunciation")) }
            } }
            2 -> course.a("grammar").forEach { note -> LearningNote(note, { model.speak(it, course.s("locale")) }) }
            3 -> {
                Text(course.tr("wordsIntro", fa))
                course.a("words").forEach { word -> Panel {
                    Foreign(word.s("text").ifEmpty { word.s("word") }, large = true)
                    Text(word.tr("translation", fa).ifEmpty { word.s(if(fa) "fa" else "en") })
                    Foreign(word.s("reading")); if(fa) Text(word.s("faReading"))
                    if(word.s("article").isNotEmpty()) Text("${word.s("article")} · ${word.tr("gender", fa)} · ${word.s("plural")}")
                    Text(word.tr("part", fa), style = MaterialTheme.typography.labelMedium)
                    Foreign(word.s("example"))
                    Text(word.tr("example", fa))
                    PressTextButton({ model.speak(word.s("text").ifEmpty { word.s("word") }, course.s("locale")) }) { Text(tr("شنیدن", "Listen")) }
                } }
                course.a("vocabularyTables").forEach { Panel { LearningTable(it) } }
            }
            4 -> Panel {
                val practice = course.obj("practice")
                Foreign(practice.s("line"), large = true)
                if(fa) Text(practice.s("faReading"))
                Text(practice.tr("translation", fa))
                PressTextButton({ model.speak(practice.s("line"), course.s("locale")) }) { Text(tr("شنیدن سطر", "Hear the line")) }
                practice.a("pieces").forEach { part -> Foreign(part.s("text")); Text(part.s(if(fa) "fa" else "en")); HorizontalDivider() }
                Text(practice.tr("tip", fa))
            }
        }
        Action(if(progress.any { it.targetLanguage == code && it.stepId == step }) tr("انجام‌شده ✓ (لغو)", "Completed ✓ (undo)") else tr("این قدم را یاد گرفتم", "Mark this step complete"), Modifier.fillMaxWidth()) { model.step(code, step) }
        if(step < 4) PressTextButton({ step++ }) { Text(tr("قدم بعدی", "Next step")) }
        Text(tr("راهنمای فارسی تقریب تلفظ است. پخش صدا به صدای آفلاین نصب‌شدهٔ دستگاه بستگی دارد.", "Pronunciation hints are approximate. Audio requires an installed offline device voice."), style = MaterialTheme.typography.bodySmall)
    }
}

package com.hydro.reedance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydro.reedance.data.AppModel
import com.hydro.reedance.domain.GameWordDefinition
import com.hydro.reedance.domain.HoneycombLetterBoard
import com.hydro.reedance.domain.areHoneycombNeighbors
import com.hydro.reedance.domain.gameWordKey
import kotlinx.coroutines.delay

@Composable internal fun HoneycombPuzzle(
    model: AppModel,
    targetLanguage: String,
    board: HoneycombLetterBoard,
    roundIndex: Int,
    targets: List<GameWordDefinition>,
    solvedTargetKeys: Set<String>,
    solvedCellIndices: Map<String, List<Int>>,
    wordColors: Map<String, SolvedWordColor>
) {
    val boardKey = remember(targets) { targets.joinToString("|") { gameWordKey(it.songId, it.lexemeId) } }
    var selected by rememberSaveable(boardKey) { mutableStateOf(emptyList<Int>()) }
    var feedback by rememberSaveable(boardKey) { mutableStateOf("") }
    var pendingWinKey by rememberSaveable(boardKey) { mutableStateOf("") }
    var pendingWinCells by rememberSaveable(boardKey) { mutableStateOf(emptyList<Int>()) }
    var rejecting by rememberSaveable(boardKey) { mutableStateOf(false) }
    val correctFeedback = tr("آفرین! واژه پیدا شد", "Word found!")
    val incorrectFeedback = tr("این مسیر مناسب نیست؛ مسیر دیگری را امتحان کن", "Try a different path for this word.")

    LaunchedEffect(rejecting, boardKey) {
        if(rejecting) {
            delay(480)
            selected = emptyList()
            feedback = ""
            rejecting = false
        }
    }
    LaunchedEffect(solvedTargetKeys, boardKey) {
        selected = emptyList()
        pendingWinKey = ""
        pendingWinCells = emptyList()
        feedback = ""
    }

    val solvedColors = remember(solvedCellIndices, wordColors) {
        solvedCellIndices.flatMap { (word, cells) ->
            wordColors[word]?.tile?.let { color -> cells.map { it to color } }.orEmpty()
        }.groupBy({ it.first }, { it.second })
    }
    val winningCells = remember(pendingWinCells) { pendingWinCells.toSet() }
    val winningColor = wordColors[pendingWinKey]
    val canInteract = pendingWinKey.isEmpty() && !rejecting

    Panel {
        Column(Modifier.fillMaxWidth()) {
            Text(tr("حروف را به هم وصل کن", "Connect the letters"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                tr("واژه‌ها را به هر ترتیبی پیدا کن؛ خانهٔ آخر را دوباره لمس کن تا حذف شود", "Find words in any order; tap the last hex again to undo"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(NestedTileShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)).padding(10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                selected.map { board.letters[it].toString().uppercase() }.joinToString(" ").ifEmpty { "····" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HoneycombBoard(
            board = board,
            selectedCells = selected.toSet(),
            winningCells = winningCells,
            solvedColors = solvedColors,
            winningColor = winningColor?.tile,
            roundIndex = roundIndex,
            targetLanguage = targetLanguage,
            canInteract = canInteract
        ) { index ->
            if(!canInteract) return@HoneycombBoard
            if(selected.lastOrNull() == index) {
                selected = selected.dropLast(1)
                feedback = ""
                GameSounds.release()
                return@HoneycombBoard
            }
            if(index in selected) {
                selected = selected.take(selected.indexOf(index) + 1)
                feedback = ""
                GameSounds.release()
                return@HoneycombBoard
            }
            val nextSelection = when {
                selected.isEmpty() -> listOf(index)
                areHoneycombNeighbors(selected.last(), index, board.columns) -> selected + index
                else -> listOf(index)
            }
            selected = nextSelection
            feedback = ""
            GameSounds.connect()
            val guess = nextSelection.map { board.letters[it] }.joinToString("")
            val solvedKeys = solvedTargetKeys + pendingWinKey
            val candidates = targets.filter { target ->
                val wordKey = gameWordKey(target.songId, target.lexemeId)
                wordKey !in solvedKeys &&
                    target.letterAnswer.startsWith(guess, ignoreCase = true)
            }
            val match = candidates.firstNotNullOfOrNull { target ->
                if(target.letterAnswer.length != guess.length) null
                else board.acceptedSolvedPath(gameWordKey(target.songId, target.lexemeId), nextSelection)
                    ?.let { target to it }
            }
            if(match != null) {
                val (word, drawnPath) = match
                val matchKey = gameWordKey(word.songId, word.lexemeId)
                pendingWinKey = matchKey
                pendingWinCells = drawnPath
                feedback = correctFeedback
                GameSounds.win()
                model.solveGameWord(
                    targetLanguage,
                    word.songId,
                    word.lexemeId,
                    revealDelayMs = 620,
                    usedCellIndices = drawnPath
                )
            } else {
                if(candidates.isEmpty()) {
                    rejecting = true
                    feedback = incorrectFeedback
                    GameSounds.incorrect()
                }
            }
        }
        if(feedback.isNotBlank()) Text(
            feedback,
            color = winningColor?.ink() ?: MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable private fun HoneycombBoard(
    board: HoneycombLetterBoard,
    selectedCells: Set<Int>,
    winningCells: Set<Int>,
    solvedColors: Map<Int, List<Color>>,
    winningColor: Color?,
    roundIndex: Int,
    targetLanguage: String,
    canInteract: Boolean,
    onTap: (Int) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var previousRound by rememberSaveable(targetLanguage) { mutableIntStateOf(roundIndex) }
    val wave = remember(targetLanguage, roundIndex, reducedMotion) {
        Animatable(if(!reducedMotion && roundIndex > previousRound) 0f else 1f)
    }
    LaunchedEffect(wave) {
        previousRound = roundIndex
        if(wave.value < 1f) wave.animateTo(1f, tween(650, easing = LinearEasing))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = board.columns
        val lightTheme = MaterialTheme.colorScheme.background.luminance() > .5f
        val blueRange = if(lightTheme) listOf(
            Color(0xFFDCEBFF), Color(0xFFCBE0FB), Color(0xFFBBD7F5), Color(0xFFE7F1FF)
        ) else listOf(
            Color(0xFF284A70), Color(0xFF315987), Color(0xFF3B6794), Color(0xFF244366)
        )
        val idleLetterColor = if(lightTheme) Color(0xFF183653) else Color(0xFFEAF3FF)
        val tileWidth = minOf(42.dp, (maxWidth - 8.dp) / (columns + .5f))
        val tileHeight = tileWidth * 1.12f
        val rowStep = tileHeight * .75f
        val boardWidth = tileWidth * (columns + .5f)
        val boardHeight = tileHeight + rowStep * (board.rows - 1).coerceAtLeast(0)
        Box(Modifier.fillMaxWidth().height(boardHeight), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(boardWidth).height(boardHeight)) {
                board.letters.forEachIndexed { index, letter ->
                    val row = index / columns
                    val column = index % columns
                    val waveDelay = .60f * (row + column).toFloat() / (board.rows + columns - 2).coerceAtLeast(1)
                    val selected = index in selectedCells
                    val winning = index in winningCells
                    val previousColors = solvedColors[index].orEmpty()
                    val solvedColor = previousColors.firstOrNull()
                    val color = animateColorAsState(
                        targetValue = when {
                            winning && winningColor != null -> winningColor
                            selected -> MaterialTheme.colorScheme.primary
                            solvedColor != null -> solvedColor
                            else -> blueRange[index % blueRange.size]
                        },
                        label = "honeycomb-cell-$index"
                    )
                    val textColor = animateColorAsState(
                        targetValue = when {
                            winning -> Color.White
                            selected -> MaterialTheme.colorScheme.onPrimary
                            solvedColor != null -> Color.White
                            else -> idleLetterColor
                        },
                        label = "honeycomb-letter-$index"
                    )
                    val scale by animateFloatAsState(if(selected || winning) 1.035f else 1f, label = "honeycomb-scale-$index")
                    val ready by remember(wave, waveDelay) { derivedStateOf { wave.value >= waveDelay + .39f } }
                    Box(
                        Modifier.offset(
                            x = tileWidth * column.toFloat() + if(row % 2 == 1) tileWidth * .5f else 0.dp,
                            y = rowStep * row.toFloat()
                        ).size(tileWidth, tileHeight)
                            .graphicsLayer {
                                // A shared clock sends the entrance diagonally across the grid.
                                val t = ((wave.value - waveDelay) / .39f).coerceIn(0f, 1f)
                                val eased = FastOutSlowInEasing.transform(t)
                                alpha = eased
                                translationY = (1f - eased) * 12.dp.toPx() - 4f * eased * (1f - eased) * 9.dp.toPx()
                                scaleX = scale * (.78f + .22f * eased)
                                scaleY = scaleX
                            }
                            .shadow(if(selected || winning) 5.dp else 2.dp, HexagonShape)
                            .clip(HexagonShape)
                            .drawBehind {
                                if(!winning && !selected && previousColors.size > 1) {
                                    val stripe = size.width / previousColors.size
                                    previousColors.forEachIndexed { i, ink ->
                                        drawRect(ink, Offset(i * stripe, 0f), Size(stripe, size.height))
                                    }
                                } else drawRect(color.value)
                            }
                            .border(1.5.dp, Color.White.copy(alpha = if(lightTheme) .95f else .78f), HexagonShape)
                            .clickable(enabled = canInteract && ready) { onTap(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        HoneycombLetter(letter, textColor)
                    }
                }
            }
        }
    }
}

private val HexagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * .5f, h * .035f)
    cubicTo(w * .535f, h * .035f, w * .56f, h * .05f, w * .59f, h * .07f)
    lineTo(w * .91f, h * .25f)
    cubicTo(w * .96f, h * .28f, w * .975f, h * .31f, w * .975f, h * .36f)
    lineTo(w * .975f, h * .64f)
    cubicTo(w * .975f, h * .69f, w * .96f, h * .72f, w * .91f, h * .75f)
    lineTo(w * .59f, h * .93f)
    cubicTo(w * .56f, h * .95f, w * .535f, h * .965f, w * .5f, h * .965f)
    cubicTo(w * .465f, h * .965f, w * .44f, h * .95f, w * .41f, h * .93f)
    lineTo(w * .09f, h * .75f)
    cubicTo(w * .04f, h * .72f, w * .025f, h * .69f, w * .025f, h * .64f)
    lineTo(w * .025f, h * .36f)
    cubicTo(w * .025f, h * .31f, w * .04f, h * .28f, w * .09f, h * .25f)
    lineTo(w * .41f, h * .07f)
    cubicTo(w * .44f, h * .05f, w * .465f, h * .035f, w * .5f, h * .035f)
    close()
}

// Only the changing glyph observes its foreground animation in composition.
@Composable private fun HoneycombLetter(letter: Char, color: State<Color>) {
    Text(letter.toString().uppercase(), color = color.value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

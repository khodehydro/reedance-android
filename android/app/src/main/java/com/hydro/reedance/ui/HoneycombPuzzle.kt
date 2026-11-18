package com.hydro.reedance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    targets: List<GameWordDefinition>,
    solvedTargetKeys: Set<String>,
    solvedCellIndices: Map<String, List<Int>>
) {
    val boardKey = remember(targets) { targets.joinToString("|") { gameWordKey(it.songId, it.lexemeId) } }
    var selected by rememberSaveable(boardKey) { mutableStateOf(emptyList<Int>()) }
    var feedback by rememberSaveable(boardKey) { mutableStateOf("") }
    var pendingWinKey by rememberSaveable(boardKey) { mutableStateOf("") }
    var pendingWinCells by rememberSaveable(boardKey) { mutableStateOf(emptyList<Int>()) }
    var rejecting by rememberSaveable(boardKey) { mutableStateOf(false) }
    val correctFeedback = tr("آفرین! واژه پیدا شد", "Word found!")
    val incorrectFeedback = tr("این مسیر واژه‌ای نمی‌سازد؛ دوباره تلاش کن", "Those letters do not make a word. Try again.")

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

    val solvedCells = remember(board, solvedCellIndices) {
        solvedCellIndices.values.flatten().toSet()
    }
    val winningCells = remember(pendingWinCells) { pendingWinCells.toSet() }
    val lockedCells = remember(solvedCells, winningCells) { solvedCells + winningCells }
    val canInteract = pendingWinKey.isEmpty() && !rejecting

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(tr("حروف را به هم وصل کن", "Connect the letters"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tr("خانه‌های مجاور را به‌ترتیب انتخاب کن", "Choose neighboring hexes in order"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PressIconButton(
                onClick = {
                    selected = selected.dropLast(1)
                    feedback = ""
                    GameSounds.release()
                },
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                enabled = selected.isNotEmpty() && canInteract
            ) { AppIcon(UiIcons.Backspace, tr("حذف خانهٔ آخر", "Backspace"), Modifier.size(23.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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
            lockedCells = lockedCells,
            winningCells = winningCells,
            canInteract = canInteract
        ) { index ->
            if(index in lockedCells || !canInteract) return@HoneycombBoard
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
            val match = targets.firstOrNull { target ->
                gameWordKey(target.songId, target.lexemeId) !in solvedKeys && target.letterAnswer.equals(guess, ignoreCase = true)
            }
            if(match != null) {
                val matchKey = gameWordKey(match.songId, match.lexemeId)
                pendingWinKey = matchKey
                pendingWinCells = nextSelection
                feedback = correctFeedback
                GameSounds.win()
                model.solveGameWord(
                    targetLanguage,
                    match.songId,
                    match.lexemeId,
                    revealDelayMs = 620,
                    usedCellIndices = nextSelection
                )
            } else {
                val couldContinue = targets.any { target ->
                    gameWordKey(target.songId, target.lexemeId) !in solvedKeys && target.letterAnswer.startsWith(guess, ignoreCase = true)
                }
                if(!couldContinue) {
                    rejecting = true
                    feedback = incorrectFeedback
                    GameSounds.incorrect()
                }
            }
        }
        if(feedback.isNotBlank()) Text(
            feedback,
            color = if(pendingWinKey.isNotEmpty()) Color(0xFF2F9E62) else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable private fun HoneycombBoard(
    board: HoneycombLetterBoard,
    selectedCells: Set<Int>,
    lockedCells: Set<Int>,
    winningCells: Set<Int>,
    canInteract: Boolean,
    onTap: (Int) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = board.columns
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
                    val locked = index in lockedCells
                    val selected = index in selectedCells
                    val winning = index in winningCells
                    val color by animateColorAsState(
                        targetValue = when {
                            winning -> Color(0xFF71850B)
                            locked -> Color(0xFF71850B)
                            selected -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFFB9E914)
                        },
                        label = "honeycomb-cell-$index"
                    )
                    val textColor by animateColorAsState(
                        targetValue = when {
                            winning || locked || selected -> Color.White
                            else -> Color(0xFF293704)
                        },
                        label = "honeycomb-letter-$index"
                    )
                    Box(
                        Modifier.offset(
                            x = tileWidth * column.toFloat() + if(row % 2 == 1) tileWidth * .5f else 0.dp,
                            y = rowStep * row.toFloat()
                        ).size(tileWidth, tileHeight)
                            .clip(HexagonShape)
                            .background(color)
                            .border(2.dp, Color.White, HexagonShape)
                            .clickable(enabled = canInteract && !locked) { onTap(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(letter.toString().uppercase(), color = textColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private val HexagonShape = GenericShape { size, _ ->
    moveTo(size.width * .5f, 0f)
    lineTo(size.width * .94f, size.height * .25f)
    lineTo(size.width * .94f, size.height * .75f)
    lineTo(size.width * .5f, size.height)
    lineTo(size.width * .06f, size.height * .75f)
    lineTo(size.width * .06f, size.height * .25f)
    close()
}

package com.hydro.reedance.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Lightweight repeating chat doodles, drawn behind the bubbles rather than baked into a bitmap. */
internal fun Modifier.dialogueWallpaper(accent: Color, theme: Int): Modifier = drawWithCache {
    val tile = 144.dp.toPx()
    val factor = tile / 144f
    val motifs = PathParser().parsePathString(
        "M12,29 C5,23 9,12 18,12 H32 C43,12 46,28 35,32 H25 L17,38 V32 Z " +
            "M19,22 H32 M72,12 C84,2 94,19 85,26 C77,32 68,21 75,16 C80,13 84,17 81,20 " +
            "M122,33 Q122,43 132,44 Q121,45 120,55 Q120,45 111,44 Q121,43 122,33 " +
            "M42,61 C47,52 57,55 61,62 C66,53 80,56 80,65 C80,74 69,81 61,85 C52,79 36,70 42,61 " +
            "M95,82 Q103,71 111,81 T130,79 M94,89 Q103,79 111,89 T130,87 " +
            "M10,99 C18,92 18,110 27,104 S35,102 37,107 " +
            "M51,121 Q56,106 72,111 Q83,117 76,128 Q71,135 61,131 L54,135 L55,128 Q50,126 51,121 " +
            "M109,121 Q109,128 116,129 Q109,130 108,137 Q107,130 101,129 Q108,128 109,121"
    ).toPath()
    val extra = PathParser().parsePathString(when(DialoguePaper.entries[theme]) {
        DialoguePaper.Sepia -> "M99,56 Q91,50 94,42 Q103,43 105,51 Q103,55 99,56 M99,50 L105,58 M22,127 Q30,119 37,126"
        DialoguePaper.Blue -> "M95,52 Q96,41 107,41 Q107,50 95,52 M95,52 L91,57 M26,122 Q35,114 41,122"
        DialoguePaper.Peach -> "M89,61 Q100,49 111,61 M89,56 Q100,44 111,56 M89,51 Q100,39 111,51 M21,128 Q31,118 41,127"
        DialoguePaper.Mint -> "M93,59 Q94,43 111,40 Q113,55 93,59 M91,62 Q99,51 104,48 M25,128 Q21,119 34,116 Q39,127 25,128"
        DialoguePaper.Lavender -> "M101,40 Q101,49 111,50 Q101,51 101,61 Q100,51 91,50 Q101,49 101,40 M26,118 Q26,124 34,125 Q26,126 26,133 Q25,126 19,125 Q26,124 26,118"
        DialoguePaper.System -> "M92,55 Q99,43 108,50 Q107,61 96,61 M24,126 Q32,117 42,124"
    }).toPath()
    val rows = ceil(size.height / tile).toInt()
    val columns = ceil(size.width / tile).toInt()
    val line = accent.copy(alpha = .19f)
    val stroke = Stroke(width = 1.25f, cap = StrokeCap.Round)
    onDrawBehind {
        clipRect {
            for(row in 0 until rows) for(column in -1 until columns) {
                translate((column + if(row % 2 == 0) 0f else .5f) * tile, row * tile) {
                    scale(factor, factor, pivot = Offset.Zero) {
                        drawPath(motifs, line, style = stroke)
                        drawPath(extra, line, style = stroke)
                        drawCircle(line, 1.5f, Offset(11f, 66f))
                        drawCircle(line, 1.2f, Offset(95f, 106f))
                    }
                }
            }
        }
    }
}

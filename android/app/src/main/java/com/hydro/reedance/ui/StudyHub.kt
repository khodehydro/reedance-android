package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal const val StudyHubRoute = "study"
internal const val StoriesRoute = "stories"
internal const val DialoguesRoute = "dialogues"
internal val studySectionRoutes = setOf("review", "gym", "game", "library", StoriesRoute, DialoguesRoute)

@Composable internal fun studySectionTitle(route: String): String = when(route) {
    "review" -> tr("مرور", "Review")
    "gym" -> tr("باشگاه", "Gym")
    "game" -> tr("بازی", "Game")
    "library" -> tr("کتابخانه", "Library")
    DialoguesRoute -> tr("گفت‌وگو", "Dialogues")
    StoriesRoute -> tr("داستان‌ها", "Stories")
    else -> tr("یادگیری", "Learn")
}

private data class StudyTile(val route: String, val icon: Int, val backdrop: Int, val color: Color)

private val studyTiles = listOf(
    StudyTile("review", UiIcons.Review, UiIcons.ReviewFilled, Color(0xFF245198)),
    StudyTile("gym", UiIcons.Dumbbell, UiIcons.DumbbellFilled, Color(0xFF17634F)),
    StudyTile("game", UiIcons.Game, UiIcons.GameFilled, Color(0xFF603C91)),
    StudyTile("library", UiIcons.Library, UiIcons.LibraryFilled, Color(0xFF814B20)),
    StudyTile(StoriesRoute, UiIcons.Headphones, UiIcons.Headphones, Color(0xFF903D5B)),
    StudyTile(DialoguesRoute, UiIcons.Phrases, UiIcons.PhrasesFilled, Color(0xFF24616E))
)

@Composable internal fun StudyHubScreen(open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp + LocalOverlayBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Heading(tr("یادگیری", "Learn"), tr("مرور کن، تمرین کن و به ذخیره‌هایت برگرد.", "Review, practise, and revisit what you’ve saved."))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            studyTiles.chunked(2).forEach { tiles ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tiles.forEach { tile ->
                        StudySectionTile(tile, Modifier.weight(1f)) { open(tile.route) }
                    }
                    if(tiles.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun StudySectionTile(tile: StudyTile, modifier: Modifier, open: () -> Unit) {
    Surface(modifier.aspectRatio(1f).clip(GroupedCardShape).clickable(role = Role.Button, onClick = open),
        shape = GroupedCardShape, color = tile.color, contentColor = Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tileWidth = maxWidth
            AppIcon(tile.backdrop, null, Modifier.align(Alignment.CenterEnd).size(tileWidth * .67f)
                .graphicsLayer { rotationZ = -13f }, tint = Color.White.copy(alpha = .16f))
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = NestedTileShape, color = Color.White.copy(alpha = .14f), contentColor = Color.White) {
                    Box(Modifier.size(minOf(42.dp, tileWidth * .3f)), contentAlignment = Alignment.Center) {
                        AppIcon(tile.icon, null, Modifier.size(22.dp))
                    }
                }
                Text(studySectionTitle(tile.route), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

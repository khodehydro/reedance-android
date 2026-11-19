package com.hydro.reedance.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.hydro.reedance.R

// Exact Regular Rounded outlines from the web reference, drawn as native vectors.
object UiIcons {
    val Headphones = R.drawable.uicon_headphones
    val Game = R.drawable.uicon_game
    val GameFilled = R.drawable.uicon_game_filled
    val Eye = R.drawable.uicon_eye
    val EyeClosed = R.drawable.uicon_eye_closed
    val Lyrics = R.drawable.uicon_lyrics
    val LyricsFilled = R.drawable.uicon_lyrics_filled
    val Notes = R.drawable.uicon_notes
    val NotesFilled = R.drawable.uicon_notes_filled
    val Words = R.drawable.uicon_words
    val WordsFilled = R.drawable.uicon_words_filled
    val Phrases = R.drawable.uicon_phrases
    val PhrasesFilled = R.drawable.uicon_phrases_filled
    val Original = R.drawable.uicon_original
    val OriginalFilled = R.drawable.uicon_original_filled
    val Lines = R.drawable.uicon_lines
    val LinesFilled = R.drawable.uicon_lines_filled
    val WordMode = R.drawable.uicon_word_mode
    val WordModeFilled = R.drawable.uicon_word_mode_filled
    val System = R.drawable.uicon_system
    val SystemFilled = R.drawable.uicon_system_filled
    val Light = R.drawable.uicon_light
    val LightFilled = R.drawable.uicon_light_filled
    val Dark = R.drawable.uicon_dark
    val DarkFilled = R.drawable.uicon_dark_filled
    val AllFilled = R.drawable.uicon_all_filled
    val CheckFilled = R.drawable.uicon_check_filled
    val GlobeFilled = R.drawable.uicon_globe_filled
    val VolumeFilled = R.drawable.uicon_volume_filled
    val HomeFilled = R.drawable.uicon_home_filled
    val ExploreFilled = R.drawable.uicon_explore_filled
    val ReviewFilled = R.drawable.uicon_review_filled
    val LibraryFilled = R.drawable.uicon_library_filled
    val Back = R.drawable.uicon_back
    val Backspace = R.drawable.uicon_backspace
    val Home = R.drawable.uicon_home
    val Explore = R.drawable.uicon_explore
    val Review = R.drawable.uicon_review
    val Library = R.drawable.uicon_library
    val User = R.drawable.uicon_user
    val PlayFilled = R.drawable.uicon_play_filled
    val PauseFilled = R.drawable.uicon_pause_filled
    val ShareFilled = R.drawable.uicon_share_filled
    val CoffeeFilled = R.drawable.uicon_coffee_filled
    val Play = R.drawable.uicon_play
    val Pause = R.drawable.uicon_pause
    val Lock = R.drawable.uicon_lock
    val LockOpen = R.drawable.uicon_lock_open
    val BookmarkFilled = R.drawable.uicon_bookmark_filled
    val Bookmark = R.drawable.uicon_bookmark
    val Quiz = R.drawable.uicon_quiz
    val Repeat = R.drawable.uicon_repeat
    val Replay10 = R.drawable.uicon_replay10
    val Forward10 = R.drawable.uicon_forward10
    val Volume = R.drawable.uicon_volume
    val Mute = R.drawable.uicon_mute
    val Star = R.drawable.uicon_star
    val CrownFilled = R.drawable.uicon_crown_filled
    val Crown = R.drawable.uicon_crown
    val Check = R.drawable.uicon_check
    val All = R.drawable.uicon_all
    val Search = R.drawable.uicon_search
    val Globe = R.drawable.uicon_globe
}
@Composable fun AppIcon(icon: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription, modifier, tint)
}

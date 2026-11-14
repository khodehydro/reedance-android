package com.hydro.reedance.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.hydro.reedance.R

// Exact Regular Rounded outlines from the web reference, drawn as native vectors.
object UiIcons {
    val Back = R.drawable.uicon_back
    val Home = R.drawable.uicon_home
    val Explore = R.drawable.uicon_explore
    val Review = R.drawable.uicon_review
    val Library = R.drawable.uicon_library
    val User = R.drawable.uicon_user
    val Play = R.drawable.uicon_play
    val Pause = R.drawable.uicon_pause
    val Lock = R.drawable.uicon_lock
    val LockOpen = R.drawable.uicon_lock_open
    val Bookmark = R.drawable.uicon_bookmark
    val Quiz = R.drawable.uicon_quiz
    val Repeat = R.drawable.uicon_repeat
    val Replay10 = R.drawable.uicon_replay10
    val Forward10 = R.drawable.uicon_forward10
    val Volume = R.drawable.uicon_volume
    val Mute = R.drawable.uicon_mute
    val Star = R.drawable.uicon_star
    val Crown = R.drawable.uicon_crown
    val Check = R.drawable.uicon_check
    val All = R.drawable.uicon_all
    val Globe = R.drawable.uicon_globe
}
@Composable fun AppIcon(icon: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription, modifier, tint)
}

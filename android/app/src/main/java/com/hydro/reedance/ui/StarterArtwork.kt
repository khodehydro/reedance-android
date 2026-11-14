package com.hydro.reedance.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hydro.reedance.R

/** Decorative alongside the localized Starter heading; keep ears/headphones intact. */
@Composable internal fun StarterArtwork(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.reedance_starter), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Fit)
}

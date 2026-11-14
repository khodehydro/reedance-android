package com.hydro.reedance.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.sin

internal enum class FluidPalette { Ocean, Aurora, Midnight }

/** Fluid light ribbons over near-black pools, not moving circular color stops. */
@Composable internal fun BannerGradient(animate: Boolean, modifier: Modifier = Modifier, palette: FluidPalette = FluidPalette.Ocean) {
    val reduced = LocalReducedMotion.current
    val paused = LocalBackgroundMotion.current.paused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var seconds by remember { mutableFloatStateOf(0f) }
    val fluid = remember(palette) {
        if(Build.VERSION.SDK_INT >= 33) runCatching { FluidShader(palette) }.getOrNull() else null
    }
    LaunchedEffect(animate, reduced, paused, lifecycle, palette) {
        if(reduced) seconds = 0f
        if(animate && !reduced && !paused) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val frameInterval = if(palette == FluidPalette.Midnight) 66_000_000L else 32_000_000L
            var previous = 0L
            // Cap redraws around 15fps for the mini player, 30fps otherwise; time is draw-only.
            while(isActive) withFrameNanos { frame ->
                if(previous == 0L) previous = frame
                if(frame - previous >= frameInterval) {
                    seconds += ((frame - previous) / 1_000_000_000f).coerceAtMost(.1f)
                    previous = frame
                }
            }
        }
    }
    Canvas(modifier) {
        val t = seconds
        if(Build.VERSION.SDK_INT >= 33 && fluid != null) {
            fluid.shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            fluid.shader.setFloatUniform("time", t)
            drawRect(fluid.brush)
        } else if(palette == FluidPalette.Midnight) drawFluidFallback(t, midnight = true)
        else if(palette == FluidPalette.Aurora) drawFluidFallback(t, true) else drawFluidFallback(t)
        // Preserve the light text's contrast; the fluid itself remains unblurred text-wise.
        drawRect(Brush.verticalGradient(listOf(Color(0x33000410), Color.Transparent, Color(0x55000312))))
    }
}

@RequiresApi(33)
private class FluidShader(palette: FluidPalette) {
    val shader = if(palette == FluidPalette.Midnight) RuntimeShader(MIDNIGHT_FLUID_SHADER)
        else if(palette == FluidPalette.Aurora) RuntimeShader(AURORA_FLUID_SHADER) else RuntimeShader(FLUID_SHADER)
    val brush = ShaderBrush(shader)
}

// Domain-warped fractal noise bends the cyan contour into branching, liquid
// channels. Wide cobalt fields fade into black; fine grain is intentionally subtle.
internal const val FLUID_SHADER = """
uniform float2 resolution;
uniform float time;
float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * noise(p);
        p = float2(1.6 * p.x - 1.2 * p.y, 1.2 * p.x + 1.6 * p.y) + 3.7;
        amplitude *= 0.5;
    }
    return sum;
}
half4 main(float2 position) {
    float2 p = (position - 0.5 * resolution) / min(resolution.x, resolution.y) * 2.4;
    float t = time * 0.075;
    float2 warp = float2(fbm(p + float2(t, -t * 0.6)),
                         fbm(p + float2(4.2 - t * 0.7, 1.8 + t)));
    float field = fbm(p + warp * 2.6 + float2(0.1 * t, -0.25 * t));
    float cobalt = 1.0 - smoothstep(0.35, 0.57, field);
    float ribbon = exp(-abs(field - 0.50) * 70.0);
    float halo = exp(-abs(field - 0.50) * 22.0);
    float3 color = float3(0.002, 0.003, 0.014);
    color += float3(0.015, 0.012, 0.65) * cobalt;
    color += float3(0.0, 0.14, 0.30) * halo;
    color = mix(color, float3(0.0, 0.56, 0.72), ribbon * 0.85);
    float grain = (hash(position + floor(time * 8.0)) - 0.5) * 0.012;
    color += grain;
    return half4(half3(clamp(color, 0.0, 1.0)), 1.0);
}
"""

/** Same domain-warped fluid, recolored orchid / coral over plum for invitation cards. */
internal val AURORA_FLUID_SHADER = FLUID_SHADER
    .replace("float3(0.002, 0.003, 0.014)", "float3(0.025, 0.006, 0.045)")
    .replace("float3(0.015, 0.012, 0.65)", "float3(0.32, 0.025, 0.55)")
    .replace("float3(0.0, 0.14, 0.30)", "float3(0.35, 0.05, 0.15)")
    .replace("float3(0.0, 0.56, 0.72)", "float3(0.98, 0.46, 0.40)")

/** Low-luminance blue/violet treatment for the compact playback surface. */
internal val MIDNIGHT_FLUID_SHADER = FLUID_SHADER
    // Normalize each axis separately: a 7:1 capsule needs broad flowing fields,
    // not the tiny densely packed features of the tall Home banner.
    .replace("(position - 0.5 * resolution) / min(resolution.x, resolution.y) * 2.4",
        "float2((position.x / resolution.x - 0.5) * 3.8, (position.y / resolution.y - 0.5) * 1.6)")
    .replace("float t = time * 0.075", "float t = time * 0.30")
    .replace("float3(0.002, 0.003, 0.014)", "float3(0.012, 0.018, 0.055)")
    .replace("float3(0.015, 0.012, 0.65)", "float3(0.10, 0.04, 0.32)")
    .replace("float3(0.0, 0.14, 0.30)", "float3(0.035, 0.045, 0.14)")
    .replace("float3(0.0, 0.56, 0.72)", "float3(0.20, 0.27, 0.52)")

/** API 26–32 / shader-failure fallback: soft nested ribbons, no bitmap/CPU noise loop. */
private fun DrawScope.drawFluidFallback(t: Float, aurora: Boolean = false, midnight: Boolean = false) {
    drawRect(if(midnight) Color(0xFF03050E) else if(aurora) Color(0xFF180923) else Color(0xFF01020A))
    drawRect(Brush.radialGradient(listOf(if(midnight) Color(0xFF191044) else if(aurora) Color(0xFF7427A8) else Color(0xFF1010A8), Color.Transparent),
        center = Offset(size.width * .08f, size.height * .55f), radius = size.maxDimension * .8f))
    val drift = if(midnight) .55f else .14f
    val counterDrift = if(midnight) .36f else .09f
    repeat(2) { ribbon ->
        val path = Path()
        for(step in 0..48) {
            val y = size.height * (step / 48f * 1.4f - .2f)
            val n = step / 48f
            val x = size.width * (.5f + .26f * sin(n * 7.5f + t * drift + ribbon * 2f) +
                .15f * sin(n * 13f - t * counterDrift + ribbon))
            if(step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for(layer in 10 downTo 1) {
            val outer = layer / 10f
            val color = if(midnight) { if(layer > 5) Color(0xFF2D1B66) else Color(0xFF3C4B91) }
                else if(aurora) { if(layer > 5) Color(0xFFAB3AA8) else Color(0xFFFF897C) }
                else if(layer > 5) Color(0xFF0719BB) else Color(0xFF0094C4)
            drawPath(path, color.copy(alpha = .075f + (1f - outer) * .065f),
                style = Stroke(width = size.minDimension * (.025f + outer * .23f)))
        }
    }
}

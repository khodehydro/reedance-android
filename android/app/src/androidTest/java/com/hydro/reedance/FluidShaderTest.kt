package com.hydro.reedance

import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.hydro.reedance.ui.FLUID_SHADER
import com.hydro.reedance.ui.AURORA_FLUID_SHADER
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class FluidShaderTest {
    @Test fun invitationProgramCompilesAndAcceptsUniforms() {
        val shader = RuntimeShader(AURORA_FLUID_SHADER)
        shader.setFloatUniform("resolution", 340f, 340f)
        shader.setFloatUniform("time", 12f)
    }
    @Test fun fluidProgramCompilesAndAcceptsUniforms() {
        val shader = RuntimeShader(FLUID_SHADER)
        shader.setFloatUniform("resolution", 320f, 420f)
        shader.setFloatUniform("time", 12f)
    }
}

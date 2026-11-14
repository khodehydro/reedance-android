package com.hydro.reedance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Draw-only touch feedback: no ripple/halo, no layout changes or delayed clicks.
private data class PressFeedback(val enabled: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, enabled)
}
private class PressNode(private val source: InteractionSource, private val enabled: Boolean) : Modifier.Node(), DrawModifierNode {
    private val amount = Animatable(1f)
    override fun onAttach() {
        if(enabled) coroutineScope.launch {
            amount.snapTo(1f)
            source.interactions.filter { it is PressInteraction.Press || it is PressInteraction.Release || it is PressInteraction.Cancel }.collectLatest { interaction ->
                when(interaction) {
                    is PressInteraction.Press -> amount.animateTo(.965f, tween(70))
                    is PressInteraction.Release -> {
                        if(amount.value > .985f) amount.snapTo(.985f)
                        amount.animateTo(1f, tween(140))
                    }
                    is PressInteraction.Cancel -> amount.animateTo(1f, tween(100))
                    else -> Unit
                }
            }
        }
    }
    override fun ContentDrawScope.draw() { scale(if(enabled) amount.value else 1f) { this@draw.drawContent() } }
}
@Composable fun rememberPressFeedback(): IndicationNodeFactory {
    val reduced = LocalReducedMotion.current
    return remember(reduced) { PressFeedback(!reduced) }
}
@Composable private fun Modifier.pressFeedback(source: MutableInteractionSource) = indication(source, rememberPressFeedback())

@Composable fun PressButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(onClick, modifier.pressFeedback(source), enabled = enabled, shape = shape, colors = colors, interactionSource = source, content = content)
}
@Composable fun PressOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(onClick, modifier.pressFeedback(source), enabled = enabled, colors = colors, interactionSource = source, content = content)
}
@Composable fun PressTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(onClick, modifier.pressFeedback(source), enabled = enabled, interactionSource = source, content = content)
}
@Composable fun PressIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick, modifier.pressFeedback(source), enabled = enabled, interactionSource = source, content = content)
}
@Composable fun PressFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilledIconButton(onClick, modifier.pressFeedback(source), enabled = enabled, interactionSource = source, content = content)
}
@Composable fun PressRadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    RadioButton(selected, onClick, modifier.pressFeedback(source), enabled = enabled, interactionSource = source)
}
@Composable fun PressSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    Switch(checked, onCheckedChange, modifier.pressFeedback(source), enabled = enabled, interactionSource = source)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PressFilterChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: @Composable () -> Unit, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    FilterChip(selected, onClick, label = label, modifier = modifier.pressFeedback(source), enabled = enabled, interactionSource = source)
}

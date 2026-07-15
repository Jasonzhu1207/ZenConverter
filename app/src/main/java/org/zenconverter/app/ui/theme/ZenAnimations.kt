package org.zenconverter.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

object ZenAnimations {
    val DefaultSpring = spring<Float>(stiffness = 420f)
    val SlowerSpring = spring<Float>(stiffness = Spring.StiffnessLow)
    
    // Extracted from original animated visibility blocks
    val VisibilityEnterSpring = spring<Int>(stiffness = 420f)
    val VisibilityExitSpring = spring<Int>(stiffness = 520f)
}

/**
 * A modifier that provides a subtle "bounce" or "scale down" effect when the element is pressed.
 * It also handles the actual click action.
 */
fun Modifier.bounceClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    scaleDown: Float = 0.95f
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = ZenAnimations.DefaultSpring,
        label = "bounceClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // Disable default ripple for a cleaner bounce
            enabled = enabled,
            onClick = onClick
        )
        .pointerInput(enabled) {
            if (enabled) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                    }
                }
            }
        }
}

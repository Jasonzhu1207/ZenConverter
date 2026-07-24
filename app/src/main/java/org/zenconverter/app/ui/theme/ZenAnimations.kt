package org.zenconverter.app.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
    // ── Base spring specs ──────────────────────────────────────────────
    val DefaultSpring = spring<Float>(stiffness = 420f)
    val SlowerSpring = spring<Float>(stiffness = Spring.StiffnessLow)

    // ── Panel expand / collapse (Settings, About, Metadata) ─────────
    /** Enter: slower spring for a gentle unfold */
    const val PanelEnterStiffness = 420f
    /** Exit: slightly stiffer so collapse feels snappy */
    const val PanelExitStiffness = 520f

    val PanelEnterSpring = spring<Int>(stiffness = PanelEnterStiffness)
    val PanelExitSpring = spring<Int>(stiffness = PanelExitStiffness)

    // ── Dropdown / Advanced section ─────────────────────────────────
    const val DropdownEnterStiffness = 520f
    const val DropdownExitStiffness = 620f

    // ── Content crossfade (state machine switches, AnimatedContent) ─
    const val ContentFadeDuration = 220   // ms — quick crossfade
    const val ContentFadeOutDuration = 150 // ms — exit slightly faster

    // ── Icon rotation (expand arrows, chevrons) ─────────────────────
    val IconRotationSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 600f
    )

    // ── Dialog scale-in ─────────────────────────────────────────────
    const val DialogScaleFrom = 0.94f
    const val DialogScaleDuration = 240  // ms

    // ── Hero "+" morph: center → header ─────────────────────────────
    /** Critically damped spring for the morph — smooth, no overshoot. */
    val HeroMorphSpring = spring<Float>(
        dampingRatio = 1.0f,
        stiffness = 380f
    )
    val HeroMorphDpSpring = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 1.0f,
        stiffness = 380f
    )

    const val HeroHeaderSize = 44f
    const val HeroCenterSize = 118f
    const val HeroHeaderIconSize = 24f
    const val HeroCenterIconSize = 58f

    // Legacy aliases (kept for compatibility)
    val VisibilityEnterSpring = PanelEnterSpring
    val VisibilityExitSpring = PanelExitSpring

    // ── Helpers ─────────────────────────────────────────────────────

    /** Standard enter transition for inline panels (Settings, About, Metadata). */
    val PanelEnter: EnterTransition = fadeIn(
        animationSpec = tween(ContentFadeDuration)
    ) + expandVertically(
        animationSpec = spring(stiffness = PanelEnterStiffness)
    )

    /** Standard exit transition for inline panels. */
    val PanelExit: ExitTransition = fadeOut(
        animationSpec = tween(ContentFadeOutDuration)
    ) + shrinkVertically(
        animationSpec = spring(stiffness = PanelExitStiffness)
    )

    /** Enter transition for dropdown / advanced sections. */
    val DropdownEnter: EnterTransition = fadeIn(
        animationSpec = spring(stiffness = DropdownEnterStiffness)
    ) + expandVertically(
        animationSpec = spring(stiffness = DropdownEnterStiffness)
    )

    /** Exit transition for dropdown / advanced sections. */
    val DropdownExit: ExitTransition = fadeOut(
        animationSpec = spring(stiffness = DropdownExitStiffness)
    ) + shrinkVertically(
        animationSpec = spring(stiffness = DropdownExitStiffness)
    )
}

/**
 * Standard animated-visibility wrapper for inline panels that expand below
 * the header (Settings, About, Metadata Security).
 */
@Composable
fun ZenPanelVisibility(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = ZenAnimations.PanelEnter,
        exit = ZenAnimations.PanelExit,
        content = content
    )
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

package org.zenconverter.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.zenconverter.app.ui.AppScreen
import org.zenconverter.app.ui.theme.ZenAnimations

/** Retain home composition and task controls without drawing or placing hidden input targets. */
@Composable
internal fun MountedHome(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (visible) placeable.placeRelative(0, 0)
                }
            }
    ) { content() }
}

@Composable
internal fun SubpageHost(
    screen: AppScreen,
    returning: Boolean,
    content: @Composable (AppScreen) -> Unit
) {
    val savedPages = rememberSaveableStateHolder()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(screen) { focusManager.clearFocus(force = true) }
    val distance = with(LocalDensity.current) { 16.dp.roundToPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    AnimatedContent(
        targetState = screen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val duration = if (returning) ZenAnimations.SubpageExitDuration else ZenAnimations.SubpageEnterDuration
            val offset = distance * direction * if (returning) -1 else 1
            (slideInHorizontally(tween(duration), initialOffsetX = { offset }) + fadeIn(tween(duration)))
                .togetherWith(slideOutHorizontally(tween(duration), targetOffsetX = { -offset }) + fadeOut(tween(duration)))
                .using(null)
        },
        label = "subpage"
    ) { destination ->
        if (destination != AppScreen.Home) {
            // Outgoing pages may still draw during the transition, but cannot receive input or TalkBack focus.
            val inactive = if (destination == screen) Modifier else Modifier
                .clearAndSetSemantics {}
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            Box(Modifier.fillMaxSize().then(inactive)) {
                savedPages.SaveableStateProvider(destination.name) { content(destination) }
            }
        }
    }
}

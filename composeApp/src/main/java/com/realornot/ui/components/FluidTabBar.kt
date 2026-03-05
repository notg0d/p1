package com.realornot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.theme.*
import kotlin.math.abs

@Composable
fun FluidTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<Pair<String, @Composable () -> Unit>> = emptyList(),
) {
    val density = LocalDensity.current

    // Track layout coordinates of each tab
    val tabCenters = remember { mutableStateMapOf<Int, Float>() }
    val tabLeftEdges = remember { mutableStateMapOf<Int, Float>() }
    val tabRightEdges = remember { mutableStateMapOf<Int, Float>() }

    // ── DRAG STATE ─────────────────────────────────────────────────
    var isDragging by remember { mutableStateOf(false) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    // The pill's target left & right edges (based on selected tab or drag)
    val restLeft = tabLeftEdges[selectedIndex] ?: 0f
    val restRight = tabRightEdges[selectedIndex] ?: 0f

    // ── GOOEY SPRING PHYSICS ───────────────────────────────────────
    // Two separate Animatable floats for left and right edges.
    // Leading edge is fast (stiff), trailing edge is slow (soft).
    // This creates the stretch effect where the pill elongates during movement.
    val animLeft = remember { Animatable(0f) }
    val animRight = remember { Animatable(0f) }

    // Detect movement direction for leading/trailing edge assignment
    var prevTargetLeft by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isDragging, dragCenterX, restLeft, restRight) {
        if (isDragging) {
            // During drag: pill center follows finger, stretches based on velocity
            val pillHalfWidth = (restRight - restLeft) / 2f
            val stretchFactor = (abs(dragVelocity) / 3000f).coerceIn(0f, 0.6f)
            val stretchAmount = pillHalfWidth * stretchFactor

            // Leading edge moves faster, trailing lags — creates stretch
            val targetL = dragCenterX - pillHalfWidth - stretchAmount
            val targetR = dragCenterX + pillHalfWidth + stretchAmount

            animLeft.animateTo(targetL, spring(dampingRatio = 0.8f, stiffness = 600f))
        } else {
            // Not dragging: animate to the selected tab with gooey physics
            val movingRight = restLeft > prevTargetLeft
            prevTargetLeft = restLeft

            // Leading edge: stiff spring (arrives first)
            // Trailing edge: soft spring (lags behind = stretch)
            val leadSpec = spring<Float>(dampingRatio = 0.55f, stiffness = 350f)
            val trailSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 150f)

            animLeft.animateTo(
                restLeft,
                if (movingRight) trailSpec else leadSpec
            )
        }
    }

    LaunchedEffect(isDragging, dragCenterX, restLeft, restRight) {
        if (isDragging) {
            val pillHalfWidth = (restRight - restLeft) / 2f
            val stretchFactor = (abs(dragVelocity) / 3000f).coerceIn(0f, 0.6f)
            val stretchAmount = pillHalfWidth * stretchFactor

            val targetR = dragCenterX + pillHalfWidth + stretchAmount
            animRight.animateTo(targetR, spring(dampingRatio = 0.8f, stiffness = 600f))
        } else {
            val movingRight = restLeft > prevTargetLeft

            val leadSpec = spring<Float>(dampingRatio = 0.55f, stiffness = 350f)
            val trailSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 150f)

            animRight.animateTo(
                restRight,
                if (movingRight) leadSpec else trailSpec
            )
        }
    }

    // Initialize pill position on first composition
    LaunchedEffect(Unit) {
        snapshotFlow { restLeft to restRight }
            .collect { (l, r) ->
                if (animLeft.value == 0f && animRight.value == 0f && r > 0f) {
                    animLeft.snapTo(l)
                    animRight.snapTo(r)
                }
            }
    }

    val pillColor = SageWarm
    val pillPadding = with(density) { 4.dp.toPx() }
    val pillCornerRadius = with(density) { 16.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            // ── DRAG GESTURE ───────────────────────────────────────
            .pointerInput(tabs.size) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragCenterX = offset.x
                        dragVelocity = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        // Snap to nearest tab
                        val nearest = tabCenters.entries
                            .minByOrNull { abs(it.value - dragCenterX) }
                            ?.key ?: selectedIndex
                        if (nearest != selectedIndex) {
                            onTabSelected(nearest)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragCenterX += dragAmount
                        dragVelocity = dragAmount * 60f // approximate px/s

                        // Live tab highlighting during drag
                        val nearest = tabCenters.entries
                            .minByOrNull { abs(it.value - dragCenterX) }
                            ?.key
                        if (nearest != null && nearest != selectedIndex) {
                            onTabSelected(nearest)
                        }
                    }
                )
            }
            // ── DRAW THE GOOEY PILL ────────────────────────────────
            .drawBehind {
                val left = animLeft.value + pillPadding
                val top = pillPadding
                val right = animRight.value - pillPadding
                val bottom = size.height - pillPadding
                val w = right - left
                val h = bottom - top

                if (w > 0f) {
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
                    )
                }
            }
    ) {
        // Foreground Tab items
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) BgDark else TextMuted,
                    animationSpec = tween(200),
                    label = "textColor"
                )

                Box(
                    modifier = Modifier
                        .weight(if (isSelected) 1.5f else 1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInParent()
                            tabLeftEdges[index] = bounds.left
                            tabRightEdges[index] = bounds.right
                            tabCenters[index] = bounds.center.x
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        icon()

                        // Only show text for the selected tab
                        AnimatedVisibility(visible = isSelected) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

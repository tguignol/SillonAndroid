package ch.kohlnet.sillon.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mince barre de défilement (« ascenseur ») qui APPARAÎT quand l'utilisateur scrolle puis s'estompe,
 * façon iOS. Position/taille du curseur estimées depuis le LazyListState/LazyGridState.
 */
fun Modifier.lazyColumnScrollbar(state: LazyListState, color: Color, width: Dp = 5.dp): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.85f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress) 120 else 600),
        label = "scrollbarAlpha",
    )
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (alpha > 0f && total > visible.size && visible.isNotEmpty()) {
            val first = visible.first().index
            val thumbH = (size.height * visible.size / total).coerceAtLeast(24.dp.toPx())
            val maxOffset = (size.height - thumbH).coerceAtLeast(0f)
            val offsetY = (size.height * first / total).coerceIn(0f, maxOffset)
            val w = width.toPx()
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - w - 2.dp.toPx(), offsetY),
                size = Size(w, thumbH),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

fun Modifier.lazyGridScrollbar(state: LazyGridState, color: Color, width: Dp = 5.dp): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.85f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress) 120 else 600),
        label = "scrollbarAlpha",
    )
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (alpha > 0f && total > visible.size && visible.isNotEmpty()) {
            val first = visible.first().index
            val thumbH = (size.height * visible.size / total).coerceAtLeast(24.dp.toPx())
            val maxOffset = (size.height - thumbH).coerceAtLeast(0f)
            val offsetY = (size.height * first / total).coerceIn(0f, maxOffset)
            val w = width.toPx()
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - w - 2.dp.toPx(), offsetY),
                size = Size(w, thumbH),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

package eu.hxreborn.pixelinjector.ui.reload

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import eu.hxreborn.pixelinjector.ui.util.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val BadgePolygons =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Pentagon,
        MaterialShapes.Pill,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.SoftBurst,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ReloadStatus?.restIndex: Int
    get() = if (this == ReloadStatus.SUCCEEDED) BadgePolygons.indexOf(MaterialShapes.Sunny) else 0

private const val MORPH_INTERVAL_MILLIS = 650L
private const val STEP_TURN_DEGREES = 60f
private const val ICON_ENTER_SCALE = 0.92f
private val BadgeSize = 38.dp
private val BadgeIconSize = 20.dp

private fun ColorScheme.badgeAccent(status: ReloadStatus?): Color =
    when {
        status == null -> surfaceContainerHighest
        toneOf(status) == Tone.OK -> tertiary
        toneOf(status) == Tone.WARN -> secondary
        toneOf(status) == Tone.BAD -> error
        else -> primary
    }

private fun ColorScheme.badgeOnAccent(status: ReloadStatus?): Color =
    when {
        status == null -> onSurfaceVariant
        toneOf(status) == Tone.OK -> onTertiary
        toneOf(status) == Tone.WARN -> onSecondary
        toneOf(status) == Tone.BAD -> onError
        else -> onPrimary
    }

private fun badgeIcon(status: ReloadStatus?): ImageVector? =
    when {
        status == ReloadStatus.RELOADING -> null
        status == null -> Icons.Filled.Remove
        toneOf(status) == Tone.OK -> Icons.Filled.Check
        toneOf(status) == Tone.WARN -> Icons.Filled.PriorityHigh
        toneOf(status) == Tone.BAD -> Icons.Filled.Close
        else -> Icons.Filled.Remove
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultBadge(
    transition: Transition<ReloadStatus?>,
    modifier: Modifier = Modifier,
) {
    val status = transition.targetState
    val scheme = MaterialTheme.colorScheme
    val colorSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val accent by transition.animateColor(transitionSpec = { colorSpec }, label = "accent") { scheme.badgeAccent(it) }
    val onAccent by transition.animateColor(transitionSpec = { colorSpec }, label = "onAccent") { scheme.badgeOnAccent(it) }

    val morphs =
        remember {
            BadgePolygons.indices.map {
                Morph(BadgePolygons[it].normalized(), BadgePolygons[(it + 1) % BadgePolygons.size].normalized())
            }
        }
    val scaleFactor = remember { rotationSafeScale(BadgePolygons) }
    val reloading = status == ReloadStatus.RELOADING
    val reducedMotion = rememberReducedMotion()
    var step by remember { mutableIntStateOf(status.restIndex) }
    LaunchedEffect(status, reducedMotion) {
        if (reloading && !reducedMotion) {
            while (true) {
                delay(MORPH_INTERVAL_MILLIS)
                step += 1
            }
        } else {
            val rest = status.restIndex
            step = ((step - rest).toFloat() / morphs.size).roundToInt() * morphs.size + rest
        }
    }
    val morphSpec = remember { spring<Float>(dampingRatio = 0.6f, stiffness = 200f) }
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val stepTransition = updateTransition(targetState = step, label = "badgeStep")
    val progress by stepTransition.animateFloat(
        transitionSpec = { if (reloading) morphSpec else settleSpec },
        label = "badgeMorph",
    ) { it.toFloat() }

    val path = remember { Path() }
    val scaleMatrix = remember { Matrix() }
    Box(
        modifier =
            modifier.size(BadgeSize).drawBehind {
                val whole = floor(progress)
                val index = Math.floorMod(whole.toInt(), morphs.size)
                rotate(progress * STEP_TURN_DEGREES) {
                    drawPath(fitted(morphs[index].toPath(progress - whole, path), size, scaleFactor, scaleMatrix), accent)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        transition.AnimatedContent(
            transitionSpec = {
                (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = ICON_ENTER_SCALE))
                    .togetherWith(fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = ICON_ENTER_SCALE))
            },
            contentKey = ::badgeIcon,
        ) { target ->
            badgeIcon(target)?.let { Icon(it, contentDescription = null, tint = onAccent, modifier = Modifier.size(BadgeIconSize)) }
        }
    }
}

private fun rotationSafeScale(polygons: List<RoundedPolygon>): Float {
    val bounds = FloatArray(4)
    val maxBounds = FloatArray(4)
    return polygons.minOf { polygon ->
        polygon.calculateBounds(bounds)
        polygon.calculateMaxBounds(maxBounds)
        max((bounds[2] - bounds[0]) / (maxBounds[2] - maxBounds[0]), (bounds[3] - bounds[1]) / (maxBounds[3] - maxBounds[1]))
    }
}

private fun fitted(
    path: Path,
    size: Size,
    scaleFactor: Float,
    scaleMatrix: Matrix,
): Path {
    scaleMatrix.reset()
    scaleMatrix.scale(x = size.width * scaleFactor, y = size.height * scaleFactor)
    path.transform(scaleMatrix)
    path.translate(size.center - path.getBounds().center)
    return path
}

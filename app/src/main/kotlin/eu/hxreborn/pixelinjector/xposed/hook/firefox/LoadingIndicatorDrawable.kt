/*
 * Copyright (C) 2024 The Android Open Source Project
 * Copyright (C) 2026 hxreborn
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from material-components/material-components-android:
 * https://github.com/material-components/material-components-android/commit/66b0eeb53afeb6025ffbbd37734b08e5bce197ed
 * lib/java/com/google/android/material/loadingindicator/LoadingIndicatorAnimatorDelegate.java and
 * lib/java/com/google/android/material/loadingindicator/LoadingIndicatorDrawingDelegate.java
 */
package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val NANOS_PER_SHAPE = 650_000_000L
private const val CONSTANT_ROTATION_PER_SHAPE_DEGREES = 50f
private const val EXTRA_ROTATION_PER_SHAPE_DEGREES = 90f
private const val ROTATION_PER_SHAPE_DEGREES =
    CONSTANT_ROTATION_PER_SHAPE_DEGREES + EXTRA_ROTATION_PER_SHAPE_DEGREES
private const val SPRING_STIFFNESS = 200.0
private const val SPRING_DAMPING_RATIO = 0.6
private const val SPRING_VALUE_THRESHOLD = 0.01 * 0.75
private const val SPRING_VELOCITY_THRESHOLD = SPRING_VALUE_THRESHOLD * 62.5
private const val NANOS_PER_SECOND = 1e9
private const val INDICATOR_SIZE_RATIO = 38f / 48f
private const val DETERMINATE_DEGREES_PER_PROGRESS = -180f
private const val START_ANGLE_DEGREES = -90f
private const val FULL_TURN_DEGREES = 360f
private const val START_ANCHOR = 0
private const val FIRST_CONTROL = 1
private const val SECOND_CONTROL = 2
private const val END_ANCHOR = 3
private const val AXES = 2

internal class LoadingIndicatorDrawable(
    private val stock: Drawable,
    private val context: Context,
    private val progress: () -> Float,
) : Drawable(),
    Drawable.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var night: Boolean? = null
    private val path = Path()
    private var startNanos = 0L
    private var morphTarget = 1
    private var morphFactor = 0f
    private var morphVelocity = 0.0
    private var springFrameNanos = 0L
    private var rotationDegrees = 0f

    override fun draw(canvas: Canvas) {
        val running = (stock as Animatable).isRunning
        val morph: FloatArray
        val fraction: Float
        val rotation: Float
        if (running) {
            advance(System.nanoTime())
            val shapeIndex = floor(morphFactor).toInt()
            morph = indeterminateMorphs[shapeIndex.mod(indeterminateMorphs.size)]
            fraction = morphFactor - shapeIndex
            rotation = rotationDegrees
        } else {
            startNanos = 0L
            morph = determinateMorph
            fraction = progress().coerceIn(0f, 1f)
            rotation = fraction * DETERMINATE_DEGREES_PER_PROGRESS
        }
        val area = bounds
        val radius = min(area.width(), area.height()) / 2f
        resolveColors()
        canvas.drawCircle(area.exactCenterX(), area.exactCenterY(), radius, containerPaint)
        buildPath(morph, fraction, radius * INDICATOR_SIZE_RATIO)
        paint.alpha = stock.alpha
        canvas.save()
        canvas.translate(area.exactCenterX(), area.exactCenterY())
        canvas.rotate(START_ANGLE_DEGREES + rotation)
        canvas.drawPath(path, paint)
        canvas.restore()
        if (running) invalidateSelf()
    }

    private fun resolveColors() {
        val nightNow = context.resources.configuration.isNightModeActive
        if (nightNow == night) return
        night = nightNow
        val (containerId, indicatorId) =
            if (nightNow) {
                R.color.system_primary_container_dark to R.color.system_on_primary_container_dark
            } else {
                R.color.system_primary_container_light to R.color.system_on_primary_container_light
            }
        containerPaint.color = context.getColor(containerId)
        paint.color = context.getColor(indicatorId)
    }

    private fun advance(now: Long) {
        if (startNanos == 0L) {
            startNanos = now
            morphFactor = 0f
            morphVelocity = 0.0
            springFrameNanos = 0L
        }
        val elapsed = now - startNanos
        morphTarget = 1 + (elapsed / NANOS_PER_SHAPE).toInt()
        val timeFraction = (elapsed % NANOS_PER_SHAPE).toFloat() / NANOS_PER_SHAPE
        stepSpring(now)
        val shapeBase = morphTarget - 1
        rotationDegrees =
            (
                ROTATION_PER_SHAPE_DEGREES * shapeBase +
                    CONSTANT_ROTATION_PER_SHAPE_DEGREES * timeFraction +
                    EXTRA_ROTATION_PER_SHAPE_DEGREES * (morphFactor - shapeBase)
            ) % FULL_TURN_DEGREES
    }

    private fun stepSpring(frameNanos: Long) {
        val elapsedSeconds =
            if (springFrameNanos == 0L) 0.0 else (frameNanos - springFrameNanos) / NANOS_PER_SECOND
        springFrameNanos = frameNanos
        val naturalFrequency = sqrt(SPRING_STIFFNESS)
        val dampedFrequency =
            naturalFrequency * sqrt(1 - SPRING_DAMPING_RATIO * SPRING_DAMPING_RATIO)
        val decayRate = SPRING_DAMPING_RATIO * naturalFrequency
        val displacement = (morphFactor - morphTarget).toDouble()
        val sineCoefficient = (morphVelocity + decayRate * displacement) / dampedFrequency
        val decay = exp(-decayRate * elapsedSeconds)
        val cosine = cos(dampedFrequency * elapsedSeconds)
        val sine = sin(dampedFrequency * elapsedSeconds)
        var nextDisplacement = decay * (displacement * cosine + sineCoefficient * sine)
        var nextVelocity =
            -decayRate * nextDisplacement +
                decay * dampedFrequency * (sineCoefficient * cosine - displacement * sine)
        if (abs(nextDisplacement) < SPRING_VALUE_THRESHOLD &&
            abs(nextVelocity) < SPRING_VELOCITY_THRESHOLD
        ) {
            nextDisplacement = 0.0
            nextVelocity = 0.0
        }
        morphFactor = (morphTarget + nextDisplacement).toFloat()
        morphVelocity = nextVelocity
    }

    private fun buildPath(
        morph: FloatArray,
        fraction: Float,
        scale: Float,
    ) {
        path.rewind()
        val cubics = morph.size / FLOATS_PER_CUBIC

        fun point(
            cubic: Int,
            anchor: Int,
            axis: Int,
        ): Float {
            val index = cubic * FLOATS_PER_CUBIC + (anchor * AXES + axis) * 2
            return (morph[index] + (morph[index + 1] - morph[index]) * fraction) * scale
        }
        path.moveTo(point(0, START_ANCHOR, 0), point(0, START_ANCHOR, 1))
        for (cubic in 0 until cubics) {
            val last = cubic == cubics - 1
            val endCubic = if (last) 0 else cubic
            val endAnchor = if (last) START_ANCHOR else END_ANCHOR
            path.cubicTo(
                point(cubic, FIRST_CONTROL, 0),
                point(cubic, FIRST_CONTROL, 1),
                point(cubic, SECOND_CONTROL, 0),
                point(cubic, SECOND_CONTROL, 1),
                point(endCubic, endAnchor, 0),
                point(endCubic, endAnchor, 1),
            )
        }
        path.close()
    }

    override fun setAlpha(alpha: Int) {
        stock.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(
        who: Drawable,
        what: Runnable,
        `when`: Long,
    ) = scheduleSelf(what, `when`)

    override fun unscheduleDrawable(
        who: Drawable,
        what: Runnable,
    ) = unscheduleSelf(what)
}

package com.afzal.rozaalarm.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A quiet night sky: slowly twinkling stars with the occasional shooting star.
 *
 * <p>Everything is drawn procedurally so the effect costs one small view instead of a set of
 * bitmaps, and it scales to any screen size.</p>
 */
public class StarfieldView extends View {

    private static final int STAR_COUNT = 64;
    private static final long SHOOTING_STAR_INTERVAL_MS = 4_200L;
    private static final long SHOOTING_STAR_DURATION_MS = 1_100L;

    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starRadius = new float[STAR_COUNT];
    private final float[] starPhase = new float[STAR_COUNT];
    private final float[] starSpeed = new float[STAR_COUNT];

    @Nullable
    private ValueAnimator animator;
    private long startTime;
    private boolean layoutReady;

    public StarfieldView(Context context) {
        this(context, null);
    }

    public StarfieldView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StarfieldView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        starPaint.setStyle(Paint.Style.FILL);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextFloat() * w;
            // Keep the densest band in the upper half where the moon sits.
            starY[i] = (float) Math.pow(random.nextFloat(), 1.4f) * h;
            starRadius[i] = 0.8f + random.nextFloat() * 2.0f;
            starPhase[i] = random.nextFloat() * (float) Math.PI * 2f;
            starSpeed[i] = 0.7f + random.nextFloat() * 1.6f;
        }
        layoutReady = true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = System.currentTimeMillis();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2_000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> invalidate());
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!layoutReady) {
            return;
        }
        float elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000f;

        for (int i = 0; i < STAR_COUNT; i++) {
            float twinkle = 0.45f + 0.55f
                    * (float) ((Math.sin(starPhase[i] + elapsedSeconds * starSpeed[i]) + 1.0) / 2.0);
            starPaint.setColor(Color.argb((int) (twinkle * 210), 255, 252, 236));
            canvas.drawCircle(starX[i], starY[i], starRadius[i] * (0.7f + twinkle * 0.5f),
                    starPaint);
        }

        drawShootingStar(canvas);
    }

    private void drawShootingStar(@NonNull Canvas canvas) {
        long now = System.currentTimeMillis() - startTime;
        long cyclePosition = now % SHOOTING_STAR_INTERVAL_MS;
        if (cyclePosition > SHOOTING_STAR_DURATION_MS) {
            return;
        }
        float progress = cyclePosition / (float) SHOOTING_STAR_DURATION_MS;

        // A different launch point on every cycle, derived from the cycle index.
        long cycleIndex = now / SHOOTING_STAR_INTERVAL_MS;
        float seed = ((cycleIndex * 2654435761L) % 1000) / 1000f;

        float width = getWidth();
        float height = getHeight();
        float originX = width * (0.15f + seed * 0.6f);
        float originY = height * (0.05f + seed * 0.28f);
        float travel = width * 0.45f;

        float headX = originX + travel * progress;
        float headY = originY + travel * 0.55f * progress;
        float tailLength = travel * 0.22f * (1f - Math.abs(progress - 0.5f) * 1.4f);
        float alpha = (float) Math.sin(progress * Math.PI);

        trailPaint.setStrokeWidth(2.4f);
        trailPaint.setColor(Color.argb((int) (alpha * 190), 246, 215, 155));
        canvas.drawLine(headX - tailLength, headY - tailLength * 0.55f, headX, headY, trailPaint);

        starPaint.setColor(Color.argb((int) (alpha * 235), 255, 255, 245));
        canvas.drawCircle(headX, headY, 2.6f, starPaint);
    }
}

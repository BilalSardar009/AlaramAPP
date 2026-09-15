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

/** Concentric rings breathing outwards from the centre, drawn behind the ringing alarm's logo. */
public class PulseRingView extends View {

    private static final int RING_COUNT = 3;
    private static final long CYCLE_MS = 2_600L;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private ValueAnimator animator;
    private float phase;
    private int ringColor = Color.rgb(21, 101, 76);

    public PulseRingView(Context context) {
        this(context, null);
    }

    public PulseRingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PulseRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    public void setRingColor(int color) {
        ringColor = color;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
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
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float maxRadius = Math.min(centerX, centerY);
        float minRadius = maxRadius * 0.34f;

        for (int i = 0; i < RING_COUNT; i++) {
            float progress = (phase + i / (float) RING_COUNT) % 1f;
            float radius = minRadius + (maxRadius - minRadius) * progress;
            // Fade out as the ring travels, so the edge never looks cut off.
            int alpha = (int) (110 * (1f - progress) * (1f - progress));
            ringPaint.setColor(Color.argb(alpha, Color.red(ringColor), Color.green(ringColor),
                    Color.blue(ringColor)));
            ringPaint.setStrokeWidth(2.5f + 2.5f * (1f - progress));
            canvas.drawCircle(centerX, centerY, radius, ringPaint);
        }
    }
}

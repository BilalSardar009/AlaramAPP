package com.afzal.rozaalarm.ui;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.afzal.rozaalarm.R;
import com.afzal.rozaalarm.databinding.ActivitySplashBinding;

/** Animated entry screen: the crescent settles in, the wordmark fades up, then home opens. */
public class SplashActivity extends AppCompatActivity {

    private static final long HOLD_MILLIS = 2_300L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ActivitySplashBinding binding;
    private boolean handedOver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        runIntro();

        // Tapping anywhere skips straight through.
        binding.splashRoot.setOnClickListener(v -> openHome());

        handler.postDelayed(this::openHome, HOLD_MILLIS);
    }

    private void runIntro() {
        View[] staggered = {binding.splashTitle, binding.splashRule, binding.splashTagline,
                binding.splashBismillah};
        for (View view : staggered) {
            view.setAlpha(0f);
            view.setTranslationY(28f);
        }

        binding.splashLogo.setAlpha(0f);
        binding.splashLogo.setScaleX(0.55f);
        binding.splashLogo.setScaleY(0.55f);
        binding.splashLogo.setRotation(-24f);
        binding.splashLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setDuration(900L)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        binding.splashPulse.setAlpha(0f);
        binding.splashPulse.animate().alpha(1f).setStartDelay(600L).setDuration(700L).start();

        long delay = 520L;
        for (View view : staggered) {
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(620L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            delay += 180L;
        }

        ObjectAnimator progress = ObjectAnimator.ofInt(binding.splashProgress, "progress", 0, 100);
        progress.setDuration(HOLD_MILLIS - 200L);
        progress.setInterpolator(new AccelerateDecelerateInterpolator());
        progress.start();
    }


    private void openHome() {
        if (handedOver || isFinishing()) {
            return;
        }
        handedOver = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.afzal.rozaalarm.alarm.AlarmScheduler;
import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Prefs;

import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps {@link HijriDates} in step with the user's corrections.
 *
 * <p>Conversion happens on background threads and on the UI thread, so the corrections live in
 * memory inside {@code HijriDates}; this class is the only thing that loads them from storage and
 * pushes them in. Any change re-registers every alarm, because a Hijri rule may now resolve to a
 * different day.</p>
 */
public class HijriCorrections {

    private static volatile HijriCorrections instance;

    private final Context appContext;
    private final HijriAdjustmentDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private HijriCorrections(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.get(appContext).hijriAdjustmentDao();
    }

    @NonNull
    public static HijriCorrections get(@NonNull Context context) {
        if (instance == null) {
            synchronized (HijriCorrections.class) {
                if (instance == null) {
                    instance = new HijriCorrections(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public ExecutorService io() {
        return io;
    }

    /** Loads the stored corrections into {@link HijriDates}. Safe to call on any thread. */
    public void reloadAsync() {
        io.execute(this::reloadSync);
    }

    @WorkerThread
    public void reloadSync() {
        Map<Integer, Integer> months = new HashMap<>();
        List<HijriAdjustment> stored = dao.allSync();
        for (HijriAdjustment adjustment : stored) {
            if (adjustment.offsetDays != 0) {
                months.put(adjustment.monthKey, adjustment.offsetDays);
            }
        }
        HijriDates.configure(Prefs.hijriOffset(appContext), months);
    }

    /** Applies a new global correction and re-registers every alarm. */
    public void setGlobalOffset(int offsetDays) {
        setGlobalOffset(offsetDays, null);
    }

    /**
     * Applies a new global correction and re-registers every alarm, then runs {@code onApplied}
     * on the main thread so the screen can redraw against the corrected dates.
     */
    public void setGlobalOffset(int offsetDays, @Nullable Runnable onApplied) {
        Prefs.setHijriOffset(appContext, HijriDates.clampOffset(offsetDays));
        io.execute(() -> {
            reloadSync();
            AlarmScheduler.rescheduleAll(appContext);
            notifyApplied(onApplied);
        });
    }

    /** Corrects one Hijri month on top of the global correction. Zero removes the correction. */
    public void setMonthOffset(int hijriYear, int hijriMonth, int offsetDays,
                               @Nullable Runnable onApplied) {
        int key = HijriDates.monthKey(hijriYear, hijriMonth);
        int clamped = HijriDates.clampOffset(offsetDays);
        io.execute(() -> {
            if (clamped == 0) {
                dao.deleteByKey(key);
            } else {
                dao.upsert(new HijriAdjustment(key, clamped));
            }
            reloadSync();
            AlarmScheduler.rescheduleAll(appContext);
            notifyApplied(onApplied);
        });
    }

    /** Clears every per-month correction, leaving the global one in place. */
    public void clearMonthOffsets(@Nullable Runnable onApplied) {
        io.execute(() -> {
            dao.clear();
            reloadSync();
            AlarmScheduler.rescheduleAll(appContext);
            notifyApplied(onApplied);
        });
    }

    /** How many months currently carry a correction. */
    @WorkerThread
    public int monthOffsetCount() {
        int count = 0;
        for (HijriAdjustment adjustment : dao.allSync()) {
            if (adjustment.offsetDays != 0) {
                count++;
            }
        }
        return count;
    }

    private void notifyApplied(@Nullable Runnable onApplied) {
        if (onApplied != null) {
            mainThread.post(onApplied);
        }
    }
}

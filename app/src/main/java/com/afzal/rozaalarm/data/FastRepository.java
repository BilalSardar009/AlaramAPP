package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.afzal.rozaalarm.util.HijriDates;
import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.Occasions;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads and writes the fasting record. */
public class FastRepository {

    private static volatile FastRepository instance;

    private final FastLogDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private FastRepository(@NonNull Context context) {
        this.dao = AppDatabase.get(context.getApplicationContext()).fastLogDao();
    }

    @NonNull
    public static FastRepository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (FastRepository.class) {
                if (instance == null) {
                    instance = new FastRepository(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public ExecutorService io() {
        return io;
    }

    public interface Callback {
        void onDone();
    }

    // ---- observation --------------------------------------------------------------------------

    @NonNull
    public LiveData<List<FastLog>> observeAll() {
        return dao.observeAll();
    }

    @NonNull
    public LiveData<List<FastLog>> observeRange(int fromDateKey, int toDateKey) {
        return dao.observeRange(fromDateKey, toDateKey);
    }

    @NonNull
    public LiveData<List<MonthTally>> observeHijriTallies() {
        return dao.observeHijriTallies();
    }

    @NonNull
    public LiveData<List<MonthTally>> observeGregorianTallies() {
        return dao.observeGregorianTallies();
    }

    @NonNull
    public LiveData<Integer> observeTotalKept() {
        return dao.observeTotalKept();
    }

    @NonNull
    public LiveData<Integer> observeKeptInHijriYear(int hijriYear) {
        return dao.observeKeptInHijriYear(hijriYear);
    }

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Records a fast for the day containing {@code millis}, replacing anything already recorded.
     * The Hijri date and the occasion are captured at the moment of logging.
     */
    public void log(long millis, int status, @Nullable String occasionId, @Nullable String note,
                    @Nullable Callback callback) {
        io.execute(() -> {
            logSync(millis, status, occasionId, note);
            if (callback != null) {
                callback.onDone();
            }
        });
    }

    @WorkerThread
    public void logSync(long millis, int status, @Nullable String occasionId,
                        @Nullable String note) {
        FastLog log = new FastLog();
        FastLog existing = dao.byDateSync(FastLog.dateKeyOf(millis));
        if (existing != null) {
            log.id = existing.id;
        }
        log.dateKey = FastLog.dateKeyOf(millis);
        log.hijriYear = HijriDates.year(millis);
        log.hijriMonth = HijriDates.month(millis);
        log.hijriDay = HijriDates.dayOfMonth(millis);
        log.status = status;
        log.note = note;
        log.loggedAt = System.currentTimeMillis();

        if (occasionId != null) {
            log.occasionId = occasionId;
        } else {
            Occasion guessed = Occasions.primaryForInstant(millis);
            log.occasionId = guessed == null ? null : guessed.id();
        }

        dao.insert(log);
    }

    public void remove(long millis, @Nullable Callback callback) {
        io.execute(() -> {
            dao.deleteByDate(FastLog.dateKeyOf(millis));
            if (callback != null) {
                callback.onDone();
            }
        });
    }

    /** Records the fast if the day is not recorded, and clears it if it is. */
    public void toggle(long millis, @Nullable Callback callback) {
        io.execute(() -> {
            int key = FastLog.dateKeyOf(millis);
            if (dao.byDateSync(key) != null) {
                dao.deleteByDate(key);
            } else {
                logSync(millis, FastLog.STATUS_KEPT, null, null);
            }
            if (callback != null) {
                callback.onDone();
            }
        });
    }

    @WorkerThread
    @Nullable
    public FastLog byDateSync(int dateKey) {
        return dao.byDateSync(dateKey);
    }

    @WorkerThread
    @NonNull
    public List<FastLog> forHijriMonthSync(int hijriYear, int hijriMonth) {
        return dao.forHijriMonthSync(hijriYear, hijriMonth);
    }

    // ---- streak -------------------------------------------------------------------------------

    /**
     * How many days in an unbroken run end today (or yesterday, so a streak is not reported as
     * broken before the day is over).
     */
    @WorkerThread
    public int currentStreak() {
        return streakFrom(new java.util.HashSet<>(dao.keptDateKeysSync()));
    }

    /**
     * Counts back from today over a set of {@code yyyyMMdd} keys. Today not being logged does not
     * break the streak — the day is not over yet — so counting starts from yesterday in that case.
     */
    public static int streakFrom(@NonNull java.util.Set<Integer> keptDateKeys) {
        if (keptDateKeys.isEmpty()) {
            return 0;
        }
        Calendar cursor = Calendar.getInstance();
        if (!keptDateKeys.contains(FastLog.dateKeyOf(cursor))) {
            cursor.add(Calendar.DAY_OF_MONTH, -1);
            if (!keptDateKeys.contains(FastLog.dateKeyOf(cursor))) {
                return 0;
            }
        }
        int streak = 0;
        while (keptDateKeys.contains(FastLog.dateKeyOf(cursor))) {
            streak++;
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }
        return streak;
    }
}

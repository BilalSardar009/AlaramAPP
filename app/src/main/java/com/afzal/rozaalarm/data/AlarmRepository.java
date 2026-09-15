package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.afzal.rozaalarm.alarm.AlarmScheduler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single entry point for reading and writing alarms. Every write also (re)schedules the alarm with
 * {@link AlarmScheduler} so the database and the {@code AlarmManager} never drift apart.
 */
public class AlarmRepository {

    private static volatile AlarmRepository instance;

    private final Context appContext;
    private final AlarmDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private AlarmRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.get(appContext).alarmDao();
    }

    @NonNull
    public static AlarmRepository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AlarmRepository.class) {
                if (instance == null) {
                    instance = new AlarmRepository(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public LiveData<List<Alarm>> observeAll() {
        return dao.observeAll();
    }

    @NonNull
    public ExecutorService io() {
        return io;
    }

    /** Callback used by the UI so it can react once a background write has finished. */
    public interface Callback {
        void onDone(long alarmId);
    }

    public void save(@NonNull Alarm alarm, @Nullable Callback callback) {
        io.execute(() -> {
            long id = alarm.id;
            if (id == 0L) {
                id = dao.insert(alarm);
                alarm.id = id;
            } else {
                dao.update(alarm);
            }
            AlarmScheduler.schedule(appContext, alarm);
            if (callback != null) {
                callback.onDone(id);
            }
        });
    }

    public void delete(@NonNull Alarm alarm, @Nullable Callback callback) {
        io.execute(() -> {
            AlarmScheduler.cancel(appContext, alarm);
            dao.delete(alarm);
            if (callback != null) {
                callback.onDone(alarm.id);
            }
        });
    }

    public void setEnabled(long id, boolean enabled, @Nullable Callback callback) {
        io.execute(() -> {
            Alarm alarm = dao.getByIdSync(id);
            if (alarm == null) {
                return;
            }
            alarm.enabled = enabled;
            dao.update(alarm);
            if (enabled) {
                AlarmScheduler.schedule(appContext, alarm);
            } else {
                AlarmScheduler.cancel(appContext, alarm);
            }
            if (callback != null) {
                callback.onDone(id);
            }
        });
    }

    @WorkerThread
    @Nullable
    public Alarm getByIdSync(long id) {
        return dao.getByIdSync(id);
    }

    @WorkerThread
    @NonNull
    public List<Alarm> getEnabledSync() {
        return dao.getEnabledSync();
    }

    @WorkerThread
    @NonNull
    public List<Alarm> getAllSync() {
        return dao.getAllSync();
    }

    @WorkerThread
    public long insertSync(@NonNull Alarm alarm) {
        long id = dao.insert(alarm);
        alarm.id = id;
        return id;
    }

    @WorkerThread
    public void updateSync(@NonNull Alarm alarm) {
        dao.update(alarm);
    }

    @WorkerThread
    public void markTriggered(long id, long whenMillis) {
        dao.setLastTriggered(id, whenMillis);
    }
}

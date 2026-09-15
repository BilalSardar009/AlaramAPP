package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {Alarm.class, FastLog.class, HijriAdjustment.class},
        version = 2,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "roza_alarm.db";

    private static volatile AppDatabase instance;

    public abstract AlarmDao alarmDao();

    public abstract FastLogDao fastLogDao();

    public abstract HijriAdjustmentDao hijriAdjustmentDao();

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            // Schemas are exported to app/schemas from version 2 onwards, so future
                            // releases can ship real migrations instead of dropping the database.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}

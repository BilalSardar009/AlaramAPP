package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Alarm.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "roza_alarm.db";

    private static volatile AppDatabase instance;

    public abstract AlarmDao alarmDao();

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}

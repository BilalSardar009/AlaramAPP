package com.afzal.rozaalarm.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {Alarm.class, FastLog.class, HijriAdjustment.class},
        version = 3,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "roza_alarm.db";

    private static volatile AppDatabase instance;

    public abstract AlarmDao alarmDao();

    public abstract FastLogDao fastLogDao();

    public abstract HijriAdjustmentDao hijriAdjustmentDao();

    /**
     * Version 3 drops the daily, weekly and monthly rules in favour of the two the calendar
     * offers: a named fasting occasion, or a set of dates picked off the grid.
     *
     * <p>The alarms table only gains a column — the retired ones stay, because rebuilding the
     * table would put the fasting history at risk for no visible gain. The rows themselves are
     * rewritten: a one-off becomes a single-date alarm, the old Hijri 13/14/15 rule becomes the
     * White Days occasion, and anything still left on a retired rule is removed.</p>
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN dateKeys TEXT NOT NULL DEFAULT ''");

            // The classic "13, 14, 15 of every Hijri month" rule is exactly Ayyam al-Beed.
            database.execSQL("UPDATE alarms SET repeatMode = 4, occasionId = 'white_days' "
                    + "WHERE repeatMode = 3 AND calendarType = 1 AND monthDays = '13,14,15'");

            // A one-off alarm becomes a date alarm holding that single day.
            database.execSQL("UPDATE alarms SET repeatMode = 5, "
                    + "dateKeys = strftime('%Y%m%d', onceDateMillis / 1000, 'unixepoch', "
                    + "'localtime') "
                    + "WHERE repeatMode = 0 AND onceDateMillis > 0");

            database.execSQL("DELETE FROM alarms WHERE repeatMode NOT IN (4, 5)");
        }
    };

    @NonNull
    public static AppDatabase get(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_2_3)
                            // Only reached if an install somehow carries a version the migrations
                            // above do not cover; the fasting history is worth a real migration,
                            // so this stays a last resort rather than the plan.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}

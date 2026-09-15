package com.afzal.rozaalarm.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY enabled DESC, hour ASC, minute ASC, id ASC")
    LiveData<List<Alarm>> observeAll();

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC, id ASC")
    List<Alarm> getAllSync();

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    List<Alarm> getEnabledSync();

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    Alarm getByIdSync(long id);

    @Insert
    long insert(Alarm alarm);

    @Update
    void update(Alarm alarm);

    @Delete
    void delete(Alarm alarm);

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    void setEnabled(long id, boolean enabled);

    @Query("UPDATE alarms SET lastTriggeredAt = :whenMillis WHERE id = :id")
    void setLastTriggered(long id, long whenMillis);
}

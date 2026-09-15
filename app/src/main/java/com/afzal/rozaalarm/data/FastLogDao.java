package com.afzal.rozaalarm.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FastLogDao {

    @Query("SELECT * FROM fast_log ORDER BY dateKey DESC")
    LiveData<List<FastLog>> observeAll();

    @Query("SELECT * FROM fast_log WHERE dateKey BETWEEN :fromKey AND :toKey ORDER BY dateKey ASC")
    LiveData<List<FastLog>> observeRange(int fromKey, int toKey);

    @Query("SELECT * FROM fast_log WHERE dateKey BETWEEN :fromKey AND :toKey ORDER BY dateKey ASC")
    List<FastLog> rangeSync(int fromKey, int toKey);

    @Query("SELECT * FROM fast_log WHERE hijriYear = :hijriYear AND hijriMonth = :hijriMonth "
            + "ORDER BY hijriDay ASC")
    List<FastLog> forHijriMonthSync(int hijriYear, int hijriMonth);

    @Query("SELECT * FROM fast_log WHERE dateKey = :dateKey LIMIT 1")
    FastLog byDateSync(int dateKey);

    /** Kept fasts per Hijri month, newest first. */
    @Query("SELECT hijriYear AS year, hijriMonth AS month, COUNT(*) AS kept FROM fast_log "
            + "WHERE status = 0 GROUP BY hijriYear, hijriMonth "
            + "ORDER BY hijriYear DESC, hijriMonth DESC")
    LiveData<List<MonthTally>> observeHijriTallies();

    /** Kept fasts per Gregorian month, newest first. */
    @Query("SELECT (dateKey / 10000) AS year, ((dateKey / 100) % 100) AS month, COUNT(*) AS kept "
            + "FROM fast_log WHERE status = 0 GROUP BY year, month ORDER BY year DESC, month DESC")
    LiveData<List<MonthTally>> observeGregorianTallies();

    @Query("SELECT COUNT(*) FROM fast_log WHERE status = 0")
    LiveData<Integer> observeTotalKept();

    @Query("SELECT COUNT(*) FROM fast_log WHERE status = 0 AND hijriYear = :hijriYear")
    LiveData<Integer> observeKeptInHijriYear(int hijriYear);

    /** Every kept day, ascending — used to work out the current streak. */
    @Query("SELECT dateKey FROM fast_log WHERE status = 0 ORDER BY dateKey ASC")
    List<Integer> keptDateKeysSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FastLog log);

    @Update
    void update(FastLog log);

    @Delete
    void delete(FastLog log);

    @Query("DELETE FROM fast_log WHERE dateKey = :dateKey")
    void deleteByDate(int dateKey);
}

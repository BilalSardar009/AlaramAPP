package com.afzal.rozaalarm.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HijriAdjustmentDao {

    @Query("SELECT * FROM hijri_adjustments")
    List<HijriAdjustment> allSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HijriAdjustment adjustment);

    @Query("DELETE FROM hijri_adjustments WHERE monthKey = :monthKey")
    void deleteByKey(int monthKey);

    @Query("DELETE FROM hijri_adjustments")
    void clear();
}

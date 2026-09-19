package com.afzal.rozaalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.util.Occurrences;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Covers the date alarms the calendar's multi-select produces.
 *
 * <p>These run on a plain JVM, so they stay off {@code android.icu}: the occasion branch and the
 * forbidden-day guard both reach it, which is why every alarm below sets
 * {@code skipForbiddenDays = false}. The rules those paths are built on are covered by
 * {@link OccasionsTest}, which takes the Hijri date as plain numbers.</p>
 */
public class OccurrencesTest {

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, day, hour, minute, 0);
        return cal.getTimeInMillis();
    }

    /** An alarm on the given {@code yyyyMMdd} days at 3am. */
    private static Alarm onDays(Integer... dateKeys) {
        Alarm alarm = new Alarm();
        alarm.label = "Roza";
        alarm.repeatMode = Alarm.REPEAT_DATES;
        alarm.setDateKeyList(Arrays.asList(dateKeys));
        alarm.hour = 3;
        alarm.minute = 0;
        alarm.preReminderEnabled = true;
        alarm.preReminderDaysBefore = 1;
        alarm.preReminderHour = 20;
        alarm.preReminderMinute = 0;
        alarm.preReminderFirstDayOnly = true;
        alarm.skipForbiddenDays = false;
        return alarm;
    }

    // ---- the days themselves ------------------------------------------------------------------

    @Test
    public void everySelectedDayRingsInOrder() {
        Alarm alarm = onDays(20270315, 20270313, 20270314);
        List<Long> next = Occurrences.nextMany(alarm, at(2027, Calendar.MARCH, 1, 9, 0), 5);

        assertEquals(3, next.size());
        assertEquals(at(2027, Calendar.MARCH, 13, 3, 0), (long) next.get(0));
        assertEquals(at(2027, Calendar.MARCH, 14, 3, 0), (long) next.get(1));
        assertEquals(at(2027, Calendar.MARCH, 15, 3, 0), (long) next.get(2));
    }

    @Test
    public void daysAlreadyGoneAreNotOffered() {
        Alarm alarm = onDays(20270313, 20270314, 20270315);

        // Mid-morning on the 14th: the 13th and the 14th have both rung.
        assertEquals(at(2027, Calendar.MARCH, 15, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 14, 9, 0)));
    }

    @Test
    public void anAlarmWhoseDaysHaveAllPassedNeverFires() {
        assertEquals(Occurrences.NONE,
                Occurrences.next(onDays(20200101), System.currentTimeMillis()));
    }

    @Test
    public void anAlarmWithNoDaysNeverFires() {
        Alarm alarm = onDays();
        assertTrue(alarm.dateKeyList().isEmpty());
        assertEquals(Occurrences.NONE, Occurrences.next(alarm, System.currentTimeMillis()));
    }

    /**
     * The grid lets you move to any Hijri month, so a chosen day can sit years out. It is read
     * from the list rather than walked to, so distance costs nothing and nothing is missed.
     */
    @Test
    public void aDayYearsAwayIsStillFound() {
        Alarm alarm = onDays(20310612);
        assertEquals(at(2031, Calendar.JUNE, 12, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 1, 9, 0)));
    }

    @Test
    public void laterTodayStillRingsToday() {
        Alarm alarm = onDays(20270310);
        alarm.hour = 9;

        assertEquals(at(2027, Calendar.MARCH, 10, 9, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 7, 0)));
    }

    /** A time that has already gone by today does not quietly roll to tomorrow. */
    @Test
    public void earlierTodayDoesNotRollForward() {
        Alarm alarm = onDays(20270310);
        alarm.hour = 9;

        assertEquals(Occurrences.NONE,
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 11, 0)));
    }

    // ---- reminders ----------------------------------------------------------------------------

    @Test
    public void reminderLandsOnTheEveningBeforeTheFirstDay() {
        assertEquals(at(2027, Calendar.MARCH, 12, 20, 0),
                Occurrences.nextReminder(onDays(20270313, 20270314, 20270315),
                        at(2027, Calendar.MARCH, 1, 9, 0)));
    }

    @Test
    public void reminderSkipsTheMiddleOfARun() {
        // Late on the 12th the reminder has gone out; the 14th and 15th are mid-run, so there is
        // nothing left to remind about.
        assertEquals(Occurrences.NONE,
                Occurrences.nextReminder(onDays(20270313, 20270314, 20270315),
                        at(2027, Calendar.MARCH, 12, 21, 0)));
    }

    @Test
    public void everyDayOfARunRemindsWhenTheOptionIsOff() {
        Alarm alarm = onDays(20270313, 20270314, 20270315);
        alarm.preReminderFirstDayOnly = false;

        assertEquals(at(2027, Calendar.MARCH, 13, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 12, 21, 0)));
    }

    @Test
    public void twoSeparateRunsEachGetTheirOwnReminder() {
        Alarm alarm = onDays(20270313, 20270314, 20270420, 20270421);

        assertEquals(at(2027, Calendar.MARCH, 12, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 1, 9, 0)));
        assertEquals(at(2027, Calendar.APRIL, 19, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 15, 9, 0)));
    }

    @Test
    public void remindersCanBeTurnedOff() {
        Alarm alarm = onDays(20270313);
        alarm.preReminderEnabled = false;

        assertEquals(Occurrences.NONE,
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 1, 9, 0)));
    }

    @Test
    public void startsMidRunOnlyCountsAdjacentDays() {
        Alarm alarm = onDays(20270313, 20270314, 20270316);

        assertFalse(Occurrences.startsMidRun(alarm, at(2027, Calendar.MARCH, 13, 3, 0)));
        assertTrue(Occurrences.startsMidRun(alarm, at(2027, Calendar.MARCH, 14, 3, 0)));
        assertFalse(Occurrences.startsMidRun(alarm, at(2027, Calendar.MARCH, 16, 3, 0)));
    }

    // ---- occasion alarms ----------------------------------------------------------------------

    @Test
    public void anOccasionAlarmWithoutAnOccasionNeverFires() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_OCCASION;
        alarm.occasionId = null;
        alarm.skipForbiddenDays = false;

        assertEquals(Occurrences.NONE, Occurrences.next(alarm, System.currentTimeMillis()));
    }

    // ---- the date key <-> instant conversion --------------------------------------------------

    @Test
    public void dateKeysConvertBackToTheDayTheyName() {
        assertEquals(at(2027, Calendar.MARCH, 13, 5, 30),
                Occurrences.instantOfDateKey(20270313, 5, 30));
        assertEquals(at(2026, Calendar.DECEMBER, 31, 0, 0),
                Occurrences.instantOfDateKey(20261231, 0, 0));
    }

    // ---- the stored form ----------------------------------------------------------------------

    @Test
    public void dateKeyListSortsDeduplicatesAndDropsJunk() {
        Alarm alarm = new Alarm();
        alarm.dateKeys = "20270315, 20270313, abc, 20270313, , 20270314";

        assertEquals(Arrays.asList(20270313, 20270314, 20270315), alarm.dateKeyList());
    }

    @Test
    public void csvParsingIgnoresJunkAndKeepsOrder() {
        assertEquals(Arrays.asList(1, 13, 15),
                Alarm.parseCsv("15, 1, abc, 13, 99, ,13", 1, 31));
        assertEquals("13,14,15", Alarm.joinCsv(Arrays.asList(15, 13, 14)));
    }

    @Test
    public void copyCarriesTheFieldsThatDefineTheAlarm() {
        Alarm source = onDays(20270313, 20270314);
        source.occasionId = "arafah";
        source.skipForbiddenDays = true;

        Alarm copy = Alarm.copyOf(source);
        assertEquals(0L, copy.id);
        assertEquals("20270313,20270314", copy.dateKeys);
        assertEquals("arafah", copy.occasionId);
        assertEquals(Alarm.REPEAT_DATES, copy.repeatMode);
        assertTrue(copy.skipForbiddenDays);
    }
}

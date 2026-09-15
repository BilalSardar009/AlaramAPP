package com.afzal.rozaalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.afzal.rozaalarm.data.Alarm;
import com.afzal.rozaalarm.util.Occurrences;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Covers the repeat rules this app exists for.
 *
 * <p>These run on a plain JVM, so they stay on the Gregorian side of {@link Occurrences}; the Hijri
 * branch needs {@code android.icu} and is exercised on a device.</p>
 */
public class OccurrencesTest {

    /** The White Days: 13th, 14th and 15th of every month at 3am. */
    private static Alarm whiteDays() {
        Alarm alarm = new Alarm();
        alarm.label = "Ayyam al-Beed Sehri";
        alarm.repeatMode = Alarm.REPEAT_MONTHLY;
        alarm.calendarType = Alarm.CALENDAR_GREGORIAN;
        alarm.setMonthDayList(Arrays.asList(13, 14, 15));
        alarm.hour = 3;
        alarm.minute = 0;
        alarm.preReminderEnabled = true;
        alarm.preReminderDaysBefore = 1;
        alarm.preReminderHour = 20;
        alarm.preReminderMinute = 0;
        alarm.preReminderFirstDayOnly = true;
        return alarm;
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, day, hour, minute, 0);
        return cal.getTimeInMillis();
    }

    @Test
    public void whiteDaysFireOnThe13th14thAnd15th() {
        List<Long> next = Occurrences.nextMany(
                whiteDays(), at(2027, Calendar.MARCH, 1, 9, 0), 6, 0);

        assertEquals(6, next.size());
        assertEquals(at(2027, Calendar.MARCH, 13, 3, 0), (long) next.get(0));
        assertEquals(at(2027, Calendar.MARCH, 14, 3, 0), (long) next.get(1));
        assertEquals(at(2027, Calendar.MARCH, 15, 3, 0), (long) next.get(2));
        assertEquals(at(2027, Calendar.APRIL, 13, 3, 0), (long) next.get(3));
        assertEquals(at(2027, Calendar.APRIL, 14, 3, 0), (long) next.get(4));
        assertEquals(at(2027, Calendar.APRIL, 15, 3, 0), (long) next.get(5));
    }

    @Test
    public void reminderLandsOnTheEveningOfThe12th() {
        assertEquals(at(2027, Calendar.MARCH, 12, 20, 0),
                Occurrences.nextReminder(whiteDays(), at(2027, Calendar.MARCH, 1, 9, 0), 0));
    }

    @Test
    public void reminderSkipsTheMiddleOfARun() {
        // Late on the 12th the reminder has been delivered; the 14th and 15th are mid-run, so the
        // next reminder belongs to next month's 13th.
        assertEquals(at(2027, Calendar.APRIL, 12, 20, 0),
                Occurrences.nextReminder(whiteDays(), at(2027, Calendar.MARCH, 12, 21, 0), 0));
    }

    @Test
    public void everyDayOfARunRemindsWhenTheOptionIsOff() {
        Alarm alarm = whiteDays();
        alarm.preReminderFirstDayOnly = false;
        assertEquals(at(2027, Calendar.MARCH, 13, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 12, 21, 0), 0));
    }

    @Test
    public void the31stClampsToTheLastDayOfAShortMonth() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_MONTHLY;
        alarm.setMonthDayList(Arrays.asList(31));
        alarm.hour = 5;
        alarm.minute = 30;
        alarm.clampToMonthEnd = true;

        long from = at(2027, Calendar.FEBRUARY, 1, 0, 0);
        assertEquals(at(2027, Calendar.FEBRUARY, 28, 5, 30), Occurrences.next(alarm, from, 0));

        alarm.clampToMonthEnd = false;
        assertEquals(at(2027, Calendar.MARCH, 31, 5, 30), Occurrences.next(alarm, from, 0));
    }

    @Test
    public void weeklyRuleHitsTheSelectedWeekdays() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_WEEKLY;
        alarm.setWeekDayList(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY));
        alarm.hour = 4;
        alarm.minute = 15;

        // Starting midday on Tuesday 2 March 2027.
        List<Long> next = Occurrences.nextMany(alarm, at(2027, Calendar.MARCH, 2, 12, 0), 3, 0);
        assertEquals(at(2027, Calendar.MARCH, 4, 4, 15), (long) next.get(0));
        assertEquals(at(2027, Calendar.MARCH, 8, 4, 15), (long) next.get(1));
        assertEquals(at(2027, Calendar.MARCH, 11, 4, 15), (long) next.get(2));
    }

    @Test
    public void dailyRollsOverOnceTheTimeHasPassed() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_DAILY;
        alarm.hour = 3;
        alarm.minute = 0;

        assertEquals(at(2027, Calendar.MARCH, 10, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 1, 0), 0));
        assertEquals(at(2027, Calendar.MARCH, 11, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 4, 0), 0));
    }

    @Test
    public void aOneOffInThePastNeverFires() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_ONCE;
        alarm.hour = 7;
        alarm.minute = 0;
        alarm.onceDateMillis = at(2020, Calendar.JANUARY, 1, 0, 0);

        assertEquals(Occurrences.NONE,
                Occurrences.next(alarm, System.currentTimeMillis(), 0));
    }

    @Test
    public void monthlyRuleWithNoDaysSelectedNeverFires() {
        Alarm alarm = new Alarm();
        alarm.repeatMode = Alarm.REPEAT_MONTHLY;
        alarm.monthDays = "";

        assertEquals(Occurrences.NONE,
                Occurrences.next(alarm, System.currentTimeMillis(), 0));
    }

    @Test
    public void csvParsingIgnoresJunkAndKeepsOrder() {
        assertEquals(Arrays.asList(1, 13, 15),
                Alarm.parseCsv("15, 1, abc, 13, 99, ,13", 1, 31));
        assertEquals("13,14,15", Alarm.joinCsv(Arrays.asList(15, 13, 14)));
    }

    @Test
    public void ayyamAlBeedIsRecognised() {
        assertTrue(whiteDays().isAyyamAlBeed());
    }
}

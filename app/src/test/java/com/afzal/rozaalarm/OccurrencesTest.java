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
 * <p>These run on a plain JVM, so they stay on the Gregorian side of {@link Occurrences}. The
 * Hijri branch and the forbidden-day guard both reach {@code android.icu}, which is unavailable
 * here, so every alarm below sets {@code skipForbiddenDays = false}. Those paths are covered by
 * {@link OccasionsTest} (the pure rules) and by the instrumented calendar tests.</p>
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
        alarm.skipForbiddenDays = false;
        return alarm;
    }

    private static Alarm plainAlarm(int repeatMode, int hour, int minute) {
        Alarm alarm = new Alarm();
        alarm.repeatMode = repeatMode;
        alarm.hour = hour;
        alarm.minute = minute;
        alarm.skipForbiddenDays = false;
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
        List<Long> next = Occurrences.nextMany(whiteDays(), at(2027, Calendar.MARCH, 1, 9, 0), 6);

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
                Occurrences.nextReminder(whiteDays(), at(2027, Calendar.MARCH, 1, 9, 0)));
    }

    @Test
    public void reminderSkipsTheMiddleOfARun() {
        // Late on the 12th the reminder has been delivered; the 14th and 15th are mid-run, so the
        // next reminder belongs to next month's 13th.
        assertEquals(at(2027, Calendar.APRIL, 12, 20, 0),
                Occurrences.nextReminder(whiteDays(), at(2027, Calendar.MARCH, 12, 21, 0)));
    }

    @Test
    public void everyDayOfARunRemindsWhenTheOptionIsOff() {
        Alarm alarm = whiteDays();
        alarm.preReminderFirstDayOnly = false;
        assertEquals(at(2027, Calendar.MARCH, 13, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 12, 21, 0)));
    }

    @Test
    public void aDailyAlarmStillReminds() {
        // "First day of a run" is meaningless for a daily alarm, so it must not suppress reminders.
        Alarm alarm = plainAlarm(Alarm.REPEAT_DAILY, 3, 0);
        alarm.preReminderEnabled = true;
        alarm.preReminderFirstDayOnly = true;
        alarm.preReminderDaysBefore = 1;
        alarm.preReminderHour = 20;
        alarm.preReminderMinute = 0;

        assertEquals(at(2027, Calendar.MARCH, 10, 20, 0),
                Occurrences.nextReminder(alarm, at(2027, Calendar.MARCH, 10, 9, 0)));
    }

    @Test
    public void the31stClampsToTheLastDayOfAShortMonth() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_MONTHLY, 5, 30);
        alarm.setMonthDayList(Arrays.asList(31));
        alarm.clampToMonthEnd = true;

        long from = at(2027, Calendar.FEBRUARY, 1, 0, 0);
        assertEquals(at(2027, Calendar.FEBRUARY, 28, 5, 30), Occurrences.next(alarm, from));

        alarm.clampToMonthEnd = false;
        assertEquals(at(2027, Calendar.MARCH, 31, 5, 30), Occurrences.next(alarm, from));
    }

    @Test
    public void weeklyRuleHitsTheSelectedWeekdays() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_WEEKLY, 4, 15);
        alarm.setWeekDayList(Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY));

        // Starting midday on Tuesday 2 March 2027.
        List<Long> next = Occurrences.nextMany(alarm, at(2027, Calendar.MARCH, 2, 12, 0), 3);
        assertEquals(at(2027, Calendar.MARCH, 4, 4, 15), (long) next.get(0));
        assertEquals(at(2027, Calendar.MARCH, 8, 4, 15), (long) next.get(1));
        assertEquals(at(2027, Calendar.MARCH, 11, 4, 15), (long) next.get(2));
    }

    @Test
    public void dailyRollsOverOnceTheTimeHasPassed() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_DAILY, 3, 0);

        assertEquals(at(2027, Calendar.MARCH, 10, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 1, 0)));
        assertEquals(at(2027, Calendar.MARCH, 11, 3, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 4, 0)));
    }

    /** Picking today and a time still to come rings today. */
    @Test
    public void aOneOffLaterTodayRingsToday() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_ONCE, 9, 0);
        alarm.onceDateMillis = at(2027, Calendar.MARCH, 10, 0, 0);

        assertEquals(at(2027, Calendar.MARCH, 10, 9, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 7, 0)));
    }

    /**
     * Picking today and a time that has already gone by is not an error: as on any alarm clock,
     * it rings at that time tomorrow.
     */
    @Test
    public void aOneOffEarlierTodayRollsToTomorrow() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_ONCE, 9, 0);
        alarm.onceDateMillis = at(2027, Calendar.MARCH, 10, 0, 0);

        assertEquals(at(2027, Calendar.MARCH, 11, 9, 0),
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 11, 0)));
    }

    /** A date that is genuinely past has no firing, and does not quietly roll forward. */
    @Test
    public void aOneOffOnAnEarlierDateDoesNotRoll() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_ONCE, 9, 0);
        alarm.onceDateMillis = at(2027, Calendar.MARCH, 9, 0, 0);

        assertEquals(Occurrences.NONE,
                Occurrences.next(alarm, at(2027, Calendar.MARCH, 10, 11, 0)));
    }

    @Test
    public void aOneOffInThePastNeverFires() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_ONCE, 7, 0);
        alarm.onceDateMillis = at(2020, Calendar.JANUARY, 1, 0, 0);

        assertEquals(Occurrences.NONE, Occurrences.next(alarm, System.currentTimeMillis()));
    }

    @Test
    public void monthlyRuleWithNoDaysSelectedNeverFires() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_MONTHLY, 3, 0);
        alarm.monthDays = "";

        assertEquals(Occurrences.NONE, Occurrences.next(alarm, System.currentTimeMillis()));
    }

    @Test
    public void anOccasionAlarmWithoutAnOccasionNeverFires() {
        Alarm alarm = plainAlarm(Alarm.REPEAT_OCCASION, 3, 0);
        alarm.occasionId = null;

        assertEquals(Occurrences.NONE, Occurrences.next(alarm, System.currentTimeMillis()));
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

    @Test
    public void copyCarriesTheOccasionFields() {
        Alarm source = whiteDays();
        source.repeatMode = Alarm.REPEAT_OCCASION;
        source.occasionId = "arafah";
        source.skipForbiddenDays = true;

        Alarm copy = Alarm.copyOf(source);
        assertEquals(0L, copy.id);
        assertEquals("arafah", copy.occasionId);
        assertTrue(copy.skipForbiddenDays);
    }
}

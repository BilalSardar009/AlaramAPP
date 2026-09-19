package com.afzal.rozaalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.afzal.rozaalarm.util.Occasion;
import com.afzal.rozaalarm.util.Occasions;

import org.junit.Test;

import java.util.List;

/**
 * The fasting calendar rules. {@link Occasions#forHijriDay} takes the Hijri date as plain numbers,
 * so every rule below is checked without touching the ICU calendar.
 */
public class OccasionsTest {

    private static List<Occasion> on(int month, int day) {
        return Occasions.forHijriDay(month, day);
    }

    // ---- the named days -----------------------------------------------------------------------

    @Test
    public void arafahIsTheNinthOfDhulHijjah() {
        assertTrue(on(Occasions.DHUL_HIJJAH, 9).contains(Occasion.ARAFAH));
        assertFalse(on(Occasions.DHUL_HIJJAH, 8).contains(Occasion.ARAFAH));
        assertFalse(on(Occasions.DHUL_QIDAH, 9).contains(Occasion.ARAFAH));
    }

    @Test
    public void ashuraIsTheTenthOfMuharramAndTasuaThePreviousDay() {
        assertTrue(on(Occasions.MUHARRAM, 10).contains(Occasion.ASHURA));
        assertTrue(on(Occasions.MUHARRAM, 9).contains(Occasion.TASUA));
        assertFalse(on(Occasions.MUHARRAM, 10).contains(Occasion.TASUA));
    }

    @Test
    public void everyDayOfRamadanIsObligatoryAndNothingElse() {
        for (int day = 1; day <= 30; day++) {
            List<Occasion> occasions = on(Occasions.RAMADAN, day);
            assertEquals("Ramadan day " + day, 1, occasions.size());
            assertEquals(Occasion.RAMADAN, occasions.get(0));
            assertEquals(Occasion.Category.OBLIGATORY, occasions.get(0).category());
        }
    }

    @Test
    public void ramadanSupersedesTheWhiteDays() {
        List<Occasion> occasions = on(Occasions.RAMADAN, 14);
        assertEquals(1, occasions.size());
        assertEquals(Occasion.RAMADAN, occasions.get(0));
    }

    @Test
    public void whiteDaysAreThe13th14thAnd15thOfAnyMonth() {
        for (int month = 0; month <= 11; month++) {
            if (month == Occasions.RAMADAN || month == Occasions.DHUL_HIJJAH) {
                continue; // Covered separately: Ramadan supersedes, Dhul-Hijjah 13 is forbidden.
            }
            assertTrue("month " + month, on(month, 13).contains(Occasion.WHITE_DAYS));
            assertTrue("month " + month, on(month, 14).contains(Occasion.WHITE_DAYS));
            assertTrue("month " + month, on(month, 15).contains(Occasion.WHITE_DAYS));
            assertFalse("month " + month, on(month, 12).contains(Occasion.WHITE_DAYS));
            assertFalse("month " + month, on(month, 16).contains(Occasion.WHITE_DAYS));
        }
    }

    @Test
    public void sixOfShawwalRunsFromTheSecondToTheSeventh() {
        assertFalse(on(Occasions.SHAWWAL, 1).contains(Occasion.SHAWWAL_SIX));
        for (int day = 2; day <= 7; day++) {
            assertTrue("shawwal " + day, on(Occasions.SHAWWAL, day).contains(Occasion.SHAWWAL_SIX));
        }
        assertFalse(on(Occasions.SHAWWAL, 8).contains(Occasion.SHAWWAL_SIX));
    }

    @Test
    public void midShabanIsMarked() {
        assertTrue(on(Occasions.SHABAN, 15).contains(Occasion.SHABAN_MID));
        assertFalse(on(Occasions.RAJAB, 15).contains(Occasion.SHABAN_MID));
    }

    @Test
    public void theFirstNineOfDhulHijjahAreMarked() {
        for (int day = 1; day <= 9; day++) {
            assertTrue("dhul-hijjah " + day,
                    on(Occasions.DHUL_HIJJAH, day).contains(Occasion.DHUL_HIJJAH_FIRST_NINE));
        }
        assertFalse(on(Occasions.DHUL_HIJJAH, 10).contains(Occasion.DHUL_HIJJAH_FIRST_NINE));
    }

    // ---- days fasting is not permitted on -----------------------------------------------------

    @Test
    public void eidAlFitrIsTheFirstOfShawwalAndForbidden() {
        List<Occasion> occasions = on(Occasions.SHAWWAL, 1);
        assertEquals(1, occasions.size());
        assertEquals(Occasion.EID_AL_FITR, occasions.get(0));
        assertTrue(occasions.get(0).isForbidden());
    }

    @Test
    public void eidAlAdhaIsTheTenthOfDhulHijjahAndForbidden() {
        List<Occasion> occasions = on(Occasions.DHUL_HIJJAH, 10);
        assertEquals(1, occasions.size());
        assertEquals(Occasion.EID_AL_ADHA, occasions.get(0));
        assertTrue(occasions.get(0).isForbidden());
    }

    @Test
    public void tashreeqCoversThe11thTo13thOfDhulHijjah() {
        for (int day = 11; day <= 13; day++) {
            List<Occasion> occasions = on(Occasions.DHUL_HIJJAH, day);
            assertEquals("dhul-hijjah " + day, 1, occasions.size());
            assertEquals(Occasion.TASHREEQ, occasions.get(0));
        }
        assertFalse(on(Occasions.DHUL_HIJJAH, 14).contains(Occasion.TASHREEQ));
    }

    /** The accuracy point: 13 Dhul-Hijjah is a day of Tashreeq, so it is not offered as a fast. */
    @Test
    public void the13thOfDhulHijjahIsNotAWhiteDay() {
        List<Occasion> occasions = on(Occasions.DHUL_HIJJAH, 13);
        assertFalse(occasions.contains(Occasion.WHITE_DAYS));
        assertTrue(occasions.contains(Occasion.TASHREEQ));
    }

    @Test
    public void the14thAndOf15thDhulHijjahAreStillWhiteDays() {
        assertTrue(on(Occasions.DHUL_HIJJAH, 14).contains(Occasion.WHITE_DAYS));
        assertTrue(on(Occasions.DHUL_HIJJAH, 15).contains(Occasion.WHITE_DAYS));
    }

    @Test
    public void nothingVoluntaryIsListedAlongsideAForbiddenDay() {
        // 1 Shawwal is Eid, and only Eid.
        List<Occasion> occasions = on(Occasions.SHAWWAL, 1);
        assertEquals(1, occasions.size());
        assertEquals(Occasion.EID_AL_FITR, occasions.get(0));
    }

    // ---- ordering and identity ----------------------------------------------------------------

    @Test
    public void theMostSignificantOccasionComesFirst() {
        // 9 Dhul-Hijjah: Arafah leads, ahead of the first ten days of the month.
        List<Occasion> occasions = on(Occasions.DHUL_HIJJAH, 9);
        assertEquals(Occasion.ARAFAH, occasions.get(0));
        assertTrue(occasions.contains(Occasion.DHUL_HIJJAH_FIRST_NINE));
    }

    /** Retired: it marked about a third of the calendar and buried the days that matter. */
    @Test
    public void mondaysAndThursdaysAreNeverReported() {
        for (int month = 0; month <= 11; month++) {
            for (int day = 1; day <= 30; day++) {
                assertFalse("month " + month + " day " + day,
                        on(month, day).contains(Occasion.MONDAY_THURSDAY));
            }
        }
    }

    /** Muharram is the least specific label, so it never hides a named day. */
    @Test
    public void muharramDoesNotOutrankTheNamedDays() {
        assertEquals(Occasion.WHITE_DAYS, on(Occasions.MUHARRAM, 13).get(0));
        assertTrue(on(Occasions.MUHARRAM, 13).contains(Occasion.MUHARRAM));
        assertEquals(Occasion.ASHURA, on(Occasions.MUHARRAM, 10).get(0));
        assertEquals(Occasion.TASUA, on(Occasions.MUHARRAM, 9).get(0));
        // An ordinary day of Muharram is still marked.
        assertEquals(Occasion.MUHARRAM, on(Occasions.MUHARRAM, 5).get(0));
    }

    @Test
    public void ordinaryDaysHaveNoOccasion() {
        assertTrue(on(Occasions.SAFAR, 3).isEmpty());
    }

    @Test
    public void idsRoundTripAndAreUnique() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Occasion occasion : Occasion.values()) {
            assertTrue("duplicate id " + occasion.id(), ids.add(occasion.id()));
            assertEquals(occasion, Occasion.fromId(occasion.id()));
        }
        assertEquals(null, Occasion.fromId("not-an-occasion"));
        assertEquals(null, Occasion.fromId(null));
    }

    // ---- the 9-and-10 Muharram shortcut -------------------------------------------------------

    /**
     * The shortcut covers both days, but it is never drawn on the calendar: those two days are
     * already labelled Tasu'a and Ashura, and a third marker would say the same thing twice.
     */
    @Test
    public void muharram9And10IsCoveredButNeverLabelled() {
        assertTrue(Occasions.covers(Occasion.MUHARRAM_9_10, Occasions.MUHARRAM, 9));
        assertTrue(Occasions.covers(Occasion.MUHARRAM_9_10, Occasions.MUHARRAM, 10));
        assertFalse(Occasions.covers(Occasion.MUHARRAM_9_10, Occasions.MUHARRAM, 8));
        assertFalse(Occasions.covers(Occasion.MUHARRAM_9_10, Occasions.MUHARRAM, 11));
        assertFalse(Occasions.covers(Occasion.MUHARRAM_9_10, Occasions.SAFAR, 10));

        for (int month = 0; month <= 11; month++) {
            for (int day = 1; day <= 30; day++) {
                assertFalse("month " + month + " day " + day,
                        on(month, day).contains(Occasion.MUHARRAM_9_10));
            }
        }
    }

    @Test
    public void coversAgreesWithTheLabelsForEveryOtherOccasion() {
        for (Occasion occasion : Occasion.values()) {
            if (occasion == Occasion.MUHARRAM_9_10) {
                continue;
            }
            for (int month = 0; month <= 11; month++) {
                for (int day = 1; day <= 30; day++) {
                    assertEquals(occasion + " on " + month + "/" + day,
                            on(month, day).contains(occasion),
                            Occasions.covers(occasion, month, day));
                }
            }
        }
    }

    @Test
    public void theFastListLeadsWithRamadan() {
        List<Occasion> offered = com.afzal.rozaalarm.util.OccasionText.schedulable();

        assertEquals(Occasion.RAMADAN, offered.get(0));
        assertEquals(Occasion.MUHARRAM_9_10, offered.get(1));
        assertTrue(offered.contains(Occasion.WHITE_DAYS));
        assertFalse(offered.contains(Occasion.MONDAY_THURSDAY));
        assertEquals(new java.util.HashSet<>(offered).size(), offered.size());
        for (Occasion occasion : offered) {
            assertTrue(occasion.name(), occasion.isSchedulable());
        }
    }

    /**
     * No fast on the home screen may sit inside another one.
     *
     * <p>This is the rule the list is built on. Arafah is the ninth of Dhu al-Hijjah, Ashura is
     * the tenth of Muharram, 15 Sha'ban is a White Day: offering any of them next to the fast that
     * already contains it reads as two separate fasts and sets the same alarm twice. Adding one
     * back, or widening an existing fast until it swallows another, fails here.</p>
     */
    @Test
    public void theFastListNeverRepeatsItself() {
        List<Occasion> offered = com.afzal.rozaalarm.util.OccasionText.schedulable();

        for (Occasion inner : offered) {
            for (Occasion outer : offered) {
                if (inner == outer) {
                    continue;
                }
                boolean everyDayOfInnerIsAlsoOuter = true;
                boolean innerHasAnyDay = false;
                for (int month = 0; month <= 11; month++) {
                    for (int day = 1; day <= 30; day++) {
                        if (!Occasions.covers(inner, month, day)) {
                            continue;
                        }
                        innerHasAnyDay = true;
                        if (!Occasions.covers(outer, month, day)) {
                            everyDayOfInnerIsAlsoOuter = false;
                        }
                    }
                }
                assertFalse(inner + " sits entirely inside " + outer,
                        innerHasAnyDay && everyDayOfInnerIsAlsoOuter);
            }
        }
    }

    /** Every fast on the list has days of its own to offer. */
    @Test
    public void everyFastOnTheListFallsSomewhere() {
        for (Occasion occasion : com.afzal.rozaalarm.util.OccasionText.schedulable()) {
            boolean found = false;
            for (int month = 0; month <= 11 && !found; month++) {
                for (int day = 1; day <= 30; day++) {
                    if (Occasions.covers(occasion, month, day)) {
                        found = true;
                        break;
                    }
                }
            }
            assertTrue(occasion.name() + " never falls on any day", found);
        }
    }

    /** Whatever is dropped from the list is still drawn and named on the calendar. */
    @Test
    public void theFastsLeftOutOfTheListAreStillLabelled() {
        assertTrue(on(Occasions.DHUL_HIJJAH, 9).contains(Occasion.ARAFAH));
        assertTrue(on(Occasions.MUHARRAM, 10).contains(Occasion.ASHURA));
        assertTrue(on(Occasions.MUHARRAM, 9).contains(Occasion.TASUA));
        assertTrue(on(Occasions.SHABAN, 15).contains(Occasion.SHABAN_MID));
        assertTrue(on(Occasions.MUHARRAM, 5).contains(Occasion.MUHARRAM));
    }

    @Test
    public void forbiddenAndRetiredOccasionsAreNotSchedulable() {
        for (Occasion occasion : Occasion.values()) {
            boolean expected = !occasion.isForbidden() && occasion != Occasion.MONDAY_THURSDAY;
            assertEquals(occasion.name(), expected, occasion.isSchedulable());
        }
    }
}

<p align="center">
  <img src="assets/icon-512-round.png" width="120" alt="Afzal's Roza Alarm" />
</p>

<h1 align="center">Afzal's Roza Alarm</h1>

<p align="center">Never miss a fast — an Android alarm app built around the Islamic calendar.</p>

---

## What it does

A normal alarm clock can do "every Monday". It cannot do **"Ramadan, at 3:00 AM, every year"**, or
**"these five days I just tapped on the Islamic calendar"** — which is exactly what keeping the
fasts needs. The whole app is one calendar screen, and everything is done from it.

* **One Islamic calendar** — a whole Hijri month at a glance, picked from a dropdown of the twelve
  Islamic month names, with the English date in the corner of every cell and each fasting day
  marked and colour-coded by ruling.
* **Pick days, set one alarm** — tap as many days as you like, across as many months as you like,
  then set a single alarm that rings on every one of them.
* **Fasting-day shortcuts** — one tap sets the alarm for *Ramadan*, *9 & 10 Muharram*, *Ashura*,
  *Tasu'a*, the *Day of Arafah*, the *White Days*, the *six of Shawwal*, the *first nine of Dhu
  al-Hijjah*, *15 Sha'ban* or *Muharram*. The app works out the dates every year, forever. Tap the
  same shortcut again to move its time rather than stacking a second alarm on top of it.
* **Set today's date** — the calculated calendar and local moon sighting rarely agree. Tell the app
  what today's Islamic date really is on your own calendar and every other date moves with it. It
  ships set to what Pakistan goes by, which runs two days behind the Umm al-Qura calculation.
* **Days fasting is not permitted are never scheduled** — Eid al-Fitr, Eid al-Adha and the days of
  Tashreeq. This is why a White Days alarm rings on the 14th and 15th of Dhu al-Hijjah but not the
  13th, which is a day of Tashreeq.
* **Advance reminders** — a notification the evening before (1–7 days before, at any time) so
  Suhoor is planned, not rushed. A run like 13–15 gives **one** reminder on the 12th, not three.
* **History** — how many fasts you kept, month by month, in Islamic or English months. Tap a month
  to see the exact dates, or jump straight to it on the calendar. Totals, this year's count and
  your current streak sit on top.
* **Bilingual notifications** — every alarm and reminder is written in **English and Urdu**, and
  reminders name the occasion. The interface itself stays in simple English.
* **Full-screen alarm** — rings over the lock screen with a fade-in tone, vibration, snooze and
  dismiss.

## Screens

Two tabs, plus the alarm itself.

| Calendar | History | Ringing |
| --- | --- | --- |
| Today's date, the month dropdown, the grid, whatever you have selected, the fasting shortcuts and your alarms — the whole app on one screen | Fasts per month, expandable to the exact dates, with totals and streak | Breathing rings, bilingual message, snooze / dismiss |

The splash screen draws a procedural starfield with twinkling stars and shooting stars behind an
animated crescent — no image assets involved.

## How the scheduling works

There are two kinds of alarm and nothing else
([`Occurrences.java`](app/src/main/java/com/afzal/rozaalarm/util/Occurrences.java)):

* a **date alarm**, holding the `yyyyMMdd` days picked off the grid. Its firings are read straight
  out of that list, so a day years away costs nothing to find; and
* an **occasion alarm**, following a named fast. It is resolved by walking forward from now a day
  at a time and asking whether each candidate day belongs to the occasion, which keeps Hijri
  months, leap years and daylight-saving shifts in one place.

* Alarms are registered with `AlarmManager.setAlarmClock()`, so the system shows them in the status
  bar and exempts them from Doze.
* Firing, snoozing, rebooting, a time-zone change, an app update or a Hijri correction all
  re-register through
  [`AlarmScheduler`](app/src/main/java/com/afzal/rozaalarm/alarm/AlarmScheduler.java), so the
  database and `AlarmManager` never drift apart.
* The ringtone is owned by a foreground service, not the notification, so it keeps playing if the
  full-screen activity is swiped away and stops cleanly from the notification actions.
* A date alarm turns itself off once its last day has rung. The row stays, so you can see what it
  was and delete it yourself.

## Which fast falls on which day

[`Occasions.java`](app/src/main/java/com/afzal/rozaalarm/util/Occasions.java) answers that, and it
takes the Hijri date as plain numbers rather than an instant, so the rules are unit-tested without
a calendar in the way:

| Occasion | When | Ruling |
| --- | --- | --- |
| Ramadan | the whole month | obligatory |
| Day of Arafah | 9 Dhu al-Hijjah | highly recommended |
| Ashura | 10 Muharram | highly recommended |
| Tasu'a | 9 Muharram | voluntary |
| 9 &amp; 10 Muharram | 9 and 10 Muharram, as one shortcut | highly recommended |
| First nine of Dhu al-Hijjah | 1–9 Dhu al-Hijjah | voluntary |
| Muharram | the whole month | voluntary |
| 15 Sha'ban | 15 Sha'ban | voluntary |
| Six of Shawwal | any six days after Eid; 2–7 shown | voluntary |
| White Days | 13th, 14th, 15th of every month | voluntary |
| Eid al-Fitr | 1 Shawwal | **not permitted** |
| Eid al-Adha | 10 Dhu al-Hijjah | **not permitted** |
| Days of Tashreeq | 11–13 Dhu al-Hijjah | **not permitted** |

Ramadan supersedes every voluntary fast, and nothing voluntary is ever listed next to a day
fasting is not permitted on. *9 & 10 Muharram* is a shortcut rather than a label: those two days
are already drawn as Tasu'a and Ashura, so it never adds a third marker of its own.

## Look and feel

One light theme — paper, one green, one gold — so the marked days carry the screen instead of the
background. There is no alarm editor: an alarm is made by picking days or tapping a fasting day,
and changed by tapping it in the list. Animations are kept to the ones that help: the splash
crescent settling in, and a slow pulse behind a ringing alarm.

## Hijri dates

Conversion uses `android.icu.util.IslamicCalendar` with the **Umm al-Qura** calculation, the civil
calendar used across most of the Muslim world.

Calculated dates and local moon sighting do not always agree, so every conversion goes through a
resolved offset built from two corrections:

1. a **global correction** — *Set today's date*, on the calendar screen and in Settings: choose
   the day and month your own calendar shows and the app derives the offset itself (±5 days). It
   starts at **−2 days**, which is what Pakistan's moon sighting has been running behind Umm
   al-Qura; and
2. a **per-month correction** — the calendar's *Adjust this month*, for when a single month is
   announced early or late. The month header shows the adjustment while it is in effect.

Changing either re-registers every alarm, because a Hijri rule may now resolve to a different day.

The calendar is verified by invariants rather than memorised dates: the Hijri date advances exactly
one day per Gregorian day across twelve years, every month is 29–30 days and every year 354–355,
Hijri → Gregorian → Hijri round-trips for every day of 1445–1460 AH, and every Hijri year contains
exactly one Arafah, one Ashura, one of each Eid and three days of Tashreeq.

## Permissions

| Permission | Why |
| --- | --- |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Ring at the exact minute rather than in a system-chosen window |
| `POST_NOTIFICATIONS` | Show alarms and advance reminders (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Show the alarm over the lock screen |
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms after a reboot |
| `FOREGROUND_SERVICE` + `..._MEDIA_PLAYBACK` | Keep the ringtone playing while the alarm sounds |
| `VIBRATE`, `WAKE_LOCK` | Vibration pattern and keeping the CPU awake while ringing |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional prompt so alarms are not delayed while idle |

The app asks for these in context and Settings shows the live status of each, with a shortcut to
the relevant system screen.

## Building

Requires Android Studio (Koala or newer) or a local Android SDK with platform 34.

```bash
git clone https://github.com/BilalSardar009/AlaramAPP.git
cd AlaramAPP
./gradlew assembleDebug      # APK at app/build/outputs/apk/debug/
./gradlew test               # scheduling unit tests
```

| | |
| --- | --- |
| Language | Java |
| Theme | Light only, Material 3 |
| Min / target SDK | 24 / 34 |
| UI | Material 3, view binding, `RecyclerView`, custom canvas views |
| Storage | Room (schemas exported to `app/schemas`) |

## Project layout

```
app/src/main/java/com/afzal/rozaalarm/
├── alarm/      AlarmScheduler, receivers, the ringing foreground service
├── data/       Room entities (alarms, fast log, Hijri corrections), DAOs, repositories
├── ui/         Splash, the two tabs, Ringing, Settings + the animated views
└── util/       Occurrences (repeat rules), Occasions (the fasting calendar), HijriDates,
                Notifications, Prefs, TimeText
```

On first launch the app seeds one alarm — **Ayyam al-Beed Sehri**, 3:00 AM on the 13th, 14th and
15th of every *Islamic* month, with a reminder the evening before. Change or delete it like any
other alarm.

Upgrading from an earlier build keeps the fasting history and the Hijri corrections. Alarms on the
retired daily and weekly rules are removed; a one-off becomes a single-date alarm, and the old
"13, 14, 15 of every Hijri month" rule becomes the White Days shortcut.

## Licence

Released for personal use. Ramadan Mubarak. 🌙

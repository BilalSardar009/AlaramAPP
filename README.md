<p align="center">
  <img src="assets/icon-512-round.png" width="120" alt="Afzal's Roza Alarm" />
</p>

<h1 align="center">Afzal's Roza Alarm</h1>

<p align="center">Never miss a fast — an Android alarm app built around recurring fasting days.</p>

---

## What it does

A normal alarm clock can do "every Monday". It cannot do **"the 13th, 14th and 15th of every
Islamic month at 3:00 AM, and remind me the evening before"** — which is exactly what keeping the
voluntary fasts needs. This app is built around that rule, and around the calendar behind it.

* **Monthly day rules** — pick any days of the month and the alarm fires on each of them, every
  month, forever. Counted in the **Hijri** month (Umm al-Qura) or the English one.
* **Fasting-day rules** — set an alarm for *Ramadan*, the *Day of Arafah*, *Ashura*, *Tasu'a*, the
  *first nine of Dhu al-Hijjah*, the *six of Shawwal*, *15 Sha'ban*, *Muharram* or the *White
  Days*. The app works out the dates every year, forever. For Mondays and Thursdays use the plain
  Weekly repeat — it says the same thing without marking a third of the calendar.
* **Days fasting is not permitted are never scheduled** — Eid al-Fitr, Eid al-Adha and the days of
  Tashreeq. This is why a White Days rule rings on the 14th and 15th of Dhu al-Hijjah but not the
  13th, which is a day of Tashreeq.
* **Advance reminders** — a notification the evening before (1–7 days before, at any time) so
  Suhoor is planned, not rushed. A run like 13–15 gives **one** reminder on the 12th, not three.
* **A fasting calendar** — a whole Hijri month at a glance, each day showing its Hijri and English
  date and what fast falls on it. Tap any day to record a fast, read what the day is, or set an
  alarm for it.
* **History** — how many fasts you kept, month by month, in Islamic or English months. Tap a month
  to see the exact dates, or jump straight to it on the calendar. Totals, this year's count and
  your current streak sit on top.
* **Corrections when the calculation is wrong** — tell the app what today's Hijri date really is
  and it works out the adjustment itself, or shift a single month (Ramadan and Shawwal are the
  usual ones) when it is announced a day early or late. Either way every alarm is re-registered.
* **Bilingual notifications** — every alarm and reminder is written in **English and Urdu**, and
  reminders name the occasion. The interface itself stays in simple English.
* **Full-screen alarm** — rings over the lock screen with a fade-in tone, vibration, snooze and
  dismiss.

## Screens

Three tabs, plus the alarm itself.

| Alarms | Calendar | History | Ringing |
| --- | --- | --- | --- |
| Collapsing hero with today's Gregorian **and** Hijri date and a live countdown to the next alarm | A Hijri month per screen, every fasting day marked and colour-coded by ruling | Fasts per month, expandable to the exact dates, with totals and streak | Breathing rings, bilingual message, snooze / dismiss |

The splash screen draws a procedural starfield with twinkling stars and shooting stars behind an
animated crescent — no image assets involved.

## How the scheduling works

Every repeat rule is resolved by the same day-by-day walk forward from now
([`Occurrences.java`](app/src/main/java/com/afzal/rozaalarm/util/Occurrences.java)): for each
candidate day the alarm instant is built and the rule is asked whether that day counts. Keeping
one algorithm means Gregorian months, Hijri months, occasions, leap years and daylight-saving
shifts are all handled in one place.

* Alarms are registered with `AlarmManager.setAlarmClock()`, so the system shows them in the status
  bar and exempts them from Doze.
* Firing, snoozing, rebooting, a time-zone change, an app update or a Hijri correction all
  re-register through
  [`AlarmScheduler`](app/src/main/java/com/afzal/rozaalarm/alarm/AlarmScheduler.java), so the
  database and `AlarmManager` never drift apart.
* The ringtone is owned by a foreground service, not the notification, so it keeps playing if the
  full-screen activity is swiped away and stops cleanly from the notification actions.
* **Short months:** a rule containing the 31st fires on the last day of a 30- or 28-day month when
  "shift to the last day" is on, and skips that month when it is off.

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
| First nine of Dhu al-Hijjah | 1–9 Dhu al-Hijjah | voluntary |
| Muharram | the whole month | voluntary |
| 15 Sha'ban | 15 Sha'ban | voluntary |
| Six of Shawwal | any six days after Eid; 2–7 shown | voluntary |
| White Days | 13th, 14th, 15th of every month | voluntary |
| Eid al-Fitr | 1 Shawwal | **not permitted** |
| Eid al-Adha | 10 Dhu al-Hijjah | **not permitted** |
| Days of Tashreeq | 11–13 Dhu al-Hijjah | **not permitted** |

Ramadan supersedes every voluntary fast, and nothing voluntary is ever listed next to a day
fasting is not permitted on.

## Look and feel

One light theme — paper, one green, one gold — so the marked days carry the screen instead of the
background. The alarm editor shows only time, name, repeat and reminder by default; the settings
most people never touch sit behind **More options**. Animations are kept to the ones that help:
the splash crescent settling in, alarm cards easing up as the list loads, the extended button
collapsing as you scroll, and a slow pulse behind a ringing alarm.

## Hijri dates

Conversion uses `android.icu.util.IslamicCalendar` with the **Umm al-Qura** calculation, the civil
calendar used across most of the Muslim world.

Calculated dates and local moon sighting do not always agree, so every conversion goes through a
resolved offset built from two corrections:

1. a **global correction** — Settings → *Correct today's Hijri date*: pick the day your local
   calendar shows and the app derives the offset itself (±3 days); and
2. a **per-month correction** — the Calendar tab's *Adjust this month*, for when a single month is
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
├── ui/         Splash, the three tabs, Editor, Ringing, Settings + the animated views
└── util/       Occurrences (repeat rules), Occasions (the fasting calendar), HijriDates,
                Notifications, Prefs, TimeText
```

On first launch the app seeds one alarm — **Ayyam al-Beed Sehri**, 3:00 AM on the 13th, 14th and
15th of every *Islamic* month, with a reminder the evening before. Change or delete it like any
other alarm.

## Licence

Released for personal use. Ramadan Mubarak. 🌙

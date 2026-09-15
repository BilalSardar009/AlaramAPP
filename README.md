<p align="center">
  <img src="assets/icon-512-round.png" width="120" alt="Afzal's Roza Alarm" />
</p>

<h1 align="center">Afzal's Roza Alarm</h1>

<p align="center">Never miss a fast — an Android alarm app built around recurring fasting days.</p>

---

## What it does

A normal alarm clock can do "every Monday". It cannot do **"the 13th, 14th and 15th of every
month at 3:00 AM, and remind me the evening before"** — which is exactly what keeping the
voluntary fasts needs. This app is built around that rule.

* **Monthly day rules** — pick any days of the month (13, 14, 15 by default) and the alarm fires on
  each of them, every month, forever.
* **Gregorian *or* Hijri months** — the same 13/14/15 rule can be counted in the Islamic month
  (Umm al-Qura), so *Ayyam al-Beed* lands on the real white days rather than the English calendar.
* **Advance reminders** — a notification the evening before (configurable: 1–7 days before, at any
  time) so Suhoor is planned, not rushed. For a run like 13–15 you get **one** reminder on the 12th
  instead of three.
* **Every other repeat you'd expect** — once on a specific date, daily, or chosen weekdays
  (Mondays & Thursdays is a one-tap preset).
* **Bilingual notifications** — every alarm and reminder is written in **English and Urdu**. The app
  interface itself stays in simple English.
* **Full-screen alarm** — rings over the lock screen with a fade-in tone, vibration, snooze and
  dismiss.

## Screens

| Splash | Home | Editor | Ringing |
| --- | --- | --- | --- |
| Animated crescent, twinkling starfield and shooting stars | Collapsing hero with today's Gregorian **and** Hijri date plus a live countdown to the next alarm | Live preview of the next four firings before you save | Breathing rings, bilingual message, snooze / dismiss |

## How the scheduling works

Every repeat rule is resolved by the same day-by-day walk forward from now
([`Occurrences.java`](app/src/main/java/com/afzal/rozaalarm/util/Occurrences.java)): for each
candidate day the alarm instant is built and the rule is asked whether that day counts. Keeping
one algorithm means Gregorian months, Hijri months, leap years and daylight-saving shifts are all
handled in one place.

* Alarms are registered with `AlarmManager.setAlarmClock()`, so the system shows them in the status
  bar and exempts them from Doze.
* Firing, snoozing, rebooting, a time-zone change or an app update all re-register through
  [`AlarmScheduler`](app/src/main/java/com/afzal/rozaalarm/alarm/AlarmScheduler.java), so the
  database and `AlarmManager` never drift apart.
* The ringtone is owned by a foreground service, not the notification, so it keeps playing if the
  full-screen activity is swiped away and stops cleanly from the notification actions.
* **Short months:** a rule containing the 31st fires on the last day of a 30- or 28-day month when
  "shift to the last day" is on, and skips that month when it is off.

The rules are covered by unit tests in
[`OccurrencesTest.java`](app/src/test/java/com/afzal/rozaalarm/OccurrencesTest.java).

## Hijri dates

Conversion uses `android.icu.util.IslamicCalendar` with the **Umm al-Qura** calculation. Because
moon sighting differs by region, Settings has a **Hijri date adjustment** of ±2 days; changing it
re-registers every alarm so Hijri rules resolve to the corrected dates.

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
| Min / target SDK | 24 / 34 |
| UI | Material 3, view binding, `RecyclerView`, custom canvas views |
| Storage | Room |

## Project layout

```
app/src/main/java/com/afzal/rozaalarm/
├── alarm/      AlarmScheduler, receivers, the ringing foreground service
├── data/       Room entity, DAO, database, repository
├── ui/         Splash, Home, Editor, Ringing, Settings + the animated views
└── util/       Occurrences (repeat rules), HijriDates, Notifications, Prefs, TimeText
```

## Licence

Released for personal use. Ramadan Mubarak. 🌙

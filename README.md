# Payday Planner

An Android budget app built around **semi-monthly paydays** and **"to be paid" bills**.

## Features

- **Custom paydays.** Pick two days, e.g. the 10th and 25th. Each period runs from one payday to the day before the next. Days past the end of a month (like 30 in February) fall on its last day.
- **Budget per period.** Set a default income for each payday plus an optional spending cap, then override either one for a single period.
- **To Be Paid bills.** Add monthly or one-time bills, and they show up in the period they're due. Tick one off to record the payment as an expense (you can change the amount for bills that vary, like electricity).
- **Overdue tracking.** Unpaid bills from earlier periods stay visible until you pay them.
- **Safe to spend** = income − spent − bills still to pay.
- **Reminders.** A daily notification lists bills that are overdue or due within N days.
- Spending breakdown by category. Browse past and future periods.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · WorkManager. Min SDK 26 (Android 8.0).

## Build

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** this folder and let Gradle sync.
3. Run on a phone (enable USB debugging) or an emulator.

Command line (needs the Android SDK; set `sdk.dir` in `local.properties` or `ANDROID_HOME`):

```bash
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`.

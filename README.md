# Payday Planner

An Android budget app built around **semi-monthly paydays** and **"to be paid" bills**.

## Features

### Budget (per payday)
- **Custom paydays.** Pick two days, e.g. the 10th and 25th. Each period runs from one payday to the day before the next. Days past the end of a month (like 30 in February) fall on its last day.
- **Income and spending cap.** Set a default income for each payday plus an optional spending cap for everyday spending, then override either one for a single period.
- **Safe to spend** = income − spent − what's still left on bills.
- Spending breakdown by category. Browse past and future periods.

### Bills (by month)
- **Monthly or one-time bills** with due dates. The *By month* view shows each month's bills with total, paid and left to pay; *All bills* is where you add and edit them.
- **Partial payments.** Pay any amount toward a bill and the rest stays as remaining. Each bill keeps its payment history. *Mark as fully paid* covers bills that came out lower than usual, like electricity.
- **Needs to be paid.** Bills from earlier months that aren't fully paid stay flagged until cleared.
- **Loans and installments.** Set a number of payments ("payment 4 of 12"); the bill stops after the last one and shows as *Paid off*.
- **Sorting** by due date, amount left, name or unpaid first (remembered).
- **Reminders.** A daily notification lists bills that are overdue or due within N days.

Everything is stored on the phone. The app collects no data and needs no account.

## Install

Download the latest APK from [Releases](../../releases) on your phone and open it. Allow installing from that app if asked. If Play Protect warns about an unknown app, tap *More details → Install anyway*. Requires Android 8.0 or newer.

Updates install over the old version and keep your data.

## Tech

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · WorkManager. Min SDK 26 (Android 8.0), target SDK 35.

## Development

Open the folder in Android Studio or IntelliJ IDEA (with the Android plugin) and let Gradle sync. Use **JDK 21** as the Gradle JVM; Gradle 8.11 doesn't run on newer JDKs.

Command line (needs the Android SDK via `sdk.dir` in `local.properties` or `ANDROID_HOME`):

```bash
./gradlew assembleDebug        # dev build: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # signed release: app/build/outputs/apk/release/
```

Debug builds install as a separate app, **Payday Planner (Dev)**, so testing never touches the real app's data.

### Release signing

Release builds are signed with a key in `signing/`, which is gitignored:

```
signing/release.jks
signing/keystore.properties   # storeFile, storePassword, keyAlias, keyPassword
```

Without it, `assembleRelease` produces an unsigned APK. **Keep a backup of this folder.** Every update must be signed with the same key, or it won't install over the existing app.

### Releasing a new version

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease`
3. Tag the commit (e.g. `v1.2`) and create a GitHub release with the APK attached.

# Nothing Budget

A minimal home-screen widget for Nothing Phones (and any Android 8+ device) that reads your bank SMS and shows a live budget summary.

## What it does

- Watches incoming SMS for keywords:
  - `deducted` → expense
  - `transferred to` / `transferred` → income
- Extracts the amount and merchant/source from the message
- Stores everything locally on the phone (no network calls, no servers)
- Resizable home-screen widget showing remaining budget, totals, and a scrollable transaction list
- In-app **Auto / Light / Dark** toggle (Auto follows the phone's system mode — that's the default)

## How to install — no laptop or USB needed

The cleanest way without USB is to have GitHub build the APK in the cloud and download it directly on your phone.

### One-time setup

1. **Create a free GitHub account** at <https://github.com/signup>.
2. **Make a new repository.** Click `+` (top-right) → `New repository`. Name it anything (e.g. `nothing-budget`). Private is fine. Click **Create repository**.
3. **Upload the project.**
   - On the empty repo page, click **uploading an existing file** (it's a link inside the "quick setup" box).
   - On your computer, unzip `NothingBudget.zip`. Open the unzipped `NothingBudget` folder.
   - Select **everything inside that folder** (not the folder itself — the contents) and drag them into the GitHub upload area. Make sure the hidden `.github` folder comes along; on macOS press `Cmd+Shift+.` in Finder to show hidden folders before selecting.
   - Scroll down, click **Commit changes**.
4. **Wait for the build.** Click the **Actions** tab at the top of the repo. You'll see a "Build APK" run with a yellow circle (running). It takes ~3–5 minutes the first time. When it turns into a green checkmark, you're done.

### Every time you want a fresh APK on your phone

5. On your **phone's browser**, open `https://github.com/<your-username>/<repo-name>/actions`.
6. Tap the most recent successful run.
7. Scroll to the bottom — under **Artifacts** there's a file called `NothingBudget-APK`. Tap it to download. (You need to be signed into GitHub on the phone for the download to work.)
8. The download is a `.zip`. Open it (Files app → Downloads → tap the zip), and tap the `app-debug.apk` inside.
9. Android will say "For your security, your phone is not allowed to install unknown apps from this source." Tap **Settings**, enable **Allow from this source**, go back, and tap **Install**.
10. Done. Open **Budget** from your app drawer.

### After installing

1. Open the app, tap **Grant** to give SMS permission. The app does an initial scan of your inbox.
2. Enter your monthly budget and tap **Save**.
3. Choose your theme: **Auto** (follows phone), **Light**, or **Dark**.
4. Long-press your home screen → **Widgets** → find **Budget** → drag onto the home screen. Resize by long-pressing and dragging the corners.

## Theme behavior

- **App** uses whatever you pick: Auto, Light, or Dark.
- **Widget** always follows the phone's system mode, regardless of the in-app override. This is intentional — widgets visually belong to the home screen, so they should match whatever your phone is doing.
- The widget refreshes when the system flips between light and dark (e.g. on a schedule, or when you toggle it manually in Quick Settings).

## Customizing the keywords

Open `Settings.kt` and edit the defaults:

```kotlin
expenseKeywords = listOf("deducted", "debited", "purchase of")
incomeKeywords = listOf("transferred to", "transferred", "credited")
```

Push the change to GitHub, wait for the new build, download the new APK, install over the existing one.

## Notes

- All data stays on the device. No analytics, no internet permission requested.
- The widget refreshes every 30 minutes via Android's update period, plus immediately whenever a new matching SMS arrives.
- The list inside the widget shows transactions from the current calendar month and resets on the 1st.
- Currency is auto-detected from the SMS body (AED, USD, INR, etc.). Falls back to AED if nothing matches.
- Some banks send SMS containing "transferred" in confirmations of *outgoing* transfers — if you see income that should be an expense, narrow the income keyword to `"transferred to your"` or similar in `Settings.kt`.

## Project layout

```
NothingBudget/
├── .github/workflows/build.yml         # builds APK on every push
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nothing/budget/
│       │   ├── NothingBudgetApp.kt     # applies saved theme on launch
│       │   ├── MainActivity.kt
│       │   ├── BudgetWidget.kt
│       │   ├── TransactionListService.kt
│       │   ├── TransactionAdapter.kt
│       │   ├── SmsReceiver.kt
│       │   ├── InboxScanner.kt
│       │   ├── SmsParser.kt
│       │   ├── Storage.kt              # includes ThemeMode enum
│       │   ├── Settings.kt
│       │   └── Transaction.kt
│       └── res/
│           ├── layout/
│           ├── xml/widget_info.xml
│           ├── drawable/               # uses @color tokens
│           ├── mipmap-anydpi-v26/
│           ├── values/                 # light-mode colors + theme
│           └── values-night/           # dark-mode colors + bools
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

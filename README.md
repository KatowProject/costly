# Costly 💰

<div align="center">

### Offline-first personal finance for Android

[![Android](https://img.shields.io/badge/Android-13%2B%20(API%2033%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%20Design-3-7437DB?style=for-the-badge)](https://m3.material.io)
[![GitHub Release](https://img.shields.io/github/v/release/KatowProject/costly?style=for-the-badge)](https://github.com/KatowProject/costly/releases/latest)

<br/>

<img src="artifacts/costly-home.png" alt="Costly Application Home Screen" width="340" style="border-radius: 20px; box-shadow: 0 12px 36px rgba(116, 55, 219, 0.25);" />

<br/>
<br/>

Track your finances offline. Cloud backup and app update checks use an internet connection.

</div>

---

## Install & Update

Download the APK from [GitHub Releases](https://github.com/KatowProject/costly/releases/latest) and open it on a device running Android 13 or later.

In builds with the update checker, open **More → Check for updates** to compare your installed version with the latest GitHub release. The app also checks on launch when at least 12 hours have passed since the last recorded check. When a new version is available, you can open the APK download, snooze the reminder for 1, 3, or 7 days, or skip that version. Complete the installation in Android after downloading the APK.

## ✨ Features at a Glance

- 📊 The **dashboard** shows your balance across all accounts, monthly income and expenses, spending by category, budget progress, and recent transactions.
- 💳 **Wallets** track balances for cash, bank accounts, and e-wallets. Costly protects wallets linked to transactions from deletion.
- 🏷️ **Custom categories** group income and expenses and let you choose an icon for each category.
- 🎯 **Monthly budgets** show how much you have spent against each category limit and how far over budget you are.
- 🌱 **Savings goals** track contributions against a target amount and date.
- 🔁 **Recurring transactions** support daily, weekly, or monthly schedules. Costly processes due entries on launch and through WorkManager.
- 🤝 **Debts and receivables** track borrowed and lent money, repayments, remaining balances, and due dates.
- 📈 **Financial reports** show monthly summaries, spending by category, and cash flow. In **Reports → Category**, tap a category to see its expense transactions for the selected month. Export reports to **PDF**, **Excel (.xlsx)**, or **CSV** using your selected language and currency.
- 🔐 **App lock** supports a six-digit PIN and biometric unlock on supported devices. Choose whether to lock immediately when leaving the app or allow a grace period of 30 seconds, 1 minute, or 5 minutes.
- 📦 **Encrypted backups** support local files, Google Drive, and Microsoft OneDrive. Schedule backups and see when each destination last backed up successfully.
- 🎨 **Appearance settings** let you use a light or dark theme or follow your system setting. Screen transitions and charts are animated.
- 🌐 **Language and currency settings** include Bahasa Indonesia and English, and eight display currencies (IDR, USD, EUR, GBP, SGD, MYR, JPY, AUD).

---

## 🌐 Localization & Multi-Currency Architecture

Costly adheres to standard Android localization practices for UI strings, coupled with a flexible runtime model for currency formatting.

```
app/src/main/res/
├── values/strings.xml          # Default language (Bahasa Indonesia)
├── values-en/strings.xml       # English translations
├── xml/locales_config.xml      # Android 13+ Per-App Language configuration
```

### Standard XML Resource Strings
The default and English UI strings are defined in `res/values/strings.xml` and `res/values-en/strings.xml`. Compose screens access them through `LocalStrings.current`, backed by `AppStrings`.

### Android 13+ Per-App Language Preferences
With `minSdk 33` and `locales_config.xml`, users can change the language:
1. Directly inside the app via **Settings > Language**.
2. Or via Android System Settings: **Settings > System > Languages > App Languages > Costly**.

### How to Add a New Language

1. In Android Studio, right-click `res/values` → **New** → **Values Resource File**, name it `strings.xml`, and select the locale. You can also use the **Translations Editor**.
2. Add your new language tag to `app/src/main/res/xml/locales_config.xml`:
   ```xml
   <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
       <locale android:name="id" />
       <locale android:name="en" />
       <locale android:name="es" /> <!-- New language -->
   </locale-config>
   ```
3. Add the locale to `LocalizationManager` and update the `AppLanguage` mapping in [Localization.kt](app/src/main/java/com/katowproject/costly/Localization.kt) so in-app language selection and formatting can use it.

### How to Add a New Currency

Currencies are managed via `LocalizationManager.registerCurrency()` in [Localization.kt](app/src/main/java/com/katowproject/costly/Localization.kt):

```kotlin
val btc = AppCurrency(
    code = "BTC",
    symbol = "₿",
    name = "Bitcoin",
    symbolPrefix = true
)
LocalizationManager.registerCurrency(btc)
```

The newly registered currency automatically appears in **Settings > Currency** and dynamically updates all amount displays, summary cards, and PDF/Excel/CSV exports.

---

## ☁️ Backup & Restore

Costly stores financial records in SQLite on the device. A `.costly` backup contains a JSON export and transaction attachments, encrypted on the device with AES-256-GCM using a key derived from your backup password with PBKDF2-HMAC-SHA256.

- **Local backup**. Export a backup on demand, restore from a file, or choose a folder for scheduled backups.
- **Google Drive and Microsoft OneDrive**. Connect your account in **Settings** to upload a backup on demand, browse remote backups, or restore a selected file. OneDrive sign-in uses OAuth with PKCE and uploads through Microsoft Graph.
- **Scheduling and retention**. Choose a 1, 6, 12, or 24-hour interval and keep 3, 7, 14, or 30 backup versions. For scheduled OneDrive uploads, also enable automatic local or Google Drive backup.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: Jetpack Compose with Material 3 design system.
- **Language and SDK**. Kotlin 2.2.10, compile and target SDK 37, and minimum SDK 33 (Android 13).
- **Architecture**: MVVM with unidirectional data flow (UDF) and `AndroidViewModel`.
- **Database**: Offline-first SQLite database (`SQLiteOpenHelper`) with transactional queries, foreign key enforcement, and relational consistency.
- **Security**: AES-256-GCM encryption, PBKDF2-HMAC-SHA256 key derivation, Android BiometricPrompt API, and Android Keystore.
- **Background Tasks**: Android WorkManager for scheduled encrypted backups and recurring transaction processing.
- **Reporting & Documents**: Custom low-memory streaming OOXML (.xlsx) builder, Android native `PdfDocument` graphics canvas, and RFC 4180 CSV exporter with formula injection mitigation.

---

## 🚀 Getting Started

### Prerequisites

- Android 13+ emulator or physical device (API level 33+).
- JDK 17 or newer to launch the Gradle wrapper. The Gradle daemon uses Java 25 and can provision it when needed.
- Android SDK Platform 37.

### Running with VS Code

1. Open the project root directory in VS Code.
2. Open **Run and Debug** (`Ctrl+Shift+D`), select **Costly: Build & Run Android**, and press **F5** (or press `Ctrl+Shift+B` to run the default build task).
3. The build automation script will start the emulator (if not already running), compile the debug APK, install it, and launch Costly.

See the [Windows automation guide](scripts/README.md) for emulator options and SDK/JDK detection.

### Windows Automation Scripts

PowerShell / CMD:
```powershell
# Start the Android emulator
.\scripts\start-emulator.cmd

# Build, install, and run the app
.\scripts\build-and-run.cmd

# Stop the emulator when finished
.\scripts\stop-emulator.cmd
```

Git Bash:
```bash
bash scripts/start-emulator.sh
bash scripts/build-and-run.sh
```

### Manual Gradle Build

Set `JAVA_HOME` to your JDK directory. On Windows, run the wrapper from the project root.

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux, use `./gradlew :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release Builds & Versioning

Publishing a GitHub Release with a `vMAJOR.MINOR.PATCH` tag triggers the [release workflow](.github/workflows/release.yml). It runs the JVM unit tests, builds the APK, and attaches `Costly-<tag>.apk` to the release. A manual workflow run produces a workflow artifact.

Gradle strips the leading `v` for `versionName` and calculates `versionCode` as `major * 10000 + minor * 100 + patch`. For example, `v1.1.0` produces version name `1.1.0` and version code `10100`. A local build defaults to `1.0.0`. Override the version with `-PversionName=1.1.0` or `APP_VERSION_NAME`.

Use the same signing key as the previous APK for Android to install an update over an existing copy. The workflow reads the release key from the `KEYSTORE_BASE64` repository secret.

---

## 🧪 Verification & Testing

JVM unit tests cover balance and budget calculations, localization, CSV and Excel exports, update version comparison and GitHub fallback parsing, and category transaction filtering. Instrumented tests cover app startup, transaction flows, SQLite persistence, recurring transactions, PDF rendering, and encrypted backup and restore.

Run the JVM unit tests, then run the instrumented tests with an emulator or device connected.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

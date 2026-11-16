# Running Costly from VS Code

Android Studio does not need to be running. Existing installed SDKs, JDKs, and AVDs are reused directly.

To run using the Play button in VS Code:

1. Open the `cost-app` project root directory in VS Code.
2. Open the **Run and Debug** panel with `Ctrl+Shift+D`.
3. Select **Costly: Build & Run Android**, then click the **Play (triangle)** button or press **F5**.
4. The script builds the APK, starts or attaches to an active emulator, installs the APK, and launches Costly.

Additional configurations: **Costly: Run existing APK** runs the already-built APK without rebuilding, and **Costly: Start emulator** only opens the emulator window. `Ctrl+Shift+B` also executes the default build-and-run task. The Play button runs the Android automation workflow through the built-in VS Code terminal; setting Kotlin breakpoints on a device requires a dedicated Android debugger.

The package and application ID is `com.katowproject.costly`. Changing the application ID will cause Android to install it as a distinct application. To migrate data from an older installation, export an encrypted backup from the old app and restore it in the new one.

For Git Bash on Windows, use the `.sh` scripts:

```bash
# Start emulator window only
bash scripts/start-emulator.sh

# Install existing APK and launch Costly
bash scripts/run-app.sh

# Build latest APK, start emulator, install, and run Costly
bash scripts/build-and-run.sh
```

Every `.cmd` command below is also available as `.sh`: `build-apk.sh`, `list-emulators.sh`, `check-android.sh`, and `stop-emulator.sh`. All `.cmd` files remain available. The `.sh` version invokes the same `android.ps1` backend, ensuring consistent SDK/JDK detection and build execution; Windows PowerShell is leveraged automatically without switching terminals. The `.sh` scripts target Git Bash on Windows, not WSL, Linux, or macOS.

Git Bash options:

```bash
bash scripts/build-and-run.sh --avd Medium_Phone_API_37.0 --boot-timeout 300
bash scripts/start-emulator.sh --help

# If an older emulator is running headlessly without a window
bash scripts/stop-emulator.sh
bash scripts/start-emulator.sh
```

PowerShell arguments `-Avd` and `-BootTimeoutSeconds` are also accepted. Environment variable paths such as `ANDROID_HOME` and `COSTLY_JAVA_HOME` can use Git Bash `/c/...` format; the launcher converts them to standard Windows paths. Scripts can be executed from any directory with the appropriate relative path to the script. The `.sh` files use LF line endings, while `.cmd` files use CRLF.

From the VS Code terminal (PowerShell), run from the project root:

```powershell
# Start emulator window only
.\scripts\start-emulator.cmd

# Install existing APK and launch Costly; starts emulator automatically if needed
.\scripts\run-app.cmd

# After modifying code: build latest APK, start emulator, install, and launch Costly
.\scripts\build-and-run.cmd
```

Additional commands:

| File | Purpose |
| --- | --- |
| `build-apk.cmd` | Build debug APK without starting emulator |
| `list-emulators.cmd` | List available AVD names |
| `check-android.cmd` | Check SDK/JDK location, APK availability, and ADB devices |
| `stop-emulator.cmd` | Stop the selected emulator; physical devices are not targeted |

The `.cmd` files can also be double-clicked from File Explorer. To inspect outputs from `list-emulators.cmd` or `check-android.cmd`, run them from a terminal. In case of failure, the window pauses so error messages can be read. For headless automated runs, set `COSTLY_NO_PAUSE=1`.

The default emulator configured for this machine is `Medium_Phone_API_37.0`. To specify a different emulator:

```powershell
.\scripts\start-emulator.cmd -Avd EMULATOR_NAME
.\scripts\build-and-run.cmd -Avd EMULATOR_NAME
```

All installation commands target the specific emulator serial matching the AVD name. If the emulator is already running, the script reuses it. The script waits up to 180 seconds for Android to boot; use `-BootTimeoutSeconds 300` for slower machines.

If a previous test emulator is running headlessly (`-no-window`), run:

```powershell
.\scripts\stop-emulator.cmd
.\scripts\start-emulator.cmd
```

Save your work inside the emulator before shutting it down. Scripts do not wipe or reset emulator user data.

The Android SDK is discovered from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`, then standard `%LOCALAPPDATA%\Android\Sdk`. Android Studio's complete JDK is prioritized because some VS Code extension JREs lack `jlink`. Custom JDK paths can be set via `COSTLY_JAVA_HOME`; custom default emulators via `COSTLY_AVD`.

PowerShell's `ExecutionPolicy Bypass` only applies to the executed script process, without altering system-wide user policies. Builds download Gradle dependencies automatically when not present in the local cache.

Built APK location: `app\build\outputs\apk\debug\app-debug.apk`.

To verify native exit code handling in a new PowerShell session, run the following regression test. This test does not launch an emulator or install packages:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/native-exit-code.tests.ps1
```

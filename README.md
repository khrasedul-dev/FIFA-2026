# FIFA 2026 / T-Sports Android TV IPTV Player

A native Android TV application built in Kotlin utilizing **AndroidX Media3 ExoPlayer** to stream live IPTV content. Optimized for low-latency streaming and smooth D-pad controls.

## 📥 Direct Download

You can download the pre-compiled, optimized, and signed production APK directly from the repository:

👉 **[Download FIFA 2026 Release APK (v1.0 - 4.64 MB)](https://github.com/khrasedul-dev/FIFA-2026/raw/main/build_output/FIFA2026-release.apk)**

---

## 🚀 Key Features

* **Instant Auto-Play**: Automatically resolves and starts streaming your IPTV endpoint on app launch.
* **Low-Latency Live HLS Playback**:
  - Configured with a stable target buffer offset of 10 seconds.
  - Features an **auto-catchup mechanism**: if the player stalls or falls behind by more than **25 seconds** (due to network drops or long pauses), it automatically skips the delay and jumps straight to the live frame.
* **Pure Native Streaming (No WebViews)**:
  - Fetches the dynamic backend endpoint (`player.php`) asynchronously.
  - Extracts the direct `.m3u8` stream URL and necessary session cookie headers natively using regex, preventing memory leaks and rendering issues.
* **TV-Optimized Layout & Navigation**:
  - Pinned controls in the bottom-right corner for clean, unobtrusive viewing.
  - **Aspect Ratio Control**: Cycles between `FIT`, `FILL`, and `ZOOM` display modes.
  - **Developer Profile Panel**: Pressing the Info ("i") button toggles a glassmorphic info card.
  - **D-Pad Interactions**: Full focus support. Pressing the **Back** key on your TV remote dismisses the info panel if it's open, rather than closing the app.
* **Lightweight Build Optimization**: Fully configured with **R8/Proguard minification** and resource shrinking, compressing the final production APK size down to just **4.64 MB**!
* **Automated Signing & Keystore Generation**: The project automatically generates a secure keystore file on compilation if it's missing, signing your production release build seamlessly.

---

## 🛠️ Build & Compilation

### One-Click Build (Windows)
A pre-configured Windows batch script is included in the project root. To build the application:
1. Double-click the **`build_apk.bat`** file in the project folder.
2. The script will perform a clean compilation of both Debug and Production Release APKs.
3. Once finished, a folder named **`build_output`** will open automatically, containing:
   - `FIFA2026-debug.apk` (Signed with the debug key, ready to deploy to any TV or Emulator).
   - `FIFA2026-release.apk` (Signed production APK, minified and optimized).

---

## 🔒 Security & Git Configuration

The project is pre-configured with Git filters to keep files secure before pushing to GitHub:
* **Excluded Keystores (`app/.gitignore`)**: Excludes `*.jks` and `*.keystore` files to prevent your auto-generated release signing keys from leaking online.
* **Excluded Outputs (`.gitignore`)**: Excludes the compiled `build_output/` directory and `.gradle`/`.idea` directories from version control.

---

## 📂 Project Structure

```
FIFA2026/
├── app/
│   ├── src/main/java/com/bnxit/tsports/
│   │   ├── ui/MainActivity.kt          # Fullscreen TV layout & remote controller listener
│   │   └── player/ExoPlayerManager.kt  # Media3 stream manager with low-latency configuration
│   ├── src/main/res/
│   │   ├── layout/activity_main.xml    # TV layout overlay (Aspect Ratio + Info Buttons)
│   │   └── drawable/                   # Adaptive launcher icons & graphics
│   └── build.gradle                    # Minification rules and auto-key generation script
├── build_apk.bat                       # One-click Windows compilation script
└── README.md                           # Documentation
```

---

## 🔧 Prerequisites

* **Android Studio**: Ladybug / Koala or newer.
* **JDK**: Java 17/11 (configured in Android Studio).
* **Min SDK**: Android 7.0 (API 24+) to support Android TV devices.

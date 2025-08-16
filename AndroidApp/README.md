# AndroidApp Folder Guide

This folder contains the full Android Studio project for **QRhub**.

If you're exploring the codebase or want to contribute, this guide will help you quickly locate the essential files and understand the structure.

---

## Project Structure
```
AndroidApp/
├── build.gradle    # Top-level Gradle file
├── settings.gradle # Module settings
└── app/
  ├── build.gradle  # App-level Gradle configuration
  ├── src/main/
    ├── AndroidManifest.xml
    ├── java/com/ozzybozy/qrhub
    │ └── com.ozzybozy.qrhub/
    │   └── MainActivity.kt # Main body of code
    │   └── ScannerActivity.kt # Camera view code
    └── res/
      ├── layout/
      |  └── activity_main.xml
      |  └── qr_item.xml
      └── values/
         └── themes.xml
```

## Key Files

- [`MainActivity.kt`](AndroidApp/app/src/main/java/com/ozzybozy/qrhub/MainActivity.kt)  
  The core activity where all logic and UI interaction begins.
- [`ScannerActivity.kt`](AndroidApp/app/src/main/java/com/ozzybozy/qrhub/ScannerActivity.kt)  
  The activity housing the camera and qr scanner logic.

- [`activity_main.xml`](AndroidApp/app/src/main/res/layout/activity_main.xml)  
  Describes the user interface layout of the main screen.
- [`qr_item.xml`](AndroidApp/app/src/main/res/layout/qr_item.xml)  
  Defines the structure of the QR elements.

- [`build.gradle (:app)`](AndroidApp/app/build.gradle.kts)  
  App-level build configuration including dependencies and SDK settings.

---

## How to Open in Android Studio

1. Open Android Studio.
2. Click on **File → Open** and navigate to the `AndroidApp/` folder.
3. Let Gradle sync finish.
4. You can now explore, build, and run the app.

---

Feel free to explore and modify, but refer to the [main README](README.md) for licensing, contribution rules, and how to download APKs.

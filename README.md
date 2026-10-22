# Daily Devo

An offline-first Android starter app that presents a daily Bhagavad Gita verse, renders a typography-led wallpaper, and applies it manually or through deferrable daily background work.

## Build and run

Prerequisites: Android Studio with JDK 17 or later, Android SDK Platform 36, and Gradle 8.13 (the wrapper is included).

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat installDebug
```

Open this directory in Android Studio or launch the app on an emulator or Android device. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Current implementation

- Kotlin, Jetpack Compose and Material 3 user interface.
- MVVM-style screen state, a small selection domain use case, Room persistence and DataStore preferences.
- Bundled seed content is copied into a versioned local Room database on first launch.
- Daily selection is deterministic for the local calendar date and filters by enabled collection.
- Today, Library, Saved, Verse detail, Wallpaper preview and Settings screens.
- Canvas/StaticLayout wallpaper rendering with portrait dimensions from the device display, safe margins, long-text truncation with an ellipsis, and a light/dark paper palette.
- Manual wallpaper application to the home screen, lock screen or both through `WallpaperManager`; errors are shown in the app.
- Persistent periodic WorkManager scheduling plus a foreground recovery check each time the app resumes. Successful scheduled runs are transactionally claimed and idempotent per local date. Manual and scheduled update records use separate keys.
- Supported display preferences: English, Hindi, Gujarati and Sanskrit. The bundled Sanskrit source is shown when Sanskrit is selected; English, Hindi and Gujarati have editorial draft translations.
- Favorites and latest update status are kept locally.

## Scheduling and platform limits

WorkManager is deferrable work, not an exact alarm. The app requests a periodic morning-oriented run beginning around 6:00 local time, with a three-hour flex window, and checks again on app launch. Android power management, manufacturer policies, force-stop, reboot timing and other device state can delay work. The app records success only after `WallpaperManager.setBitmap` returns without throwing; an API return does not independently verify what a device launcher displays.

The application requests no runtime permissions and does not need the internet for core functionality. Its manifest declares `android.permission.SET_WALLPAPER`, which Android requires for wallpaper changes and does not show as a runtime permission prompt. `WallpaperManager` may behave differently across Android versions and manufacturers, especially for lock-only and both-screen changes.

## Content and attribution

The starter asset contains Bhagavad Gita 2.47 and 12.15 Sanskrit text with source references. References for cross-checking: [IIT Kanpur Gita Supersite, 2.47](https://www.gitasupersite.iitk.ac.in/srimad?choose=1&etsiva=1&field_chapter_value=2&field_nsutra_value=47&language=ro) and [IIT Kanpur Gita Supersite, 12.15](https://www.gitasupersite.iitk.ac.in/srimad?field_chapter_value=12&field_nsutra_value=15&language=dv).

English, Hindi and Gujarati renderings in the seed file are original editorial drafts prepared for this prototype, not copied translations or scholarly interpretations. They need review by fluent readers and religious-text editors before public distribution. The Sanskrit transcription should also be checked against the selected edition. This project does not claim licensing or scholarly verification for an external edition. No remote content or artwork is downloaded.

## Known limitations

- The library currently contains two Gita verses and one selectable collection; it is a working prototype rather than a production content catalog.
- Translation drafts are marked in code/data as editorial drafts and require review. Additional licensed content is not included.
- The app uses a display-dimension bitmap estimate; launchers may crop, scale, or position the system wallpaper differently.
- Device-specific wallpaper behavior, automatic scheduling through reboot/battery restrictions, accessibility at unusual dimensions, and Gujarati/Devanagari shaping need manual device verification.
- Room schema export/migrations should be expanded when future schema versions are added.
- Onboarding, history browsing, and a separate time-of-day preference are not included in this first implementation.

## Verification record

Executed in the development environment:

- `./gradlew.bat assembleDebug testDebugUnitTest` — passed; the debug APK and five unit tests completed successfully.
- Installed and launched on the `medium_phone` Android 16 emulator.
- Manually applied the generated wallpaper to the emulator home screen; Android displayed the verse wallpaper on its launcher.
- Visually checked the Gujarati and Devanagari script rendering in Settings and the Hindi verse card.

Not verified: a full morning-to-morning periodic WorkManager run, lock-screen and both-screen behavior, restart/battery restrictions, and physical-device manufacturer behavior. No personal device wallpaper was changed.

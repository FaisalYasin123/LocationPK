PK Location Tool - Android Studio project

BUILD
1. Install Android Studio (free): https://developer.android.com/studio
2. File > Open > select this PkLocationTool folder. Wait for Gradle sync (needs internet).
3. Build > Build Bundle(s) / APK(s) > Build APK(s)
   APK: app/build/outputs/apk/debug/app-debug.apk
4. For a proper release APK: Build > Generate Signed Bundle / APK > APK, create a keystore
   (keep it safe, you need it for every future update).

PHONE SETUP (each phone, once)
1. Settings > About phone > tap Build number 7 times
2. Settings > Developer options > Select mock location app > PK Location Tool
3. Open the app, allow location + notification permissions

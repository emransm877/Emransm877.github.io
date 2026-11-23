# Android prototype development

Use JDK 17, Android SDK versions selected in bluetooth-keyboard/app/build.gradle and the existing project tooling. Build with cd bluetooth-keyboard, then gradlew.bat assembleDebug --no-daemon on Windows or bash gradlew assembleDebug --no-daemon on Linux/macOS. Set ANDROID_HOME or a private local.properties sdk.dir. Run python tools/local_doctor.py for basic host checks; this branch stores its Android project in a subdirectory, so validate that subproject SDK/JDK manually too.

Automatic APK builds are replaced with manual fallbacks. Dispatch from the default-branch workflow and select this source branch. APK artifacts expire after seven days; no release is published. Pages on master remains independent. For rollback use git revert on the migration commit; restoring push triggers can run cloud builds.

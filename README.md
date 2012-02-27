Building
========
To build you will need:

 * A Java compiler compatible with Java 1.6
 * The Android SDK with platform 15 (Android 4.0.3) installed

Building from command-line
--------------------------
 * `android update project --path .` to generate local.properties
 * `ant debug` to build the APK at bin/VanillaMusic-debug.apk
 * Optional: `ant installd` to install the APK to a connected device

Building from Eclipse
---------------------
You can also build from Eclipse. Create a new Android Project, choosing "Create project from exisiting source", then set the compiler compliance level to 1.6 in project settings.

Documentation
=============
Javadocs can be generated using `ant doc`

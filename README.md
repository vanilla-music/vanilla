Vanilla Music
=====================
Note: This project is not available from the google playstore after some troll managed to convince google to remove the project from the playstore. You can still compile it yourself or install it using [F-Droid](https://f-droid.org). It is unlikely that Vanilla Music will ever re-appear in the playstore.

Vanilla Music player is an MP3/OGG/FLAC/PCM player for Android with the following features:
* multiple playlist support
* grouping by artist, album or genre
* plain filesystem browsing
* [ReplayGain](https://en.wikipedia.org/wiki/ReplayGain) support
* headset/Bluetooth controls
* accelerometer/shake control
* cover art support
* [ScrobbleDroid](https://code.google.com/p/scrobbledroid/) support for Last.fm integration

Translating
===========
[You can help translate here][1]. If your language isn't on the list, open an
issue and I can add it.

Building
========
To build you will need:

 * A Java compiler compatible with Java 1.6
 * The Android SDK with platform 16 (JellyBean) installed

Building from command-line
--------------------------
 * `android update project --path .` to generate local.properties
 * `ant debug` to build the APK at bin/VanillaMusic-debug.apk
 * Optional: `ant installd` to install the APK to a connected device

Building from Eclipse
---------------------
You can also build from Eclipse. Create a new Android Project, choosing "Create
project from exisiting source", then set the compiler compliance level to 1.6
in project settings.

Documentation
=============
Javadocs can be generated using `ant doc`


  [1]: https://www.transifex.com/projects/p/vanilla-music-1/


Vanilla Music is DEAD
=====================
This project is dead after some troll managed to convinced google to remove the project from the playstore. 
If you would like to pick up the project: feel free to fork and re-upload it to the store.
I will not continue working on Vanilla Music as i do not have the time to file at least one 'counter DMCA complain' per month & fight Googles absolute stupidity.

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


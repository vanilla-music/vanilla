Vanilla Music
=====================

Vanilla Music player is a [GPLv3](LICENSE) licensed MP3/OGG/FLAC/PCM player for Android with the following features:
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

 * A Java compiler compatible with Java 1.8
 * The Android SDK with platform 22 (Lollipop) installed

Building from command-line
--------------------------
 * `gradle build` to build the APK
 * Optional: `gradle installDebug` to install the APK to a connected device

Building with Android Studio
---------------------
You can also build with Android Studio by importing this project into it.

Documentation
=============
Javadocs can be generated using `gradle javadoc`


  [1]: https://www.transifex.com/projects/p/vanilla-music-1/

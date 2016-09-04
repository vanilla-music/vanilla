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

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/ch.blinkenlights.android.vanilla)

Donations
===========
You can donate to Vanilla Musics development via gratipay or Bitcoin

[![Support via Gratipay](https://cdn.rawgit.com/gratipay/gratipay-badge/2.3.0/dist/gratipay.png)](https://gratipay.com/vanilla-music/)

Bitcoin: [1JdA6ugjVXfQQcsgYZPRuAX1Tkii9Sfc3p](https://blockchain.info/address/1JdA6ugjVXfQQcsgYZPRuAX1Tkii9Sfc3p)


Community
===========
Come over and join us on our subreddit [**/r/VanillaMusic**](https://www.reddit.com/r/vanillamusic) to hangout with fellow Vanilla Music users, ask questions, or help others by answering their questions!

Contributing
===========

Translating
-----------
[You can help translate here][1]. If your language isn't on the list, open an
issue and I can add it.

Contributing code
---------------
* A list of open issues can be found at the [issue tracker][2]
* Features we would like to see (but nobody started working on them yet) have the [patches-welcome][3] label attached to them. Please let us know if you start working on such an open issue (to avoid duplicate work)
* We accept raw patches and github pull request - and we use tabs.

Building
========
To build you will need:

 * A Java compiler compatible with Java 1.7
 * The Android SDK with platform 24 (Nougat) installed

Building from command-line #1
--------------------------
 * `gradle build` to build the APK
 * Optional: `gradle installDebug` to install the APK to a connected device
 
Building from command-line #2
--------------------------
 * `android update project --path .` to generate local.properties
 * `ant debug` to build the APK at bin/VanillaMusic-debug.apk
 * Optional: `ant installd` to install the APK to a connected device

Building with Android Studio
---------------------
You can also build with Android Studio by importing this project into it.

Building from Eclipse
---------------------
You can also build from Eclipse. Create a new Android Project, choosing "Create
project from exisiting source", then set the compiler compliance level to 1.6
in project settings.

Nightly Builds
---------------------
Automatically created builds are available from http://android.eqmx.net/android/vanilla/

Documentation
=============
Javadocs can be generated using `gradle javadoc` or `ant doc`


  [1]: https://www.transifex.com/projects/p/vanilla-music-1/
  [2]: https://github.com/vanilla-music/vanilla/issues
  [3]: https://github.com/vanilla-music/vanilla/labels/patches-welcome

Vanilla Sol
=====================

A fork (May 11 2022) from Vanilla Music intended for personal use.

Changes/Differences
---------------
* Album Artist is considered the primary artist
  * If no Album Artist found, the first Artist tag will be considered the primary artist
  * Only one Album Artist is supported
* Support for multiple Artist tags
  * You will have the ability to get songs by a certain artist (like featuring artists)
* Support for iD3v2.4 year frame (TDRC)
  * I found cases where tags using this version was ignoring this frame and albums ended up with no year
* Get rid of BOM unicode character in tags
  * This was causing the underlying id generator to duplicate artists with (apparently) the same name
* Added processing on some custom tags for personal use
  * Language
  * Mood
  * Rating
  * Play Count
  * Add Date
  * Country
  * Artist Type
  * Artist Song Count
  * Artist Last Song Add Date
  * Artist Last Song Change Date
  * Artist Album Count
  * Album Song Count
  * Album Last Song Add Date
  * Album Last Song Change Date
  * Album Complete

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
* [Simple Last.fm Scrobbler](https://github.com/tgwizard/sls) support

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/ch.blinkenlights.android.vanilla)

Plugins
===========

Vanilla Music also includes support for plugins, this is a list of some existing plugins:
* [Cover fetcher](https://play.google.com/store/apps/details?id=com.kanedias.vanilla.coverfetch)
* [Lyrics search](https://play.google.com/store/apps/details?id=com.kanedias.vanilla.lyrics)
* [Tag editor](https://play.google.com/store/apps/details?id=com.kanedias.vanilla.audiotag)
* [Headphone detector](https://play.google.com/store/apps/details?id=ch.blinkenlights.android.vanillaplug)


Donations
===========
You can donate to Vanilla Musics development via Bitcoin

Bitcoin: [1adrianERDJusC4c8whyT81zAuiENEqub](https://blockchain.info/address/1adrianERDJusC4c8whyT81zAuiENEqub)


Community
===========
Come over and join us on our subreddit [**/r/VanillaMusic**](https://www.reddit.com/r/vanillamusic) to hangout with fellow Vanilla Music users, ask questions, or help others by answering their questions!

Contributing
===========

Translating
-----------
[You can help translate here][1]. If your language isn't on the list, sign in to transifex and request the language to be added to the list of translations.
(Feel free to open a bug if your request was not approved within a few days - i don't look into transifex that often.)

Contributing code
---------------
* A list of open issues can be found at the [issue tracker][2]
* Features we would like to see (but nobody started working on them yet) have the [patches-welcome][3] label attached to them. Please let us know if you start working on such an open issue (to avoid duplicate work)
* We accept raw patches and github pull request - and we use tabs (if your editor understands .editorconfig, it will help you enforce this).

Building
========
To build you will need:

 * A Java compiler compatible with Java 1.8
 * The Android SDK with platform 26 installed

Building from command-line
--------------------------
> Note: at the time of this writing, the current version of Gradle ([4.5.1](https://gradle.org/releases/)) is not compatible with the current version of JDK ([9.0.4](http://www.oracle.com/technetwork/java/javase/downloads/jdk9-downloads-3848520.html)). To have the build succeed, use JDK version [1.8.0_162](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).
 * `gradle build` to build the APK
 * Optional: `gradle installDebug` to install the APK to a connected device

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
Automatically created builds are available from http://android.eqmx.net/android/vanilla/VanillaMusic-nightly.apk

Documentation
=============
Javadocs can be generated using `gradle javadoc` or `ant doc`


  [1]: https://www.transifex.com/projects/p/vanilla-music-1/
  [2]: https://github.com/vanilla-music/vanilla/issues
  [3]: https://github.com/vanilla-music/vanilla/labels/patches-welcome

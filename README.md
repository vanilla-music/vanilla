Vanilla Music Player is a music player for Android

To build:

 * Ensure you have JDK 6 or greater installed and in the path. JDK 6 is required
for @Override annoations for interface methods.
 * Ensure you have the Android SDK installed and in your path with platform
android-11 or higher installed.
 * Execute `android update project --path .` You must provide a `--target`
parameter as well if you are not using platform 14.
 * Execute `ant debug` (or `ant release`)

Javadocs can be generated using `ant doc`

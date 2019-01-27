.PHONY: debug build release clean

default:

lint:
	ssh qemu '. .bash_aliases && cd /hostfs/vanilla && ./gradlew lintDebug'

debug: build
	adb install -r ./app/build/outputs/apk/debug/app-debug.apk

build:
	ssh qemu '. .bash_aliases && cd /hostfs/vanilla && ./gradlew build -x lintVitalRelease -x lint'

release: clean
	ssh qemu '. .bash_aliases && cd /hostfs/vanilla && ./gradlew assembleRelease && cd ./app/build/outputs/apk/release && zipalign -v -p 4 app-release-unsigned.apk aligned.apk && /home/adrian/Android/build-tools/27.0.3/apksigner sign -ks /hostfs/.android.keystore --out final.apk aligned.apk'

clean:
	rm -rf ./app/build

uninstall:
	adb uninstall ch.blinkenlights.android.vanilla

gce-nightly:
	./gradlew assembleRelease && cd ./app/build/outputs/apk/release && zipalign -v -p 4 app-release-unsigned.apk aligned.apk && echo aaaaaa | apksigner sign -ks ~/.android.keystore --out nightly-signed.apk aligned.apk

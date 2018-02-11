.PHONY: debug build release clean

default:

debug: build
	adb install -r ./app/build/outputs/apk/app-debug.apk

build:
	ssh qemu '. .bash_aliases && cd /hostfs/vanilla && ./gradlew build -x lintVitalRelease -x lint'

release: clean
	ssh qemu '. .bash_aliases && cd /hostfs/vanilla && ./gradlew assembleRelease && cd ./app/build/outputs/apk && zipalign -v -p 4 app-release-unsigned.apk aligned.apk && /home/adrian/Android/build-tools/26.0.1/apksigner sign -ks /hostfs/.android.keystore --out final.apk aligned.apk'

clean:
	rm -rf ./app/build/outputs

uninstall:
	adb uninstall ch.blinkenlights.android.vanilla

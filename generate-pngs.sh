#!/bin/sh

force=$1

gen() {
	name=`basename "$1" .svgz`
	png="res/drawable-$2/$name.png"
	if [[ "$1" -nt "$png" || $force ]]; then
		inkscape --without-gui --export-area-page --export-dpi=$3 --export-png="$png.out" $1
		pngcrush -q -brute "$png.out" "$png"
		rm "$png.out"
		echo
	fi
}

for i in orig/*.svgz; do
	gen "$i" mdpi 90
	gen "$i" hdpi 135
	gen "$i" xhdpi 180
done

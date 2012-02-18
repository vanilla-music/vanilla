#!/bin/sh

gen() {
	name=`basename "$1" .svgz`
	png="res/drawable-$2/$name.png"
	if [ "$1" -nt "$png" -o ! -e "$png" ]; then
		inkscape --without-gui --export-area-page --export-dpi=$3 --export-png="$png" $1
		convert -strip "$png" "$png"
		optipng -quiet -o7 "$png"
		echo
	fi
}

for i in orig/*.svgz; do
	gen "$i" mdpi 90
	gen "$i" hdpi 135
	gen "$i" xhdpi 180
done

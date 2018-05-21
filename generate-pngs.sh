#!/bin/sh

# This script can be used to generate PNGs from the SVGs stored in orig/.
# For each SVG in orig/, PNGs will be created in the drawable directories
# for each DPI with the same name as the SVG. If the PNGs already exist
# and are newer than the SVG, they will not be recreated.
#
# Requires:
# - inkscape
# - imagemagick
# - optipng

gen() {
	type=$1
	path=$2
	res=$3
	dpi=$4

	name=`basename "$path" .svgz`
	png="app/src/main/res/$type-$res/$name.png"
	if [ "$path" -nt "$png" -o ! -e "$png" ]; then
		inkscape --without-gui --export-area-page --export-dpi=$dpi --export-png="$png" $path
		echo
	fi
}

for i in orig/drawable/*.svgz; do
	gen drawable "$i" mdpi 96
	gen drawable "$i" hdpi 144
	gen drawable "$i" xhdpi 192
	gen drawable "$i" xxhdpi 288
done

for i in orig/mipmap/*.svgz; do
	gen mipmap "$i" mdpi 96
	gen mipmap "$i" hdpi 144
	gen mipmap "$i" xhdpi 192
	gen mipmap "$i" xxhdpi 288
done

# GOOG tells us to use xxx-hdpi only for launcher icons
for i in orig/mipmap/ic*.svgz ; do
	gen mipmap "$i" xxxhdpi 384
done

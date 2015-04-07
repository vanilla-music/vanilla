#!/usr/bin/python

# Fetch the translations from http://crowdin.net/project/vanilla-music and save
# them to res/ in the current directory. Removes files that contain no
# translations and removes incomplete plurals (they cause crashes).
#
# This script does not force crowdin to rebuild the translation package. That
# should be done through the website before running this script.
#
# Requires python-lxml.

try:
	# python 3
	from urllib.request import urlopen
except ImportError:
	# python 2
	from urllib2 import urlopen
from lxml import etree
from zipfile import ZipFile
import io
import os

data = urlopen('http://crowdin.net/download/project/vanilla-music.zip')
ar = ZipFile(io.BytesIO(data.read()))
parser = etree.XMLParser(remove_blank_text=True)

for name in ar.namelist():
	# ignore directories
	if not name.endswith('translatable.xml'):
		continue

	doc = etree.parse(ar.open(name), parser)
	# remove plurals without "other" quantity (they cause crashes)
	for e in doc.xpath("//plurals[not(item/@quantity='other')]"):
		e.getparent().remove(e)

	# ignore languages with no translations
	if len(doc.getroot()) == 0:
		continue

	# make some translations more general
	lang = name.split('/')[0]
	if lang == 'es-ES':
		lang = 'es'
	# The Android convention seems to be to put pt-PT in values-pt-rPT and
	# put pt-BR in values-pt. But since we have no pt-BR translation yet,
	# I'm just putting pt-PT in values-pt for now. Hopefully this doesn't
	# cause any issues.
	elif lang == 'pt-PT':
		lang = 'pt'

	# create dir if needed (assume res/ exists already)
	path = 'res/values-' + lang
	if not (os.path.isdir(path)):
		os.mkdir(path)

	# save result
	with io.open(path + "/translatable.xml", "w") as file:
		file.write(etree.tostring(doc, encoding='unicode', pretty_print=True, doctype='<?xml version="1.0" encoding="utf-8"?>'))

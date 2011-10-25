#!/bin/sh
git log --pretty="format:%an %ae" | sort | uniq -c | sort -n -r | sed -e 's/^ *[0-9]* //g'

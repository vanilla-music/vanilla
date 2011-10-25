#!/bin/sh
sdk=`grep sdk.dir= local.properties`
sdk=${sdk#sdk.dir=}
target=`grep target= project.properties`
target=${target#target=}
exec javadoc -classpath "$sdk/platforms/$target/android.jar" -d doc -sourcepath src -private org.kreed.vanilla

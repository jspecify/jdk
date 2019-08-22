#! /bin/bash

JAVAVERSION=11

rm -r jdk${JAVAVERSION}
mkdir jdk${JAVAVERSION}
rm jdk${JAVAVERSION}/Patch_Modules_argfile
touch jdk${JAVAVERSION}/Patch_Modules_argfile
for i in `find ./build/*/jdk/modules -maxdepth 1 -mindepth 1 -type d` ; do
  echo "Coverting `basename $i` to jar"
  jar --create --file=$i.annotated.jar --module-version=1.0 -C $i .
  echo "--patch-module `basename $i`=\${CURRENT_JDK_FOLDER}/`basename $i`.annotated.jar" >> jdk${JAVAVERSION}/Patch_Modules_argfile
  mv ./build/*/jdk/modules/`basename $i`.annotated.jar ./jdk${JAVAVERSION}/
done


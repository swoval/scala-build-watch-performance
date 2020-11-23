#!/bin/bash

n=$1
mkdir -p src/main/scala/blah
charsPerLine=70
lines=100
line=""
for y in `seq 1 $charsPerLine`; do
    line="${line}x"
done
line="//$line\n"
echo $line
for x in `seq 1 $n`;
do
z="package foo\n\nobject Blah$x\n"
for y in `seq 1 $lines`; do
    z+="$line"
done
echo $x

printf "$z" > src/main/scala/blah/Blah$x.scala

done

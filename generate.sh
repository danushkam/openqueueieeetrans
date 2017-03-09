#!/bin/bash

# Build code generation tools
rm src/*.class
javac src/*.java

# Generate modules
rm -rf policy
for i in $(ls *.oqp); do
    java -cp src/ OQGen ${i}
done

# Generate Makefile
echo "obj-m += qdisc/" > Makefile

for i in $(ls -d policy/*); do
    echo "obj-m += ${i}/" >> Makefile
done

echo  >> Makefile
echo "all:" >> Makefile
echo "	make -C /lib/modules/\$(shell uname -r)/build M=\$(PWD) modules" >> Makefile
echo  >> Makefile
echo "clean:" >> Makefile
echo "	make -C /lib/modules/\$(shell uname -r)/build M=\$(PWD) clean" >> Makefile

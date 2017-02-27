obj-m += sch_openqueue.o
sch_openqueue-objs := qdisc/sch_openqueue_base.o qdisc/gen/oq_init_port.o routine/routines.o

all:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD) modules

clean:
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD) clean

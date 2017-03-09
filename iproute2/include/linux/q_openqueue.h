#ifndef __LINUX_SCH_OPEN_QUEUE_H
#define __LINUX_SCH_OPEN_QUEUE_H

#define TCQ_OQ_NAME_LEN         32
#define TCQ_OQ_MAX_QUEUE        16

struct tc_oq_q {
        char name[TCQ_OQ_NAME_LEN + 1];
        int max_len;
        int len;
        int dropped;
        int total;
};

struct tc_oq_qopt {
        char            port_name[TCQ_OQ_NAME_LEN + 1];  /* OPEN_QUEUE port name */
        struct tc_oq_q  queues[TCQ_OQ_MAX_QUEUE];
        int             num_q;
};


#endif

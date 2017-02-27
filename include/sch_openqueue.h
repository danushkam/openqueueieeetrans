/*
 * sch_openqueue.h  OpenQueue type declaration.
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 */

#pragma once

#include <linux/btree.h>
#include "gen/oq_const.h"

#define TCQ_OQ_NAME_LEN		32
#define TCQ_OQ_MAX_QUEUE	16

/* TC options*/
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

/* Policy function */
struct Qdisc;
struct sk_buff;
struct oq_queue;

typedef int (*oq_qselc_func)(struct Qdisc *sch, struct sk_buff *skb);
typedef bool (*oq_cong_func)(struct oq_queue *queue);
typedef int (*oq_cong_act_func)(struct oq_queue *queue, struct sk_buff *skb);
typedef unsigned long (*oq_admn_func)(struct sk_buff *skb);
typedef unsigned long (*oq_proc_func)(struct sk_buff *skb);
typedef int (*oq_schd_func)(struct Qdisc *sch);

/* Queue structure */
struct oq_queue {
	struct btree_head admn_q;
	struct btree_head proc_q;
	int max_len;
	int len;
	int dropped;
	int total;
	char name[TCQ_OQ_NAME_LEN + 1];
	oq_cong_func cong_fn;
	oq_cong_act_func cong_act_fn;
	oq_admn_func admn_fn;
	oq_proc_func proc_fn;
};

/* Private data */
struct oq_priv {
	struct oq_queue queues[TCQ_OQ_NO_QUEUES];
	char port_name[TCQ_OQ_NAME_LEN + 1];
	oq_qselc_func q_select;
	oq_schd_func sched_fn;
};

/* Skb container */
struct skb_container {
        unsigned long other_key; /* Key on the other tree (admission/processing) */
        struct sk_buff *skb;
        struct skb_container *next;
        struct skb_container *prev;
};

/* Skb container list head */
struct skb_cont_list {
        struct skb_container *head;
};


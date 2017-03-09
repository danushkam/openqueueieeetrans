/*
 * sch_openqueue.h  OpenQueue type declaration.
 *
 *                  This program is free software; you can redistribute it and/or
 *                  modify it under the terms of the GNU General Public License
 *                  as published by the Free Software Foundation; either version
 *                  2 of the License, or (at your option) any later version.
 *
 * Authors:         Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

#pragma once

#include <linux/btree.h>

#define TCQ_OQ_NAME_LEN		32
#define TCQ_OQ_MAX_QUEUE	16

/* Congestion actions */
#define OQ_CON_ACT_DROP_HEAD    1
#define OQ_CON_ACT_DROP_TAIL    2
#define OQ_CON_ACT_DROP_PKT     3

/* TC options*/
struct tc_oq_q {
        char name[TCQ_OQ_NAME_LEN + 1];
        int max_len;
        int len;
        int dropped;
        int total;
};

struct tc_oq_qopt {
        char            port_name[TCQ_OQ_NAME_LEN + 1];
        struct tc_oq_q  queues[TCQ_OQ_MAX_QUEUE];
        int             num_q;
};

/* Policy function types */
struct Qdisc;
struct sk_buff;
struct oq_queue;
struct oq_priv;

typedef bool (*oq_cong_func)(struct oq_queue *queue);
typedef int (*oq_cong_act_func)(struct oq_queue *queue, struct sk_buff *skb);
typedef unsigned long (*oq_admn_func)(struct oq_queue *queue, struct sk_buff *skb);
typedef unsigned long (*oq_proc_func)(struct oq_queue *queue, struct sk_buff *skb);
typedef int (*oq_qselc_func)(struct Qdisc *sch, struct sk_buff *skb);
typedef int (*oq_schd_func)(struct Qdisc *sch);

typedef int (*oq_init_port_func)(struct oq_priv *priv);

/* Queue structure */
struct oq_queue {
    struct btree_head admn_q;
    struct btree_head proc_q;
    int max_len;
    int len;
    int dropped;
    int total;
    char name[TCQ_OQ_NAME_LEN + 1];
};

/* Private data */
struct oq_priv {
    struct oq_queue queues[TCQ_OQ_MAX_QUEUE];
    int num_q;
    char port_name[TCQ_OQ_NAME_LEN + 1];
    oq_cong_func cong_fn;
    oq_cong_act_func cong_act_fn;
    oq_admn_func admn_fn;
    oq_proc_func proc_fn;
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

/* Interfaces for register/unregister policies */
int oq_register_policy(const char *name, oq_init_port_func init_port_fn);
void oq_unregister_policy(oq_init_port_func init_port_fn);

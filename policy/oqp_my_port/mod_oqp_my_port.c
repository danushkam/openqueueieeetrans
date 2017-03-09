/*
 * mod_oqp_my_port.c    OpenQueue policy myPort
 *
 *                  This program is free software; you can redistribute it and/or
 *                  modify it under the terms of the GNU General Public License
 *                  as published by the Free Software Foundation; either version
 *                  2 of the License, or (at your option) any later version.
 *
 * Authors:         Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <net/pkt_sched.h>

#include "../../include/qdisc/sch_openqueue.h"
#include "../../include/routine/routines.h"

#define TCQ_OQ_NO_QUEUES	2

/* Congestion condition*/
bool oqp_my_port_cong_func(struct oq_queue *queue)
{
    bool cond = false;

    if (strncmp(queue->name, "q1", TCQ_OQ_NAME_LEN) == 0)
        cond = (queue->len == 1024);
    if (strncmp(queue->name, "q2", TCQ_OQ_NAME_LEN) == 0)
        cond = my_congestion_condition(queue, 0);

    return cond;
}

/* Congestion action */
int oqp_my_port_cong_act_func(struct oq_queue *queue, struct sk_buff *skb)
{
    int status = 0;

    if (strncmp(queue->name, "q1", TCQ_OQ_NAME_LEN) == 0)
        status = cong_act(queue, skb, 1, 0.85);
    if (strncmp(queue->name, "q2", TCQ_OQ_NAME_LEN) == 0)
        status = cong_act(queue, skb, 0);

    return status;
}

/* Admission priority */
unsigned long oqp_my_port_admn_func(struct oq_queue *queue, struct sk_buff *skb)
{
    unsigned long key = 0;

    if (strncmp(queue->name, "q1", TCQ_OQ_NAME_LEN) == 0)
        key = my_adm_prio(skb, 0);
    if (strncmp(queue->name, "q2", TCQ_OQ_NAME_LEN) == 0)
        key = my_adm_prio(skb, 0);

    return key;
}

/* Processing priority */
unsigned long oqp_my_port_proc_func(struct oq_queue *queue, struct sk_buff *skb)
{
    unsigned long key = 0;

    if (strncmp(queue->name, "q1", TCQ_OQ_NAME_LEN) == 0)
        key = my_pro_prio(skb, 0);
    if (strncmp(queue->name, "q2", TCQ_OQ_NAME_LEN) == 0)
        key = my_pro_prio(skb, 0);

    return key;
}

/* Queue selection priority */
int oqp_my_port_qselc_func(struct Qdisc *sch, struct sk_buff *skb)
{
    return select_admission_queue(sch, skb, 0);
}

/* Scheduling priority */
int oqp_my_port_schd_func(struct Qdisc *sch)
{
    return my_schd_prio(sch, 0);
}

/* Initialize queue */
int init_queue(struct oq_queue *queue, const char* name, int max_len)
{
	if ((btree_init(&queue->admn_q) != 0) || (btree_init(&queue->proc_q) != 0))
		return -1;

	queue->max_len = max_len;
	queue->len = 0;
	queue->dropped = 0;
	queue->total = 0;
	strncpy(queue->name, name, TCQ_OQ_NAME_LEN);

	return 0;
}

/* Initialize policy */
int oqp_my_port_init_port(struct oq_priv *priv)
{
	if (init_queue(&priv->queues[0], "q1", 128) != 0)
		return -ENOMEM;
	if (init_queue(&priv->queues[1], "q2", 1024) != 0)
		return -ENOMEM;

    priv->num_q = TCQ_OQ_NO_QUEUES;
    strncpy(priv->port_name, "myPort", TCQ_OQ_NAME_LEN);

    priv->cong_fn = oqp_my_port_cong_func;
    priv->cong_act_fn = oqp_my_port_cong_act_func;
    priv->admn_fn = oqp_my_port_admn_func;
    priv->proc_fn = oqp_my_port_proc_func;
    priv->q_select = oqp_my_port_qselc_func;
	priv->sched_fn = oqp_my_port_schd_func;

	return 0;
}

/* Initialize policy */
static int __init oqp_my_port_init(void)
{
	printk(KERN_INFO "Registered OpenQueue policy oqp_my_port\n");

	return oq_register_policy("myPort", oqp_my_port_init_port);
}

/* Exit policy */
static void __exit oqp_my_port_exit(void)
{
    printk(KERN_INFO "Unregistered OpenQueue policy oqp_my_port\n");
}

module_init(oqp_my_port_init);
module_exit(oqp_my_port_exit);
MODULE_LICENSE("GPL");

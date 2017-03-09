/*
 * sch_openqueue.c	OpenQueue core Qdisc implementation.
 *
 *              	This program is free software; you can redistribute it and/or
 *              	modify it under the terms of the GNU General Public License
 *              	as published by the Free Software Foundation; either version
 *              	2 of the License, or (at your option) any later version.
 *
 * Authors:     	Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

#include <linux/module.h>
#include <linux/slab.h>
#include <linux/types.h>
#include <linux/kernel.h>
#include <linux/errno.h>
#include <linux/skbuff.h>
#include <net/pkt_sched.h>
#include <linux/fs.h>
#include <linux/ip.h>

#include "../include/qdisc/sch_openqueue.h"
#include "../include/routine/routines.h"

/* Skb container */
struct kmem_cache *skb_container_cache;
mempool_t *skb_container_mempool;

void *skb_container_alloc(gfp_t gfp_mask, void *pool_data)
{
	return kmem_cache_alloc(skb_container_cache, gfp_mask);
}

void skb_container_free(void *element, void *pool_data)
{
	kmem_cache_free(skb_container_cache, element);
}

/* Skb container list head */
struct kmem_cache *skb_cont_list_cache;
mempool_t *skb_cont_list_mempool;

void *skb_cont_list_alloc(gfp_t gfp_mask, void *pool_data)
{
	return kmem_cache_alloc(skb_cont_list_cache, gfp_mask);
}

void skb_cont_list_free(void *element, void *pool_data)
{
	kmem_cache_free(skb_cont_list_cache, element);
}

/* Policy container */
struct oq_policy_container {
	char name[TCQ_OQ_NAME_LEN + 1];
	oq_init_port_func init_port_fn;
	struct oq_policy_container *next;
	struct oq_policy_container *prev;
};

/* Policy container list */
static DEFINE_RWLOCK(oq_policy_lock);
static struct oq_policy_container *oq_policy_base = NULL;

/* Enqueue a packet when the queue is not congested */
int do_enqueue(struct oq_priv *priv, struct oq_queue *queue, struct sk_buff *skb)
{
	unsigned long admn_key, proc_key;
	struct skb_cont_list *admn_cont_list, *proc_cont_list;
	struct skb_container *admn_container, *proc_container;

	admn_key = priv->admn_fn(queue, skb);
	proc_key = priv->proc_fn(queue, skb);

	/* Add to admission queue */
	admn_cont_list = (struct skb_cont_list *)btree_lookup(&queue->admn_q, &btree_geo64, &admn_key);
	if (NULL == admn_cont_list) {
		admn_container = (struct skb_container *)mempool_alloc(skb_container_mempool, GFP_KERNEL);
		admn_container->next = admn_container;
		admn_container->prev = admn_container;
		
		admn_cont_list = (struct skb_cont_list *)mempool_alloc(skb_cont_list_mempool, GFP_KERNEL);
		admn_cont_list->head = admn_container;
		
		btree_insert(&queue->admn_q, &btree_geo64, &admn_key, (void *)admn_cont_list, GFP_KERNEL);
	} else {
		struct skb_container *head_container, *tail_container;

		head_container = admn_cont_list->head;
		tail_container = head_container->prev;

		admn_container = (struct skb_container *)mempool_alloc(skb_container_mempool, GFP_KERNEL);
		admn_container->prev = tail_container;
		tail_container->next = admn_container;
		head_container->prev = admn_container;
		admn_container->next = head_container;
	}

	admn_container->other_key = proc_key;
	admn_container->skb = skb;

	/* Add to processing queue */
	proc_cont_list = (struct skb_cont_list *)btree_lookup(&queue->proc_q, &btree_geo64, &proc_key);
	if (NULL == proc_cont_list) {
		proc_container = (struct skb_container *)mempool_alloc(skb_container_mempool, GFP_KERNEL);
		proc_container->next = proc_container;
		proc_container->prev = proc_container;

		proc_cont_list = (struct skb_cont_list *)mempool_alloc(skb_cont_list_mempool, GFP_KERNEL);
		proc_cont_list->head = proc_container;

		btree_insert(&queue->proc_q, &btree_geo64, &proc_key, (void *)proc_cont_list, GFP_KERNEL);
	} else {
		struct skb_container *head_container, *tail_container;

		head_container = proc_cont_list->head;
		tail_container = head_container->prev;

		proc_container = (struct skb_container *)mempool_alloc(skb_container_mempool, GFP_KERNEL);
		proc_container->prev = tail_container;
		tail_container->next = proc_container;
		head_container->prev = proc_container;
		proc_container->next = head_container;
	}

	proc_container->other_key = admn_key;
	proc_container->skb = skb;

	queue->len++;
	queue->total++;
	
	return NET_XMIT_SUCCESS;
}

/* Drop packet at the tail of a given queue */
int do_drop_tail(struct oq_queue *queue)
{
	unsigned long admn_key;
	unsigned long proc_key;
	struct sk_buff *drop_skb;
	struct skb_cont_list *admn_cont_list, *proc_cont_list;
	struct skb_container *admn_container, *proc_container, *container;

	/* Drop oldest packet in the tail */
	/* Admission */
	admn_cont_list = (struct skb_cont_list *)btree_last(&queue->admn_q, &btree_geo64, &admn_key);
	if (NULL == admn_cont_list)
		return -EINVAL;

	admn_container = admn_cont_list->head; /* FIFO */

	proc_key = admn_container->other_key;
	drop_skb = admn_container->skb;

	if (admn_container->next == admn_container) { /* Last skb */
		btree_remove(&queue->admn_q, &btree_geo64, &admn_key);
		mempool_free(admn_cont_list, skb_cont_list_mempool);
	} else {
		struct skb_container *next_head, *tail;

		next_head = admn_container->next;
		tail = admn_container->prev;

		next_head->prev = tail;
		tail->next = next_head;

		admn_cont_list->head = next_head;
	}

	/* Processing */
	proc_cont_list = (struct skb_cont_list *)btree_lookup(&queue->proc_q, &btree_geo64, &proc_key);
	if (NULL == proc_cont_list) /* Not likely though */
		return -EINVAL;

	proc_container = NULL;
	container = proc_cont_list->head;
	do {
		if (container->skb == drop_skb) {
			proc_container = container;
			break;
		}

		container = container->next;
	} while (container != proc_cont_list->head);

	if (NULL == proc_container)
		return -EINVAL;

	if (proc_container->next == proc_container) { /* Last skb */
		btree_remove(&queue->proc_q, &btree_geo64, &proc_key);
		mempool_free(proc_cont_list, skb_cont_list_mempool);
	} else {
		proc_container->next->prev = proc_container->prev;
		proc_container->prev->next = proc_container->next;

		if (proc_container == proc_cont_list->head) /* Remove head */
			proc_cont_list->head = proc_container->next;
	}

	kfree_skb(drop_skb);
	mempool_free(admn_container, skb_container_mempool);
	mempool_free(proc_container, skb_container_mempool);

	queue->dropped++;

	return 0;
}

/* Enqueue an incoming packet */
static int oq_enqueue(struct sk_buff *skb, struct Qdisc *sch)
{
	struct oq_priv *priv;
	int q_id;
	struct oq_queue *queue;
	bool congested = false;
	int status = 0;

	priv = qdisc_priv(sch);

	/* Select queue */
	q_id = priv->q_select(sch, skb);
	queue = &priv->queues[q_id];

	congested = priv->cong_fn(queue);
	if (likely(!congested)) {
		status = do_enqueue(priv, queue, skb); /* Enqueue if not congested */
	} else {
		int action;
		action = priv->cong_act_fn(queue, skb); /* Resolve congestion action (when congested) */

		switch (action) {
			case OQ_CON_ACT_DROP_TAIL:
				do_drop_tail(queue); /* Drop tail */
				do_enqueue(priv, queue, skb); /* Enqueue new packet */
				break;
			case OQ_CON_ACT_DROP_PKT:
				kfree_skb(skb);
				queue->dropped++;
		}
	}

	return status;
}

/* Dequeue next eligible outgoing packet */
static struct sk_buff *oq_dequeue(struct Qdisc *sch)
{
	struct oq_priv *priv;
	struct oq_queue *queue;
	unsigned long proc_key;
	unsigned long admn_key;
	struct sk_buff *skb;
	struct skb_cont_list *proc_cont_list, *admn_cont_list;
	struct skb_container *proc_container, *admn_container, *container;
	int q_id;

	/* Pick queue */
	priv = qdisc_priv(sch);
	q_id = priv->sched_fn(sch);
	queue = &priv->queues[q_id];

	/* Dequeue the largest packet (Processing is based on pkt len for the time being) */
	/* Processing */
	proc_cont_list = (struct skb_cont_list *)btree_last(&queue->proc_q, &btree_geo64, &proc_key);
	if (NULL == proc_cont_list)
		return NULL;

	proc_container = proc_cont_list->head; /* FIFO */

	admn_key = proc_container->other_key;
	skb = proc_container->skb;

	if (proc_container->next == proc_container) { /* Last skb */
		btree_remove(&queue->proc_q, &btree_geo64, &proc_key);
		mempool_free(proc_cont_list, skb_cont_list_mempool);
	} else {
		struct skb_container *next_head, *tail;

		next_head = proc_container->next;
		tail = proc_container->prev;
		
		next_head->prev = tail;
		tail->next = next_head;

		proc_cont_list->head = next_head;
	}

	/* Admission */
	admn_cont_list = (struct skb_cont_list *)btree_lookup(&queue->admn_q, &btree_geo64, &admn_key);
	if (NULL == admn_cont_list) /* Not likely though */
		return NULL;

	admn_container = NULL;
	container = admn_cont_list->head;
	do {
		if (container->skb == skb) {
			admn_container = container;
			break;
		}
	
		container = container->next;
	} while (container != admn_cont_list->head);

	if (NULL == admn_container)
		return NULL;
	
	if (admn_container->next == admn_container) { /* Last skb */
		btree_remove(&queue->admn_q, &btree_geo64, &admn_key);
		mempool_free(admn_cont_list, skb_cont_list_mempool);
	} else {
		admn_container->next->prev = admn_container->prev;
		admn_container->prev->next = admn_container->next;

		if (admn_container == admn_cont_list->head) /* Remove head */
			admn_cont_list->head = admn_container->next;
	}

	mempool_free(proc_container, skb_container_mempool);
	mempool_free(admn_container, skb_container_mempool);

	queue->len--;
	
	return skb;
}

/* Initialize qdisc for the given policy */
int oq_init(struct Qdisc *sch, struct nlattr *opt)
{
	struct oq_priv *priv;
	oq_init_port_func init_port_fn;

	priv = qdisc_priv(sch);

	/* Initialize port */
	init_port_fn = NULL;

	if (opt != NULL) {
		struct tc_oq_qopt *ctl;
		struct oq_policy_container *container;

		ctl = nla_data(opt);
		if (nla_len(opt) < sizeof(*ctl))
			return -EINVAL;

		read_lock(&oq_policy_lock);

		container = oq_policy_base;
		do {
			if (strcmp(container->name, ctl->port_name) == 0) {
				init_port_fn = container->init_port_fn;
				break;
			}

			container = container->next;
		} while (container != oq_policy_base);

		read_unlock(&oq_policy_lock);
	}

	if ((init_port_fn == NULL) || (init_port_fn(priv) != 0))
		return -EINVAL;

	/* Initialize memory pools */
	skb_container_cache = kmem_cache_create("skb_container_cache", sizeof(struct skb_container), 0,
		SLAB_HWCACHE_ALIGN, NULL);
	skb_container_mempool = mempool_create(0, skb_container_alloc, skb_container_free, NULL);
	if (!skb_container_cache || !skb_container_mempool)
		return -ENOMEM;

	skb_cont_list_cache = kmem_cache_create("skb_cont_list_cache", sizeof(struct skb_cont_list), 0,
		SLAB_HWCACHE_ALIGN, NULL);
	skb_cont_list_mempool = mempool_create(0, skb_cont_list_alloc, skb_cont_list_free, NULL);
	if (!skb_cont_list_cache || !skb_cont_list_mempool)
		return -ENOMEM;

	return 0;
}

static int oq_dump(struct Qdisc *sch, struct sk_buff *skb)
{
	struct oq_priv *priv;
	struct tc_oq_qopt opt;
	int i;

	priv = qdisc_priv(sch);

	strncpy(opt.port_name, priv->port_name, TCQ_OQ_NAME_LEN);
	for (i = 0; i < priv->num_q; i++) {
		opt.queues[i].max_len = priv->queues[i].max_len;
		opt.queues[i].len = priv->queues[i].len;
		opt.queues[i].dropped = priv->queues[i].dropped;
		opt.queues[i].total = priv->queues[i].total;
		strncpy(opt.queues[i].name, priv->queues[i].name, TCQ_OQ_NAME_LEN);
	}
	opt.num_q = priv->num_q;

	if (nla_put(skb, TCA_OPTIONS, sizeof(opt), &opt))
		goto nla_put_failure;
	return skb->len;

nla_put_failure:
	return -1;
}

/* Register OpenQueue policy */
int oq_register_policy(const char *name, oq_init_port_func init_port_fn)
{
	struct oq_policy_container *container;

	container = (struct oq_policy_container *)kmalloc(sizeof(struct oq_policy_container), GFP_KERNEL);
	strncpy(container->name, name, TCQ_OQ_NAME_LEN);
	container->init_port_fn = init_port_fn;

	write_lock(&oq_policy_lock);

	if (NULL == oq_policy_base) { /* First policy */
		container->next = container;
		container->prev = container;

		oq_policy_base = container;
	} else { /* Append when queue is not empty */
		struct oq_policy_container *head_container, *tail_container;

		head_container = oq_policy_base;
		tail_container = head_container->prev;

		container->prev = tail_container;
		tail_container->next = container;
		head_container->prev = container;
		container->next = head_container;
	}

	write_unlock(&oq_policy_lock);

	return 0;
}
EXPORT_SYMBOL(oq_register_policy);

/* Unregister OpenQueue policy */
void oq_unregister_policy(oq_init_port_func init_port_fn)
{
	struct oq_policy_container *container;

	write_lock(&oq_policy_lock);

	container = oq_policy_base;
	do {
		if (container->init_port_fn == init_port_fn) {
			container->next->prev = container->prev;
			container->prev->next = container->next;

			if (container == oq_policy_base) /* Remove head */
				oq_policy_base = NULL;

			/* TODO: Invaildate policy in qdiscs */

			kfree(container);
			break;
		}

		container = container->next;
	} while (container != oq_policy_base);

	write_unlock(&oq_policy_lock);
}
EXPORT_SYMBOL(oq_unregister_policy);

/* OpenQueue ops */
struct Qdisc_ops oq_qdisc_ops __read_mostly = {
	.id			=	"openqueue",
	.priv_size	=	sizeof(struct oq_priv),
	.enqueue	=	oq_enqueue,
	.dequeue	=	oq_dequeue,
	.peek		=	qdisc_peek_head,
	.drop		=	qdisc_queue_drop,
	.init		=	oq_init,
	.reset		=	qdisc_reset_queue,
	.change		=	oq_init,
	.dump		=	oq_dump,
	.owner		=	THIS_MODULE,
};
EXPORT_SYMBOL(oq_qdisc_ops);

/* Register qdisc */
static int __init oq_module_init(void)
{
	return register_qdisc(&oq_qdisc_ops);
}

/* Unregister qdisc */
static void __exit oq_module_exit(void)
{
	unregister_qdisc(&oq_qdisc_ops);
}

module_init(oq_module_init)
module_exit(oq_module_exit)
MODULE_LICENSE("GPL");

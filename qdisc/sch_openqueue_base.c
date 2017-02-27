/*
 * sch_openqueue.c	OpenQueue language implementation.
 *
 *		This program is free software; you can redistribute it and/or
 *		modify it under the terms of the GNU General Public License
 *		as published by the Free Software Foundation; either version
 *		2 of the License, or (at your option) any later version.
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

#include "../include/sch_openqueue.h"
#include "../include/routine/routines.h"

extern int init_port(struct oq_priv *priv); 

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

int do_enqueue(struct oq_queue *queue, struct sk_buff *skb)
{
	struct iphdr *ip_hdr;
	unsigned long admn_key, proc_key;
	struct skb_cont_list *admn_cont_list, *proc_cont_list;
	struct skb_container *admn_container, *proc_container;

	ip_hdr = (struct iphdr *)skb_header_pointer(skb, 0, 0, NULL);
	if (NULL == ip_hdr)
		return -EINVAL;

	admn_key = queue->admn_fn(skb);
	proc_key = queue->proc_fn(skb);

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

static int oq_enqueue(struct sk_buff *skb, struct Qdisc *sch)
{
	struct oq_priv *priv;
	int q_id;
	struct oq_queue *queue;

	priv = qdisc_priv(sch);

	/* Select queue */
	q_id = priv->q_select(sch, skb);
	queue = &priv->queues[q_id];

	/* Enqueue if not congested */
	if (likely(queue->cong_fn(queue) == 0))
		return do_enqueue(queue, skb);
	else
		return queue->cong_act_fn(queue, skb);
}

static struct sk_buff *oq_dequeue(struct Qdisc *sch)
{
	struct oq_priv *priv;
	struct oq_queue *queue;
	unsigned long proc_key;
	unsigned long admn_key;
	struct sk_buff *skb;
	struct skb_cont_list *proc_cont_list, *admn_cont_list;
	struct skb_container *proc_container, *admn_container, *container;

	priv = qdisc_priv(sch);
	queue = &priv->queues[priv->sched_fn(sch)];

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

int init_queue(struct oq_queue *queue, const char* name, int max_len, 
	       oq_cong_func cong_fn, oq_cong_act_func cong_act_fn, 
	       oq_admn_func admn_fn, oq_proc_func proc_fn)
{
	if ((btree_init(&queue->admn_q) != 0) || (btree_init(&queue->proc_q) != 0))
		return -1;

	queue->max_len = max_len;
	queue->len = 0;
	strncpy(queue->name, name, TCQ_OQ_NAME_LEN);
	queue->cong_fn = cong_fn;
	queue->cong_act_fn = cong_act_fn;
	queue->admn_fn = admn_fn;
	queue->proc_fn = proc_fn;

	return 0;
}

int oq_init(struct Qdisc *sch, struct nlattr *opt)
{
        struct oq_priv *priv;

        priv = qdisc_priv(sch);

	/* Initialize port */
	if (init_port(priv) != 0)
		return -1;

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
	opt.num_q = TCQ_OQ_NO_QUEUES;
	for (i = 0; i < TCQ_OQ_NO_QUEUES; i++) {
			opt.queues[i].max_len = priv->queues[i].max_len;
			opt.queues[i].len = priv->queues[i].len;
			opt.queues[i].dropped = priv->queues[i].dropped;
			opt.queues[i].total = priv->queues[i].total;
			strncpy(opt.queues[i].name, priv->queues[i].name, TCQ_OQ_NAME_LEN);
	}


	if (nla_put(skb, TCA_OPTIONS, sizeof(opt), &opt))
		goto nla_put_failure;
	return skb->len;

nla_put_failure:
	return -1;
}

struct Qdisc_ops oq_qdisc_ops __read_mostly = {
	.id		=	"openqueue",
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

static int __init oq_module_init(void)
{
        return register_qdisc(&oq_qdisc_ops);
}

static void __exit oq_module_exit(void)
{
	unregister_qdisc(&oq_qdisc_ops);
}

module_init(oq_module_init)
module_exit(oq_module_exit)
MODULE_LICENSE("GPL");

#include <net/pkt_sched.h>
#include <linux/ip.h>
#include "../include/sch_openqueue.h"

extern mempool_t *skb_container_mempool;
extern mempool_t *skb_cont_list_mempool;

extern int do_enqueue(struct oq_queue *queue, struct sk_buff *skb);

int select_admission_queue(struct Qdisc *sch, struct sk_buff *skb)
{
	return 0;
}

bool my_congestion_condition(struct oq_queue *queue)
{
	return queue->len == 256;
}

unsigned long my_adm_prio(struct sk_buff *skb)
{
	struct iphdr *ip_hdr;

	ip_hdr = (struct iphdr *)skb_header_pointer(skb, 0, 0, NULL);
        if (NULL == ip_hdr)
                return 0;
	
	return ip_hdr->tos;
}

unsigned long my_pro_prio(struct sk_buff *skb)
{
	return skb->len;
}

int my_schd_prio(struct Qdisc *sch)
{
	return 0;
}

int drop_tail(struct oq_queue *queue, struct sk_buff *skb)
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
		if (container->skb == skb) {
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

	/* Enqueue new packet */
	return do_enqueue(queue, skb);
}

int reject_packet(struct oq_queue *queue, struct sk_buff *skb)
{
	kfree_skb(skb);

	queue->dropped++;

	return 0;
}

#include <net/pkt_sched.h>
#include <linux/ip.h>
#include "../include/qdisc/sch_openqueue.h"

bool my_congestion_condition(struct oq_queue *queue, int argc, ...)
{
	return queue->len == 256;
}

int cong_act(struct oq_queue *queue, struct sk_buff *skb, int argc, ...)
{
	return 2; /* Drop tail */
}

unsigned long my_adm_prio(struct sk_buff *skb, int argc, ...)
{
	struct iphdr *ip_hdr;

	ip_hdr = (struct iphdr *)skb_header_pointer(skb, 0, 0, NULL);
        if (NULL == ip_hdr)
                return 0;
	
	return ip_hdr->tos;
}

unsigned long my_pro_prio(struct sk_buff *skb, int argc, ...)
{
	return skb->len;
}

int select_admission_queue(struct Qdisc *sch, struct sk_buff *skb, int argc, ...)
{
	return 0;
}

int my_schd_prio(struct Qdisc *sch, int argc, ...)
{
	return 0;
}

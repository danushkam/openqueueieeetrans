#pragma once

// Queue selector during admission
int select_admission_queue(struct Qdisc *sch, struct sk_buff *skb);

// Congestion condition
bool my_congestion_condition(struct oq_queue *queue);

// Admission priority
unsigned long my_adm_prio(struct sk_buff *skb);

// Processing priority
unsigned long my_pro_prio(struct sk_buff *skb);

// Scheduling priority
int my_schd_prio(struct Qdisc *sch);

// Drop tail
int drop_tail(struct oq_queue *queue, struct sk_buff *skb);

// Reject packet
int reject_packet(struct oq_queue *queue, struct sk_buff *skb);
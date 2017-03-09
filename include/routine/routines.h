// Congestion condition
// @oq_cong_func
bool my_congestion_condition(struct oq_queue* queue, int argc, ...);

// Congestion action
// @oq_cong_act_func
int cong_act(struct oq_queue* queue, struct sk_buff* skb, int argc, ...);

// Admission priority
// @oq_admn_func
unsigned long my_adm_prio(struct sk_buff* skb, int argc, ...);

// Processing priority
// @oq_proc_func
unsigned long my_pro_prio(struct sk_buff* skb, int argc, ...);

// Queue selector during admission
// @oq_qsel_func
int select_admission_queue(struct Qdisc* sch, struct sk_buff* skb, int argc, ...);

// Scheduling priority
// @oq_schd_func
int my_schd_prio(struct Qdisc* sch, int argc, ...);
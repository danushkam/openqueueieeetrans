/*
 * oq_init_port.c  OpenQueue port initialization (Generated code).
 *
 *              This program is free software; you can redistribute it and/or
 *              modify it under the terms of the GNU General Public License
 *              as published by the Free Software Foundation; either version
 *              2 of the License, or (at your option) any later version.
 */

#include "../../include/sch_openqueue.h"
#include "../../include/routine/routines.h"

extern int init_queue(struct oq_queue *queue, const char* name, int max_len, 
               	     oq_cong_func cong_fn, oq_cong_act_func cong_act_fn, 
               	     oq_admn_func admn_fn, oq_proc_func proc_fn);

int init_port(struct oq_priv *priv)
{
	if (init_queue(&priv->queues[0], "q1", 128, 
		my_congestion_condition, drop_tail, my_adm_prio, my_pro_prio) != 0)
		return -ENOMEM;
	if (init_queue(&priv->queues[1], "q2", 1024, 
		my_congestion_condition, drop_tail, my_adm_prio, my_pro_prio) != 0)
		return -ENOMEM;

	priv->q_select = select_admission_queue;
	priv->sched_fn = my_schd_prio;
	strncpy(priv->port_name, "myPort", TCQ_OQ_NAME_LEN);

	return 0;
}

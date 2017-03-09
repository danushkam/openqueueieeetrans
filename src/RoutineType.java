/*
 * RoutineType      Defines all supported routine types in OpenQueue.
 *
 *                  This program is free software; you can redistribute it and/or
 *                  modify it under the terms of the GNU General Public License
 *                  as published by the Free Software Foundation; either version
 *                  2 of the License, or (at your option) any later version.
 *
 * Authors:         Danushka Menikkumbura, <dmenikku@purdue.edu>
 */

public enum RoutineType {
    UNDEFINED,
    CONGESTION_CONDITION,
    CONGESTION_ACTION,
    ADMISSION_PRIORITY,
    PROCESSING_PRIORITY,
    QUEUE_SELECTOR,
    SCHEDULING_PRIORITY
}

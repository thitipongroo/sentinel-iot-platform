package com.sentinel.iot.model;

/**
 * State lifecycle for alerts:
 * <ul>
 *   <li><b>FIRING:</b> Condition threshold exceeded, active alert.</li>
 *   <li><b>ACKNOWLEDGED:</b> Operator acknowledged receipt and is investigating.</li>
 *   <li><b>RESOLVED:</b> Problem mitigated or condition returned to normal range.</li>
 *   <li><b>SILENCED:</b> Temporarily suppressed during maintenance or flapping.</li>
 * </ul>
 */
public enum AlertState {
    FIRING,
    ACKNOWLEDGED,
    RESOLVED,
    SILENCED
}

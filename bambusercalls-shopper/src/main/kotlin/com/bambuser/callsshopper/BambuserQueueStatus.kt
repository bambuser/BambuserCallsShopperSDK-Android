package com.bambuser.callsshopper

/**
 * Strongly-typed convenience payloads for the queue-status events
 * and async queue-query methods. Every struct also carries a `raw`
 * field so hosts can read fields the SDK doesn't surface — including
 * anything Bambuser adds later.
 *
 * See https://bambuser.com/docs/video-consultation/queue-status/
 */

/**
 * Queue open/closed snapshot delivered by `queue-is-open` and
 * `queue-is-closed`, and returned by `areQueuesOpen()`.
 */
data class BambuserQueueOpenState(
    val isOpen: Boolean,
    /** ISO-8601 timestamp of the next scheduled open. */
    val nextOpenTime: String? = null,
    /** ISO-8601 timestamp of the next scheduled close. */
    val nextCloseTime: String? = null,
    /** Full unmodified JSON payload from the embed. */
    val raw: BambuserJSONValue = BambuserJSONValue.Null,
)

/**
 * Payload of `agents-online` and the `getNumberOfOnlineAgents()`
 * reply.
 */
data class BambuserAgentsOnline(
    val numberOfAgentsOnline: Int,
    val queueId: String? = null,
    val raw: BambuserJSONValue = BambuserJSONValue.Null,
)

/**
 * Payload of `queue-estimated-waiting-time` and
 * `getEstimatedWaitingTime()`.
 */
data class BambuserQueueWaitingTime(
    /** Estimated seconds a new shopper would wait before being connected. */
    val estimatedWaitingTime: Int,
    /** Total online agents currently serving the queue. */
    val agents: Int,
    /**
     * Place this shopper would take after joining the queue
     * (`current line length + 1`).
     */
    val place: Int,
    val queueId: String? = null,
    val raw: BambuserJSONValue = BambuserJSONValue.Null,
)

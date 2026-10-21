package app.pukaar.sos

/**
 * How many Bluetooth links each public mesh message travelled to reach this phone (a direct
 * neighbour is 1), by bitchat message id. Recorded by bitchat's MessageHandler (one-line Pukaar
 * hook) and sent with mesh SOS uploads, so the dashboard can show "Mesh · 3 hops".
 *
 * Depends on relays decrementing the TTL and senders starting at MESSAGE_TTL_HOPS; check with
 * three real phones in a line before trusting the number.
 */
object MeshHops {
    private const val MAX = 2_000
    private val hops = object : LinkedHashMap<String, Int>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > MAX
    }

    fun record(messageId: String, count: Int) {
        if (count !in 0..50) return
        synchronized(hops) { hops[messageId] = count }
    }

    fun get(messageId: String): Int? = synchronized(hops) { hops[messageId] }
}

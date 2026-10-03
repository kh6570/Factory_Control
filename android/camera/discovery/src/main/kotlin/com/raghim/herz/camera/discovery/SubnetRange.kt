// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

/**
 * IPv4 hosts to port-scan around the phone's own address. Networks wider than /24 are
 * narrowed to the /24 that contains [self], so a scan never exceeds 254 hosts.
 */
class SubnetRange private constructor(
    private val network: Int,
    private val prefixLength: Int,
    private val self: Int,
) {
    /** Usable host addresses in ascending order, without network, broadcast and [self]. */
    val hosts: List<String>
        get() {
            val size = 1 shl (32 - prefixLength)
            return (1 until size - 1)
                .map { network + it }
                .filter { it != self }
                .map(::formatIpv4)
        }

    override fun toString(): String = "${formatIpv4(network)}/$prefixLength"

    companion object {
        private const val MAX_SCAN_PREFIX = 24
        private const val MIN_HOSTS_PREFIX = 30

        /** Null when [address] is not IPv4 or the prefix leaves no other host to scan. */
        fun around(address: String, prefixLength: Int): SubnetRange? {
            val ip = parseIpv4(address) ?: return null
            if (prefixLength !in 0..MIN_HOSTS_PREFIX) return null
            val prefix = maxOf(prefixLength, MAX_SCAN_PREFIX)
            val mask = -1 shl (32 - prefix)
            return SubnetRange(network = ip and mask, prefixLength = prefix, self = ip)
        }

        fun parseIpv4(address: String): Int? {
            val parts = address.split('.')
            if (parts.size != 4) return null
            var value = 0
            for (part in parts) {
                if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
                val octet = part.toInt()
                if (octet > 255) return null
                value = (value shl 8) or octet
            }
            return value
        }

        fun formatIpv4(value: Int): String =
            (3 downTo 0).joinToString(".") { ((value ushr (it * 8)) and 0xFF).toString() }
    }
}

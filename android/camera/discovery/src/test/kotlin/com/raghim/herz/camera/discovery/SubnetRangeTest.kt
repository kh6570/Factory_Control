// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SubnetRangeTest {

    @Test
    fun `slash 24 lists every host except network, broadcast and self`() {
        val hosts = SubnetRange.around("192.168.1.37", 24)!!.hosts

        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertFalse("192.168.1.37" in hosts)
    }

    @Test
    fun `wider networks are narrowed to the phone's slash 24`() {
        val range = SubnetRange.around("10.20.5.9", 16)!!

        assertEquals("10.20.5.0/24", range.toString())
        assertEquals(253, range.hosts.size)
        assertEquals("10.20.5.1", range.hosts.first())
        assertEquals("10.20.5.254", range.hosts.last())
    }

    @Test
    fun `narrower networks keep their own size`() {
        val range = SubnetRange.around("192.168.1.70", 26)!!

        assertEquals("192.168.1.64/26", range.toString())
        assertEquals(61, range.hosts.size)
        assertEquals("192.168.1.65", range.hosts.first())
        assertEquals("192.168.1.126", range.hosts.last())
    }

    @Test
    fun `high addresses do not overflow`() {
        val hosts = SubnetRange.around("250.1.2.3", 24)!!.hosts

        assertEquals("250.1.2.1", hosts.first())
        assertEquals("250.1.2.254", hosts.last())
    }

    @Test
    fun `invalid input or tiny prefixes give no range`() {
        assertNull(SubnetRange.around("192.168.1", 24))
        assertNull(SubnetRange.around("192.168.1.300", 24))
        assertNull(SubnetRange.around("fe80::1", 64))
        assertNull(SubnetRange.around("192.168.1.5", 31))
        assertNull(SubnetRange.around("192.168.1.5", 32))
        assertNull(SubnetRange.around("192.168.1.5", -1))
    }

    @Test
    fun `parse and format round trip`() {
        val value = SubnetRange.parseIpv4("172.16.254.3")!!

        assertEquals("172.16.254.3", SubnetRange.formatIpv4(value))
        assertNull(SubnetRange.parseIpv4("1.2.3.4.5"))
        assertNull(SubnetRange.parseIpv4("a.b.c.d"))
    }
}

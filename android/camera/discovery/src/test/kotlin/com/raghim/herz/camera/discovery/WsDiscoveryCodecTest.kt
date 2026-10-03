// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class WsDiscoveryCodecTest {

    @Test
    fun `probe is a SOAP 1_2 WS-Discovery probe for video transmitters`() {
        val xml = WsDiscoveryCodec.probe(messageId = "uuid:1234")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        assertEquals("http://www.w3.org/2003/05/soap-envelope", document.documentElement.namespaceURI)
        assertEquals("Envelope", document.documentElement.localName)
        assertEquals("uuid:1234", document.text(WSA, "MessageID"))
        assertEquals("http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe", document.text(WSA, "Action"))
        val types = document.getElementsByTagNameNS(WSD, "Types").item(0) as Element
        assertEquals("dn:NetworkVideoTransmitter", types.textContent.trim())
        assertEquals("http://www.onvif.org/ver10/network/wsdl", types.lookupNamespaceURI("dn"))
    }

    @Test
    fun `probe uses a random message id by default`() {
        assertTrue(WsDiscoveryCodec.probe() != WsDiscoveryCodec.probe())
    }

    @Test
    fun `parses Hikvision probe match`() {
        val devices = WsDiscoveryCodec.parseProbeMatches(HIKVISION, sourceHost = "192.168.1.64")

        assertEquals(
            listOf(
                DiscoveredDevice(
                    host = "192.168.1.64",
                    method = DiscoveryMethod.ONVIF,
                    name = "HIKVISION DS-2CD2143G0-I",
                    manufacturer = null,
                    model = "DS-2CD2143G0-I",
                    onvifServiceUri = "http://192.168.1.64/onvif/device_service",
                ),
            ),
            devices,
        )
    }

    @Test
    fun `parses Dahua probe match with 2009 namespace and mfr scope`() {
        val device = WsDiscoveryCodec.parseProbeMatches(DAHUA, sourceHost = "10.0.0.108").single()

        assertEquals("10.0.0.108", device.host)
        assertEquals("Front Gate", device.name)
        assertEquals("Amcrest Technologies", device.manufacturer)
        assertEquals("IPC-HDW2431T-AS-S2", device.model)
        assertEquals("http://10.0.0.108/onvif/device_service", device.onvifServiceUri)
    }

    @Test
    fun `prefers the XAddr that matches the sender`() {
        val xml = probeMatch(
            xAddrs = "http://[fe80::1]/onvif/device_service http://192.168.5.20:8080/onvif/device_service " +
                "http://192.168.1.30/onvif/device_service",
            scopes = "onvif://www.onvif.org/manufacturer/Reolink",
        )

        val device = WsDiscoveryCodec.parseProbeMatches(xml, sourceHost = "192.168.1.30").single()

        assertEquals("192.168.1.30", device.host)
        assertEquals("http://192.168.1.30/onvif/device_service", device.onvifServiceUri)
        assertEquals("Reolink", device.manufacturer)
    }

    @Test
    fun `falls back to sender address when there is no IPv4 XAddr`() {
        val xml = probeMatch(xAddrs = "", scopes = "onvif://www.onvif.org/name/Cam_1")

        val device = WsDiscoveryCodec.parseProbeMatches(xml, sourceHost = "192.168.1.9").single()

        assertEquals("192.168.1.9", device.host)
        assertEquals(null, device.onvifServiceUri)
        assertEquals("Cam 1", device.name)
    }

    @Test
    fun `match without any host is dropped`() {
        val xml = probeMatch(xAddrs = "", scopes = "")

        assertEquals(emptyList<DiscoveredDevice>(), WsDiscoveryCodec.parseProbeMatches(xml, sourceHost = null))
    }

    @Test
    fun `own probe and malformed input yield nothing`() {
        assertEquals(emptyList<DiscoveredDevice>(), WsDiscoveryCodec.parseProbeMatches(WsDiscoveryCodec.probe(), "1.2.3.4"))
        assertEquals(emptyList<DiscoveredDevice>(), WsDiscoveryCodec.parseProbeMatches("<not xml", "1.2.3.4"))
        assertEquals(emptyList<DiscoveredDevice>(), WsDiscoveryCodec.parseProbeMatches("", "1.2.3.4"))
    }

    private fun org.w3c.dom.Document.text(namespace: String, localName: String): String =
        getElementsByTagNameNS(namespace, localName).item(0).textContent.trim()

    private fun probeMatch(xAddrs: String, scopes: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
        <s:Body><d:ProbeMatches><d:ProbeMatch>
        <d:Scopes>$scopes</d:Scopes>
        <d:XAddrs>$xAddrs</d:XAddrs>
        </d:ProbeMatch></d:ProbeMatches></s:Body>
        </s:Envelope>
    """.trimIndent()

    private companion object {
        const val WSA = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
        const val WSD = "http://schemas.xmlsoap.org/ws/2005/04/discovery"

        val HIKVISION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:soapenc="http://www.w3.org/2003/05/soap-encoding" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:tt="http://www.onvif.org/ver10/schema" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:wsadis="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
            <env:Header>
            <wsadis:MessageID>urn:uuid:5d6ee000-1dd2-11b2-8160-4419b6a1b2c3</wsadis:MessageID>
            <wsadis:RelatesTo>uuid:1234</wsadis:RelatesTo>
            <wsadis:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsadis:To>
            <wsadis:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsadis:Action>
            <d:AppSequence InstanceId="1696330000" MessageNumber="12"/>
            </env:Header>
            <env:Body>
            <d:ProbeMatches>
            <d:ProbeMatch>
            <wsadis:EndpointReference>
            <wsadis:Address>urn:uuid:5d6ee000-1dd2-11b2-8160-4419b6a1b2c3</wsadis:Address>
            </wsadis:EndpointReference>
            <d:Types>dn:NetworkVideoTransmitter tds:Device</d:Types>
            <d:Scopes>onvif://www.onvif.org/type/video_encoder onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/MAC/44:19:b6:a1:b2:c3 onvif://www.onvif.org/hardware/DS-2CD2143G0-I onvif://www.onvif.org/name/HIKVISION%20DS-2CD2143G0-I onvif://www.onvif.org/location/city/hangzhou</d:Scopes>
            <d:XAddrs>http://192.168.1.64/onvif/device_service http://[fe80::4619:b6ff:fea1:b2c3]/onvif/device_service</d:XAddrs>
            <d:MetadataVersion>10</d:MetadataVersion>
            </d:ProbeMatch>
            </d:ProbeMatches>
            </env:Body>
            </env:Envelope>
        """.trimIndent()

        val DAHUA = """
            <?xml version="1.0" encoding="utf-8" standalone="yes" ?>
            <s:Envelope xmlns:sc="http://www.w3.org/2003/05/soap-encoding" xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:dn="http://www.onvif.org/ver10/network/wsdl" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:d="http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01" xmlns:a="http://www.w3.org/2005/08/addressing">
            <s:Header>
            <a:MessageID>urn:uuid:a6e1b1f2-0000-4d3e-9a7c-3ce36b000000</a:MessageID>
            <a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>
            <a:Action>http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ProbeMatches</a:Action>
            <a:RelatesTo>uuid:1234</a:RelatesTo>
            </s:Header>
            <s:Body>
            <d:ProbeMatches>
            <d:ProbeMatch>
            <a:EndpointReference><a:Address>uuid:a6e1b1f2-0000-4d3e-9a7c-3ce36b000000</a:Address></a:EndpointReference>
            <d:Types>dn:NetworkVideoTransmitter tds:Device</d:Types>
            <d:Scopes>onvif://www.onvif.org/location/country/china onvif://www.onvif.org/name/Front+Gate onvif://www.onvif.org/hardware/IPC-HDW2431T-AS-S2 onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/type/Network_Video_Transmitter onvif://www.onvif.org/mfr/Amcrest_Technologies</d:Scopes>
            <d:XAddrs>http://10.0.0.108/onvif/device_service</d:XAddrs>
            <d:MetadataVersion>1</d:MetadataVersion>
            </d:ProbeMatch>
            </d:ProbeMatches>
            </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}

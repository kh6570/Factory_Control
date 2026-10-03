// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.discovery

import com.raghim.herz.core.model.DiscoveredDevice
import com.raghim.herz.core.model.DiscoveryMethod
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/** Builds ONVIF WS-Discovery probes and parses ProbeMatches replies. Pure Kotlin. */
object WsDiscoveryCodec {
    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 3702

    private const val SCOPE_PREFIX = "onvif://www.onvif.org/"
    private val WHITESPACE = Regex("\\s+")

    fun probe(messageId: String = "uuid:${UUID.randomUUID()}"): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
        <e:Header>
        <w:MessageID>$messageId</w:MessageID>
        <w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
        <w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
        </e:Header>
        <e:Body>
        <d:Probe>
        <d:Types>dn:NetworkVideoTransmitter</d:Types>
        </d:Probe>
        </e:Body>
        </e:Envelope>
        """.trimIndent()

    /**
     * Returns one device per `ProbeMatch` in [xml]. [sourceHost] is the UDP sender and is used
     * when the reply has no usable IPv4 XAddr. Malformed input yields an empty list.
     */
    fun parseProbeMatches(xml: String, sourceHost: String?): List<DiscoveredDevice> {
        val document = parse(xml) ?: return emptyList()
        val matches = document.getElementsByTagNameNS("*", "ProbeMatch")
        return (0 until matches.length).mapNotNull { index ->
            val match = matches.item(index) as? Element ?: return@mapNotNull null
            toDevice(
                xAddrs = match.childText("XAddrs").split(WHITESPACE).filter { it.isNotEmpty() },
                scopes = match.childText("Scopes").split(WHITESPACE).filter { it.isNotEmpty() },
                sourceHost = sourceHost,
            )
        }
    }

    private fun toDevice(xAddrs: List<String>, scopes: List<String>, sourceHost: String?): DiscoveredDevice? {
        val httpAddrs = xAddrs.filter {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
        val serviceUri = httpAddrs.firstOrNull { hostOf(it) == sourceHost }
            ?: httpAddrs.firstOrNull { hostOf(it)?.isIpv4() == true }
            ?: httpAddrs.firstOrNull()
        val host = serviceUri?.let(::hostOf)?.takeIf { it.isIpv4() } ?: sourceHost ?: return null
        return DiscoveredDevice(
            host = host,
            method = DiscoveryMethod.ONVIF,
            name = scopeValue(scopes, "name"),
            manufacturer = scopeValue(scopes, "mfr", "manufacturer"),
            model = scopeValue(scopes, "hardware"),
            onvifServiceUri = serviceUri,
        )
    }

    private fun scopeValue(scopes: List<String>, vararg keys: String): String? =
        scopes.firstNotNullOfOrNull { scope ->
            keys.firstNotNullOfOrNull { key ->
                val prefix = "$SCOPE_PREFIX$key/"
                if (scope.startsWith(prefix, ignoreCase = true)) decode(scope.substring(prefix.length)) else null
            }
        }

    private fun decode(raw: String): String? {
        val decoded = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: IllegalArgumentException) {
            raw
        }
        return decoded.replace('_', ' ').trim().ifBlank { null }
    }

    private fun hostOf(uri: String): String? = try {
        URI(uri).host?.removeSurrounding("[", "]")
    } catch (e: Exception) {
        null
    }

    private fun String.isIpv4(): Boolean = SubnetRange.parseIpv4(this) != null

    private fun Element.childText(localName: String): String =
        getElementsByTagNameNS("*", localName).item(0)?.textContent.orEmpty()

    private fun parse(xml: String): Document? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (e: Exception) {
                // Not supported by every parser (e.g. Android's); the reply is still parsed.
            }
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    } catch (e: Exception) {
        null
    }
}

package io.signallq.app.feature.fibra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GH#865 Fase 1 — cobre os campos novos de Wi-Fi/LAN extraidos a partir de
 * fixtures representativas do padrao JS real do firmware Nokia
 * (`docs/technical/NOKIA_GPON_FIELD_MAP.md`, revisado apos revalidacao
 * contra equipamento real em 2026-07-10). `wlan_status` e um objeto indexado
 * por chave numerica (`{1:{...}, 2:{...}}`), nao um array — e vive na pagina
 * `lan_status.cgi?lan`, nao em `?wlan`. `Enable` (por SSID) tem prioridade
 * sobre `RadioEnabled` (do radio fisico inteiro, fica 1 mesmo com SSID
 * guest desligado).
 */
class NokiaModemParserTest {
    // ─── parseWifi ──────────────────────────────────────────────────────

    private val wlanStatusFixture =
        """
        var wlan_status = {
        1:{"RadioEnabled":1,"Enable":1,"SSID":"CasaWifi","Channel":6,"Standard":"b,g,n","BeaconType":"WPAand11i","TransmitPower":100,"TotalAssociations":5},
        5:{"RadioEnabled":1,"Enable":0,"SSID":"CasaWifi_5G","Channel":44,"Standard":"a,n,ac","BeaconType":"11i","TransmitPower":80,"TotalAssociations":0},
        6:{"RadioEnabled":1,"Enable":1,"Channel":1,"Standard":"b,g,n","BeaconType":"None","TransmitPower":100,"TotalAssociations":0}
        };
        """.trimIndent()

    @Test
    fun `parseWifi extrai radios 2_4GHz e 5GHz e ignora bloco sem SSID`() {
        val resultado = NokiaModemParser.parseWifi(wlanStatusFixture)

        requireNotNull(resultado)
        assertEquals(2, resultado.radios.size)

        val radio24 = resultado.radios[0]
        assertEquals("2.4GHz", radio24.banda)
        assertEquals("CasaWifi", radio24.ssid)
        assertEquals(6, radio24.canal)
        assertTrue(radio24.habilitado)
        assertEquals("WPAand11i", radio24.criptografia)
        assertEquals("100%", radio24.potenciaTx)

        val radio5 = resultado.radios[1]
        assertEquals("5GHz", radio5.banda)
        assertEquals("CasaWifi_5G", radio5.ssid)
        assertEquals(44, radio5.canal)
        assertFalse(radio5.habilitado)
        assertEquals("11i", radio5.criptografia)
        assertEquals("80%", radio5.potenciaTx)
    }

    @Test
    fun `parseWifi prioriza Enable sobre RadioEnabled para SSID guest desligado`() {
        // Achado real da revalidacao de 2026-07-10: guest SSID desligado tem
        // RadioEnabled=1 (radio fisico do band ligado) mas Enable=0 (essa
        // rede especifica desligada) — usar RadioEnabled primeiro faria o
        // guest aparecer como ativo por engano.
        val fixture =
            """
            var wlan_status = {
            2:{"RadioEnabled":1,"Enable":0,"SSID":"Guest","Channel":6,"Standard":"b,g,n","BeaconType":"11i","TransmitPower":100,"TotalAssociations":0}
            };
            """.trimIndent()
        val resultado = NokiaModemParser.parseWifi(fixture)

        requireNotNull(resultado)
        assertFalse(resultado.radios.single().habilitado)
    }

    @Test
    fun `parseWifi retorna null quando nao ha wlan_status na resposta`() {
        val resultado = NokiaModemParser.parseWifi("<html><body>pagina sem dado</body></html>")
        assertNull(resultado)
    }

    @Test
    fun `parseWifi nunca extrai PreSharedKey mesmo se presente no HTML`() {
        // Regressao de seguranca: mesmo que a pagina real venha com PSK em algum
        // lugar do documento completo, o parser so le as chaves conhecidas do
        // objeto wlan_status — nunca deve produzir um campo de senha.
        val fixtureComPsk = wlanStatusFixture + "\nvar psks = {PreSharedKey:'segredo123'};"
        val resultado = NokiaModemParser.parseWifi(fixtureComPsk)

        requireNotNull(resultado)
        resultado.radios.forEach { radio ->
            assertFalse(radio.toString().contains("segredo123"))
        }
    }

    // ─── parseLan ───────────────────────────────────────────────────────

    private val lanStatusFixture =
        """
        var lan_ifip = {IPAddress:'192.168.1.254',SubnetMask:'255.255.255.0'};
        var lan_ether = [
        {Enable:true,Status:'Up',MACAddress:'AA:BB:CC:DD:EE:01',MaxBitRate:'1000',stat:{BytesSent:100,BytesReceived:200}},
        {Enable:true,Status:'NoLink',MACAddress:'AA:BB:CC:DD:EE:02',MaxBitRate:'Auto',stat:{BytesSent:0,BytesReceived:0}}
        ];
        """.trimIndent()

    private val lanConfigFixture =
        """
        var ipv4_config = {DHCPServerEnable:true,MinAddress:'192.168.1.100',MaxAddress:'192.168.1.200',SubnetMask:'255.255.255.0'};
        """.trimIndent()

    @Test
    fun `parseLan combina IP do roteador com faixa de DHCP`() {
        val resultado = NokiaModemParser.parseLan(lanStatusFixture, lanConfigFixture)

        requireNotNull(resultado)
        assertEquals("192.168.1.254", resultado.routerIp)
        assertEquals("255.255.255.0", resultado.subnetMask)
        assertTrue(resultado.dhcpHabilitado)
        assertEquals("192.168.1.100", resultado.dhcpFaixaInicio)
        assertEquals("192.168.1.200", resultado.dhcpFaixaFim)
    }

    @Test
    fun `parseLan retorna null quando nenhuma das duas paginas traz dado util`() {
        val resultado = NokiaModemParser.parseLan("<html></html>", "<html></html>")
        assertNull(resultado)
    }

    @Test
    fun `parseLan usa apenas a pagina de config quando status nao tem lan_ifip`() {
        val resultado = NokiaModemParser.parseLan("<html>sem lan_ifip</html>", lanConfigFixture)

        requireNotNull(resultado)
        assertEquals("—", resultado.routerIp)
        assertEquals("255.255.255.0", resultado.subnetMask)
        assertTrue(resultado.dhcpHabilitado)
    }

    // ─── extractJsObjectBlocks (helper interno reaproveitado por parseWifi/parseLan) ──

    @Test
    fun `extractJsObjectBlocks respeita chaves aninhadas ao separar blocos`() {
        val blocks = NokiaModemParser.extractJsObjectBlocks(lanStatusFixture, "lan_ether")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].contains("stat:{BytesSent:100,BytesReceived:200}"))
    }

    @Test
    fun `extractJsObjectBlocks retorna lista vazia quando variavel nao existe`() {
        val blocks = NokiaModemParser.extractJsObjectBlocks("var outra_coisa = [];", "lan_ether")
        assertTrue(blocks.isEmpty())
    }
}

package io.signallq.app.feature.fibra

import io.signallq.app.core.network.contracts.localdevice.TipoConexaoFisica
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GH#983 Fase 4 — cobre [normalizarTipoConexaoFisica], a normalizacao do
 * `InterfaceType` cru do firmware Nokia (`device_cfg`, `lan_status.cgi?wlan`)
 * pro enum canonico [TipoConexaoFisica]. So o valor "Ethernet" esta confirmado
 * no field-map (`docs/technical/NOKIA_GPON_FIELD_MAP.md`); os demais casos
 * cobrem variantes plausiveis de firmware e o fallback seguro.
 */
class NormalizarTipoConexaoFisicaTest {
    @Test
    fun `string tipo wired mapeia para ETHERNET`() {
        assertEquals(TipoConexaoFisica.ETHERNET, normalizarTipoConexaoFisica("Ethernet"))
        assertEquals(TipoConexaoFisica.ETHERNET, normalizarTipoConexaoFisica("ethernet"))
        assertEquals(TipoConexaoFisica.ETHERNET, normalizarTipoConexaoFisica("Wired"))
    }

    @Test
    fun `string tipo wireless mapeia para WIFI`() {
        assertEquals(TipoConexaoFisica.WIFI, normalizarTipoConexaoFisica("Wireless"))
        assertEquals(TipoConexaoFisica.WIFI, normalizarTipoConexaoFisica("wifi"))
        assertEquals(TipoConexaoFisica.WIFI, normalizarTipoConexaoFisica("WLAN"))
    }

    @Test
    fun `wlan nao e classificado como ETHERNET por causa da substring lan`() {
        // Regressao: "wlan" contem a substring "lan" — a checagem de Wi-Fi
        // precisa vir antes da checagem de Ethernet no normalizador.
        assertEquals(TipoConexaoFisica.WIFI, normalizarTipoConexaoFisica("wlan0"))
    }

    @Test
    fun `string desconhecida ou nula mapeia para DESCONHECIDO`() {
        assertEquals(TipoConexaoFisica.DESCONHECIDO, normalizarTipoConexaoFisica(null))
        assertEquals(TipoConexaoFisica.DESCONHECIDO, normalizarTipoConexaoFisica(""))
        assertEquals(TipoConexaoFisica.DESCONHECIDO, normalizarTipoConexaoFisica("   "))
        assertEquals(TipoConexaoFisica.DESCONHECIDO, normalizarTipoConexaoFisica("PowerLine"))
    }
}

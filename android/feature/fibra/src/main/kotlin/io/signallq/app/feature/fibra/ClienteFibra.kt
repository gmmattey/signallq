package io.signallq.app.feature.fibra

import io.signallq.app.core.network.contracts.localdevice.TipoConexaoFisica

/**
 * Um cliente conectado reportado pelo próprio roteador Nokia (`device_cfg`,
 * tela "Home Networking", `lan_status.cgi?wlan`) — GH#839/#865 Fase 2. Ver
 * `docs/technical/NOKIA_GPON_FIELD_MAP.md`.
 */
data class ClienteFibra(
    val mac: String?,
    val ip: String?,
    val hostname: String?,
    val tipoConexao: String?,
    // GH#983 Fase 4 — normalizado por NokiaModemParser.normalizarTipoConexaoFisica
    // a partir de [tipoConexao] (InterfaceType cru do firmware).
    val tipoConexaoFisica: TipoConexaoFisica = TipoConexaoFisica.DESCONHECIDO,
)

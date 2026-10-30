package io.signallq.app.core.network.contracts.localdevice

/**
 * Dados da conexão WAN do equipamento — comum a ONT e roteador (ex: Nokia
 * reporta a WAN da fibra; TP-Link reporta a WAN recebida via DHCP da ONT
 * upstream, cenário de double NAT documentado em
 * `docs/technical/TPLINK_ARCHER_ROUTER_FIELD_MAP.md`).
 */
data class WanSnapshot(
    val ipExterno: String?,
    val gateway: String?,
    val dnsPrimario: String?,
    val dnsSecundario: String?,
    val tipoConexao: String?,
    val nomeInterface: String?,
    val uptimeSegundos: Int?,
)

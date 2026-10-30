package io.signallq.app.feature.fibra

/**
 * Configuracao de rede local (LAN) do roteador Nokia — combina o IP/mascara
 * da propria interface LAN do ONT (`lan_status.cgi?lan` -> `lan_ifip`) com a
 * configuracao do servidor DHCP (`lan_ipv4.cgi` -> `ipv4_config`). Ver
 * `docs/technical/NOKIA_GPON_FIELD_MAP.md`.
 */
data class LanStatus(
    val routerIp: String,
    val subnetMask: String,
    val dhcpHabilitado: Boolean,
    val dhcpFaixaInicio: String,
    val dhcpFaixaFim: String,
)

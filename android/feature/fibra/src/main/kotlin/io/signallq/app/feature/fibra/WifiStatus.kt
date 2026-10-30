package io.signallq.app.feature.fibra

/**
 * Estado de um radio Wi-Fi (2.4GHz/5GHz) lido da tela "Home Networking"
 * (`lan_status.cgi?wlan`) do roteador Nokia — ver
 * `docs/technical/NOKIA_GPON_FIELD_MAP.md`.
 *
 * Deliberadamente nao carrega PSK/senha — o field-map confirma que a senha
 * Wi-Fi so aparece na tela de configuracao (`wlan_config.cgi`), nunca
 * consultada por este parser.
 */
data class WifiRadioStatus(
    val banda: String,
    val ssid: String,
    val canal: Int?,
    val habilitado: Boolean,
    val criptografia: String,
    val potenciaTx: String,
)

data class WifiStatus(
    val radios: List<WifiRadioStatus>,
)

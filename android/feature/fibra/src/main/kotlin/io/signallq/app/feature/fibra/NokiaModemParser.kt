package io.signallq.app.feature.fibra

import io.signallq.app.core.network.contracts.localdevice.TipoConexaoFisica
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.log10

internal object NokiaModemParser {
    fun parseGpon(html: String): GponStatus? =
        try {
            val stat = extractJsIntAny(html, listOf("GponConnectionStat"))
            val status = if ((stat ?: 0) == 1) "up" else "down"
            val mode = extractJsStringAny(html, listOf("GponMode", "ConnectionMode")) ?: "—"

            val rxRaw = extractJsNumberAny(html, listOf("RXPower", "RxPower", "ReceivePower"))
            val txRaw = extractJsNumberAny(html, listOf("TXPower", "TxPower", "TransmitPower"))
            val tempRaw = extractJsIntAny(html, listOf("TransceiverTemperature"))
            val serial = extractJsStringAny(html, listOf("SerialNumber", "GPONSerial")) ?: "—"
            // Nokia firmware tem typo: SupplyVottage (sic). Aceitar as duas grafias.
            val voltRaw = extractJsIntAny(html, listOf("SupplyVoltage", "SupplyVottage"))
            // LaserCurrent raw está em 0.5 µA (não µA), então /500 = mA.
            val laserRaw = extractJsIntAny(html, listOf("LaserCurrent", "TxBiasCurrent", "BiasCurrent"))

            val rxDbm = normalizeRx(convertJsRxPowerToDbm(rxRaw?.toString()))
            val txDbm = convertJsTxPowerToDbm(txRaw?.toString())
            // TransceiverTemperature é Q8.8 fixed-point: raw / 256.0 = °C.
            val tempC = (tempRaw ?: 0) / 256.0
            val voltV = (voltRaw ?: 0) / 10000.0
            val laserMa = (laserRaw ?: 0) / 500.0

            GponStatus(
                status = status,
                mode = mode,
                rxPowerDbm = rxDbm,
                txPowerDbm = txDbm,
                temperatureCelsius = tempC,
                serial = serial,
                voltageV = voltV,
                laserCurrentMa = laserMa,
            )
        } catch (_: Throwable) {
            null
        }

    fun parseWan(html: String): WanStatus? {
        return try {
            // Extrai dados do objeto JS wan_conns (path primário no Nokia G-1425G-B).
            val connPattern =
                Regex(
                    """ConnectionStatus:'Connected'[^}]*?ExternalIPAddress:'([^']*)'[^}]*?RemoteIPAddress:'([^']*)'[^}]*?DNSServers:'([^']*)'""",
                    setOf(RegexOption.DOT_MATCHES_ALL),
                )
            val match = connPattern.find(html)

            val externalIp = match?.groupValues?.get(1).orEmpty()
            val gateway = match?.groupValues?.get(2).orEmpty()
            val dnsServers = match?.groupValues?.get(3).orEmpty()
            val dnsList = dnsServers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val primaryDns = dnsList.getOrElse(0) { "" }
            val secondaryDns = dnsList.getOrElse(1) { "" }

            val vlanId = Regex("""VLANIDMark:\s*(\d+)""").find(html)?.groupValues?.get(1) ?: ""
            val interfaceName = Regex("""X_ASB_COM_IfName:'([^']*)'""").find(html)?.groupValues?.get(1) ?: ""
            val pppoeConc = Regex("""PPPoEACName:'([^']*)'""").find(html)?.groupValues?.get(1) ?: ""
            val uptime =
                Regex("""Uptime:(\d+),""")
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0
            val connType = Regex("""ConnectionType:'([^']*)'""").find(html)?.groupValues?.get(1) ?: ""

            if (externalIp.isEmpty() && gateway.isEmpty()) return null

            WanStatus(
                externalIp = externalIp.ifEmpty { "—" },
                gateway = gateway.ifEmpty { "—" },
                primaryDns = primaryDns.ifEmpty { "—" },
                secondaryDns = secondaryDns.ifEmpty { "—" },
                vlanId = vlanId.ifEmpty { "—" },
                interfaceName = interfaceName.ifEmpty { "—" },
                pppoeConcentrator = pppoeConc.ifEmpty { "—" },
                connectionType = connType.ifEmpty { "—" },
                connectionUptimeSeconds = uptime,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun parsePpp(json: String): PppStatus? {
        return try {
            val obj = JSONObject(json)
            val list = obj.optJSONArray("ppp_status") ?: return null
            if (list.length() == 0) return null
            val entry = list.getJSONObject(0)
            val status = entry.optString("ConnectionStatus", "Unknown")
            PppStatus(
                isConnected = status == "Connected",
                connectionStatus = status,
                connectionType = entry.optString("ConnectionType", ""),
                name = entry.optString("Name", ""),
                lastError = entry.optString("LastConnectionError", ""),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extrai os radios Wi-Fi (2.4GHz/5GHz) do objeto JS `wlan_status`
     * — GH#865 Fase 1, corrigido apos revalidacao contra equipamento real
     * em 2026-07-10.
     *
     * IMPORTANTE: `wlan_status` vive na pagina `lan_status.cgi?lan` (a mesma
     * ja buscada para LAN), NAO em `lan_status.cgi?wlan` como o documento de
     * campo original (e a primeira versao deste parser) assumiam. A pagina
     * `?wlan` contem outros objetos (`wlan_ssid`, `device_cfg`, `alias_cfg`)
     * — ver `docs/technical/NOKIA_GPON_FIELD_MAP.md`, secao revisada.
     *
     * `larguraCanal` fica sempre null nesta fase: o unico campo documentado
     * para largura de banda (`X_ASB_COM_OperatingChannelBandwidth`) vive na
     * tela de configuracao (`wlan_config.cgi`), a mesma que carrega PSK/senha
     * em texto plano — nao buscamos essa pagina para evitar qualquer risco de
     * trafegar segredo, mesmo que so leiamos um campo nao-secreto dela.
     */
    fun parseWifi(html: String): WifiStatus? {
        return try {
            val blocks = extractJsKeyedObjectBlocks(html, "wlan_status")
            if (blocks.isEmpty()) return null

            val radios =
                blocks.mapNotNull { block ->
                    val ssid = extractJsStringAny(block, listOf("SSID")) ?: return@mapNotNull null
                    val standard = extractJsStringAny(block, listOf("Standard")).orEmpty()
                    val banda =
                        if (standard.contains("ac", ignoreCase = true) ||
                            standard.contains("ax", ignoreCase = true)
                        ) {
                            "5GHz"
                        } else {
                            "2.4GHz"
                        }
                    // "Enable" e o flag do SSID especifico (liga/desliga essa rede);
                    // "RadioEnabled" e do radio fisico do band inteiro e fica 1 mesmo
                    // pras redes guest desativadas — usar Enable primeiro, senao todo
                    // SSID guest desligado aparece como ativo. Achado na revalidacao
                    // de 2026-07-10.
                    val habilitado = extractJsBoolAny(block, listOf("Enable", "RadioEnabled")) ?: true
                    val canal = extractJsIntAny(block, listOf("Channel"))
                    val criptografia = extractJsStringAny(block, listOf("BeaconType")) ?: "—"
                    val potenciaRaw =
                        extractJsStringAny(block, listOf("TransmitPower"))
                            ?: extractJsIntAny(block, listOf("TransmitPower"))?.toString()

                    WifiRadioStatus(
                        banda = banda,
                        ssid = ssid,
                        canal = canal,
                        habilitado = habilitado,
                        criptografia = criptografia,
                        potenciaTx = potenciaRaw?.let { "$it%" } ?: "—",
                    )
                }

            if (radios.isEmpty()) null else WifiStatus(radios)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Combina o IP/mascara da propria interface LAN do ONT (`lan_ifip`, tela
     * `lan_status.cgi?lan`) com a configuracao do servidor DHCP (`ipv4_config`,
     * tela `lan_ipv4.cgi`) — GH#865 Fase 1. Ver
     * `docs/technical/NOKIA_GPON_FIELD_MAP.md`.
     */
    fun parseLan(
        lanStatusHtml: String,
        lanConfigHtml: String,
    ): LanStatus? {
        return try {
            val routerIp =
                extractJsStringAny(
                    lanStatusHtml,
                    listOf("IPAddress", "IPInterfaceIPAddress", "LanIPAddress"),
                )
            val maskFromStatus =
                extractJsStringAny(
                    lanStatusHtml,
                    listOf("SubnetMask", "IPInterfaceSubnetMask", "NetMask"),
                )
            val maskFromConfig = extractJsStringAny(lanConfigHtml, listOf("SubnetMask"))
            val dhcpMin = extractJsStringAny(lanConfigHtml, listOf("MinAddress"))
            val dhcpMax = extractJsStringAny(lanConfigHtml, listOf("MaxAddress"))
            val dhcpEnabled = extractJsBoolAny(lanConfigHtml, listOf("DHCPServerEnable"))

            if (routerIp.isNullOrEmpty() && dhcpMin.isNullOrEmpty()) return null

            LanStatus(
                routerIp = routerIp?.ifEmpty { "—" } ?: "—",
                subnetMask = (maskFromStatus ?: maskFromConfig)?.ifEmpty { "—" } ?: "—",
                dhcpHabilitado = dhcpEnabled ?: false,
                dhcpFaixaInicio = dhcpMin?.ifEmpty { "—" } ?: "—",
                dhcpFaixaFim = dhcpMax?.ifEmpty { "—" } ?: "—",
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extrai a lista real de clientes conectados (`device_cfg`, array JS) e
     * funde com os apelidos que o usuário deu no próprio roteador
     * (`alias_cfg`) — ambos vivem em `lan_status.cgi?wlan`. GH#839/#865
     * Fase 2, ver `docs/technical/NOKIA_GPON_FIELD_MAP.md`.
     *
     * Apelido (`HostAlias`) tem prioridade sobre o hostname técnico
     * (`HostName`, que costuma vir genérico tipo "Unknown_90:75:bc:39:83:30")
     * quando o usuário deu um nome no roteador — o filtro de nomes genéricos
     * na tela "Dispositivos" (`NamingPrioridade`) cuida do resto.
     */
    fun parseClientes(html: String): List<ClienteFibra> {
        return try {
            val aliasPorMac =
                extractJsObjectBlocks(html, "alias_cfg")
                    .mapNotNull { block ->
                        val mac = extractJsStringAny(block, listOf("MACAddress"))?.lowercase()?.takeIf { it.isNotBlank() }
                        val alias = extractJsStringAny(block, listOf("HostAlias"))?.takeIf { it.isNotBlank() }
                        if (mac != null && alias != null) mac to alias else null
                    }.toMap()

            extractJsObjectBlocks(html, "device_cfg").mapNotNull { block ->
                val mac =
                    extractJsStringAny(block, listOf("MACAddress"))?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                val ip = extractJsStringAny(block, listOf("IPAddress"))?.takeIf { it.isNotBlank() }
                val hostName = extractJsStringAny(block, listOf("HostName"))?.takeIf { it.isNotBlank() }
                val tipoConexao = extractJsStringAny(block, listOf("InterfaceType"))?.takeIf { it.isNotBlank() }
                ClienteFibra(
                    mac = mac,
                    ip = ip,
                    hostname = aliasPorMac[mac.lowercase()] ?: hostName,
                    tipoConexao = tipoConexao,
                    tipoConexaoFisica = normalizarTipoConexaoFisica(tipoConexao),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun parseDeviceInfo(html: String): DeviceInfoFibra? {
        return try {
            val model = Regex(""""ModelName":"([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""
            val manufacturer = Regex(""""Manufacturer":"([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""
            val serial = Regex(""""SerialNumber":"([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""
            val firmware = Regex(""""SoftwareVersion":"([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""
            val hardware = Regex(""""HardwareVersion":"([^"]*)"""").find(html)?.groupValues?.get(1) ?: ""
            val uptime =
                Regex(""""UpTime":(\d+)""")
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: 0

            if (model.isEmpty() && serial.isEmpty()) return null

            DeviceInfoFibra(
                model = model.ifEmpty { "—" },
                manufacturer = manufacturer.ifEmpty { "—" },
                serialNumber = serial.ifEmpty { "—" },
                firmwareVersion = firmware.ifEmpty { "—" },
                hardwareVersion = hardware.ifEmpty { "—" },
                uptimeSeconds = uptime,
            )
        } catch (_: Throwable) {
            null
        }
    }

    // --- Helpers de extração JS ---

    internal fun extractJsStringAny(
        source: String,
        keys: List<String>,
    ): String? {
        for (key in keys) {
            val escaped = Regex.escape(key)
            val patterns =
                listOf(
                    Regex(""""$escaped"\s*:\s*"([^"]*)""""),
                    Regex(""""$escaped"\s*:\s*'([^']*)'"""),
                    Regex("""\b$escaped\b\s*:\s*"([^"]*)""""),
                    Regex("""\b$escaped\b\s*:\s*'([^']*)'"""),
                    Regex("""\b$escaped\b\s*=\s*"([^"]*)""""),
                    Regex("""\b$escaped\b\s*=\s*'([^']*)'"""),
                )
            for (p in patterns) {
                val m = p.find(source) ?: continue
                return m.groupValues[1]
            }
        }
        return null
    }

    internal fun extractJsIntAny(
        source: String,
        keys: List<String>,
    ): Int? {
        for (key in keys) {
            val escaped = Regex.escape(key)
            val patterns =
                listOf(
                    Regex(""""$escaped"\s*:\s*(-?\d+)"""),
                    Regex("""\b$escaped\b\s*:\s*(-?\d+)"""),
                    Regex("""\b$escaped\b\s*=\s*(-?\d+)"""),
                )
            for (p in patterns) {
                val m = p.find(source) ?: continue
                return m.groupValues[1].toIntOrNull()
            }
        }
        return null
    }

    internal fun extractJsBoolAny(
        source: String,
        keys: List<String>,
    ): Boolean? {
        for (key in keys) {
            val escaped = Regex.escape(key)
            val patterns =
                listOf(
                    Regex(""""$escaped"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE),
                    Regex("""\b$escaped\b\s*:\s*(true|false)""", RegexOption.IGNORE_CASE),
                    Regex("""\b$escaped\b\s*=\s*(true|false)""", RegexOption.IGNORE_CASE),
                )
            for (p in patterns) {
                val m = p.find(source) ?: continue
                return m.groupValues[1].equals("true", ignoreCase = true)
            }
        }
        // Alguns firmwares reportam booleano como 0/1 em vez de true/false.
        val intVal = extractJsIntAny(source, keys) ?: return null
        return intVal == 1
    }

    /**
     * Localiza um array JS pelo nome da variavel (ex: `wlan_status:[...]` ou
     * `var lan_ether = [...]`) e retorna o conteudo bruto de cada objeto
     * `{...}` do array, respeitando aninhamento de chaves (ex: um `stat:{...}`
     * dentro do item). Necessario porque os campos novos deste parser vivem
     * em listas (varios radios Wi-Fi, varias portas LAN), diferente dos
     * objetos unicos ja tratados por [extractJsStringAny]/[extractJsIntAny].
     */
    internal fun extractJsObjectBlocks(
        source: String,
        arrayVarName: String,
    ): List<String> {
        val escaped = Regex.escape(arrayVarName)
        val varMatch = Regex("""\b$escaped\b\s*[:=]\s*\[""").find(source) ?: return emptyList()
        val arrayStart = varMatch.range.last // índice do '['

        var depthBracket = 0
        var arrayEnd = -1
        var i = arrayStart
        while (i < source.length) {
            when (source[i]) {
                '[' -> depthBracket++
                ']' -> {
                    depthBracket--
                    if (depthBracket == 0) {
                        arrayEnd = i
                        break
                    }
                }
            }
            i++
        }
        if (arrayEnd == -1) return emptyList()

        val arrayContent = source.substring(arrayStart + 1, arrayEnd)
        val blocks = mutableListOf<String>()
        var depthBrace = 0
        var blockStart = -1
        var j = 0
        while (j < arrayContent.length) {
            when (arrayContent[j]) {
                '{' -> {
                    if (depthBrace == 0) blockStart = j
                    depthBrace++
                }
                '}' -> {
                    depthBrace--
                    if (depthBrace == 0 && blockStart != -1) {
                        blocks.add(arrayContent.substring(blockStart, j + 1))
                        blockStart = -1
                    }
                }
            }
            j++
        }
        return blocks
    }

    /**
     * Extrai os blocos `{...}` de um objeto JS indexado por chave numerica
     * (`var nome = { 1:{...}, 2:{...}, ... }`), diferente de um array
     * (`var nome = [{...}, {...}]`) que `extractJsObjectBlocks` cobre.
     * Achado na revalidacao de 2026-07-10: `wlan_status` no Nokia G-1425G-B
     * usa esse formato, nao o de array que o parser original assumia.
     */
    internal fun extractJsKeyedObjectBlocks(
        source: String,
        objVarName: String,
    ): List<String> {
        val escaped = Regex.escape(objVarName)
        val varMatch = Regex("""\b$escaped\b\s*[:=]\s*\{""").find(source) ?: return emptyList()
        val objStart = varMatch.range.last // índice do '{' de abertura do objeto

        var depthBrace = 0
        var objEnd = -1
        var i = objStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> depthBrace++
                '}' -> {
                    depthBrace--
                    if (depthBrace == 0) {
                        objEnd = i
                        break
                    }
                }
            }
            i++
        }
        if (objEnd == -1) return emptyList()

        val objContent = source.substring(objStart + 1, objEnd)
        val blocks = mutableListOf<String>()
        var depthInner = 0
        var blockStart = -1
        var j = 0
        while (j < objContent.length) {
            when (objContent[j]) {
                '{' -> {
                    if (depthInner == 0) blockStart = j
                    depthInner++
                }
                '}' -> {
                    depthInner--
                    if (depthInner == 0 && blockStart != -1) {
                        blocks.add(objContent.substring(blockStart, j + 1))
                        blockStart = -1
                    }
                }
            }
            j++
        }
        return blocks
    }

    private fun extractJsNumberAny(
        source: String,
        keys: List<String>,
    ): String? {
        val quoted = extractJsStringAny(source, keys)
        if (quoted != null) return quoted
        for (key in keys) {
            val escaped = Regex.escape(key)
            val patterns =
                listOf(
                    Regex(""""$escaped"\s*:\s*(-?\d+(?:[,.]\d+)?)"""),
                    Regex("""\b$escaped\b\s*:\s*(-?\d+(?:[,.]\d+)?)"""),
                    Regex("""\b$escaped\b\s*=\s*(-?\d+(?:[,.]\d+)?)"""),
                )
            for (p in patterns) {
                val m = p.find(source) ?: continue
                return m.groupValues[1]
            }
        }
        return null
    }

    // Alguns firmwares exibem RX sem o sinal negativo (ex: "22.7" em vez de "-22.7").
    private fun normalizeRx(rxDbm: Double): Double {
        if (rxDbm > 0 && rxDbm >= 1 && rxDbm <= 60) return -rxDbm
        return rxDbm
    }

    // Converte valor bruto JS (milliwatts) para dBm.
    // Fórmula do modem: floor(log10(mW * 0.00001) * 1000) / 100
    private fun convertJsRxPowerToDbm(raw: String?): Double {
        if (raw.isNullOrEmpty()) return 0.0
        val mw = raw.toDoubleOrNull()?.toInt() ?: return 0.0
        if (mw <= 0) return 0.0
        val converted = floor(log10(mw * 0.00001) * 1000) / 100.0
        if (converted > 0 || converted < -80) return 0.0
        return converted
    }

    private fun convertJsTxPowerToDbm(raw: String?): Double {
        if (raw.isNullOrEmpty()) return 0.0
        val mw = raw.toDoubleOrNull()?.toInt() ?: return 0.0
        if (mw <= 0) return 0.0
        val converted = floor(log10(mw * 0.00001) * 1000) / 100.0
        if (converted > 10 || converted < -40) return 0.0
        return converted
    }
}

/**
 * Normaliza a string crua de `InterfaceType` (`device_cfg`, `lan_status.cgi?wlan`)
 * para [TipoConexaoFisica] — GH#983 Fase 4. Único valor confirmado no field-map
 * (`docs/technical/NOKIA_GPON_FIELD_MAP.md`) é `"Ethernet"`; os demais valores
 * de firmware não estão documentados, então qualquer variante contendo termos de
 * rede sem fio ("wifi"/"wireless"/"wlan") cai em [TipoConexaoFisica.WIFI] e o
 * restante (nulo, vazio ou não reconhecido) cai em [TipoConexaoFisica.DESCONHECIDO].
 * Extraída como função pura top-level (fora do objeto [NokiaModemParser]) para
 * ser testável isoladamente, seguindo o padrão desta sessão.
 */
internal fun normalizarTipoConexaoFisica(tipoConexaoCru: String?): TipoConexaoFisica {
    val valor = tipoConexaoCru?.trim()?.lowercase()
    return when {
        valor.isNullOrEmpty() -> TipoConexaoFisica.DESCONHECIDO
        // Checar termos sem fio antes: "wlan" contem a substring "lan", entao a
        // checagem de Ethernet precisa vir depois pra nao classificar errado.
        valor.contains("wifi") || valor.contains("wireless") || valor.contains("wlan") -> TipoConexaoFisica.WIFI
        valor.contains("ethernet") || valor.contains("wired") -> TipoConexaoFisica.ETHERNET
        else -> TipoConexaoFisica.DESCONHECIDO
    }
}

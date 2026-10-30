package io.signallq.app.core.network.contracts.gateway

/**
 * Uma entrada do catalogo interno de drivers/perfis de equipamento — GH#545.
 *
 * Espelha o modelo de manifesto do NetHAL (`deviceType` ~ [deviceType],
 * `stage`+`capabilities[]` ~ [supportLevel]+[fibraCapable]) para que, quando o
 * SignallQ passar a consumir o manifesto JSON real do NetHAL (fora do escopo
 * desta issue — NetHAL vive em repositorio separado), só o carregamento
 * mude — a logica de [EquipmentClassifier] permanece igual, pois ja opera
 * sobre esta lista de dados, nunca sobre if/else fixo por fabricante.
 */
data class DeviceDriverProfile(
    val driverId: String,
    val vendor: String,
    /** Trechos (case-insensitive) que, se contidos em vendor/model hint, indicam este driver. */
    val modelPatterns: List<String> = emptyList(),
    /** Trechos (case-insensitive) que, se contidos no banner/HTML de identificacao, indicam este driver. */
    val bannerPatterns: List<String> = emptyList(),
    /** Rotas HTTP conhecidas associadas a este driver. */
    val routeSignatures: List<String> = emptyList(),
    /** IPs de gateway canonicos conhecidos para este driver (quando aplicavel). */
    val canonicalGatewayIps: List<String> = emptyList(),
    val deviceType: DeviceType,
    val supportLevel: SupportLevel,
    /** Campos que a pessoa informa na UI administrativa deste firmware. */
    val credentialRequirement: GatewayCredentialRequirement = GatewayCredentialRequirement.USERNAME_AND_PASSWORD,
    /**
     * Declara se este driver expoe capabilities de fibra. So tem efeito real
     * quando [deviceType] == [DeviceType.ONT_GPON] — [EquipmentClassifier]
     * força false para qualquer outro tipo, mesmo que o catalogo diga o
     * contrario (defesa em profundidade do criterio "TP-Link nunca recebe
     * capabilities de fibra").
     */
    val fibraCapable: Boolean = false,
    /**
     * Nome de modelo amigavel para exibicao publica (GH#539) — ex.: "G-1425G-B",
     * "Archer C20". Nulo para entradas genericas/experimentais que nao
     * representam um modelo especifico (a familia inteira e citada, nao um
     * aparelho). Nunca usar [modelPatterns] diretamente como nome de exibicao —
     * sao trechos de match case-insensitive, nao texto pronto para o usuario.
     */
    val displayModel: String? = null,
)

/**
 * Catalogo interno de drivers conhecidos — curado manualmente pelo time
 * SignallQ. Apenas entradas fisicamente validadas (ver
 * `docs/technical/NOKIA_GPON_FIELD_MAP.md` e
 * `docs/technical/TPLINK_ARCHER_ROUTER_FIELD_MAP.md`) usam
 * [SupportLevel.LAB_VALIDATED] — qualquer entrada nova entra como
 * [SupportLevel.PARSER_IMPORTED] ou [SupportLevel.INFERRED_FAMILY] ate
 * validacao fisica (GH#540 valida C20/C6 no SignallQ; GH#539 trata parsers
 * externos como experimentais).
 */
object DeviceDriverCatalog {
    val entries: List<DeviceDriverProfile> =
        listOf(
            // Nokia G-1425G-B — ONT/GPON, validado fisicamente (Fase 2 ja em producao via device_status.cgi).
            DeviceDriverProfile(
                driverId = "nokia-g1425g-b",
                vendor = "Nokia",
                modelPatterns = listOf("g-1425g-b", "g1425g-b", "g-1425g"),
                bannerPatterns = listOf("alcl", "gpon", "alcatel-lucent"),
                routeSignatures = listOf("device_status.cgi", "overview.cgi", "menu.cgi"),
                canonicalGatewayIps = listOf("192.168.1.1"),
                deviceType = DeviceType.ONT_GPON,
                supportLevel = SupportLevel.LAB_VALIDATED,
                fibraCapable = true,
                displayModel = "G-1425G-B",
            ),
            // TP-Link Archer C20 — roteador, validado fisicamente em LAB.
            // routeSignatures fica de fora de proposito: o endpoint "cgi-bin/luci" e
            // compartilhado por toda a familia stok-luci (C20, C6, genericos) e por
            // si so nao distingue modelo — so o modelPatterns (dado de Fase 2, pos-
            // -login) promove uma entrada especifica; rota sozinha cai no generico.
            DeviceDriverProfile(
                driverId = "tplink-archer-c20",
                vendor = "TP-Link",
                modelPatterns = listOf("archer c20", "archerc20"),
                canonicalGatewayIps = listOf("192.168.0.1"),
                deviceType = DeviceType.ROUTER,
                supportLevel = SupportLevel.LAB_VALIDATED,
                fibraCapable = false,
                displayModel = "Archer C20",
            ),
            // TP-Link Archer C6 (tambem se anuncia como "Archer A6 v2" via OneMesh) — roteador, validado fisicamente em LAB.
            DeviceDriverProfile(
                driverId = "tplink-archer-c6",
                vendor = "TP-Link",
                modelPatterns = listOf("archer c6", "archerc6", "archer a6"),
                canonicalGatewayIps = listOf("192.168.0.1"),
                deviceType = DeviceType.ROUTER,
                supportLevel = SupportLevel.LAB_VALIDATED,
                credentialRequirement = GatewayCredentialRequirement.PASSWORD_ONLY,
                fibraCapable = false,
                displayModel = "Archer C6",
            ),
            // Familia stok-luci generica (outros TP-Link/Mercusys) — parser importado do NetHAL,
            // ainda sem validacao fisica do SignallQ. Unica entrada que casa so pela rota
            // generica "cgi-bin/luci" quando o modelo exato ainda nao foi identificado.
            DeviceDriverProfile(
                driverId = "tplink-stok-luci-generic",
                vendor = "TP-Link",
                modelPatterns = listOf("tp-link", "tplink", "mercusys", "archer"),
                routeSignatures = listOf("cgi-bin/luci"),
                deviceType = DeviceType.ROUTER,
                supportLevel = SupportLevel.PARSER_IMPORTED,
                fibraCapable = false,
                displayModel = "Familia stok-luci (roteador TP-Link/Mercusys nao identificado)",
            ),
            // Mesh/extensores conhecidos por nome — sem parser dedicado, so familia inferida.
            DeviceDriverProfile(
                driverId = "mesh-generic",
                vendor = "Generico",
                modelPatterns = listOf("deco", "eero", "orbi", "velop", "aimesh", "nest wifi", "unifi mesh"),
                deviceType = DeviceType.MESH_OR_EXTENDER,
                supportLevel = SupportLevel.INFERRED_FAMILY,
                fibraCapable = false,
                displayModel = "Mesh/extensor (familia inferida)",
            ),
        )
}

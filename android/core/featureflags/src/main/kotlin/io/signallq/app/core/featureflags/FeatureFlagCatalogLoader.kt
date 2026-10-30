package io.signallq.app.core.featureflags

/**
 * Le o catalogo canonico do classpath (`src/main/resources/featureflags/consumer-catalog.json`)
 * -- funciona identico em teste JVM puro, Robolectric e device real, sem depender de
 * `Context`/`AssetManager` (motivo de usar `resources/`, nao `assets/`, ver decisao
 * tecnica em `docs/technical/feature-flags-catalog.md`).
 */
object FeatureFlagCatalogLoader {
    private const val CAMINHO_CATALOGO_CONSUMER = "/featureflags/consumer-catalog.json"

    /** Carrega o catalogo real do Consumer. Lanca [FeatureFlagCatalogParseException]
     *  se o arquivo estiver ausente do classpath ou for invalido -- catalogo ausente
     *  e erro de build, nunca deveria acontecer em runtime. */
    fun loadConsumerCatalog(): FeatureFlagCatalog = FeatureFlagCatalog(parseFromClasspath(CAMINHO_CATALOGO_CONSUMER))

    private fun parseFromClasspath(caminho: String): List<FeatureFlagDefinition> {
        val json =
            javaClass.getResourceAsStream(caminho)?.bufferedReader()?.use { it.readText() }
                ?: throw FeatureFlagCatalogParseException("Catalogo de feature flags nao encontrado no classpath: $caminho")
        return FeatureFlagCatalogParser.parse(json)
    }
}

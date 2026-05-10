package com.nikolaspaci.app.llamallmlocal.data.curated

data class CuratedModel(
    val hfRepoId: String,
    val filename: String,
    val displayName: String,
    val provider: String,
    val paramsBillions: Double,
    val paramsLabel: String,
    val quantization: String,
    val fileSizeBytes: Long,
    val tags: List<String>,
    val notes: String,
    val hasMmproj: Boolean,
    val minAppVersionCode: Long
) {
    val key: String get() = curatedKey(hfRepoId, filename)
}

data class AppVersionCode(val value: Long)

fun curatedKey(repoId: String, filename: String): String = "$repoId|$filename"

data class CuratedFilter(
    val paramsLabels: Set<String> = emptySet(),
    val quantizations: Set<String> = emptySet(),
    val providers: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = paramsLabels.isEmpty() &&
            quantizations.isEmpty() &&
            providers.isEmpty()
}

data class CuratedFacets(
    val paramsLabels: List<String>,
    val quantizations: List<String>,
    val providers: List<String>
) {
    companion object {
        val EMPTY = CuratedFacets(emptyList(), emptyList(), emptyList())
    }
}

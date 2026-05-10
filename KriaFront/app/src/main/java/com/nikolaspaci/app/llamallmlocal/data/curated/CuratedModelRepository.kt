package com.nikolaspaci.app.llamallmlocal.data.curated

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuratedModelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val appVersion: AppVersionCode
) {
    companion object {
        private const val COLLECTION = "curated_models"
    }

    fun observeAll(): Flow<List<CuratedModel>> =
        firestore.collection(COLLECTION)
            .snapshots()
            .map { snap ->
                snap.toCuratedModels().filter { it.minAppVersionCode <= appVersion.value }
            }
            .catch { emit(emptyList()) }

    fun applyFilter(
        all: List<CuratedModel>,
        nameQuery: String,
        filter: CuratedFilter
    ): List<CuratedModel> {
        val query = nameQuery.trim().lowercase()
        if (query.isEmpty() && filter.isEmpty) return all
        return all.filter { model ->
            (query.isEmpty() ||
                model.displayName.lowercase().contains(query) ||
                model.hfRepoId.lowercase().contains(query) ||
                model.filename.lowercase().contains(query)) &&
                (filter.paramsLabels.isEmpty() || model.paramsLabel in filter.paramsLabels) &&
                (filter.quantizations.isEmpty() || model.quantization in filter.quantizations) &&
                (filter.providers.isEmpty() || model.provider in filter.providers)
        }
    }

    fun computeFacets(all: List<CuratedModel>): CuratedFacets {
        if (all.isEmpty()) return CuratedFacets.EMPTY
        return CuratedFacets(
            paramsLabels = all.map { it.paramsLabel }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(paramsLabelComparator(all)),
            quantizations = all.map { it.quantization }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            providers = all.map { it.provider }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        )
    }

    private fun paramsLabelComparator(all: List<CuratedModel>): Comparator<String> {
        val byLabel = all.associateBy({ it.paramsLabel }, { it.paramsBillions })
        return compareBy { byLabel[it] ?: Double.MAX_VALUE }
    }

    private fun QuerySnapshot.toCuratedModels(): List<CuratedModel> =
        documents.mapNotNull { doc ->
            val repoId = doc.getString("hfRepoId") ?: return@mapNotNull null
            val filename = doc.getString("filename") ?: return@mapNotNull null
            val minAppVersionCode = doc.getLong("minAppVersionCode") ?: return@mapNotNull null
            CuratedModel(
                hfRepoId = repoId,
                filename = filename,
                displayName = doc.getString("displayName") ?: repoId,
                provider = doc.getString("provider").orEmpty(),
                paramsBillions = doc.getDouble("paramsBillions") ?: 0.0,
                paramsLabel = doc.getString("paramsLabel").orEmpty(),
                quantization = doc.getString("quantization").orEmpty(),
                fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
                tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                notes = doc.getString("notes").orEmpty(),
                hasMmproj = doc.getBoolean("hasMmproj") ?: false,
                minAppVersionCode = minAppVersionCode
            )
        }
}

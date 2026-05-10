package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.database.Model
import com.nikolaspaci.app.llamallmlocal.data.database.ModelDao

class ModelRepository(private val modelDao: ModelDao) {

    suspend fun insert(model: Model): Long {
        return modelDao.insert(model)
    }

    suspend fun getByFilePath(filePath: String): Model? {
        return modelDao.getByFilePath(filePath)
    }

    suspend fun updatePendingParameterId(modelId: Long, paramId: Long?) {
        modelDao.updatePendingParameterId(modelId, paramId)
    }

    suspend fun updateDefaultParameterId(modelId: Long, paramId: Long?) {
        modelDao.updateDefaultParameterId(modelId, paramId)
    }

    suspend fun deleteByFilePath(filePath: String) {
        modelDao.deleteByFilePath(filePath)
    }
}

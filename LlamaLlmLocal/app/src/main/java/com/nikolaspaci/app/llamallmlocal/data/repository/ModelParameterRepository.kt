package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameterDao

class ModelParameterRepository(private val modelParameterDao: ModelParameterDao) {

    suspend fun getModelParameter(modelId: String): ModelParameter? {
        return modelParameterDao.getModelParameter(modelId)
    }

    suspend fun insert(modelParameter: ModelParameter) {
        modelParameterDao.insert(modelParameter)
    }

    suspend fun update(modelParameter: ModelParameter) {
        modelParameterDao.update(modelParameter)
    }
}

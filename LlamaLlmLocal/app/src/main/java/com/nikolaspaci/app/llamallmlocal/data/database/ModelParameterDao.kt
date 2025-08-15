package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ModelParameterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(modelParameter: ModelParameter)

    @Update
    suspend fun update(modelParameter: ModelParameter)

    @Query("SELECT * FROM model_parameters WHERE modelId = :modelId")
    suspend fun getModelParameter(modelId: String): ModelParameter?
}

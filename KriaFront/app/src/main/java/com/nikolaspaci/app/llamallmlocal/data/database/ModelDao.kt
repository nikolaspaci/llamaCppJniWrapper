package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(model: Model): Long

    @Query("SELECT * FROM models WHERE filePath = :filePath")
    suspend fun getByFilePath(filePath: String): Model?

    @Query("UPDATE models SET pendingParameterId = :paramId WHERE id = :modelId")
    suspend fun updatePendingParameterId(modelId: Long, paramId: Long?)

    @Query("UPDATE models SET defaultParameterId = :paramId WHERE id = :modelId")
    suspend fun updateDefaultParameterId(modelId: Long, paramId: Long?)

    @Query("DELETE FROM models WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)
}

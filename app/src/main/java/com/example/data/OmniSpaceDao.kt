package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OmniSpaceDao {
    @Query("SELECT * FROM cloned_apps ORDER BY originalAppName ASC, cloneNumber ASC")
    fun getAllClonedApps(): Flow<List<ClonedAppEntity>>

    @Query("SELECT * FROM cloned_apps WHERE originalPackageName = :pkg ORDER BY cloneNumber ASC")
    suspend fun getClonesForPackage(pkg: String): List<ClonedAppEntity>

    @Query("SELECT MAX(cloneNumber) FROM cloned_apps WHERE originalPackageName = :pkg")
    suspend fun getMaxCloneNumberForPackage(pkg: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClonedApp(app: ClonedAppEntity): Long

    @Update
    suspend fun updateClonedApp(app: ClonedAppEntity)

    @Query("DELETE FROM cloned_apps WHERE id = :id")
    suspend fun deleteClonedAppById(id: Long)

    @Query("SELECT * FROM space_categories ORDER BY createdAt ASC")
    fun getAllSpaces(): Flow<List<SpaceCategoryEntity>>

    @Query("SELECT COUNT(*) FROM space_categories")
    suspend fun getSpaceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: SpaceCategoryEntity)

    @Query("DELETE FROM space_categories WHERE spaceId = :spaceId")
    suspend fun deleteSpace(spaceId: String)
}

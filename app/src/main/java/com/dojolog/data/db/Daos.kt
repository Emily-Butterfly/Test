package com.dojolog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<SessionWithEntries>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithEntries?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithEntries?

    @Query(
        "SELECT discipline FROM sessions WHERE TRIM(discipline) != '' " +
            "GROUP BY discipline ORDER BY MAX(date) DESC, MAX(createdAt) DESC LIMIT 8",
    )
    fun observeRecentDisciplines(): Flow<List<String>>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEntries(entries: List<SessionTechniqueEntity>)

    @Query("DELETE FROM session_techniques WHERE sessionId = :sessionId")
    suspend fun deleteEntries(sessionId: Long)
}

@Dao
interface TechniqueDao {
    @Query("SELECT * FROM techniques ORDER BY name")
    fun observeAll(): Flow<List<TechniqueEntity>>

    @Query("SELECT * FROM techniques WHERE id = :id")
    fun observe(id: Long): Flow<TechniqueEntity?>

    /** Case-insensitive thanks to the NOCASE collation on [TechniqueEntity.name]. */
    @Query("SELECT * FROM techniques WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TechniqueEntity?

    @Insert
    suspend fun insert(technique: TechniqueEntity): Long

    @Query("UPDATE techniques SET name = :name, category = :category, notes = :notes WHERE id = :id")
    suspend fun update(id: Long, name: String, category: String, notes: String)

    @Query("DELETE FROM techniques WHERE id = :id")
    suspend fun delete(id: Long)
}

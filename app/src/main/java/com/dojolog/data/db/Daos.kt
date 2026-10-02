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

    @Insert
    suspend fun insertMatchups(matchups: List<SessionMatchupEntity>)

    @Query("DELETE FROM session_matchups WHERE sessionId = :sessionId")
    suspend fun deleteMatchups(sessionId: Long)

    /** Every session, oldest first, for a backup. */
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date, createdAt, id")
    suspend fun getAll(): List<SessionWithEntries>

    @Query("SELECT createdAt FROM sessions")
    suspend fun getAllCreatedAt(): List<Long>

    /** Also removes every technique entry and matchup (foreign keys cascade). */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
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

    @Query("SELECT * FROM techniques ORDER BY id")
    suspend fun getAll(): List<TechniqueEntity>

    @Query("DELETE FROM techniques")
    suspend fun deleteAll()
}

@Dao
interface OpponentDao {
    @Query("SELECT * FROM opponents ORDER BY name")
    fun observeAll(): Flow<List<OpponentEntity>>

    @Query("SELECT * FROM opponents WHERE id = :id")
    fun observe(id: Long): Flow<OpponentEntity?>

    @Query("SELECT * FROM opponents WHERE id = :id")
    suspend fun get(id: Long): OpponentEntity?

    /** Case-insensitive thanks to the NOCASE collation on [OpponentEntity.name]. */
    @Query("SELECT * FROM opponents WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): OpponentEntity?

    @Insert
    suspend fun insert(opponent: OpponentEntity): Long

    @Query(
        "UPDATE opponents SET name = :name, club = :club, grade = :grade, weight = :weight, notes = :notes " +
            "WHERE id = :id",
    )
    suspend fun update(id: Long, name: String, club: String, grade: String, weight: String, notes: String)

    @Query("DELETE FROM opponents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM opponents ORDER BY id")
    suspend fun getAll(): List<OpponentEntity>

    @Query("DELETE FROM opponents")
    suspend fun deleteAll()
}

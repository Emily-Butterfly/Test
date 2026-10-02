package com.dojolog.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.dojolog.domain.Ratings
import java.time.LocalDate

@Entity(tableName = "sessions", indices = [Index("date")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val durationMinutes: Int,
    val discipline: String,
    /** [com.dojolog.domain.SessionType.key] */
    val sessionType: String,
    val notes: String,
    val overallRating: Float,
    val overallAuto: Boolean,
    @Embedded(prefix = "rating_") val ratings: Ratings,
    val createdAt: Long,
)

@Entity(tableName = "techniques", indices = [Index(value = ["name"], unique = true)])
data class TechniqueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** [com.dojolog.domain.TechniqueCategory.key] */
    val category: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "session_techniques",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TechniqueEntity::class,
            parentColumns = ["id"],
            childColumns = ["techniqueId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("techniqueId")],
)
data class SessionTechniqueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val techniqueId: Long,
    val reps: Int,
    val quality: Int,
    val notes: String,
    /** Order within the session, as entered. */
    val position: Int,
)

@Entity(tableName = "opponents", indices = [Index(value = ["name"], unique = true)])
data class OpponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val club: String,
    val grade: String,
    val weight: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "session_matchups",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OpponentEntity::class,
            parentColumns = ["id"],
            childColumns = ["opponentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("opponentId")],
)
data class SessionMatchupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val opponentId: Long,
    /** [com.dojolog.domain.MatchResult.key] */
    val result: String,
    val rating: Int,
    val notes: String,
    /** Order within the session, as entered. */
    val position: Int,
)

data class EntryWithTechnique(
    @Embedded val entry: SessionTechniqueEntity,
    @Relation(parentColumn = "techniqueId", entityColumn = "id")
    val technique: TechniqueEntity,
)

data class MatchupWithOpponent(
    @Embedded val matchup: SessionMatchupEntity,
    @Relation(parentColumn = "opponentId", entityColumn = "id")
    val opponent: OpponentEntity,
)

data class SessionWithEntries(
    @Embedded val session: SessionEntity,
    @Relation(entity = SessionTechniqueEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val entries: List<EntryWithTechnique>,
    @Relation(entity = SessionMatchupEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val matchups: List<MatchupWithOpponent>,
)

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}

package com.moleculesoft.dcs.data.local

import androidx.room.*
import com.moleculesoft.dcs.data.SensorData
import com.moleculesoft.dcs.data.UrbanReport
import com.moleculesoft.dcs.data.UserStats

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensorData(data: SensorData)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentSensorData(): List<SensorData>

    @Query("DELETE FROM sensor_data")
    suspend fun clearAll()
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: UrbanReport)

    @Query("SELECT * FROM urban_reports ORDER BY timestamp DESC")
    suspend fun getAllReports(): List<UrbanReport>
}

@Database(entities = [SensorData::class, UrbanReport::class, UserStats::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao
    abstract fun reportDao(): ReportDao
}

class Converters {
    @TypeConverter
    fun fromInstant(value: kotlinx.datetime.Instant): String = value.toString()
    @TypeConverter
    fun toInstant(value: String): kotlinx.datetime.Instant = kotlinx.datetime.Instant.parse(value)
}

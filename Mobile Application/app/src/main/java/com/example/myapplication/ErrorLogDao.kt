package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ErrorLogDao {

    @Insert
    suspend fun insertError(errorLog: ErrorLog)

    // Query to fetch all errors ordered by the timestamp (most recent first)
    @Query("SELECT * FROM error_log ORDER BY timestamp DESC")
    fun getAllErrors(): LiveData<List<ErrorLog>>

    // Query to fetch errors ordered by severity (highest severity first)
    @Query("SELECT * FROM error_log ORDER BY severity DESC, timestamp DESC")
    fun getErrorsBySeverity(): LiveData<List<ErrorLog>>

    // Query to fetch errors of a specific severity
    @Query("SELECT * FROM error_log WHERE severity = :severityLevel ORDER BY timestamp DESC")
    fun getErrorsBySpecificSeverity(severityLevel: Int): LiveData<List<ErrorLog>>

    // New query to delete an error log by its ID
    @Query("DELETE FROM error_log WHERE id = :errorId")
    suspend fun deleteErrorById(errorId: Int)
}




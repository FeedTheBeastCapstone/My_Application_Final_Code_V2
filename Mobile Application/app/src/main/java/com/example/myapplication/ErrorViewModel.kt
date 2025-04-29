package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Date

// ViewModel for managing error logs in the app
class ErrorViewModel(application: Application) : AndroidViewModel(application) {

    // DAO for interacting with the ErrorLog table in the database
    private val errorLogDao: ErrorLogDao = ErrorDatabase.getInstance(application).errorLogDao()

    // LiveData object that observes all error logs in the database
    val allErrors: LiveData<List<ErrorLog>> = errorLogDao.getAllErrors()

    // Logs a new error by inserting it into the database
    // Creates an ErrorLog object with the provided type, message, severity, and a timestamp
    fun logError(errorType: String, errorMessage: String, severity: Int) {
        val errorLog = ErrorLog(
            errorType = errorType,
            errorMessage = errorMessage,
            severity = severity,
            timestamp = Date()
        )
        viewModelScope.launch {
            errorLogDao.insertError(errorLog)
        }
    }

    // Removes an error log from the database using its unique ID
    fun removeErrorById(errorId: Int) {
        viewModelScope.launch {
            errorLogDao.deleteErrorById(errorId)
        }
    }
}




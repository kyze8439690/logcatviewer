package com.github.logviewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportLogFileUtils {

    suspend fun exportLogs(cacheDir: File?, logs: Array<LogItem>?): File? = withContext(Dispatchers.IO) {
        if (cacheDir == null || cacheDir.isFile() || logs.isNullOrEmpty()) {
            null
        } else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val logFile = File(cacheDir, dateFormat.format(Date()) + ".log")
            if (logFile.exists() && !logFile.delete()) {
                null
            } else {
                try {
                    val writer = BufferedWriter(
                        OutputStreamWriter(
                            FileOutputStream(logFile)
                        )
                    )
                    for (log in logs) {
                        writer.write(log.origin + "\n")
                    }
                    writer.close()
                    logFile
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }
}
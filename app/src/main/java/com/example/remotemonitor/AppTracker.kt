package com.example.remotemonitor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

class AppTracker(private val context: Context) {

    fun getCurrentApp(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
        
        if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            return sortedStats[0].packageName
        }
        
        return "Unknown"
    }
}

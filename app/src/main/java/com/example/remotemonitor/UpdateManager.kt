package com.example.remotemonitor

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    fun checkForUpdates() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val serverUrl = context.getString(R.string.signaling_server_url)
                    val url = URL("$serverUrl/version.json")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        JSONObject(response)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (result != null) {
                val latestVersionCode = result.getInt("versionCode")
                val latestVersionName = result.getString("versionName")
                val apkUrl = result.getString("apkUrl")
                val releaseNotes = result.optString("releaseNotes", "New version available.")

                val currentVersionCode = try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode
                    }
                } catch (_: Exception) {
                    0
                }

                if (latestVersionCode > currentVersionCode) {
                    showUpdateDialog(latestVersionName, apkUrl, releaseNotes)
                } else {
                    Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(versionName: String, apkUrl: String, releaseNotes: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available (v$versionName)")
            .setMessage(releaseNotes)
            .setPositiveButton("Update") { _, _ ->
                downloadAndInstall(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
        val request = DownloadManager.Request(apkUrl.toUri())
            .setTitle("RemoteMonitor Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RemoteMonitor_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    installApk()
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        
        Toast.makeText(context, "Update download started...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk() {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RemoteMonitor_Update.apk")
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Update file not found", Toast.LENGTH_SHORT).show()
        }
    }
}

package io.github.teamomuito.colony

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import io.github.teamomuito.colony.sim.UpdatePolicy
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Checks GitHub for a newer build, downloads it, and hands it to Android to install. Saves are kept. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/teamomuito/rimworld/releases/latest"

    class Release(val tag: String, val apkUrl: String, val sha256: String?)

    /** Blocking; call from a background thread. Throws when GitHub can't be reached or the release has no APK. */
    fun fetchLatest(): Release {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name") == "colony.apk" }
        val digest = apk.optString("digest", "").removePrefix("sha256:").ifEmpty { null }
        return Release(json.getString("tag_name"), apk.getString("browser_download_url"), digest)
    }

    /** The build number installed on this device. */
    fun installedBuild(ctx: Context): Int = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()

    fun isNewer(ctx: Context, r: Release): Boolean = UpdatePolicy.isNewer(r.tag, installedBuild(ctx))

    /** Blocking; call from a background thread. Returns the downloaded file, which has passed its checksum. */
    fun download(ctx: Context, r: Release): File {
        val file = File(ctx.cacheDir, "update/colony.apk")
        file.parentFile?.mkdirs()
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        val expected = r.sha256
        if (expected != null && sha256(file) != expected) {
            file.delete()
            throw IllegalStateException("the download did not match its checksum")
        }
        return file
    }

    /** Starts Android's installer with the APK. The player confirms there; the result comes back to [InstallResult]. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { session ->
            session.openWrite("colony.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = Intent(ctx, InstallResult::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, id, result, flags).intentSender)
        }
    }

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
}

/** Receives the installer's result. Shows the confirmation screen when Android asks for one. */
class InstallResult : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // the app restarts itself as the new build
            else -> Toast.makeText(ctx, "The update was not installed. Allow installs from this app in Android settings, then try again.", Toast.LENGTH_LONG).show()
        }
    }
}

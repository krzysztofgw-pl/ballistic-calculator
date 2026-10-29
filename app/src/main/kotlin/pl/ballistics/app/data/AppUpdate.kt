package pl.ballistics.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Updates from the repository's releases: CI tags each build of main `build-N` and gives it versionCode N + 100. */
object AppUpdate {
    private const val LATEST = "https://api.github.com/repos/krzysztofgw-pl/ballistics-calculator/releases/latest"
    private const val ASSET = "ballistics.apk"
    private const val CODE_OFFSET = 100

    data class Release(val build: Int, val notes: String, val apkUrl: String) {
        val versionCode get() = build.toLong() + CODE_OFFSET
    }

    fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** Builds made outside CI have no release number to compare, so they never offer updates on their own. */
    fun isCiBuild(context: Context) = installedVersionCode(context) > CODE_OFFSET

    /** The latest release when it is newer than the installed app, otherwise null. */
    suspend fun newer(context: Context): Release? {
        val release = latest()
        return release.takeIf { it.versionCode > installedVersionCode(context) }
    }

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val connection = URL(LATEST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun parse(text: String): Release {
        val json = Json.parseToJsonElement(text).jsonObject
        val tag = json["tag_name"]?.jsonPrimitive?.content.orEmpty()
        val build = tag.removePrefix("build-").toIntOrNull() ?: throw IOException("Unexpected release tag $tag")
        val url = json["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content == ASSET }
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: throw IOException("Release $tag has no $ASSET")
        return Release(build, json["body"]?.jsonPrimitive?.content.orEmpty(), url)
    }

    /** Android asks once to let this app install others; until then [install] opens that setting instead. */
    fun canInstall(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    suspend fun download(context: Context, release: Release): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/$ASSET").apply { parentFile?.mkdirs() }
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 60_000
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        file
    }

    /** Hands the APK to the system installer, which asks the user to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

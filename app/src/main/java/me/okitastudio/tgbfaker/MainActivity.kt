package me.okitastudio.tgbfaker

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

@SuppressLint("SetTextI18n", "SdCardPath")
class MainActivity : AppCompatActivity() {

    private val uriOfPUBG = "com.tencent.ig"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (File("/system/bin/genyd.bak").exists().not()) {
            AlertDialog.Builder(this)
                .setTitle("Geny Daemon detected!")
                .setMessage("Patch it ? (original genydaemon will be at /system/bin/genyd.bak)")
                .setPositiveButton("Yes") {_,_-> patchGenyDaemon("/system/bin/genyd") }
                .create().show()
        }

        tv_logs.text = "Logs :"
        tv_credits.text = "Version : ${BuildConfig.VERSION_NAME}\n" +
                "Based on Ciceron's methods\n" +
                "App and Method Improvements by Okitavera"
        btn_apply.text = "Apply Patch and start PUBG"

        btn_apply.setOnClickListener {
            Thread(Runnable {
                runOnUiThread {
                    tv_logs.text = "Logs :"
                    tv_logs.text = tv_logs.text.toString().plus("\nCleanup Tencent tmps data")
                }

                su("rm -rf /data/data/$uriOfPUBG/files/tss_*")

                runOnUiThread {
                    tv_logs.text = tv_logs.text.toString().plus("\nPatching complete.")
                    btn_apply.visibility = View.VISIBLE
                    launchApp(uriOfPUBG)
                }
            }).start()
        }
    }

    override fun onResume() {
        super.onResume()
        intent?.action.equals("ACT_REPATCH").run {
            if (this) reloadPatch()
        }
    }

    private fun patchGenyDaemon(file: String) {
        su("cp $file $file.bak")
        File(file).printWriter().use { it.println("#DUMMY FILE") }
        su("chmod 755 $file")

        AlertDialog.Builder(this)
            .setTitle("Geny daemon patched!")
            .setMessage("You need to restart TGB to start using this app")
            .setPositiveButton("Yes") {_,_-> finishAffinity() }
            .create().show()
    }

    private fun reloadPatch() {
        val pid = pidOf(uriOfPUBG)
        su("rm -rf /data/data/$uriOfPUBG/files/tss_*")
        su("kill -9 $pid")
        launchApp(uriOfPUBG)
    }

    private fun launchApp(app: String) {
        if (serviceRunning(NotifierService::class.java))
            stopService(Intent(this, NotifierService::class.java))
        val launchIntent = packageManager.getLaunchIntentForPackage(app)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(launchIntent)
        Thread.sleep(1000)
        val serviceIntent = Intent(this, NotifierService::class.java)
        serviceIntent.putExtra("pid", pidOf(uriOfPUBG))
        startService(serviceIntent)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun serviceRunning(serviceClass: Class<*>): Boolean =
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }


    private fun su(a: String): Process =
            Runtime.getRuntime().exec(arrayOf("su", "-c", a))

    private fun pidOf(name: String) =
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps"))
                    .inputStream.bufferedReader().readLines()
                    .lastOrNull{ it.contains(Regex.escape(name).plus("$").toRegex()) }
                    ?.replace("^\\w+\\s+(\\d+)\\s.*$".toRegex(),"$1") ?: "N/A"
}

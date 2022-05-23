package com.example.phonelink

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.content.Intent
import android.net.Uri
import android.os.*
import android.widget.Toast
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread


class SyncServer : Service() {
    private var mClipboardManager: ClipboardManager? = null
    var link:ServerLink = ServerLink()
    val mHandler: Handler = Handler()
    var syncSign:Boolean = false

    var serverSign = false

    var textSyncHign:String = ""
    lateinit var wakeLock:PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        mClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        mClipboardManager!!.addPrimaryClipChangedListener(
            mOnPrimaryClipChangedListener
        )
        var string:String

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SyncServer::class.java.getName())
        wakeLock.acquire()

        thread {
            try{
                var string = link.serverInit("106.75.62.81",2564,"Phone")
                if(string != "OK")
                {
                    mHandler.post{
                        this.message("服务器link错误,错误信息:" + string)
                    }
                }
                serverSign = true
                ServerSync()
            }
            catch (e: Exception) {
                println(e.message)
            }
        }
        thread {
            for (i in 0..10)
            {
                if (serverSign == true)
                {
                    break
                }
                Thread.sleep(200)
            }
            if (link != null)
            {
                message(link.GetTemporaryData())
            }
        }
        Toast.makeText(this, "同步后台服务 started running..", Toast.LENGTH_SHORT).show()

    }

    fun message(string: String)
    {
        try {
            Toast.makeText(this, string, Toast.LENGTH_SHORT).show()
        } catch (e: java.lang.Exception) {
            //解决在子线程中调用Toast的异常情况处理
            Looper.prepare()
            Toast.makeText(this, string, Toast.LENGTH_SHORT).show()
            Looper.loop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mClipboardManager != null) {
            mClipboardManager!!.removePrimaryClipChangedListener(
                mOnPrimaryClipChangedListener
            )
        }
        wakeLock.release()
    }


    fun setTextSync(string: String)
    {
        var clipData:ClipData = ClipData.newPlainText("text",string)
        mClipboardManager!!.setPrimaryClip(clipData)
    }

    fun ServerSync()
    {
        var filePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/PhoneLink/"
        if(!File(filePath).exists())
        {
            File(filePath).mkdir()
        }
        while (link != null) {
            when (link.dataOut.readString()) {
                "textSync" -> {
                    syncSign = true
                    link.dataOut.sendString("OK")
                    setTextSync(link.dataOut.readString()!!)
                }
                "SyncImage" -> {
                    link.dataOut.sendString("OK")
                    var fileName = link.dataOut.readString()
                    link.dataOut.sendString("OK")
                    var imageData = link.dataOut.read()
                    if (imageData != null) {
                        var imagefilpath = File(filePath, fileName!!.replace(":", "_"))
                        imagefilpath.appendBytes(imageData)
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imagefilpath)))
                    }
                }
                else -> {
                    link.dataOut.sendString("无此服务")
                }
            }
        }
    }
    private val mOnPrimaryClipChangedListener = OnPrimaryClipChangedListener {
        val charSequence = mClipboardManager!!.primaryClip!!.getItemAt(0).text.toString()
        if (syncSign == true)
        {
            syncSign = false
            return@OnPrimaryClipChangedListener
        }
        if(textSyncHign == charSequence)
        {
            return@OnPrimaryClipChangedListener
        }
        textSyncHign = charSequence
        thread {
            var string = link.textSync(charSequence)
            mHandler.post {
                this.message("同步状态:$string")
            }
        }
        Toast.makeText(this, "接收到粘贴板:$charSequence", Toast.LENGTH_SHORT).show()
    }
    private val binder = MsgBinder()

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class MsgBinder : Binder() {
        fun  getService():SyncServer{
            return this@SyncServer
        }
    }

    public fun ServerPrint()
    {
        println("hello Service")
    }

    public fun ImageSync(name:String,imageUri: Uri)
    {
        if (link == null)
        {
            return
        }
        val myDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        var fileStream = getContentResolver().openInputStream(imageUri)
        message(link.SetImageSync(myDateTimeFormatter.format(LocalDateTime.now()) + "."+name,fileStream!!)!!)
    }
}
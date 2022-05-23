package com.example.phonelink

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.URI
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity(){

    var msgService: SyncServer? = null

    var fileUri:Uri? = null
    var fileType:String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val action = intent.action
        val type = intent.type


        if (Intent.ACTION_SEND == action && type != null) {
            val uri: Uri? = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if ("image" == type.substringBefore("/")) {
                fileUri = uri
                fileType = type.substringAfterLast("/")
                val intent = Intent(this,SyncServer::class.java)
                if (msgService == null)
                {
                    bindService(intent, conn, Context.BIND_AUTO_CREATE)
                }
            }
        }
    }

    override fun onStop() {
        if (msgService != null)
        {
            unbindService(conn)
        }
        super.onStop()
    }

    fun textButton(view: View)
    {
        setTextSync()
    }

    fun setupListener() {
        val clipboardMgr = this.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardMgr.addPrimaryClipChangedListener {
            val contents = clipboardMgr.text.toString()
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "textSync " + contents,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun setTextSync()
    {
        startService(Intent(this, SyncServer::class.java))
    }

    fun startButton(view: View)
    {
        startService(Intent(this, SyncServer::class.java))

        /*
        var reader = BufferedReader(InputStreamReader(socketData.getInputStream()))
        var writer = PrintWriter(socketData.getOutputStream())
        writer.println("hello PC");

         */
    }


    var conn: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {}
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            //返回一个MsgService对象
            msgService = (service as SyncServer.MsgBinder).getService()
            msgService?.ServerPrint()
            thread {
                msgService?.ImageSync(fileType!!,fileUri!!)
            }
        }
    }
}
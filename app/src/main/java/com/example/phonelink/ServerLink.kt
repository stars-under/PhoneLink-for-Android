package com.example.phonelink

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.experimental.inv

fun Int.to32ByteArray() : ByteArray = byteArrayOf(toByte(), shr(8).toByte(),
    shr(8 * 2).toByte(),shr(8 * 3).toByte())

fun ByteArray.to32Int(): Int =
    (this[3].toInt() and 0xff shl 24) or (this[2].toInt() and 0xff shl 16) or (this[1].toInt() and 0xff shl 8) or (this[0].toInt() and 0xff)

fun byteToInt(bytes: ByteArray): Int {
    var result = 0
    for (i in bytes.indices) {
        result = result or (bytes[i].toInt() shl 8 * i)
    }
    return result
}

class SocketStream(host: String,port: Int,key: Int,direction:String)
{
    lateinit var socket: Socket
    lateinit var dataOut: OutputStream
    lateinit var dataIn: InputStream
    init {
        socket = Socket(host,port)
        dataOut = socket.getOutputStream()
        dataIn = socket.getInputStream()
        sendKey(key,direction)
    }

    fun readString(): String?
    {
        return read()?.let { String(it,0,it.size-1) }
    }

    fun sendString(string: String)
    {
        var byte:ByteArray = string.toByteArray()
        byte += 0.toByte()
        send(byte,byte.size)
    }

    fun sendKey(key: Int,direction:String):String
    {
        var headKey = key.to32ByteArray()
        var headCrc = key.inv().to32ByteArray()
        var headDirection:Int
        if (direction == "IN")
        {
            headDirection = 0;
        }else{
            headDirection = 1;
        }
        dataOut.write(headKey)
        dataOut.write(headCrc)
        dataOut.write(headDirection.to32ByteArray())
        dataOut.flush()
        return readString().toString()
    }

    fun send(byteArray: ByteArray,len:Int)
    {
        var headLen = len.to32ByteArray()
        var headCrc = len.inv().to32ByteArray()
        dataOut.write(headLen)
        dataOut.write(headCrc)
        dataOut.write(byteArray,0,len)
        dataOut.flush()
    }

    fun read(): ByteArray?
    {
        var byteBuff:ByteArray = ByteArray(4)
        dataIn.read(byteBuff,0,4)

        var headLen = byteBuff.to32Int()
        dataIn.read(byteBuff,0,4)
        for (i in 0..3)
        {
            byteBuff[i] = byteBuff[i].inv()
        }
        var headCrc = byteBuff.to32Int()
        if (headLen > 0x1000000){
            return null
        }
        if (headLen != headCrc){
            return null
        }
        try{
            byteBuff = ByteArray(headLen)
            var len = dataIn.read(byteBuff,0,headLen)
            var byteOff = 0
            while (len != headLen)
            {
                println("LEN {$len}")
                byteOff += len
                headLen -= len
                len = dataIn.read(byteBuff,byteOff,headLen)
                if (len == -1)
                {
                    return null
                }
            }
            println(headLen)
            println(len)
            println(byteOff)
            println(headCrc)
        }
        catch (e: Exception) {
            println(e.message)
            return null
        }
        return byteBuff
    }
}

class ServerLink{

    lateinit var dataIn:SocketStream
    lateinit var dataOut:SocketStream

    fun serverInit(host:String,port:Int,deviceName:String):String
    {
        dataIn = SocketStream(host,port,1,"IN")
        dataIn.sendString(deviceName)
        var string:String = String()
        string = dataIn.readString().toString()
        if (string != "OK"){
            return string
        }
        dataOut = SocketStream(host,port,1,"OUT")
        dataOut.sendString(deviceName)
        string = dataOut.readString().toString()
        if (string != "OK"){
            return string
        }
        return "OK"
    }
    
    fun textSync(Data: String): String
    {
        dataIn.sendString("textSync")
        var string: String = String()
        string = dataIn.readString().toString()
        if (string != "OK")
        {
            return string
        }
        dataIn.sendString(Data)
        string = dataIn.readString().toString()
        if (string != "OK")
        {
            return string
        }
        var deviceNum:Int = dataIn.read()!!.to32Int()
        return  "同步给了" + deviceNum.toString() + "台设备"
    }

    fun  GetTemporaryData():String
    {
        dataIn.sendString("GetTemporaryData")
        return dataIn.readString().toString()
    }

    fun SetImageSync(fileName:String,fileByte: InputStream): String?
    {
        dataIn.sendString("SyncImage")
        var str = dataIn.readString()
        if (str != "OK")
        {
            return str
        }
        dataIn.sendString(fileName)
        str = dataIn.readString()
        if (str != "OK")
        {
            return str
        }
        var fileByte = fileByte.readBytes()
        dataIn.send(fileByte,fileByte.size)
        str = dataIn.readString()
        if (str != "OK")
        {
            return str
        }

        var deviceNum:Int = dataIn.read()!!.to32Int()
        return  "同步给了" + deviceNum.toString() + "台设备"
    }
}
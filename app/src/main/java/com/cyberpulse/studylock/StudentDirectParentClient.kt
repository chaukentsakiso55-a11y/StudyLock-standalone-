package com.cyberpulse.studylock

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class StudentDirectParentClient(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPairingResult(success: Boolean, message: String)
        fun onCommand(command: JSONObject)
        fun onConnectionChanged(connected: Boolean, message: String)
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeLock = Any()

    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var connected = false
    @Volatile private var activeCode = ""
    @Volatile private var sessionToken = ""
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun pair(code: String) {
        val normalized = code.filter(Char::isDigit).take(6)
        if (!normalized.matches(Regex("\\d{6}"))) {
            postPairing(false, "Enter the 6-digit code from StudyLock Parent.")
            return
        }
        disconnect(clearSaved = false)
        activeCode = normalized
        saveCode(normalized)
        acquireMulticastLock()
        startDiscovery(normalized)
    }

    fun reconnectSaved() {
        val code = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, "")
            .orEmpty()
        if (code.matches(Regex("\\d{6}"))) pair(code)
    }

    fun sendState(rawState: String): Boolean {
        val output = writer ?: return false
        val state = runCatching { JSONObject(rawState) }.getOrElse { JSONObject() }
        val message = JSONObject()
            .put("type", "state")
            .put("token", sessionToken)
            .put("state", state)
        return write(output, message)
    }

    fun stateJson(): String = JSONObject()
        .put("connected", connected)
        .put("pairingCode", activeCode)
        .put("direct", true)
        .toString()

    fun disconnect(clearSaved: Boolean = true) {
        stopDiscovery()
        connected = false
        sessionToken = ""
        writer = null
        runCatching { socket?.close() }
        socket = null
        if (clearSaved) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CODE)
                .apply()
            activeCode = ""
        }
        releaseMulticastLock()
    }

    fun close() {
        disconnect(clearSaved = false)
        executor.shutdownNow()
    }

    private fun startDiscovery(code: String) {
        postConnection(false, "Searching for StudyLock Parent on this Wi-Fi…")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val expected = ParentService.SERVICE_PREFIX + code
                if (!serviceInfo.serviceName.equals(expected, ignoreCase = true)) return
                stopDiscovery()
                resolveAndConnect(serviceInfo, code)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (connected && serviceInfo.serviceName.endsWith(code)) {
                    postConnection(false, "StudyLock Parent left the local network.")
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
                postPairing(false, "Direct pairing could not start ($errorCode). Cloud pairing can still be used.")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(ParentService.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            stopDiscovery()
            postPairing(false, "Direct pairing is unavailable on this network.")
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveAndConnect(info: NsdServiceInfo, code: String) {
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                postPairing(false, "Found StudyLock Parent but could not resolve it ($errorCode).")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host ?: run {
                    postPairing(false, "StudyLock Parent did not provide a local address.")
                    return
                }
                executor.execute { connectSocket(host.hostAddress.orEmpty(), serviceInfo.port, code) }
            }
        })
    }

    private fun connectSocket(host: String, port: Int, code: String) {
        if (host.isBlank() || port <= 0) {
            postPairing(false, "StudyLock Parent local address was invalid.")
            return
        }
        try {
            val newSocket = Socket()
            newSocket.tcpNoDelay = true
            newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            val output = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
            val input = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
            socket = newSocket
            writer = output
            write(
                output,
                JSONObject()
                    .put("type", "hello")
                    .put("code", code)
                    .put("device", "StudyLock Student")
            )

            while (!newSocket.isClosed) {
                val line = input.readLine() ?: break
                val message = runCatching { JSONObject(line) }.getOrNull() ?: continue
                when (message.optString("type")) {
                    "ack" -> {
                        sessionToken = message.optString("token")
                        connected = true
                        postPairing(true, message.optString("message", "Connected directly to StudyLock Parent ✓"))
                        postConnection(true, "Direct Parent sync active")
                    }
                    "cmd" -> {
                        if (!connected) continue
                        ParentCommandStore.enqueue(appContext, message)
                        mainHandler.post { listener.onCommand(message) }
                    }
                    "error" -> postPairing(false, message.optString("message", "Pairing rejected"))
                }
            }
        } catch (error: Throwable) {
            postPairing(false, "Direct connection failed: ${error.localizedMessage ?: "network error"}")
        } finally {
            val wasConnected = connected
            connected = false
            writer = null
            sessionToken = ""
            runCatching { socket?.close() }
            socket = null
            if (wasConnected) postConnection(false, "Direct Parent connection lost; cloud fallback can continue.")
        }
    }

    private fun write(output: BufferedWriter, message: JSONObject): Boolean = runCatching {
        synchronized(writeLock) {
            output.write(message.toString())
            output.newLine()
            output.flush()
        }
        true
    }.getOrDefault(false)

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    private fun saveCode(code: String) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, code)
            .apply()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("StudyLockStudentDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun postPairing(success: Boolean, message: String) {
        mainHandler.post { listener.onPairingResult(success, message) }
    }

    private fun postConnection(isConnected: Boolean, message: String) {
        mainHandler.post { listener.onConnectionChanged(isConnected, message) }
    }

    private object ParentService {
        const val SERVICE_TYPE = "_studylock._tcp."
        const val SERVICE_PREFIX = "StudyLockParent-"
    }

    companion object {
        private const val PREFS = "studylock_direct_parent"
        private const val KEY_CODE = "pairing_code"
        private const val CONNECT_TIMEOUT_MS = 8_000
    }
}

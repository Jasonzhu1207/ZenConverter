package org.zenconverter.app.system

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A tiny read-only HTTP server that shares converted files with a PC on the same
 * Wi-Fi network. It binds to the LAN address only, serves a simple index, and
 * never uploads anything anywhere. This is deliberately minimal: a handful of
 * routes, no directory traversal, no writes.
 */
class LocalTransferServer(
    private val context: Context,
    private val filesProvider: () -> List<SharedFile>
) {
    data class SharedFile(val displayName: String, val file: File)

    data class Status(val running: Boolean, val address: String?)

    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    fun lanAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    suspend fun start(port: Int = DEFAULT_PORT): Status = withContext(Dispatchers.IO) {
        if (running.get()) return@withContext Status(true, urlFor(port))
        val socket = ServerSocket(port)
        serverSocket = socket
        running.set(true)
        thread = Thread {
            while (running.get()) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching { handle(client) }
            }
        }.also { it.isDaemon = true; it.start() }
        Status(true, urlFor(port))
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread = null
    }

    fun isRunning(): Boolean = running.get()

    private fun urlFor(port: Int): String = "http://${lanAddress() ?: "127.0.0.1"}:$port/"

    private fun handle(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine().orEmpty()
            // Drain headers.
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = requestLine.split(' ').getOrNull(1).orEmpty()
            val output = socket.getOutputStream()
            when {
                path == "/" || path.isEmpty() -> writeIndex(output)
                path.startsWith("/file/") -> writeFile(output, path.removePrefix("/file/"))
                else -> writeNotFound(output)
            }
            output.flush()
        }
    }

    private fun writeIndex(output: java.io.OutputStream) {
        val items = filesProvider()
        val html = buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>ZenConverter</title></head><body>")
            append("<h1>ZenConverter local transfer</h1>")
            if (items.isEmpty()) {
                append("<p>No converted files yet.</p>")
            } else {
                append("<ul>")
                items.forEach { item ->
                    val encoded = java.net.URLEncoder.encode(item.displayName, "UTF-8")
                    append("<li><a href=\"/file/$encoded\">")
                    append(escapeHtml(item.displayName))
                    append("</a> &mdash; ").append(item.file.length() / 1024).append(" KB</li>")
                }
                append("</ul>")
            }
            append("<p>Served locally. Nothing leaves your network.</p>")
            append("</body></html>")
        }
        writeResponse(output, "200 OK", "text/html; charset=utf-8", html.toByteArray())
    }

    private fun writeFile(output: java.io.OutputStream, encodedName: String) {
        val name = URLDecoder.decode(encodedName, "UTF-8")
        val target = filesProvider().firstOrNull { it.displayName == name }
        if (target == null || !target.file.isFile) {
            writeNotFound(output)
            return
        }
        val bytes = target.file.readBytes()
        writeResponse(output, "200 OK", "application/octet-stream", bytes, fileName = target.displayName)
    }

    private fun writeNotFound(output: java.io.OutputStream) {
        writeResponse(output, "404 Not Found", "text/plain; charset=utf-8", "Not found".toByteArray())
    }

    private fun writeResponse(
        output: java.io.OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        fileName: String? = null
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            fileName?.let {
                append("Content-Disposition: attachment; filename=\"")
                    .append(it.replace("\"", ""))
                    .append("\"\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray())
        output.write(body)
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    @Suppress("unused")
    private fun wifiManager(): WifiManager? = context.getSystemService(WifiManager::class.java)

    companion object {
        const val DEFAULT_PORT = 8080
    }
}

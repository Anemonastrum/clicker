package id.anemona.clicker.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Result of one NTP round-trip. */
data class NtpResult(
    /** How far the device clock is behind/ahead: ntpTime = deviceTime + offsetMs. */
    val offsetMs: Long,
    /** Network round-trip time in ms. */
    val rttMs: Long,
    /** Server time at the moment the reply was received. */
    val serverTimeMs: Long,
)

/**
 * Minimal SNTP client (RFC 5905, client mode). No extra dependencies —
 * plain [DatagramSocket] against any NTP server (default pool.ntp.org).
 */
object NtpClient {
    private const val NTP_PORT = 123
    private const val PACKET_SIZE = 48
    private const val TRANSMIT_OFFSET = 40
    private const val RECEIVE_OFFSET = 32
    /** Seconds between the NTP epoch (1900) and the Unix epoch (1970). */
    private const val NTP_EPOCH_OFFSET = 2208988800L

    suspend fun sync(host: String, timeoutMs: Int = 4000): NtpResult =
        withContext(Dispatchers.IO) {
            val address = InetAddress.getByName(host)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val buffer = ByteArray(PACKET_SIZE)
                // LI = 0 (no warning), VN = 4, Mode = 3 (client).
                buffer[0] = 0x1B.toByte()
                val t1 = System.currentTimeMillis()
                writeTimestamp(buffer, TRANSMIT_OFFSET, t1)
                socket.send(DatagramPacket(buffer, buffer.size, address, NTP_PORT))

                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                val t4 = System.currentTimeMillis()

                val t2 = readTimestamp(buffer, RECEIVE_OFFSET)
                val t3 = readTimestamp(buffer, TRANSMIT_OFFSET)
                // Standard NTP offset / delay formulas.
                val offset = ((t2 - t1) + (t3 - t4)) / 2
                val rtt = ((t4 - t1) - (t3 - t2)).coerceAtLeast(0)
                NtpResult(offset, rtt, t4 + offset)
            }
        }

    /** Current best-effort NTP time from a previously measured offset. */
    fun nowWithOffset(offsetMs: Long): Long = System.currentTimeMillis() + offsetMs

    private fun writeTimestamp(buf: ByteArray, offset: Int, javaTimeMs: Long) {
        val seconds = javaTimeMs / 1000L + NTP_EPOCH_OFFSET
        val fraction = ((javaTimeMs % 1000L) * 0x1_0000_0000L) / 1000L
        val ts = (seconds shl 32) or fraction
        for (i in 0 until 8) {
            buf[offset + i] = (ts ushr (56 - i * 8)).toByte()
        }
    }

    private fun readTimestamp(buf: ByteArray, offset: Int): Long {
        var seconds = 0L
        var fraction = 0L
        for (i in 0 until 4) seconds = (seconds shl 8) or (buf[offset + i].toLong() and 0xFF)
        for (i in 4 until 8) fraction = (fraction shl 8) or (buf[offset + i].toLong() and 0xFF)
        return (seconds - NTP_EPOCH_OFFSET) * 1000L + (fraction * 1000L) / 0x1_0000_0000L
    }
}

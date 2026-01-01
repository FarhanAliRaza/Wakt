package com.farhanaliraza.wakt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.farhanaliraza.wakt.MainActivity
import com.farhanaliraza.wakt.R
import com.farhanaliraza.wakt.data.database.dao.BlockedItemDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockDao
import com.farhanaliraza.wakt.data.database.dao.GoalBlockItemDao
import com.farhanaliraza.wakt.data.database.entity.BlockType
import com.farhanaliraza.wakt.utils.TemporaryUnlock
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class WebsiteBlockingVpnService : VpnService() {

    @Inject lateinit var blockedItemDao: BlockedItemDao
    @Inject lateinit var goalBlockDao: GoalBlockDao
    @Inject lateinit var goalBlockItemDao: GoalBlockItemDao
    @Inject lateinit var temporaryUnlock: TemporaryUnlock

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blockedWebsites = setOf<String>()

    companion object {
        private const val TAG = "WebsiteBlockingVpnService"
        // Virtual interface details (single /24)
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 24
        // We advertise a local DNS on the VPN. Only DNS to this IP hits the TUN.
        private const val VPN_DNS_LOCAL = "10.0.0.1"
        // Real upstream DNS for allowed queries
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_PORT = 53
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_service_channel"

        const val ACTION_START_VPN = "START_VPN"
        const val ACTION_STOP_VPN = "STOP_VPN"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) {
            Log.d(TAG, "VPN already running")
            return
        }

        Log.d(TAG, "Starting VPN service")

        // Load blocked websites from database
        serviceScope.launch { loadBlockedWebsites() }

        // Start VPN as DNS-only: advertise a local DNS on the TUN and do NOT add a default route
        val vpnBuilder =
                Builder()
                        .setSession("WaktVPN")
                        .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                        .addDnsServer(VPN_DNS_LOCAL)
        // Restrict to browsers to minimize impact and overhead
        browserPackages().forEach { pkg ->
            try {
                vpnBuilder.addAllowedApplication(pkg)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot allow app $pkg on VPN", e)
            }
        }

        try {
            vpnInterface = vpnBuilder.establish()
            if (vpnInterface != null) {
                isRunning = true
                startForeground(NOTIFICATION_ID, createNotification())

                // Start packet processing thread (DNS-only)
                vpnThread = Thread { runVpnLoop() }.apply { start() }

                Log.d(TAG, "VPN started successfully")
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")

        isRunning = false
        vpnThread?.interrupt()
        vpnInterface?.close()
        vpnInterface = null
        vpnThread = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun loadBlockedWebsites() {
        try {
            // Clean up expired blocks first
            blockedItemDao.deleteExpiredBlocks()
            goalBlockDao.markExpiredGoalsAsCompleted()
            
            val currentTime = System.currentTimeMillis()
            
            // Get regular blocked websites
            val regularWebsites =
                    blockedItemDao
                            .getAllBlockedItemsList()
                            .filter { 
                                it.type == BlockType.WEBSITE &&
                                (it.blockEndTime == null || it.blockEndTime!! > currentTime)
                            }
                            .map { cleanDomain(it.packageNameOrUrl) }
                            .toSet()
            
            // Get goal blocked websites from new goal_block_items table
            val goalItemWebsites = goalBlockItemDao
                    .getAllActiveGoalItems(currentTime)
                    .filter { it.itemType == BlockType.WEBSITE }
                    .map { cleanDomain(it.packageOrUrl) }
                    .toSet()
                    
            // Get goal blocked websites from old single-item goals (for backward compatibility)
            val oldGoalWebsites = 
                    goalBlockDao
                            .getActiveGoalsNotExpired(currentTime)
                            .filter { it.type == BlockType.WEBSITE && it.packageNameOrUrl.isNotBlank() }
                            .map { cleanDomain(it.packageNameOrUrl) }
                            .toSet()
            
            // Combine all sets
            val allBlockedWebsites = regularWebsites + goalItemWebsites + oldGoalWebsites
            
            // Filter out temporarily unlocked websites
            blockedWebsites = allBlockedWebsites.filter { domain ->
                !temporaryUnlock.isTemporarilyUnlocked(domain)
            }.toSet()
            
            val unlockedCount = allBlockedWebsites.size - blockedWebsites.size
            Log.d(TAG, "Loaded ${blockedWebsites.size} blocked websites (${regularWebsites.size} regular, ${goalItemWebsites.size} goal items, ${oldGoalWebsites.size} old goals, ${unlockedCount} temporarily unlocked): $blockedWebsites")

            // Battery optimization: Stop VPN if no websites to block
            if (blockedWebsites.isEmpty()) {
                Log.d(TAG, "No websites to block, stopping VPN service")
                stopVpn()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading blocked websites", e)
        }
    }

    private fun cleanDomain(url: String): String {
        return url.lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .split("/")[0] // Take only domain part
    }

    private fun runVpnLoop() {
        Log.d(TAG, "VPN packet processing loop started (DNS-only)")

        try {
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            val packetBuffer = ByteArray(32767)

            // Reuse a UDP socket to upstream DNS for performance
            val upstreamSocket = java.net.DatagramSocket()
            upstreamSocket.soTimeout = 2000
            protect(upstreamSocket)

            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    val length = vpnInput.read(packetBuffer)
                    if (length <= 0) {
                        Thread.sleep(1)
                        continue
                    }

                    // Only process IPv4 DNS UDP destined to our local VPN DNS IP
                    val responsePacket: ByteArray? =
                            handleDnsPacketIfAny(packetBuffer, length, upstreamSocket)

                    if (responsePacket != null) {
                        vpnOutput.write(responsePacket)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error processing packet", e)
                    }
                    break
                }
            }
            upstreamSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "VPN loop error", e)
        } finally {
            Log.d(TAG, "VPN packet processing loop ended")
        }
    }

    private fun handleDnsPacketIfAny(
            packet: ByteArray,
            length: Int,
            upstreamSocket: java.net.DatagramSocket
    ): ByteArray? {
        try {
            val ipVersion = (packet[0].toInt() ushr 4) and 0xF
            if (ipVersion != 4) return null

            val ipHeaderLengthBytes = (packet[0].toInt() and 0x0F) * 4
            if (length < ipHeaderLengthBytes + 8) return null // Not enough for UDP

            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return null // Not UDP

            val destIpBytes = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
            val destIp =
                    (destIpBytes[0].toInt() and 0xFF).toString() +
                            "." +
                            (destIpBytes[1].toInt() and 0xFF) +
                            "." +
                            (destIpBytes[2].toInt() and 0xFF) +
                            "." +
                            (destIpBytes[3].toInt() and 0xFF)
            if (destIp != VPN_DNS_LOCAL) return null

            val srcIpBytes = byteArrayOf(packet[12], packet[13], packet[14], packet[15])

            val udpOffset = ipHeaderLengthBytes
            val srcPort =
                    ((packet[udpOffset].toInt() and 0xFF) shl 8) or
                            (packet[udpOffset + 1].toInt() and 0xFF)
            val destPort =
                    ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
                            (packet[udpOffset + 3].toInt() and 0xFF)
            if (destPort != 53) return null

            val udpLength =
                    ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
                            (packet[udpOffset + 5].toInt() and 0xFF)
            val dnsPayloadLength = udpLength - 8
            if (dnsPayloadLength <= 0 || udpOffset + 8 + dnsPayloadLength > length) return null

            val dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + 8 + dnsPayloadLength)

            val domain = extractDomainFromDnsQueryPayload(dnsPayload)
            if (domain != null) {
                val isBlocked =
                        blockedWebsites.any { blocked ->
                            domain == blocked || domain.endsWith(".$blocked")
                        }

                val replyDnsPayload: ByteArray? =
                        if (isBlocked) {
                            Log.d(TAG, "Blocking DNS for $domain")
                            buildDnsNxDomainResponse(dnsPayload)
                        } else {
                            forwardDnsToUpstream(dnsPayload, upstreamSocket)
                        }

                if (replyDnsPayload != null) {
                    return buildIpv4UdpPacket(
                            srcIp = destIpBytes, // from VPN_DNS_LOCAL
                            srcPort = 53,
                            destIp = srcIpBytes,
                            destPort = srcPort,
                            udpPayload = replyDnsPayload
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling DNS packet", e)
        }
        return null
    }

    // No generic UDP forwarding; we are DNS-only. Non-DNS traffic never enters this VPN.

    // No TCP handling required in DNS-only mode.

    // Deprecated: replaced by DNS-only handling above

    private fun extractDomainFromDnsQueryPayload(dnsPayload: ByteArray): String? {
        return try {
            var pos = 12 // DNS header is 12 bytes
            val domain = StringBuilder()
            while (pos < dnsPayload.size) {
                val labelLength = dnsPayload[pos].toInt() and 0xFF
                if (labelLength == 0) break
                pos++
                for (i in 0 until labelLength) {
                    if (pos >= dnsPayload.size) break
                    domain.append((dnsPayload[pos].toInt() and 0xFF).toChar())
                    pos++
                }
                domain.append('.')
            }
            if (domain.endsWith('.')) domain.deleteCharAt(domain.length - 1)
            domain.toString().lowercase()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from DNS payload", e)
            null
        }
    }

    private fun forwardDnsToUpstream(
            dnsPayload: ByteArray,
            socket: java.net.DatagramSocket
    ): ByteArray? {
        return try {
            val requestPacket =
                    java.net.DatagramPacket(
                            dnsPayload,
                            dnsPayload.size,
                            java.net.InetAddress.getByName(UPSTREAM_DNS),
                            UPSTREAM_DNS_PORT
                    )
            socket.send(requestPacket)

            val buffer = ByteArray(1500)
            val responsePacket = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            buffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            Log.e(TAG, "DNS upstream forward failed", e)
            null
        }
    }

    private fun buildDnsNxDomainResponse(requestPayload: ByteArray): ByteArray {
        // Build minimal NXDOMAIN response preserving the question section
        val txId0 = requestPayload[0]
        val txId1 = requestPayload[1]
        val flags0 = requestPayload[2]
        val flags1 = requestPayload[3]

        // Preserve RD bit from request (bit 0 of flags1)
        val rdBit = (flags1.toInt() and 0x01)

        val responseFlags0 =
                (0x80 or 0x01).toByte() // QR=1, OPCODE=0, AA=0, TC=0, RD=1? we'll set RD below
        var responseFlags1 = (0x80 or rdBit or 0x03).toByte() // RA=1, Z=0, RCODE=3 (NXDOMAIN)

        // If request had RD=0, clear RD in response
        if ((flags1.toInt() and 0x01) == 0) {
            responseFlags0 // no-op, RD bit sits in flags1 in DNS, but above we applied RD based on
            // rdBit already
        }

        // Copy question section
        val qdCount =
                ((requestPayload[4].toInt() and 0xFF) shl 8) or (requestPayload[5].toInt() and 0xFF)
        // Find end of question (labels + QTYPE + QCLASS) for the first question only
        var pos = 12
        repeat(qdCount) {
            while (pos < requestPayload.size && (requestPayload[pos].toInt() and 0xFF) != 0) {
                pos += 1 + (requestPayload[pos].toInt() and 0xFF)
            }
            pos++ // zero label
            pos += 4 // QTYPE + QCLASS
        }
        val questionLength = pos - 12

        val response = ByteArray(12 + questionLength)
        response[0] = txId0
        response[1] = txId1
        response[2] = responseFlags0
        response[3] = responseFlags1
        // QDCOUNT same as request
        response[4] = requestPayload[4]
        response[5] = requestPayload[5]
        // ANCOUNT = 0
        response[6] = 0
        response[7] = 0
        // NSCOUNT = 0
        response[8] = 0
        response[9] = 0
        // ARCOUNT = 0
        response[10] = 0
        response[11] = 0
        // Copy question
        System.arraycopy(requestPayload, 12, response, 12, questionLength)

        return response
    }

    private fun buildIpv4UdpPacket(
            srcIp: ByteArray,
            srcPort: Int,
            destIp: ByteArray,
            destPort: Int,
            udpPayload: ByteArray
    ): ByteArray {
        val ipHeaderLength = 20
        val udpHeaderLength = 8
        val totalLength = ipHeaderLength + udpHeaderLength + udpPayload.size
        val packet = ByteArray(totalLength)

        // IPv4 header
        packet[0] = 0x45.toByte() // Version 4, IHL 5
        packet[1] = 0 // DSCP/ECN
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0 // Identification
        packet[5] = 0
        packet[6] = 0 // Flags/Fragment offset
        packet[7] = 0
        packet[8] = 64.toByte() // TTL
        packet[9] = 17.toByte() // Protocol UDP
        packet[10] = 0 // Header checksum (temp)
        packet[11] = 0
        // Source IP
        packet[12] = srcIp[0]
        packet[13] = srcIp[1]
        packet[14] = srcIp[2]
        packet[15] = srcIp[3]
        // Destination IP
        packet[16] = destIp[0]
        packet[17] = destIp[1]
        packet[18] = destIp[2]
        packet[19] = destIp[3]

        // Compute IPv4 header checksum
        val checksum = ipv4HeaderChecksum(packet, 0, ipHeaderLength)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()

        // UDP header
        val udpOffset = ipHeaderLength
        packet[udpOffset] = (srcPort shr 8).toByte()
        packet[udpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[udpOffset + 2] = (destPort shr 8).toByte()
        packet[udpOffset + 3] = (destPort and 0xFF).toByte()
        val udpLength = udpHeaderLength + udpPayload.size
        packet[udpOffset + 4] = (udpLength shr 8).toByte()
        packet[udpOffset + 5] = (udpLength and 0xFF).toByte()
        packet[udpOffset + 6] = 0 // UDP checksum optional for IPv4; set 0
        packet[udpOffset + 7] = 0

        // UDP payload
        System.arraycopy(udpPayload, 0, packet, udpOffset + udpHeaderLength, udpPayload.size)

        return packet
    }

    private fun ipv4HeaderChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val first = (buffer[i].toInt() and 0xFF)
            val second = (buffer[i + 1].toInt() and 0xFF)
            sum += (first shl 8) + second
            i += 2
        }
        // Add carries
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun browserPackages(): List<String> =
            listOf(
                    "com.android.chrome",
                    "com.chrome.beta",
                    "com.chrome.dev",
                    "org.mozilla.firefox",
                    "com.microsoft.emmx",
                    "com.opera.browser",
                    "com.brave.browser",
                    "com.duckduckgo.mobile.android",
                    "com.samsung.android.sbrowser",
                    "com.UCMobile.intl",
                    "com.android.browser"
            )

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
                            .apply { description = "Website blocking VPN service" }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wakt Website Blocking")
                .setContentText("Blocking ${blockedWebsites.size} websites")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
        Log.d(TAG, "VPN Service destroyed")
    }
}

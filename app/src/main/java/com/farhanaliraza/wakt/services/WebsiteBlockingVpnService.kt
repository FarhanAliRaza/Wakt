package com.farhanaliraza.wakt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.*

/**
 * DNS-filtering VPN that blocks websites without slowing down the internet.
 *
 * Only DNS queries ever enter this service: the VPN advertises a local DNS
 * server (10.0.0.1) on the TUN interface and adds NO default route, so all
 * actual browsing traffic (video, images, downloads) goes directly over
 * Wi-Fi/mobile data, untouched.
 *
 * The packet pipeline is fully asynchronous:
 * - The TUN reader thread only parses queries and dispatches them upstream
 *   (blocked domains are answered with NXDOMAIN immediately, cached domains
 *   from the response cache). It never waits for an upstream reply.
 * - A separate receiver thread matches upstream replies back to clients via
 *   remapped DNS transaction IDs and writes them to the TUN.
 * - Queries that get no upstream reply within QUERY_TIMEOUT_MS receive
 *   SERVFAIL so resolvers fail fast instead of retrying for seconds.
 */
@AndroidEntryPoint
class WebsiteBlockingVpnService : VpnService() {

    @Inject lateinit var blockedItemDao: BlockedItemDao
    @Inject lateinit var goalBlockDao: GoalBlockDao
    @Inject lateinit var goalBlockItemDao: GoalBlockItemDao
    @Inject lateinit var temporaryUnlock: TemporaryUnlock

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunReaderThread: Thread? = null
    private var upstreamReceiverThread: Thread? = null
    private var upstreamSocket: DatagramSocket? = null
    private var tunOutput: FileOutputStream? = null
    private val tunWriteLock = Any()

    @Volatile private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blocklistJob: Job? = null

    /** Cleaned domain -> original identifier as stored in the DB (for TemporaryUnlock lookups). */
    @Volatile private var blockedDomains: Map<String, String> = emptyMap()

    @Volatile private var upstreamServers: List<InetAddress> = emptyList()
    @Volatile private var upstreamIndex = 0
    @Volatile private var consecutiveTimeouts = 0

    private val txnIdCounter = AtomicInteger(1)
    private val pendingQueries = ConcurrentHashMap<Int, PendingQuery>()
    private val responseCache = ConcurrentHashMap<String, CachedResponse>()

    private class PendingQuery(
            val originalTxnId0: Byte,
            val originalTxnId1: Byte,
            val clientIp: ByteArray,
            val clientPort: Int,
            val cacheKey: String?,
            val queryPayload: ByteArray,
            val sentAtMs: Long
    )

    private class CachedResponse(val payload: ByteArray, val expiresAtMs: Long)

    companion object {
        private const val TAG = "WebsiteBlockingVpnService"
        // Virtual interface details (single /24)
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 24
        // We advertise a local DNS on the VPN. Only DNS to this IP hits the TUN.
        private const val VPN_DNS_LOCAL = "10.0.0.1"
        // Fallbacks when the underlying network's DNS servers can't be determined
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")
        private const val UPSTREAM_DNS_PORT = 53
        private const val QUERY_TIMEOUT_MS = 3000L
        private const val TIMEOUTS_BEFORE_UPSTREAM_ROTATION = 5
        private const val MAX_CACHE_ENTRIES = 512
        private const val MIN_CACHE_TTL_MS = 10_000L
        private const val MAX_CACHE_TTL_MS = 3_600_000L
        private const val NEGATIVE_CACHE_TTL_MS = 30_000L
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_service_channel"

        const val ACTION_START_VPN = "START_VPN"
        const val ACTION_STOP_VPN = "STOP_VPN"

        /** Lets ServiceOptimizer avoid spinning up the service just to deliver a stop action. */
        @Volatile var isServiceRunning = false
            private set

        /** Diagnostics shown in the UI: DNS queries seen by the filter and how many were blocked. */
        val queriesSeen = AtomicInteger(0)
        val queriesBlocked = AtomicInteger(0)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter foreground first: the service may be launched with
        // startForegroundService(), whose contract requires this call.
        startForeground(NOTIFICATION_ID, createNotification())
        when (intent?.action) {
            ACTION_STOP_VPN -> stopVpn()
            else -> {
                if (isRunning) {
                    // Already running: refresh the blocklist instead of restarting
                    serviceScope.launch { loadBlockedWebsites() }
                } else {
                    startVpn()
                }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.d(TAG, "Starting VPN service")

        upstreamServers = resolveUpstreamServers()
        Log.d(TAG, "Upstream DNS servers: $upstreamServers")

        // DNS-only VPN: advertise a local DNS on the TUN and do NOT add a
        // default route, so only DNS packets are processed in userspace and
        // all other traffic flows directly over the real network.
        val vpnBuilder =
                Builder()
                        .setSession("WaktVPN")
                        .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                        .addDnsServer(VPN_DNS_LOCAL)
                        .setBlocking(true)
                        .setMtu(4096)
        // Cover ALL apps (so DNS blocking also stops apps like Instagram/TikTok,
        // not just browsers), but keep Wakt itself off the VPN.
        try {
            vpnBuilder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot exclude own package from VPN", e)
        }

        try {
            vpnInterface = vpnBuilder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface (consent missing or revoked?)")
                stopVpn()
                return
            }

            isRunning = true
            isServiceRunning = true
            tunOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            upstreamSocket =
                    DatagramSocket().apply {
                        soTimeout = 1000 // wake up periodically to sweep timed-out queries
                        protect(this)
                    }

            tunReaderThread = Thread({ runTunReaderLoop() }, "WaktVpn-TunReader").apply { start() }
            upstreamReceiverThread =
                    Thread({ runUpstreamReceiverLoop() }, "WaktVpn-Upstream").apply { start() }

            // Reload the blocklist whenever blocked items change in the DB
            blocklistJob?.cancel()
            blocklistJob =
                    serviceScope.launch {
                        blockedItemDao.getAllBlockedItems().collect { loadBlockedWebsites() }
                    }

            Log.d(TAG, "VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")

        isRunning = false
        isServiceRunning = false
        blocklistJob?.cancel()
        blocklistJob = null
        tunReaderThread?.interrupt()
        upstreamReceiverThread?.interrupt()
        try {
            upstreamSocket?.close()
        } catch (_: Exception) {}
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        upstreamSocket = null
        vpnInterface = null
        tunOutput = null
        tunReaderThread = null
        upstreamReceiverThread = null
        pendingQueries.clear()
        responseCache.clear()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // User revoked consent or another VPN took over
        Log.w(TAG, "VPN permission revoked")
        stopVpn()
    }

    // ============== BLOCKLIST ==============

    private suspend fun loadBlockedWebsites() {
        try {
            // Clean up expired blocks first
            blockedItemDao.deleteExpiredBlocks()
            goalBlockDao.markExpiredGoalsAsCompleted()

            val currentTime = System.currentTimeMillis()

            // Regular blocked websites
            val regularWebsites =
                    blockedItemDao
                            .getAllBlockedItemsList()
                            .filter {
                                it.type == BlockType.WEBSITE &&
                                        (it.blockEndTime == null || it.blockEndTime!! > currentTime)
                            }
                            .associate { cleanDomain(it.packageNameOrUrl) to it.packageNameOrUrl }

            // Goal blocked websites from goal_block_items table
            val goalItemWebsites =
                    goalBlockItemDao
                            .getAllActiveGoalItems(currentTime)
                            .filter { it.itemType == BlockType.WEBSITE }
                            .associate { cleanDomain(it.packageOrUrl) to it.packageOrUrl }

            // Goal blocked websites from old single-item goals (backward compatibility)
            val oldGoalWebsites =
                    goalBlockDao
                            .getActiveGoalsNotExpired(currentTime)
                            .filter { it.type == BlockType.WEBSITE && it.packageNameOrUrl.isNotBlank() }
                            .associate { cleanDomain(it.packageNameOrUrl) to it.packageNameOrUrl }

            // Temporary unlocks are checked live per-query, so the full set stays
            // loaded and blocking resumes automatically when an unlock expires.
            blockedDomains = regularWebsites + goalItemWebsites + oldGoalWebsites

            Log.d(TAG, "Loaded ${blockedDomains.size} blocked websites: ${blockedDomains.keys}")

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, createNotification())

            // Battery optimization: stop the VPN entirely when there is nothing to block
            if (blockedDomains.isEmpty() && isRunning) {
                Log.d(TAG, "No websites to block, stopping VPN service")
                withContext(Dispatchers.Main) { stopVpn() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading blocked websites", e)
        }
    }

    /**
     * Reduces whatever the user typed ("https://www.facebook.com/", "m.facebook.com")
     * to a bare parent domain ("facebook.com"), so the block covers every subdomain.
     * The "m." (mobile) and "www." prefixes are stripped because browsers pick
     * between them freely and blocking just one of them blocks nothing in practice.
     */
    private fun cleanDomain(url: String): String {
        return url.trim()
                .lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .split("/")[0] // Take only domain part
                .split(":")[0] // Drop any port
                .removePrefix("www.")
                .removePrefix("m.")
                .trimEnd('.')
    }

    /**
     * A domain is blocked when it (or a parent domain) is on the blocklist and
     * no temporary unlock is currently active for it. Checked live per query so
     * challenge unlocks apply instantly and re-block when they expire.
     */
    private fun isDomainBlocked(domain: String): Boolean {
        for ((blocked, originalIdentifier) in blockedDomains) {
            if (domain == blocked || domain.endsWith(".$blocked")) {
                val unlocked =
                        temporaryUnlock.isTemporarilyUnlocked(originalIdentifier) ||
                                temporaryUnlock.isTemporarilyUnlocked(blocked)
                return !unlocked
            }
        }
        return false
    }

    // ============== UPSTREAM DNS SELECTION ==============

    /**
     * Prefer the underlying (non-VPN) network's own DNS servers - they are
     * usually closer and faster than public resolvers - with public fallbacks.
     */
    private fun resolveUpstreamServers(): List<InetAddress> {
        val servers = LinkedHashSet<InetAddress>()
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                try {
                    val caps = cm.getNetworkCapabilities(network) ?: continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                    val linkProperties = cm.getLinkProperties(network) ?: continue
                    for (dns in linkProperties.dnsServers) {
                        if (dns.isLinkLocalAddress || dns.isLoopbackAddress) continue
                        if (dns.hostAddress == VPN_DNS_LOCAL || dns.hostAddress == VPN_ADDRESS) continue
                        servers.add(dns)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading DNS for network $network", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving network DNS servers", e)
        }
        for (fallback in FALLBACK_DNS) {
            try {
                servers.add(InetAddress.getByName(fallback))
            } catch (_: Exception) {}
        }
        // IPv4 upstreams first - the upstream socket handles them on any network
        return servers.sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    private fun currentUpstream(): InetAddress? {
        val servers = upstreamServers
        if (servers.isEmpty()) return null
        return servers[upstreamIndex % servers.size]
    }

    private fun rotateUpstream() {
        val servers = upstreamServers
        if (servers.size > 1) {
            upstreamIndex = (upstreamIndex + 1) % servers.size
            Log.w(TAG, "Rotating upstream DNS to ${currentUpstream()}")
        }
        consecutiveTimeouts = 0
    }

    // ============== TUN READER (query dispatch, never blocks on upstream) ==============

    private fun runTunReaderLoop() {
        Log.d(TAG, "TUN reader loop started (DNS-only)")
        try {
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val packetBuffer = ByteArray(32767)

            while (isRunning && !Thread.currentThread().isInterrupted) {
                val length =
                        try {
                            vpnInput.read(packetBuffer)
                        } catch (e: Exception) {
                            if (isRunning) Log.e(TAG, "TUN read failed", e)
                            break
                        }
                if (length <= 0) continue

                try {
                    handlePacket(packetBuffer, length)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling packet", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TUN reader loop error", e)
        } finally {
            Log.d(TAG, "TUN reader loop ended")
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int) {
        // Only IPv4 UDP destined to our local VPN DNS on port 53
        val ipVersion = (packet[0].toInt() ushr 4) and 0xF
        if (ipVersion != 4) return

        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLength + 8) return

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return // Not UDP

        val destIpBytes = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        if (ipv4ToString(destIpBytes) != VPN_DNS_LOCAL) return

        val udpOffset = ipHeaderLength
        val destPort =
                ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
                        (packet[udpOffset + 3].toInt() and 0xFF)
        if (destPort != 53) return

        val srcPort =
                ((packet[udpOffset].toInt() and 0xFF) shl 8) or
                        (packet[udpOffset + 1].toInt() and 0xFF)
        val srcIpBytes = byteArrayOf(packet[12], packet[13], packet[14], packet[15])

        val udpLength =
                ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
                        (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsPayloadLength = udpLength - 8
        if (dnsPayloadLength < 12 || udpOffset + 8 + dnsPayloadLength > length) return

        val dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + 8 + dnsPayloadLength)

        // Ignore anything that isn't a query (QR bit set = response)
        if ((dnsPayload[2].toInt() and 0x80) != 0) return

        val question = extractQuestion(dnsPayload)
        val domain = question?.first
        queriesSeen.incrementAndGet()

        // 1. Blocked domain: answer NXDOMAIN immediately, nothing goes upstream
        if (domain != null && isDomainBlocked(domain)) {
            queriesBlocked.incrementAndGet()
            Log.d(TAG, "Blocking DNS for $domain")
            writeDnsResponseToTun(buildDnsErrorResponse(dnsPayload, RCODE_NXDOMAIN), srcIpBytes, srcPort)
            return
        }

        // 2. Cache hit: answer instantly from cache
        val cacheKey = question?.let { "${it.first}:${it.second}" }
        if (cacheKey != null) {
            val cached = responseCache[cacheKey]
            if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                val reply = cached.payload.copyOf()
                reply[0] = dnsPayload[0]
                reply[1] = dnsPayload[1]
                writeDnsResponseToTun(reply, srcIpBytes, srcPort)
                return
            }
        }

        // 3. Forward upstream asynchronously - remap the transaction ID so
        //    concurrent queries from different clients can't collide, and let
        //    the receiver thread deliver the reply. The reader never waits.
        forwardQueryUpstream(dnsPayload, srcIpBytes, srcPort, cacheKey)
    }

    private fun forwardQueryUpstream(
            dnsPayload: ByteArray,
            clientIp: ByteArray,
            clientPort: Int,
            cacheKey: String?
    ) {
        val socket = upstreamSocket ?: return
        val upstream = currentUpstream()
        if (upstream == null) {
            writeDnsResponseToTun(buildDnsErrorResponse(dnsPayload, RCODE_SERVFAIL), clientIp, clientPort)
            return
        }

        // Allocate an unused upstream transaction ID
        var upstreamId: Int
        var attempts = 0
        do {
            upstreamId = txnIdCounter.getAndIncrement() and 0xFFFF
            attempts++
        } while (pendingQueries.containsKey(upstreamId) && attempts < 10)

        pendingQueries[upstreamId] =
                PendingQuery(
                        originalTxnId0 = dnsPayload[0],
                        originalTxnId1 = dnsPayload[1],
                        clientIp = clientIp,
                        clientPort = clientPort,
                        cacheKey = cacheKey,
                        queryPayload = dnsPayload,
                        sentAtMs = System.currentTimeMillis()
                )

        val outbound = dnsPayload.copyOf()
        outbound[0] = (upstreamId shr 8).toByte()
        outbound[1] = (upstreamId and 0xFF).toByte()

        try {
            socket.send(DatagramPacket(outbound, outbound.size, upstream, UPSTREAM_DNS_PORT))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send DNS query upstream", e)
            pendingQueries.remove(upstreamId)
            writeDnsResponseToTun(buildDnsErrorResponse(dnsPayload, RCODE_SERVFAIL), clientIp, clientPort)
        }
    }

    // ============== UPSTREAM RECEIVER (reply delivery + timeout sweep) ==============

    private fun runUpstreamReceiverLoop() {
        Log.d(TAG, "Upstream receiver loop started")
        val buffer = ByteArray(4096)
        val socket = upstreamSocket ?: return

        while (isRunning && !Thread.currentThread().isInterrupted) {
            try {
                val responsePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(responsePacket)
                consecutiveTimeouts = 0
                handleUpstreamResponse(buffer, responsePacket.length)
            } catch (e: SocketTimeoutException) {
                // Normal wake-up: sweep queries that never got an answer
                sweepTimedOutQueries()
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Upstream receive failed", e)
                break
            }
        }
        Log.d(TAG, "Upstream receiver loop ended")
    }

    private fun handleUpstreamResponse(buffer: ByteArray, length: Int) {
        if (length < 12) return
        val upstreamId = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
        val pending = pendingQueries.remove(upstreamId) ?: return

        val reply = buffer.copyOf(length)
        reply[0] = pending.originalTxnId0
        reply[1] = pending.originalTxnId1

        if (pending.cacheKey != null) {
            cacheResponse(pending.cacheKey, reply)
        }

        writeDnsResponseToTun(reply, pending.clientIp, pending.clientPort)
    }

    private fun sweepTimedOutQueries() {
        if (pendingQueries.isEmpty()) return
        val now = System.currentTimeMillis()
        var timedOut = 0
        val iterator = pendingQueries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pending = entry.value
            if (now - pending.sentAtMs > QUERY_TIMEOUT_MS) {
                iterator.remove()
                timedOut++
                // Fail fast: SERVFAIL beats letting the client's resolver
                // retry into multi-second timeouts (that reads as "slow internet")
                writeDnsResponseToTun(
                        buildDnsErrorResponse(pending.queryPayload, RCODE_SERVFAIL),
                        pending.clientIp,
                        pending.clientPort
                )
            }
        }
        if (timedOut > 0) {
            consecutiveTimeouts += timedOut
            if (consecutiveTimeouts >= TIMEOUTS_BEFORE_UPSTREAM_ROTATION) {
                rotateUpstream()
            }
        }
    }

    // ============== RESPONSE CACHE ==============

    private fun cacheResponse(cacheKey: String, responsePayload: ByteArray) {
        try {
            val rcode = responsePayload[3].toInt() and 0x0F
            val anCount =
                    ((responsePayload[6].toInt() and 0xFF) shl 8) or
                            (responsePayload[7].toInt() and 0xFF)

            val ttlMs =
                    if (rcode == 0 && anCount > 0) {
                        val minTtlSeconds = parseMinAnswerTtl(responsePayload) ?: return
                        (minTtlSeconds * 1000L).coerceIn(MIN_CACHE_TTL_MS, MAX_CACHE_TTL_MS)
                    } else {
                        // Negative caching (NXDOMAIN / no data): short TTL
                        NEGATIVE_CACHE_TTL_MS
                    }

            if (responseCache.size >= MAX_CACHE_ENTRIES) {
                val now = System.currentTimeMillis()
                responseCache.entries.removeIf { it.value.expiresAtMs <= now }
                if (responseCache.size >= MAX_CACHE_ENTRIES) {
                    responseCache.clear()
                }
            }
            responseCache[cacheKey] = CachedResponse(responsePayload, System.currentTimeMillis() + ttlMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache DNS response", e)
        }
    }

    /** Minimum TTL across the answer section, or null if the message can't be parsed. */
    private fun parseMinAnswerTtl(payload: ByteArray): Long? {
        try {
            val qdCount = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
            val anCount = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)

            var pos = 12
            repeat(qdCount) {
                pos = skipDnsName(payload, pos) ?: return null
                pos += 4 // QTYPE + QCLASS
            }

            var minTtl = Long.MAX_VALUE
            repeat(anCount) {
                pos = skipDnsName(payload, pos) ?: return null
                if (pos + 10 > payload.size) return null
                val ttl =
                        ((payload[pos + 4].toLong() and 0xFF) shl 24) or
                                ((payload[pos + 5].toLong() and 0xFF) shl 16) or
                                ((payload[pos + 6].toLong() and 0xFF) shl 8) or
                                (payload[pos + 7].toLong() and 0xFF)
                if (ttl < minTtl) minTtl = ttl
                val rdLength =
                        ((payload[pos + 8].toInt() and 0xFF) shl 8) or (payload[pos + 9].toInt() and 0xFF)
                pos += 10 + rdLength
                if (pos > payload.size) return null
            }
            return if (minTtl == Long.MAX_VALUE) null else minTtl
        } catch (e: Exception) {
            return null
        }
    }

    /** Returns the position after a (possibly compressed) DNS name, or null on malformed input. */
    private fun skipDnsName(payload: ByteArray, startPos: Int): Int? {
        var pos = startPos
        while (pos < payload.size) {
            val len = payload[pos].toInt() and 0xFF
            when {
                len == 0 -> return pos + 1
                (len and 0xC0) == 0xC0 -> return pos + 2 // compression pointer
                else -> pos += 1 + len
            }
        }
        return null
    }

    // ============== DNS MESSAGE HELPERS ==============

    /** Extracts (domain, qtype) from the first question, or null if unparseable. */
    private fun extractQuestion(dnsPayload: ByteArray): Pair<String, Int>? {
        return try {
            val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
            if (qdCount < 1) return null

            var pos = 12
            val domain = StringBuilder()
            while (pos < dnsPayload.size) {
                val labelLength = dnsPayload[pos].toInt() and 0xFF
                if (labelLength == 0) {
                    pos++
                    break
                }
                if (pos + 1 + labelLength > dnsPayload.size) return null
                if (domain.isNotEmpty()) domain.append('.')
                for (i in 1..labelLength) {
                    domain.append((dnsPayload[pos + i].toInt() and 0xFF).toChar())
                }
                pos += 1 + labelLength
            }
            if (domain.isEmpty() || pos + 2 > dnsPayload.size) return null
            val qtype = ((dnsPayload[pos].toInt() and 0xFF) shl 8) or (dnsPayload[pos + 1].toInt() and 0xFF)
            Pair(domain.toString().lowercase(), qtype)
        } catch (e: Exception) {
            null
        }
    }

    private val RCODE_SERVFAIL = 2
    private val RCODE_NXDOMAIN = 3

    /** Minimal error response (NXDOMAIN/SERVFAIL) preserving the question section. */
    private fun buildDnsErrorResponse(requestPayload: ByteArray, rcode: Int): ByteArray {
        // Find end of question section (labels + QTYPE + QCLASS)
        val qdCount =
                ((requestPayload[4].toInt() and 0xFF) shl 8) or (requestPayload[5].toInt() and 0xFF)
        var pos = 12
        repeat(qdCount) {
            pos = skipDnsName(requestPayload, pos) ?: return@repeat
            pos += 4
        }
        val questionLength = (pos - 12).coerceIn(0, maxOf(0, requestPayload.size - 12))

        val response = ByteArray(12 + questionLength)
        response[0] = requestPayload[0]
        response[1] = requestPayload[1]
        // QR=1, opcode copied from request, RD preserved
        response[2] = (0x80 or (requestPayload[2].toInt() and 0x79)).toByte()
        // RA=1, RCODE
        response[3] = (0x80 or (rcode and 0x0F)).toByte()
        // QDCOUNT same as request; AN/NS/AR = 0
        response[4] = requestPayload[4]
        response[5] = requestPayload[5]
        System.arraycopy(requestPayload, 12, response, 12, questionLength)
        return response
    }

    private fun writeDnsResponseToTun(dnsPayload: ByteArray, clientIp: ByteArray, clientPort: Int) {
        val output = tunOutput ?: return
        val packet =
                buildIpv4UdpPacket(
                        srcIp = ipv4StringToBytes(VPN_DNS_LOCAL),
                        srcPort = 53,
                        destIp = clientIp,
                        destPort = clientPort,
                        udpPayload = dnsPayload
                )
        try {
            synchronized(tunWriteLock) { output.write(packet) }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Failed to write DNS response to TUN", e)
        }
    }

    // ============== RAW PACKET CONSTRUCTION ==============

    private fun ipv4ToString(ip: ByteArray): String =
            "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"

    private fun ipv4StringToBytes(ip: String): ByteArray {
        val parts = ip.split(".")
        return byteArrayOf(
                parts[0].toInt().toByte(),
                parts[1].toInt().toByte(),
                parts[2].toInt().toByte(),
                parts[3].toInt().toByte()
        )
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
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)

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
        packet[udpOffset + 6] = 0 // UDP checksum optional for IPv4
        packet[udpOffset + 7] = 0

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
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    // ============== NOTIFICATION ==============

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
                            .apply { description = "Website blocking DNS filter" }

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
                .setContentText("Blocking ${blockedDomains.size} websites at the DNS level")
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

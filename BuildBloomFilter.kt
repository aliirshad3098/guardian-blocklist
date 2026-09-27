/**
 * BuildBloomFilter.kt — CI-side CLI tool for guardian-blocklist repo.
 *
 * YEH KYA KARTA HAI:
 *   Guardian app (github.com/.../guardian repo) ke andar DomainBloomFilter.kt
 *   ka EXACT SAME algorithm/binary-format yahan dobara likha gaya hai
 *   (bit-for-bit identical — same FNV hash constants, same hosts-prefix
 *   parsing, same PREBUILT_MAGIC/formatVersion header) taake yeh CLI tool
 *   jo .bin file banaye, woh phone par DomainBloomFilter.loadPrebuiltFromFile()
 *   se seedha, bilkul sahi load ho — koi format-mismatch nahi.
 *
 *   ⚠️ IMPORTANT — agar kabhi guardian repo ke DomainBloomFilter.kt mein
 *   hash algorithm ya bitCount/hashCount formula badla jaye, to yahan bhi
 *   WAHI change lazmi karna hai, WARNA sirf PREBUILT_FORMAT_VERSION bump
 *   kar dena (dono jagah) — taake purana/naya mismatch safely reject ho
 *   jaye (app khud text-parse fallback pe chala jayega), silently corrupt
 *   filter kabhi na bane.
 *
 * USAGE (koi Gradle/Android SDK zaroorat nahi — sirf plain kotlinc):
 *   kotlinc BuildBloomFilter.kt -include-runtime -d build-bloom-filter.jar
 *   java -jar build-bloom-filter.jar \
 *       <input-blocklist.txt or .txt.gz> \
 *       <output.bin> \
 *       <output-manifest.json> \
 *       <version-string>
 *
 *   version-string: kuch bhi unique ho sakta hai — recommend: git commit
 *   SHA ya blocklist-update ki date (e.g. "2026-09-25" ya "a1b2c3d").
 *   Bas har naye blocklist update par ALAG hona chahiye, taake phone ka
 *   manifest-version-compare naye .bin ko download kare.
 */

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

// ── DomainBloomFilter ka EXACT copy (algorithm/format sync — see header) ────

class DomainBloomFilter private constructor(
    private val bitCount: Int,
    private val hashCount: Int,
    initialSize: Int,
    private val bits: LongArray,
) {
    var size: Int = initialSize
        private set

    constructor(
        expectedItems: Int = 4_599_258,
        falsePositiveRate: Double = 0.0001,
    ) : this(
        computeBitCount(expectedItems, falsePositiveRate),
        computeHashCount(expectedItems, falsePositiveRate),
        0,
        LongArray((computeBitCount(expectedItems, falsePositiveRate) + 63) / 64)
    )

    fun add(bytes: ByteArray, start: Int, end: Int) {
        val (h1, h2) = hashPairBytes(bytes, start, end)
        var combined = h1
        for (i in 0 until hashCount) {
            val idx = ((combined and Long.MAX_VALUE) % bitCount).toInt()
            bits[idx ushr 6] = bits[idx ushr 6] or (1L shl (idx and 63))
            combined += h2
        }
        size++
    }

    private fun hashPairBytes(bytes: ByteArray, start: Int, end: Int): Pair<Long, Long> {
        var h1 = -0x340d631b7bdddcdbL   // FNV offset basis — MUST match DomainBloomFilter.kt exactly
        var h2 = 0x100000001b3L         // FNV prime as second seed start
        val prime = 0x100000001b3L
        for (i in start until end) {
            var b = bytes[i].toInt() and 0xFF
            if (b in 0x41..0x5A) b += 32 // 'A'..'Z' -> 'a'..'z'
            val c = b.toLong()
            h1 = (h1 xor c) * prime
            h2 = (h2 xor (c + 0x9e3779b9L)) * prime
        }
        return Pair(h1, h2)
    }

    fun bitCountPublic() = bitCount
    fun hashCountPublic() = hashCount
    fun bitsPublic() = bits

    companion object {
        private fun computeBitCount(expectedItems: Int, falsePositiveRate: Double): Int {
            val n = expectedItems.coerceAtLeast(1)
            val p = falsePositiveRate.coerceIn(1e-6, 0.5)
            val ln2 = Math.log(2.0)
            val m = Math.ceil(-(n * Math.log(p)) / (ln2 * ln2)).toInt()
            return m.coerceAtLeast(64)
        }

        private fun computeHashCount(expectedItems: Int, falsePositiveRate: Double): Int {
            val n = expectedItems.coerceAtLeast(1)
            val m = computeBitCount(expectedItems, falsePositiveRate)
            val ln2 = Math.log(2.0)
            return Math.max(1, Math.round((m.toDouble() / n) * ln2).toInt())
        }
    }
}

// ── Prebuilt binary writer — MUST match DomainBloomFilter.loadPrebuiltFromFile exactly ──

private const val PREBUILT_MAGIC = 0x47425046 // "GBPF"
private const val PREBUILT_FORMAT_VERSION = 1  // bump BOTH here and in the app if the format ever changes

private fun savePrebuiltToFile(bloom: DomainBloomFilter, file: File, version: String) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
        out.writeInt(PREBUILT_MAGIC)
        out.writeInt(PREBUILT_FORMAT_VERSION)
        out.writeUTF(version)
        out.writeInt(bloom.bitCountPublic())
        out.writeInt(bloom.hashCountPublic())
        out.writeInt(bloom.size)
        out.writeInt(bloom.bitsPublic().size)
        for (v in bloom.bitsPublic()) out.writeLong(v)
    }
    tmp.renameTo(file)
}

// ── Hosts-format line parsing — MUST match GuardianVpnService.addLineIfNonEmpty exactly ──

private val HOSTS_PREFIX = byteArrayOf(
    '0'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(), '.'.code.toByte(),
    '0'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(), ' '.code.toByte()
)

private fun addLineIfNonEmpty(bytes: ByteArray, rawStart: Int, rawEnd: Int, target: DomainBloomFilter) {
    var start = rawStart
    var end = rawEnd
    while (start < end && (bytes[start] == ' '.code.toByte() || bytes[start] == '\t'.code.toByte())) start++
    while (end > start && (bytes[end - 1] == ' '.code.toByte() || bytes[end - 1] == '\t'.code.toByte() || bytes[end - 1] == '\r'.code.toByte())) end--
    if (end <= start) return

    var domainStart = start
    if (end - start > HOSTS_PREFIX.size) {
        var matches = true
        for (k in HOSTS_PREFIX.indices) {
            if (bytes[start + k] != HOSTS_PREFIX[k]) { matches = false; break }
        }
        if (matches) domainStart = start + HOSTS_PREFIX.size
    }
    if (end <= domainStart) return

    target.add(bytes, domainStart, end)
}

// ── main ─────────────────────────────────────────────────────────────────

fun main(args: Array<String>) {
    if (args.size < 4) {
        System.err.println("Usage: BuildBloomFilter <input.txt[.gz]> <output.bin> <output-manifest.json> <version-string>")
        kotlin.system.exitProcess(1)
    }
    val inputPath = args[0]
    val outputBinPath = args[1]
    val outputManifestPath = args[2]
    val version = args[3]

    val inputFile = File(inputPath)
    if (!inputFile.exists()) {
        System.err.println("Input file not found: $inputPath")
        kotlin.system.exitProcess(1)
    }

    println("Reading $inputPath ...")
    val rawBytes: ByteArray = if (inputPath.endsWith(".gz")) {
        GZIPInputStream(inputFile.inputStream()).use { it.readBytes() }
    } else {
        inputFile.readBytes()
    }
    println("Read ${rawBytes.size} bytes, parsing + hashing into Bloom filter ...")

    val bloom = DomainBloomFilter()
    var lineStart = 0
    var domainCount = 0
    for (i in rawBytes.indices) {
        if (rawBytes[i] == '\n'.code.toByte()) {
            addLineIfNonEmpty(rawBytes, lineStart, i, bloom)
            lineStart = i + 1
        }
    }
    if (lineStart < rawBytes.size) {
        addLineIfNonEmpty(rawBytes, lineStart, rawBytes.size, bloom)
    }
    domainCount = bloom.size

    println("Parsed $domainCount domains. Writing $outputBinPath ...")
    savePrebuiltToFile(bloom, File(outputBinPath), version)

    val manifestJson = """
        {
          "version": "$version",
          "formatVersion": $PREBUILT_FORMAT_VERSION,
          "domainCount": $domainCount,
          "builtAt": "${java.time.Instant.now()}"
        }
    """.trimIndent()
    File(outputManifestPath).writeText(manifestJson)
    println("Wrote $outputManifestPath")
    println("Done. version=$version domainCount=$domainCount")
}

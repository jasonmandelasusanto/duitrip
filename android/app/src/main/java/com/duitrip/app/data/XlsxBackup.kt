package com.duitrip.app.data

import com.duitrip.app.data.model.Expense
import com.duitrip.app.data.model.Settlement
import com.duitrip.app.data.model.SplitEntry
import com.duitrip.app.data.model.Trip
import com.duitrip.app.data.model.TripMember
import com.duitrip.app.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable XLSX backup: one worksheet per trip. Each sheet has human-readable expense
 *  rows plus a lossless base64 JSON payload (used for import). */
data class TripBackup(val trip: Trip, val expenses: List<Expense>, val settlements: List<Settlement>)

object XlsxBackup {
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun write(backups: List<TripBackup>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            val overrides = backups.indices.joinToString("") {
                "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            }
            entry(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    overrides + "</Types>")
            entry(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            val sheets = backups.mapIndexed { i, b ->
                "<sheet name=\"${escape(sheetName(b.trip.name, i + 1))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
            }.joinToString("")
            entry(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>$sheets</sheets></workbook>")
            val rels = backups.indices.joinToString("") {
                "<Relationship Id=\"rId${it + 1}\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>"
            }
            entry(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels</Relationships>")
            backups.forEachIndexed { i, backup ->
                entry(zip, "xl/worksheets/sheet${i + 1}.xml", buildSheet(backup))
            }
        }
        bytes.toByteArray()
    }

    private fun buildSheet(backup: TripBackup): String {
        val payload = Base64.getEncoder().encodeToString(toJson(backup).toString().toByteArray())
        val destCur = backup.trip.destinationCurrency
        val nameOf: (String) -> String = { id -> backup.trip.members.firstOrNull { it.memberId == id }?.displayName ?: id }
        val rows = StringBuilder()
        // Row 1: import marker + version. Row 2: lossless payload.
        rows.append(rowXml(1, cell("A1", "DUITRIP_BACKUP"), cell("B1", "1")))
        rows.append(rowXml(2, cell("A2", "Trip"), cell("B2", payload)))
        // Human-readable header + one row per expense.
        rows.append(rowXml(3, cell("A3", "Description"), cell("B3", "Category"), cell("C3", "Original"),
            cell("D3", "In $destCur"), cell("E3", "Paid by"), cell("F3", "Date")))
        backup.expenses.forEachIndexed { idx, e ->
            val r = idx + 4
            rows.append(rowXml(r,
                cell("A$r", e.description),
                cell("B$r", e.category),
                cell("C$r", "${trimNum(e.originalAmount)} ${e.originalCurrency}"),
                cell("D$r", trimNum(e.amountInDestinationCurrency)),
                cell("E$r", nameOf(e.paidBy)),
                cell("F$r", fmtDate(e.createdAt))))
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"$NS_MAIN\"><sheetData>$rows</sheetData></worksheet>"
    }

    fun read(bytes: ByteArray): List<TripBackup> {
        val result = mutableListOf<TripBackup>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.filter { it.name.startsWith("xl/worksheets/") }.forEach {
                val xml = zip.readBytes().toString(Charsets.UTF_8)
                val cells = Regex("<t[^>]*>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).map { m -> unescape(m.groupValues[1]) }.toList()
                if (cells.getOrNull(0) == "DUITRIP_BACKUP" && cells.getOrNull(1) == "1" && cells.getOrNull(3) != null) {
                    result += fromJson(JSONObject(String(Base64.getDecoder().decode(cells[3]))))
                }
            }
        }
        require(result.isNotEmpty()) { "This is not a Duitrip XLSX backup." }
        return result
    }

    private fun entry(zip: ZipOutputStream, name: String, value: String) { zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
    private fun cell(ref: String, text: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t>${escape(text)}</t></is></c>"
    private fun rowXml(n: Int, vararg cells: String) = "<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun trimNum(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    private fun fmtDate(t: Timestamp?) = t?.toDate()?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) } ?: ""
    private fun sheetName(name: String, n: Int) = (name.replace(Regex("[\\\\/:*?\"<>|]"), " ").ifBlank { "Trip" }).take(28) + "-$n"
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unescape(value: String) = value.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")

    private fun ts(t: Timestamp?) = JSONObject().put("s", t?.seconds).put("n", t?.nanoseconds)
    private fun ts(o: JSONObject) = if (o.isNull("s")) null else Timestamp(o.getLong("s"), o.optInt("n"))
    private fun toJson(b: TripBackup): JSONObject = JSONObject().put("trip", tripJson(b.trip)).put("expenses", JSONArray(b.expenses.map(::expenseJson))).put("settlements", JSONArray(b.settlements.map(::settlementJson)))
    private fun tripJson(t: Trip) = JSONObject().put("id", t.tripId).put("name", t.name).put("dest", t.destination).put("cur", t.destinationCurrency).put("start", t.startDate).put("end", t.endDate).put("by", t.createdBy).put("status", t.status).put("members", JSONArray(t.members.map { m -> JSONObject().put("id", m.userId).put("email", m.email).put("name", m.displayName).put("photo", m.photoURL).put("home", m.homeCurrency).put("role", m.role).put("ghost", m.ghostId).put("joined", ts(m.joinedAt)) })).put("created", ts(t.createdAt)).put("updated", ts(t.updatedAt))
    private fun expenseJson(e: Expense) = JSONObject().put("id", e.expenseId).put("desc", e.description).put("cat", e.category).put("original", e.originalAmount).put("originalCur", e.originalCurrency).put("destCur", e.destinationCurrency).put("amount", e.amountInDestinationCurrency).put("rate", e.exchangeRateUsed).put("rateAt", e.exchangeRateTimestamp).put("mode", e.splitMode).put("paid", e.paidBy).put("by", e.createdBy).put("created", ts(e.createdAt)).put("updated", ts(e.updatedAt)).put("splits", JSONArray(e.splits.map { s -> JSONObject().put("id", s.userId).put("pct", s.percentage).put("dest", s.amountInDestinationCurrency).put("home", s.amountInHomeCurrency).put("cur", s.homeCurrency) }))
    private fun settlementJson(s: Settlement) = JSONObject().put("id", s.settlementId).put("from", s.fromUserId).put("to", s.toUserId).put("amount", s.amountInDestinationCurrency).put("cur", s.destinationCurrency).put("note", s.note).put("at", ts(s.settledAt)).put("by", s.createdBy)
    private fun fromJson(o: JSONObject): TripBackup {
        val t = o.getJSONObject("trip"); val members = t.getJSONArray("members").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { m -> TripMember(m.optString("id").takeIf { !m.isNull("id") }, m.optString("email").takeIf { !m.isNull("email") }, m.optString("name"), m.optString("photo").takeIf { !m.isNull("photo") }, m.optString("home", "USD"), m.optString("role", "member"), ts(m.getJSONObject("joined")), m.optString("ghost").takeIf { !m.isNull("ghost") }) } } }
        val trip = Trip(t.optString("id"), t.optString("name"), t.optString("dest"), t.optString("cur", "USD"), t.optString("start"), t.optString("end"), t.optString("by"), members, members.mapNotNull { it.userId }, status = t.optString("status", "active"), createdAt = ts(t.getJSONObject("created")), updatedAt = ts(t.getJSONObject("updated")))
        fun expenses() = o.getJSONArray("expenses").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { e -> Expense(e.optString("id"), e.optString("desc"), e.optString("cat", "Other"), e.optDouble("original"), e.optString("originalCur"), e.optString("destCur"), e.optDouble("amount"), e.optDouble("rate", 1.0), e.optString("rateAt"), splitMode = e.optString("mode", "equal"), paidBy = e.optString("paid"), splits = e.getJSONArray("splits").let { x -> List(x.length()) { j -> x.getJSONObject(j).let { s -> SplitEntry(s.optString("id"), s.optDouble("pct"), s.optDouble("dest"), s.optDouble("home"), s.optString("cur")) } } }, createdBy = e.optString("by"), createdAt = ts(e.getJSONObject("created")), updatedAt = ts(e.getJSONObject("updated"))) } } }
        fun settlements() = o.getJSONArray("settlements").let { a -> List(a.length()) { i -> a.getJSONObject(i).let { s -> Settlement(s.optString("id"), s.optString("from"), s.optString("to"), s.optDouble("amount"), s.optString("cur"), s.optString("note").takeIf { !s.isNull("note") }, ts(s.getJSONObject("at")), s.optString("by")) } } }
        return TripBackup(trip, expenses(), settlements())
    }
}

class BackupRepository(
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val settlementRepository: SettlementRepository,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun export(uid: String): ByteArray {
        val backups = tripRepository.getUserTrips(uid).map { trip -> TripBackup(trip, expenseRepository.getExpenses(trip.tripId), settlementRepository.getSettlements(trip.tripId)) }
        return XlsxBackup.write(backups)
    }

    /** Imports every worksheet as a new trip, preserving the source data without overwriting any live trip. */
    suspend fun restore(bytes: ByteArray, user: User): Int {
        val backups = XlsxBackup.read(bytes)
        backups.forEach { backup ->
            val tripId = IdGen.trip()
            val members = backup.trip.members.ifEmpty { listOf(TripMember(user.uid, user.email, user.displayName, user.photoURL, user.homeCurrency, "owner", Timestamp.now())) }
            val trip = backup.trip.copy(tripId = tripId, members = members, memberUids = members.mapNotNull { it.userId }.distinct(), createdBy = backup.trip.createdBy.ifBlank { user.uid }, updatedAt = Timestamp.now())
            db.collection("trips").document(tripId).set(trip).await()
            backup.expenses.forEach {
                val expenseId = IdGen.expense()
                db.collection("trips").document(tripId).collection("expenses").document(expenseId).set(it.copy(expenseId = expenseId)).await()
            }
            backup.settlements.forEach {
                val settlementId = IdGen.settlement()
                db.collection("trips").document(tripId).collection("settlements").document(settlementId).set(it.copy(settlementId = settlementId)).await()
            }
        }
        return backups.size
    }
}

package de.chaostheorybot.rykerconnect.ride

import org.json.JSONObject
import java.io.*
import java.util.zip.*

data class BackupPreview(val trips: Map<String, String>, val settings: JSONObject, val software: JSONObject, val assets: Map<String,ByteArray> = emptyMap())

object RideBackup {
    private val tripName = Regex("rides/[a-f0-9-]{36}(\\.jsonl|\\.meta\\.json)")
    fun write(output: OutputStream, trips: Map<String, String>, settings: JSONObject, software: JSONObject, assets: Map<String,ByteArray> = emptyMap()) {
        val sizes=(trips.values+listOf(settings.toString(),software.toString())).map{it.toByteArray(Charsets.UTF_8).size}
        require(trips.size<=4000 && sizes.all{it<=20_000_000} && sizes.sumOf{it.toLong()}+assets.values.sumOf{it.size.toLong()}<=99_990_000){"Backup exceeds the supported archive size; reduce receipt size or export trips separately"}
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("manifest.json", JSONObject().put("format", "RykerConnect").put("version", if(assets.isEmpty())1 else 2).put("created", System.currentTimeMillis()).toString())
            entry("settings.json", settings.toString()); entry("software.json", software.toString())
            trips.forEach { (name, text) -> entry("rides/$name", text) }
            assets.forEach { (name, bytes) -> require(name.matches(Regex("[a-f0-9-]{36}\\.(original|jpg|thumb.jpg)")));zip.putNextEntry(ZipEntry("photos/$name"));zip.write(bytes);zip.closeEntry() }
        }
    }
    fun read(input: InputStream): BackupPreview {
        val entries = LinkedHashMap<String, String>(); val assets=linkedMapOf<String,ByteArray>(); val names=mutableSetOf<String>(); var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(names.size < 10_000 && !entry.isDirectory && names.add(entry.name)) { "Too many or duplicate archive entries" }
                require(entry.name in setOf("manifest.json", "settings.json", "software.json") || tripName.matches(entry.name) || entry.name.matches(Regex("photos/[a-f0-9-]{36}\\.(original|jpg|thumb.jpg)"))) { "Unexpected archive path" }
                val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = zip.read(buffer); if (n < 0) break; total += n; require(total <= 100_000_000 && bytes.size() + n <= 20_000_000) { "Backup is too large" }; bytes.write(buffer, 0, n) }
                if(entry.name.startsWith("photos/"))assets[entry.name.removePrefix("photos/")]=bytes.toByteArray() else entries[entry.name] = bytes.toString("UTF-8")
            }
        }
        val manifest = JSONObject(entries["manifest.json"] ?: error("Missing manifest"))
        require(manifest.getString("format") == "RykerConnect" && manifest.getInt("version") in 1..2) { "Unsupported backup version" }
        val settings = JSONObject(entries["settings.json"] ?: error("Missing settings"))
        for (key in listOf("imperial", "fahrenheit", "twelve", "large", "musicLeft", "navigation", "hide", "all", "priority")) require(settings.get(key) is Boolean) { "Invalid setting" }
        settings.optJSONObject("app")?.let{BackupPreferences.validate(it)}
        val allowed = settings.getJSONArray("allowed"); require(allowed.length() <= 1000)
        for (i in 0 until allowed.length()) require(allowed.get(i) is String && allowed.getString(i).length <= 200)
        val software = JSONObject(entries["software.json"] ?: "{}")
        for(key in listOf("vehicle","reminders","widgetOptions")) if(software.has(key))require(software.get(key) is JSONObject) { "Invalid $key settings" }
        software.optJSONObject("vehicle")?.let(VehicleProfile::validate)
        software.optJSONObject("reminders")?.let(ServiceAlerts::validate)
        software.optJSONObject("widgetOptions")?.let(RykerWidgets::validate)
        if (software.has("serviceTypes")) {
            val catalog = software.getJSONArray("serviceTypes")
            require(catalog.length() <= 10_000)
            val ids = mutableSetOf<String>()
            for (i in 0 until catalog.length()) {
                val item = catalog.getJSONObject(i)
                ServiceCatalog.validate(item)
                require(ids.add(item.getString("id"))) { "Duplicate service type" }
            }
        }
        if (software.has("serviceBaselines")) {
            val baselines = software.getJSONArray("serviceBaselines")
            require(baselines.length() <= 10_000)
            val ids = mutableSetOf<String>()
            for (i in 0 until baselines.length()) {
                val item = baselines.getJSONObject(i); ServiceBaselines.validate(item)
                require(ids.add(item.getString("id"))) { "Duplicate service baseline" }
            }
        }
        for (key in listOf("fuel", "maintenance", "profiles")) {
            val list = software.optJSONArray(key) ?: continue; require(list.length() <= 10_000)
            val ids = mutableSetOf<String>()
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i); require(item.getString("id").length <= 100 && ids.add(item.getString("id")))
                require(item.toString().length < 700_000)
                item.optJSONObject("location")?.let { location ->
                    require(location.getDouble("lat") in -90.0..90.0 && location.getDouble("lon") in -180.0..180.0 && location.getDouble("accuracy") in 0.0..50.0 && location.getLong("time")>0){"Invalid garage location"}
                }
                if(item.has("calculateMpg")) require(item.get("calculateMpg") is Boolean){"Invalid MPG preference"}
                require(item.optString("receipt").length<=670_000 && item.optString("receipt").all{it.isLetterOrDigit() || it in "+/="}) { "Invalid receipt" }
                require(item.optString("notes").length<=2000 && item.optString("parts").length<=500)
                require(item.optInt("intervalDays") in 0..3650)
                if(item.has("missedFill"))require(item.get("missedFill") is Boolean)
                if(key=="maintenance" && item.has("cost"))require(item.getDouble("cost") in 0.0..100_000.0)
                when (key) {
                    "fuel" -> GarageMath.fuel(item.getDouble("odometerKm"), item.getDouble("litres"), item.getDouble("cost"))
                    "maintenance" -> { require(item.getString("name").length in 1..100); require(item.getDouble("odometerKm") in 0.0..2_000_000.0); require(item.getDouble("intervalKm") in 0.0..200_000.0) }
                    "profiles" -> { require(item.getString("name").length in 1..60); item.getJSONObject("preferences") }
                }
            }
        }
        software.optJSONObject("parking")?.let {
            require(it.getDouble("lat") in -90.0..90.0 && it.getDouble("lon") in -180.0..180.0 && it.getDouble("accuracy") in 0.0..50.0 && it.getLong("time") > 0) { "Invalid parking location" }
        }
        val trips = entries.filterKeys { it.startsWith("rides/") }.mapKeys { it.key.removePrefix("rides/") }
        for ((name, content) in trips) {
            if (name.endsWith(".meta.json")) {
                val meta = JSONObject(content); require(meta.optString("title").length <= 100 && meta.optString("notes").length <= 2000)
                require(trips.containsKey(name.removeSuffix(".meta.json") + ".jsonl")) { "Orphan trip metadata" }
            } else {
                val rows = content.lineSequence().filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
                require(rows.isNotEmpty() && rows.first().getLong("start") > 0) { "Invalid trip header" }
                rows.first().optJSONObject("websiteCopy")?.let{record->
                    RideEditValidation.record(record)
                    require(record.getString("id")==name.removeSuffix(".jsonl") && rows.none{it.has("lat")}){"Invalid website copy"}
                }
                var time = 0L
                rows.drop(1).forEach { row ->
                    when {
                        row.has("event") -> {
                            require(row.getString("event") in setOf("pause", "resume")) { "Unknown trip event" }
                            val at = row.get("at")
                            require(at is Number && at.toDouble().isFinite() && at.toDouble() == at.toLong().toDouble() && at.toLong() >= rows.first().getLong("start")) { "Invalid trip event time" }
                            if (row.has("point")) {
                                val point = row.getJSONObject("point")
                                val p = TrackPoint(point.getDouble("lat"), point.getDouble("lon"), point.getLong("time"), point.getDouble("accuracy").toFloat())
                                require(TrackMath.acceptable(null, p) && at.toLong() - p.time in 0..30_000) { "Invalid pause location" }
                            }
                            if (row.has("disconnect")) require(row.getLong("disconnect") >= 0)
                        }
                        row.has("lat") -> { val p = TrackPoint(row.getDouble("lat"), row.getDouble("lon"), row.getLong("time"), row.getDouble("accuracy").toFloat(), row.optDouble("speed").takeIf { it.isFinite() }); require(TrackMath.acceptable(null, p) && p.time >= time) { "Invalid GPS point" }; time = p.time }
                        row.has("end") -> require(row.getLong("end") >= rows.first().getLong("start"))
                        row.has("disconnect") -> require(row.getLong("disconnect") >= 0)
                        else -> error("Unknown trip record")
                    }
                }
            }
        }
        for(key in listOf("plans","journals")){val list=software.optJSONArray(key)?:continue;require(list.length()<=10000);val ids=mutableSetOf<String>();for(i in 0 until list.length()){val item=list.getJSONObject(i);require(ids.add(item.getString("id")));if(key=="plans")MaintenancePlans.validate(item) else {RidePhotos.validate(item);require(trips.containsKey(item.getString("id")+".jsonl")){"Orphan journal"};for(photo in RidePhotos.photos(item))for(ext in listOf("original","jpg","thumb.jpg"))require(assets.containsKey(photo.getString("id")+"."+ext)){"Missing journal image"}}}}
        software.optJSONArray("siteEdits")?.let{edits->
            require(edits.length()<=10000);val ids=mutableSetOf<String>()
            for(i in 0 until edits.length()){val edit=edits.getJSONObject(i);require(ids.add(edit.getString("id")));RideEditValidation.record(edit)}
        }
        for (key in listOf("modifications", "ownershipSeasons")) {
            val list=software.optJSONArray(key)?:continue
            require(list.length()<=1000);val ids=mutableSetOf<String>()
            for (i in 0 until list.length()) {
                val record=list.getJSONObject(i);require(ids.add(record.getString("id"))) { "Duplicate ownership record" }
                if(key=="modifications")ModificationRecords.validate(record) else OwnershipSeasons.validate(record)
            }
        }
        return BackupPreview(trips, settings, software, assets)
    }
}

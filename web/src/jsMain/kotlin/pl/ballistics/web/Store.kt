package pl.ballistics.web

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlin.js.Date

/** The app data, kept in the browser's local storage on this device. */
object Store {
    private const val KEY = "ballistics.data"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    var data: AppData = load()
        private set

    private val listeners = mutableListOf<() -> Unit>()

    /** Called after each change until the screen that registered it closes (see [clearListeners]). */
    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    fun clearListeners() = listeners.clear()

    fun update(change: (AppData) -> AppData) {
        val next = change(data)
        if (next == data) return
        data = next.copy(modifiedAt = Date.now().toLong())
        localStorage.setItem(KEY, encode(data))
        listeners.toList().forEach { it() }
    }

    fun updateConditions(change: (Conditions) -> Conditions) = update { it.copy(conditions = change(it.conditions)) }

    fun replace(imported: AppData) = update { imported }

    private var cacheKey: Any? = null
    private var cached: CalcResult? = null

    /** The table solution for the active rifle; solved again only when its inputs change. */
    val result: CalcResult
        get() {
            val weapon = data.activeWeapon
            val ammo = weapon?.let { data.ammoFor(it) }
            val c = data.conditions
            // Only the target screen reads these, and it solves for them itself.
            val key = Triple(weapon, ammo, c.copy(targetRangeM = "", targetMoving = false, targetSpeedMps = "", targetDirectionDeg = ""))
            if (key != cacheKey || cached == null) {
                cached = Calculator.calculate(weapon, ammo, c)
                cacheKey = key
            }
            return cached!!
        }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    fun decode(text: String): AppData? = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull()

    private fun load(): AppData = localStorage.getItem(KEY)?.let { decode(it) } ?: AppData.sample()
}

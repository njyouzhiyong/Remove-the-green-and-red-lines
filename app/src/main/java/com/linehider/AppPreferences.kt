package com.linehider

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 应用设置持久化（SharedPreferences）。
 *
 * 遮罩线列表以 JSON 数组形式存储，便于扩展。
 * 通过 [SharedPreferences.OnSharedPreferenceChangeListener] 通知设置变化，
 * 覆盖层服务监听后实时刷新。
 */
class AppPreferences(context: Context) {

    internal val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 遮罩总开关（关闭后 OverlayService 不显示覆盖层）。 */
    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    /** 开机自启开关。 */
    var bootAutoStart: Boolean
        get() = prefs.getBoolean(KEY_BOOT_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_AUTO_START, value).apply()

    /**
     * 读取所有遮罩线配置。
     * 始终返回长度不超过 [MaskLine.MAX_LINES] 的列表，按 id 排序。
     */
    fun getLines(): List<MaskLine> {
        val json = prefs.getString(KEY_LINES, null) ?: return defaultLines()
        return try {
            val arr = JSONArray(json)
            val lines = ArrayList<MaskLine>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                lines += MaskLine(
                    id = obj.getInt("id"),
                    x = obj.getInt("x"),
                    width = obj.optInt("width", MaskLine.DEFAULT_WIDTH),
                    color = obj.optInt("color", MaskLine.DEFAULT_COLOR),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
            lines.sortedBy { it.id }.take(MaskLine.MAX_LINES)
        } catch (_: Exception) {
            defaultLines()
        }
    }

    /** 写入遮罩线列表（会去重 id 并裁剪到上限）。 */
    fun setLines(lines: List<MaskLine>) {
        val deduped = lines.distinctBy { it.id }
            .sortedBy { it.id }
            .take(MaskLine.MAX_LINES)
        val arr = JSONArray()
        for (line in deduped) {
            arr.put(
                org.json.JSONObject().apply {
                    put("id", line.id)
                    put("x", line.x)
                    put("width", line.width)
                    put("color", line.color)
                    put("enabled", line.enabled)
                }
            )
        }
        prefs.edit().putString(KEY_LINES, arr.toString()).apply()
    }

    /** 新增一条线，返回新线；若已达上限返回 null。 */
    fun addLine(screenWidth: Int): MaskLine? {
        val current = getLines().toMutableList()
        if (current.size >= MaskLine.MAX_LINES) return null
        val usedIds = current.map { it.id }.toSet()
        val newId = (0 until MaskLine.MAX_LINES).first { it !in usedIds }
        val line = MaskLine(
            id = newId,
            x = screenWidth / 2,
            width = MaskLine.DEFAULT_WIDTH,
            color = MaskLine.DEFAULT_COLOR,
            enabled = true
        )
        current += line
        setLines(current)
        return line
    }

    /** 删除指定 id 的线。 */
    fun removeLine(id: Int) {
        setLines(getLines().filter { it.id != id })
    }

    /** 更新单条线（按 id 覆盖）。 */
    fun updateLine(line: MaskLine) {
        val current = getLines().toMutableList()
        val idx = current.indexOfFirst { it.id == line.id }
        if (idx >= 0) current[idx] = line else current += line
        setLines(current)
    }

    private fun defaultLines(): List<MaskLine> = listOf(
        MaskLine(
            id = 0,
            x = 540,
            width = MaskLine.DEFAULT_WIDTH,
            color = MaskLine.DEFAULT_COLOR,
            enabled = true
        )
    )

    companion object {
        private const val FILE = "line_hider_prefs"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_BOOT_AUTO_START = "boot_auto_start"
        private const val KEY_LINES = "lines"
    }
}

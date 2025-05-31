package com.xposed.hook.entity

import android.os.Parcelable
import com.xposed.hook.App
import kotlinx.parcelize.Parcelize

/**
 * 位置历史记录数据类
 */
@Parcelize
data class LocationHistory(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val lac: String = "",
    val cid: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
) : Parcelable

/**
 * 位置历史记录管理类
 */
object LocationHistoryManager {
    private const val PREF_NAME = "location_history"
    private const val KEY_HISTORY_LIST = "history_list"

    fun saveHistory(history: LocationHistory) {
        val sp = App.current.getSharedPreferences(PREF_NAME, 0)
        val historyList = getHistoryList().toMutableList()

        // 检查是否已存在相同ID的记录，如果存在则更新，否则添加
        val existingIndex = historyList.indexOfFirst { it.id == history.id }
        if (existingIndex != -1) {
            historyList[existingIndex] = history.copy(updateTime = System.currentTimeMillis())
        } else {
            historyList.add(history)
        }

        // 保持最多50条记录
        if (historyList.size > 50) {
            historyList.sortByDescending { it.updateTime }
            historyList.removeAt(historyList.size - 1)
        }

        val json = historyList.joinToString("|") { history ->
            "${history.id},${history.name},${history.latitude},${history.longitude},${history.lac},${history.cid},${history.createTime},${history.updateTime}"
        }

        sp.edit().putString(KEY_HISTORY_LIST, json).apply()
    }

    fun getHistoryList(): List<LocationHistory> {
        val sp = App.current.getSharedPreferences(PREF_NAME, 0)
        val json = sp.getString(KEY_HISTORY_LIST, "") ?: ""

        if (json.isEmpty()) return emptyList()

        return try {
            json.split("|").mapNotNull { item ->
                val parts = item.split(",")
                if (parts.size >= 8) {
                    LocationHistory(
                        id = parts[0].toLongOrNull() ?: 0,
                        name = parts[1],
                        latitude = parts[2],
                        longitude = parts[3],
                        lac = parts[4],
                        cid = parts[5],
                        createTime = parts[6].toLongOrNull() ?: 0,
                        updateTime = parts[7].toLongOrNull() ?: 0
                    )
                } else null
            }.sortedByDescending { it.updateTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteHistory(historyId: Long) {
        val historyList = getHistoryList().toMutableList()
        historyList.removeAll { it.id == historyId }

        val sp = App.current.getSharedPreferences(PREF_NAME, 0)
        val json = historyList.joinToString("|") { history ->
            "${history.id},${history.name},${history.latitude},${history.longitude},${history.lac},${history.cid},${history.createTime},${history.updateTime}"
        }

        sp.edit().putString(KEY_HISTORY_LIST, json).apply()
    }

    fun updateHistoryName(historyId: Long, newName: String) {
        val historyList = getHistoryList().toMutableList()
        val index = historyList.indexOfFirst { it.id == historyId }
        if (index != -1) {
            historyList[index] = historyList[index].copy(
                name = newName,
                updateTime = System.currentTimeMillis()
            )

            val sp = App.current.getSharedPreferences(PREF_NAME, 0)
            val json = historyList.joinToString("|") { history ->
                "${history.id},${history.name},${history.latitude},${history.longitude},${history.lac},${history.cid},${history.createTime},${history.updateTime}"
            }

            sp.edit().putString(KEY_HISTORY_LIST, json).apply()
        }
    }
}
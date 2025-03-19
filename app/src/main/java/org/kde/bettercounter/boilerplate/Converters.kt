package org.kde.bettercounter.boilerplate

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import org.kde.bettercounter.persistence.Group

class Converters {
    companion object {
        @TypeConverter
        @JvmStatic
        fun dateFromTimestamp(value: Long?): Date? {
            return value?.let { Date(it) }
        }

        @TypeConverter
        @JvmStatic
        fun dateToTimestamp(date: Date?): Long? {
            return date?.time
        }

        @JvmStatic
        fun stringListToString(list: List<String>?): String {
            return JSONArray(list).toString()
        }

        @JvmStatic
        fun stringToStringList(jsonStr: String?): List<String> {
            return try {
                val json = JSONArray(jsonStr)
                val ret: MutableList<String> = ArrayList()
                for (i in 0 until json.length()) {
                    ret.add(json.getString(i))
                }
                ret
            } catch (e: JSONException) {
                e.printStackTrace()
                emptyList()
            }
        }

        @JvmStatic
        fun stringToGroupList(value: String?): List<Group> {
            if (value == null) {
                return listOf(Group("default", "默认", 0))
            }
            try {
                val groups = mutableListOf<Group>()
                val jsonArray = JSONArray(value)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val order = jsonObject.getInt("order")
                    groups.add(Group(id, name, order))
                }
                return groups
            } catch (e: Exception) {
                e.printStackTrace()
                return listOf(Group("default", "默认", 0))
            }
        }

        @JvmStatic
        fun groupListToString(value: List<Group>): String {
            val jsonArray = JSONArray()
            for (group in value) {
                val jsonObject = JSONObject()
                jsonObject.put("id", group.id)
                jsonObject.put("name", group.name)
                jsonObject.put("order", group.order)
                jsonArray.put(jsonObject)
            }
            return jsonArray.toString()
        }
    }
}

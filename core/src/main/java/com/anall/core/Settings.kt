package com.anall.core

/** 애드온 설정 항목 정의. 호스트가 이 목록으로 설정 화면을 그린다. */
sealed class SettingSpec(val key: String, val title: String, val description: String)

class SettingHeader(title: String) : SettingSpec("", title, "")

class IntSetting(
    key: String,
    title: String,
    val default: Int,
    val min: Int,
    val max: Int,
    val unit: String = "",
    description: String = "",
) : SettingSpec(key, title, description)

class BoolSetting(
    key: String,
    title: String,
    val default: Boolean,
    description: String = "",
) : SettingSpec(key, title, description)

class TextSetting(
    key: String,
    title: String,
    val default: String,
    description: String = "",
) : SettingSpec(key, title, description)

/** 애드온별로 분리된 키-값 저장소. */
interface SettingsStore {
    fun getInt(key: String, default: Int): Int
    fun getBool(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String): String
    fun putInt(key: String, value: Int)
    fun putBool(key: String, value: Boolean)
    fun putString(key: String, value: String)
    fun remove(key: String)
}

fun SettingsStore.get(spec: IntSetting): Int = getInt(spec.key, spec.default).coerceIn(spec.min, spec.max)
fun SettingsStore.get(spec: BoolSetting): Boolean = getBool(spec.key, spec.default)
fun SettingsStore.get(spec: TextSetting): String = getString(spec.key, spec.default)

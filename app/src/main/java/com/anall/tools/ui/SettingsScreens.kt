package com.anall.tools.ui

import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.anall.core.BoolSetting
import com.anall.core.HostApi
import com.anall.core.IntSetting
import com.anall.core.SettingHeader
import com.anall.core.SettingSpec
import com.anall.core.SettingsStore
import com.anall.core.TextSetting
import com.anall.core.get
import com.anall.core.sound.Sfx
import com.anall.core.sound.SoundChannel
import com.anall.core.sound.SoundLevel
import com.anall.core.ui.MATCH
import com.anall.core.ui.Palette
import com.anall.core.ui.ScaledLayout
import com.anall.core.ui.WRAP
import com.anall.core.ui.add
import com.anall.core.ui.horizontal
import com.anall.core.ui.onClick
import com.anall.core.ui.padding
import com.anall.core.ui.rounded
import com.anall.core.ui.text
import com.anall.core.ui.vertical
import com.anall.tools.AnAllApp
import com.anall.tools.BuildConfig
import com.anall.tools.MainActivity
import com.anall.tools.addon.AddonEntry
import com.anall.tools.ota.OtaUpdater
import java.text.NumberFormat

/** 설정 화면 공통 틀: 상단 바 + 스크롤 목록. */
private abstract class SettingsPage(protected val activity: MainActivity, private val pageTitle: String) : FrameLayout(activity) {

    init {
        setBackgroundColor(Palette.BACKGROUND)
        addView(ScaledLayout(activity) { build() }, LayoutParams(MATCH, MATCH))
    }

    protected abstract fun LinearLayout.content()

    private fun build(): View = with(activity) {
        vertical {
            padding(16, 12)
            add(
                horizontal {
                    add(pillButton("‹ 뒤로") { @Suppress("DEPRECATION") activity.onBackPressed() }, WRAP, 40)
                    add(text(pageTitle, 22, Palette.TEXT, bold = true), marginStart = 14)
                },
                MATCH, 44,
            )
            add(
                ScrollView(activity).apply {
                    isVerticalScrollBarEnabled = true
                    addView(vertical { padding(0, 4); content() })
                },
                MATCH, 0, weight = 1f, marginTop = 10,
            )
        }
    }

    protected fun LinearLayout.section(title: String) {
        add(activity.text(title, 14, Palette.CYAN, bold = true), MATCH, WRAP, marginTop = 18, marginBottom = 6)
    }

    /** 제목/설명 + 오른쪽 값(또는 스위치). */
    protected fun LinearLayout.row(title: String, desc: String, value: View?, action: (() -> Unit)?) {
        add(
            activity.horizontal {
                background = activity.rounded(Palette.PANEL, 10, Palette.PANEL_BORDER, 1)
                padding(14, 10)
                minimumHeight = (56 * resources.displayMetrics.density).toInt()
                add(
                    activity.vertical {
                        add(activity.text(title, 16, Palette.TEXT, bold = true))
                        if (desc.isNotBlank()) add(activity.text(desc, 12, Palette.TEXT_DIM), marginTop = 3)
                    },
                    0, WRAP, weight = 1f,
                )
                if (value != null) add(value, marginStart = 12)
                if (action != null) onClick(action)
            },
            MATCH, WRAP, marginTop = 6,
        )
    }

    protected fun valueText(v: String) = activity.text(v, 16, Palette.AMBER, bold = true)

    protected fun switch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(activity).apply {
        isChecked = checked
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    protected fun button(label: String, color: Int = Palette.CHIP_IDLE, action: () -> Unit) =
        activity.pillButton(label, color, if (color == Palette.CHIP_IDLE) Palette.TEXT else Palette.DARK_TEXT, action)

    protected fun askText(title: String, current: String, hint: String, password: Boolean = false, onOk: (String) -> Unit) {
        val input = EditText(activity).apply {
            // setSingleLine 은 입력 방식을 덮어쓰므로 먼저 부른다 (안 그러면 토큰이 그대로 보인다).
            setSingleLine()
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            if (password) transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            setText(current)
            this.hint = hint
            setSelection(text.length)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(FrameLayout(activity).apply { padding(20, 8); addView(input) })
            .setPositiveButton("확인") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("취소", null)
            .show()
    }

    protected fun askInt(spec: IntSetting, current: Int, onOk: (Int) -> Unit) {
        val input = EditText(activity).apply {
            setText(current.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setSelection(text.length)
            hint = "${spec.min} ~ ${spec.max}"
        }
        AlertDialog.Builder(activity)
            .setTitle("${spec.title} (${spec.unit.ifBlank { "값" }})")
            .setMessage(spec.description.ifBlank { null })
            .setView(FrameLayout(activity).apply { padding(20, 8); addView(input) })
            .setPositiveButton("확인") { _, _ ->
                val v = input.text.toString().toIntOrNull()
                if (v == null || v < spec.min || v > spec.max) {
                    activity.toast("${spec.min} ~ ${spec.max} 사이로 입력하세요")
                } else {
                    onOk(v)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

/** 안올툴 설정: 애드온 목록, OTA 업데이트, 앱 정보. */
private class HostSettingsPage(activity: MainActivity, private val app: AnAllApp) : SettingsPage(activity, "안올툴 설정") {

    override fun LinearLayout.content() {
        val u = app.updater
        val m = app.manager

        section("애드온")
        m.entries.forEach { e ->
            val info = e.addon.info
            val src = if (e.source == AddonEntry.Source.OTA) "OTA" else "내장"
            val hasSettings = runCatching { e.addon.settings().isNotEmpty() }.getOrDefault(false)
            row(
                "${info.name}  v${info.versionName}",
                "$src · ${info.description}",
                if (hasSettings) valueText("설정 ›") else null,
                if (hasSettings) ({ activity.showAddonSettings(e.id) }) else null,
            )
        }
        m.loadErrors.forEach { (id, err) -> row("$id 불러오기 실패", err, null, null) }

        section("소리")
        val snd = app.sound
        volumeRow("마스터 볼륨", "모든 애드온 소리 전체", snd.master, { snd.master = it }) {
            snd.play(Sfx.BEEP, SoundChannel.EFFECT, SoundLevel.CONFIRM)
        }
        volumeRow("음성", "안내 멘트·음성", snd.voice, { snd.voice = it }) {
            snd.play(Sfx.NOTIFY, SoundChannel.VOICE, SoundLevel.VOICE)
        }
        volumeRow("효과음", "버튼음·기계음·알림음", snd.effect, { snd.effect = it }) {
            snd.play(Sfx.BEEP, SoundChannel.EFFECT, SoundLevel.CONFIRM)
        }

        section("OTA 업데이트")
        row("업데이트 저장소", "GitHub 릴리즈의 addons-index.json 을 확인합니다", valueText(u.repo)) {
            askText("업데이트 저장소 (owner/repo)", u.repo, "owner/repo") { v ->
                if (runCatching { OtaUpdater.parseRepo(v) }.isSuccess) u.repo = v else activity.toast("owner/repo 형식으로 입력하세요")
                activity.refresh()
            }
        }
        row(
            "GitHub 토큰",
            "비공개 저장소일 때만 필요 (Contents 읽기 전용 권한)",
            valueText(if (u.token.isBlank()) "없음" else "설정됨"),
        ) {
            askText("GitHub 토큰", "", "github_pat_… (비우면 삭제)", password = true) { v ->
                u.token = v
                activity.refresh()
            }
        }
        row("자동 업데이트", "앱을 켤 때 1시간마다 확인하고 바로 적용합니다", switch(u.autoUpdate) { u.autoUpdate = it }, null)
        if (BuildConfig.DEBUG) {
            row("개발용 인덱스 주소", "비우면 GitHub 릴리즈를 씁니다", valueText(u.indexUrlOverride.ifBlank { "없음" })) {
                askText("addons-index.json 주소", u.indexUrlOverride, "http://10.0.2.2:8000/addons-index.json") { v ->
                    u.indexUrlOverride = v
                    activity.refresh()
                }
            }
        }
        val status = activity.text(
            if (u.checking) "확인 중…" else u.lastSummary.ifBlank { "아직 확인하지 않았습니다" },
            13, Palette.TEXT_DIM,
        )
        add(
            activity.horizontal {
                add(button("지금 확인", Palette.CYAN) {
                    status.text = "확인 중…"
                    u.checkNow { report ->
                        activity.toast(report.summary)
                        activity.refresh()
                    }
                }, WRAP, 44)
                if (m.installed.all().isNotEmpty()) {
                    add(button("내장 버전으로 되돌리기") {
                        AlertDialog.Builder(activity)
                            .setTitle("OTA 로 받은 애드온 삭제")
                            .setMessage("받아 둔 애드온 파일을 지우고 앱에 들어 있는 버전으로 되돌립니다.")
                            .setPositiveButton("삭제") { _, _ ->
                                m.installed.removeAll()
                                m.reload()
                                activity.refresh()
                            }
                            .setNegativeButton("취소", null)
                            .show()
                    }, WRAP, 44, marginStart = 10)
                }
                add(status, 0, WRAP, weight = 1f, marginStart = 14)
            },
            MATCH, WRAP, marginTop = 10,
        )

        section("앱 정보")
        row("안올툴 (An-All Tools)", "v${BuildConfig.VERSION_NAME} · Host API ${HostApi.VERSION}", null, null)
        add(space(), MATCH, 20)
    }

    private fun space() = View(activity)

    /** 0~100 볼륨 슬라이더. 손을 떼면 미리듣기 소리를 낸다. */
    private fun LinearLayout.volumeRow(title: String, desc: String, value: Int, onChange: (Int) -> Unit, preview: () -> Unit) {
        val label = activity.text("$value%", 16, Palette.AMBER, bold = true, gravity = Gravity.END or Gravity.CENTER_VERTICAL)
        val bar = SeekBar(activity).apply {
            max = 100
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    label.text = "$p%"
                    if (fromUser) onChange(p)
                }

                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = preview()
            })
        }
        row(
            title, desc,
            activity.horizontal {
                add(bar, 300, 40)
                add(label, 56, WRAP)
            },
            null,
        )
    }
}

/** 애드온이 선언한 설정 항목으로 자동 생성되는 설정 화면. */
private class AddonSettingsPage(
    activity: MainActivity,
    private val app: AnAllApp,
    private val addonId: String,
) : SettingsPage(activity, "${app.manager.entry(addonId)?.addon?.info?.name ?: addonId} 설정") {

    private val store: SettingsStore get() = com.anall.tools.HostBridge(app, addonId).settings
    private val numberFmt = NumberFormat.getIntegerInstance()

    override fun LinearLayout.content() {
        val specs: List<SettingSpec> = app.manager.entry(addonId)?.let { runCatching { it.addon.settings() }.getOrNull() } ?: emptyList()
        val s = store
        if (app.manager.isBusy(addonId)) {
            add(activity.text("운행 중에 바꾼 요금 설정은 다음 운행부터 적용됩니다.", 13, Palette.AMBER), MATCH, WRAP, marginTop = 4)
        }
        specs.forEach { spec ->
            when (spec) {
                is SettingHeader -> section(spec.title)
                is IntSetting -> row(spec.title, spec.description, valueText(numberFmt.format(s.get(spec)) + " " + spec.unit)) {
                    askInt(spec, s.get(spec)) { v -> s.putInt(spec.key, v); changed() }
                }
                is BoolSetting -> row(spec.title, spec.description, switch(s.get(spec)) { v -> s.putBool(spec.key, v); app.manager.notifySettingsChanged(addonId) }, null)
                is TextSetting -> row(spec.title, spec.description, valueText(s.get(spec))) {
                    askText(spec.title, s.get(spec), spec.default) { v -> s.putString(spec.key, v); changed() }
                }
            }
        }
        add(
            activity.horizontal {
                gravity = Gravity.END
                add(button("기본값으로 되돌리기") {
                    AlertDialog.Builder(activity)
                        .setTitle("기본값으로 되돌리기")
                        .setMessage("이 애드온의 설정을 모두 기본값으로 되돌립니다.")
                        .setPositiveButton("되돌리기") { _, _ ->
                            specs.forEach { if (it.key.isNotEmpty()) s.remove(it.key) }
                            changed()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }, WRAP, 44)
            },
            MATCH, WRAP, marginTop = 18, marginBottom = 20,
        )
    }

    private fun changed() {
        app.manager.notifySettingsChanged(addonId)
        activity.refresh()
    }
}

@Suppress("FunctionName")
fun HostSettingsScreen(activity: MainActivity, app: AnAllApp): View = HostSettingsPage(activity, app)

@Suppress("FunctionName")
fun AddonSettingsScreen(activity: MainActivity, app: AnAllApp, id: String): View = AddonSettingsPage(activity, app, id)

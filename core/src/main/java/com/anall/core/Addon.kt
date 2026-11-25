package com.anall.core

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import com.anall.core.sound.SoundChannel
import com.anall.core.sound.SoundClip
import com.anall.core.sound.SoundLevel

/**
 * 호스트 API 버전. core 의 공개 API 를 늘릴 때마다 올린다.
 * 애드온은 addon.properties 의 minHostApi 로 필요한 최소 버전을 적고, 호스트는 그보다 낮으면 OTA 를 건너뛴다.
 */
object HostApi {
    /**
     * 1: 최초 (애드온·세션·설정·포그라운드 작업)
     * 2: 소리 — [AddonHost.playSound], com.anall.core.sound (표준 레벨·채널·합성기)
     */
    const val VERSION = 2
}

/** 애드온 소개 정보. */
class AddonInfo(
    /** 영문 소문자 고유 ID. addon.properties 의 id 와 같아야 한다. */
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val description: String = "",
    /** 홈 화면 타일 강조색 (ARGB). */
    val accentColor: Int = 0xFF6FD3FF.toInt(),
)

/**
 * 안올툴 애드온의 진입점. 매개변수 없는 생성자가 있어야 하고, 생성자에서는 무거운 일을 하지 않는다.
 *
 * OTA 로 받은 옛 애드온이 새 호스트에서도 돌아가도록 인터페이스 대신 추상 클래스를 쓴다.
 * (메서드를 추가할 때 기본 구현을 주면 바이너리 호환이 유지된다.)
 */
abstract class Addon {
    abstract val info: AddonInfo

    /** 호스트 설정 화면에 자동으로 그려지는 설정 항목들. */
    open fun settings(): List<SettingSpec> = emptyList()

    /** 홈 화면 아이콘. null 이면 호스트가 이름 첫 글자로 그린다. */
    open fun createIcon(context: Context): Drawable? = null

    /** 애드온을 처음 열 때 한 번 만든다. 호스트는 사용자가 닫거나 업데이트될 때까지 세션을 들고 있다. */
    abstract fun createSession(host: AddonHost): AddonSession
}

/**
 * 실행 중인 애드온. 화면은 [createView] 로 매번 새로 만들 수 있고(회전, 다시 열기),
 * 운행 기록처럼 화면보다 오래 살아야 하는 상태는 세션에 둔다.
 */
abstract class AddonSession(val host: AddonHost) {
    /** 애드온 화면. Activity 컨텍스트가 넘어온다. */
    abstract fun createView(context: Context): View

    /** 화면에 나타났을 때. */
    open fun onShow() {}

    /** 홈으로 나가거나 앱이 가려졌을 때. 화면 참조는 여기서 놓는다. */
    open fun onHide() {}

    /** 뒤로 가기를 직접 처리했으면 true. */
    open fun onBackPressed(): Boolean = false

    /** 사용자가 설정을 바꿨을 때. */
    open fun onSettingsChanged() {}

    /** true 면 화면에 없어도 끝내면 안 되는 작업 중 (예: 운행 중). 업데이트 적용도 미룬다. */
    open val isBusy: Boolean get() = false

    /** 세션 종료. 리소스를 모두 정리한다. */
    open fun onDestroy() {}
}

/** 호스트가 애드온에 빌려주는 기능. 애드온은 매니페스트를 가질 수 없으므로 권한·서비스는 여기를 거친다. */
interface AddonHost {
    val appContext: Context
    val hostApi: Int
    val mainHandler: Handler

    /** 이 애드온 전용 설정 저장소. */
    val settings: SettingsStore

    fun goHome()
    fun openSettings()
    fun toast(text: String)
    fun setKeepScreenOn(on: Boolean)

    fun hasPermission(permission: String): Boolean
    fun requestPermissions(permissions: Array<String>, onResult: (allGranted: Boolean) -> Unit)

    /**
     * 화면을 벗어나도 계속 돌아야 하는 작업을 위해 포그라운드 서비스를 띄운다 (위치 사용 가능).
     * 위치 권한이 없으면 띄우지 못하고 false 를 돌려준다.
     */
    fun startBackgroundWork(title: String, text: String): Boolean
    fun updateBackgroundWork(title: String, text: String)
    fun stopBackgroundWork()

    /**
     * 효과음/음성을 재생한다 (HostApi 2+). 사용자의 마스터·채널 볼륨이 자동으로 곱해진다.
     * [levelDb] 는 [SoundLevel] 의 표준 등급 중에서 고른다. 짧은 소리는 다른 앱 음악과 섞여서 난다.
     */
    fun playSound(clip: SoundClip, channel: SoundChannel = SoundChannel.EFFECT, levelDb: Float = SoundLevel.CONFIRM)
}

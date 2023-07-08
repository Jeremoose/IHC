package com.novodin.ihc

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.content.ContentResolver
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager.LayoutParams.*
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.novodin.ihc.fragments.ProjectSelection
import com.novodin.ihc.fragments.StandbyScreen
import com.novodin.ihc.zebra.BarcodeScanner



class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val androidId: String = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        DeviceManager.getInstance().setDeviceId(androidId)

        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, StandbyScreen())
            commit()
        }
    }
}

class DeviceManager private constructor() {

    private var androidId: String? = null

    companion object {
        private var instance: DeviceManager? = null

        @Synchronized
        fun getInstance(): DeviceManager {
            if (instance == null) {
                instance = DeviceManager()
            }
            return instance!!
        }
    }

    fun setDeviceId(androidId: String) {
        this.androidId = androidId
    }

    fun getDeviceId(): String {
        if (androidId == null) {
            androidId = "noAndroidId"
        }
        return androidId as String
    }
}

class SessionManager private constructor() {

    private var sessionId: String? = null
    private var isActive: Boolean? = null

    companion object {
        private var instance: SessionManager? = null

        @Synchronized
        fun getInstance(): SessionManager {
            if (instance == null) {
                instance = SessionManager()
            }
            return instance!!
        }
    }

    fun setSessionId(sessionId: String) {
        this.sessionId = sessionId
    }

    fun getSessionId(): String? {
        return sessionId
    }

    fun setSessionState(state: Boolean) {
        this.isActive = state
    }

    fun getSessionState(): Boolean? {
        return isActive
    }

    fun clearSessionId() {
        sessionId = null
    }
}
package com.example.zipmedia.util

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 全屏沉浸（隐藏状态栏/导航栏）与横竖屏自适应 */
object Immersive {

    private var hidden = false

    /** 进入沉浸模式；返回当前是否处于沉浸状态 */
    fun enter(activity: Activity) {
        hidden = true
        activity.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    /** 离开沉浸模式 */
    fun exit(activity: Activity) {
        hidden = false
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    /** 保屏幕常亮 */
    fun keepScreenOn(activity: Activity, on: Boolean) {
        if (on) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun isPortrait(activity: Activity): Boolean =
        activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}
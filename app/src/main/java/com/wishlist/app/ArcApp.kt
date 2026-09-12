package com.wishlist.app

import android.app.Application

class ArcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

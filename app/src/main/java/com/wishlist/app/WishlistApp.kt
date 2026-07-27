package com.wishlist.app

import android.app.Application

class WishlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

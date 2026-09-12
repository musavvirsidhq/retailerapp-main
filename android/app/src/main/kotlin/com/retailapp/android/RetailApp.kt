package com.retailapp.android

import android.app.Application

class RetailApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: RetailApp
            private set
    }
}

package com.example.a4all

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config: HashMap<String, String> = HashMap()
        config["cloud_name"] = "dib4zp8ke"  // 🔹 replace with Cloudinary cloud_name

        MediaManager.init(this, config)
    }
}

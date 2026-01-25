package io.ahxxm.ic

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        cleanupOrphanedCacheFiles()
    }

    private fun cleanupOrphanedCacheFiles() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
}

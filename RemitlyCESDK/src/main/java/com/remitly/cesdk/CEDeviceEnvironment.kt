package com.remitly.cesdk

import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.remitly.cesdk.RemitlyCE.Companion.TAG


internal data class DeviceEnvironmentProps(
    val id: String,
    val hash: String
)

internal class CEDeviceEnvironment(val remitly: RemitlyCE) {
    companion object {
        const val DE_ID_KEY = "de_id"
        const val DE_HASH_KEY = "de_hash"
        const val SHARED_PREFERENCES_FILE = "com.remitly.cesdk.SHARED_PREFERENCES"
    }

    private var prefs: SharedPreferences? = null
    private var id: String? = null
    private var hash: String? = null

    init {
        Thread {
            prefs = remitly.hostActivity?.getSharedPreferences(
                SHARED_PREFERENCES_FILE,
                AppCompatActivity.MODE_PRIVATE
            )

            id = prefs?.getString(DE_ID_KEY, null)
            hash = prefs?.getString(DE_HASH_KEY, null)
        }.start()
    }

    fun set(deviceEnvironment: DeviceEnvironmentProps) {
        if (deviceEnvironment.id != id || deviceEnvironment.hash != hash) {
            Log.d(TAG, "Updating DeviceEnvironment: $deviceEnvironment")
            id = deviceEnvironment.id
            hash = deviceEnvironment.hash
            Thread {
                prefs?.run {
                    edit().putString(DE_ID_KEY, deviceEnvironment.id)
                        .putString(DE_HASH_KEY, deviceEnvironment.hash)
                        .apply()
                }
            }.start()
        }
    }

    fun delete() {
        Log.d(TAG, "Deleting DeviceEnvironment prefs")
        id = null
        hash = null
        Thread {
            prefs?.run {
                edit().remove(DE_ID_KEY)
                    .remove(DE_HASH_KEY)
                    .apply()
            }
        }.start()
    }

    fun get(): DeviceEnvironmentProps? {
        Log.v(TAG, "Got DeviceEnvironment: $id, $hash")
        return if (!id.isNullOrEmpty() && !hash.isNullOrEmpty()) {
            DeviceEnvironmentProps(id!!, hash!!)
        } else {
            null
        }
    }
}

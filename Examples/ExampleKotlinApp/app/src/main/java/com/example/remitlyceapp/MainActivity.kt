package com.example.remitlyceapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.remitlyceapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.remitly.cesdk.RemitlyCE
import com.remitly.cesdk.RemitlyCEConfiguration

class MainActivity : AppCompatActivity() {

    private val TAG = "RemitlyExample"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Extend the RemitlyCE class to add event handlers
        class ExampleRemitly : RemitlyCE() {

            // Heartbeat event called when user is active in the app
            override fun onUserActivity() {
                Log.d(TAG, "Example app onUserActivity callback called!")
            }

            // Event fired when user successfully submits a transaction request
            override fun onTransferSubmitted() {
                Log.d(TAG, "Example app onTransferSubmitted callback called!")
            }

            // Get notified of errors
            override fun onError(error: Throwable) {
                Log.d(TAG, "Example app onError callback called! ${error.message}", error)
            }
        }


        //  Instantiate the SDK
        val remitly: RemitlyCE = ExampleRemitly()


        // You can set configuration in the AndroidManifest or here in code
        val config = RemitlyCEConfiguration.build {
            defaultSendCountry = "USA"
            defaultReceiveCountry = "PHL"
            languageCode = "en"
            customerEmail = "example@remitly.com"
        }


        // Validate the config
        val isValidConfig = remitly.loadConfig(this, config)


        binding.buttonSendMoney.isEnabled = isValidConfig
        binding.buttonLogOut.isEnabled = isValidConfig


        fun onSendMoneyClick() {

            // Launch the experience
            remitly.present()

        }


        fun onLogoutClick() {

            // Make sure to call logout when the user logs out of the host app
            val didLogoutSucceed = remitly.logout()

            when (didLogoutSucceed) {
                true -> Snackbar.make(binding.root, "Remitly logged out successfully", 2000).show()
                false -> Snackbar.make(binding.root, "Error logging out of Remitly", 2000).show()
            }
        }


        binding.buttonSendMoney.setOnClickListener { onSendMoneyClick() }

        binding.buttonLogOut.setOnClickListener { onLogoutClick() }
    }

}

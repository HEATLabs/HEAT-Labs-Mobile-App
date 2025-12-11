package com.heatalabs.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var splashContainer: RelativeLayout
    private lateinit var splashLogo: ImageView

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inflate splash screen
        val splashView = layoutInflater.inflate(R.layout.splash_layout, null)
        splashContainer = splashView.findViewById(R.id.splash_container)
        splashLogo = splashView.findViewById(R.id.splash_logo)

        // Add splash to container
        findViewById<android.widget.FrameLayout>(R.id.container).addView(splashView)

        // Set appropriate logo based on theme
        setLogoBasedOnTheme()

        // Start fade in animation
        splashLogo.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.splash_fade_in)
        )

        // Start WebViewActivity and wait for page to load
        val intent = Intent(this, WebViewActivity::class.java)
        startActivity(intent)

        // Wait for page to load (you can adjust timing as needed)
        Handler(Looper.getMainLooper())
            .postDelayed(
                { fadeOutSplashAndStartWebView() },
                2000
            ) // 2 seconds delay for demonstration
    }

    private fun setLogoBasedOnTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> {
                // Dark mode
                splashLogo.setImageResource(R.drawable.logo_dark)
            }
            Configuration.UI_MODE_NIGHT_NO -> {
                // Light mode
                splashLogo.setImageResource(R.drawable.logo_light)
            }
            else -> {
                // Default to light
                splashLogo.setImageResource(R.drawable.logo_light)
            }
        }
    }

    private fun fadeOutSplashAndStartWebView() {
        // Fade out animation
        val fadeOut =
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.splash_fade_out)
        splashContainer.startAnimation(fadeOut)
        splashLogo.startAnimation(fadeOut)

        fadeOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Animation ended, remove splash
                    splashContainer.visibility = View.GONE
                    // Close MainActivity
                    finish()
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            }
        )
    }
}

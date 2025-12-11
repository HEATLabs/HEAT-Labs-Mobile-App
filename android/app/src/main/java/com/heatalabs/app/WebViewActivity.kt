package com.heatalabs.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var splashOverlay: RelativeLayout
    private lateinit var splashLogo: ImageView
    private var isPageLoaded = false
    private var isSplashMinTimePassed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // Get splash overlay views
        splashOverlay = findViewById(R.id.splash_overlay)
        splashLogo = findViewById(R.id.splash_logo)

        // Hide status bar by default
        hideStatusBar()

        // Set appropriate logo based on theme
        setLogoBasedOnTheme()

        // Start fade in animation for logo
        splashLogo.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in))

        webView = findViewById(R.id.webview)

        // Configure WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportMultipleWindows(false)

        // Set WebViewClient to handle link clicks and page loading
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoaded = true
                checkAndHideSplash()
                // Inject JavaScript to handle link clicks
                injectLinkHandler()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                return handleUrlLoading(url)
            }

            @Deprecated("Deprecated in API 24")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUrlLoading(url ?: "")
            }

            private fun handleUrlLoading(url: String): Boolean {
                return when {
                    // Handle internal navigation
                    url.startsWith("https://heatlabs.net") -> {
                        // Load in WebView
                        false
                    }
                    url.startsWith("http://heatlabs.net") -> {
                        // Load in WebView
                        false
                    }
                    // Handle subdomains
                    url.contains(".heatlabs.net") &&
                            (url.startsWith("https://") || url.startsWith("http://")) -> {
                        false
                    }
                    // Handle relative URLs
                    !url.contains("://") && !url.startsWith("javascript:") -> {
                        false
                    }
                    // Handle mailto, tel, etc.
                    url.startsWith("mailto:") || url.startsWith("tel:") ||
                            url.startsWith("sms:") || url.startsWith("intent:") -> {
                        // Open in appropriate app
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                    // External links
                    else -> {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        true
                    }
                }
            }
        }

        // Set minimum splash time (2 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            isSplashMinTimePassed = true
            checkAndHideSplash()
        }, 2000)

        // Set up back button handling
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        // Load the website
        webView.loadUrl("https://heatlabs.net")
    }

    private fun setLogoBasedOnTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> {
                // Dark mode
                splashLogo.setImageResource(R.drawable.logo_dark)
                // Update background for dark theme
                splashOverlay.setBackgroundResource(R.drawable.splash_background)
            }
            Configuration.UI_MODE_NIGHT_NO -> {
                // Light mode
                splashLogo.setImageResource(R.drawable.logo_light)
                // Update background for light theme
                splashOverlay.setBackgroundResource(R.drawable.splash_background)
            }
            else -> {
                // Default to light
                splashLogo.setImageResource(R.drawable.logo_light)
                splashOverlay.setBackgroundResource(R.drawable.splash_background)
            }
        }
    }

    private fun checkAndHideSplash() {
        // Only hide splash when both conditions are met:
        // 1. Minimum splash time has passed (2 seconds)
        // 2. Web page has loaded
        if (isSplashMinTimePassed && isPageLoaded) {
            fadeOutSplash()
        }
    }

    private fun fadeOutSplash() {
        val fadeOut = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_out)
        splashOverlay.startAnimation(fadeOut)
        splashLogo.startAnimation(fadeOut)

        fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}

            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                // Animation ended, hide splash overlay
                splashOverlay.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
    }

    private fun injectLinkHandler() {
        webView.evaluateJavascript("""
            (function() {
                // Prevent default behavior for all links
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    
                    // Find the closest anchor tag
                    while (target && target.tagName !== 'A') {
                        target = target.parentElement;
                    }
                    
                    if (target && target.tagName === 'A') {
                        var href = target.getAttribute('href');
                        
                        // Check if it's an internal link
                        if (href && (
                            href.startsWith('/') || 
                            href.startsWith('#') ||
                            href.startsWith('javascript:') ||
                            href.includes('heatlabs.net')
                        )) {
                            // Allow default behavior for internal links
                            return true;
                        }
                        
                        // For external links, they'll be handled by Android's WebViewClient
                    }
                }, true);
                
                // Also prevent middle-click and right-click opening in new tabs
                document.addEventListener('auxclick', function(e) {
                    if (e.button === 1) { // Middle click
                        e.preventDefault();
                    }
                });
                
                // Prevent context menu on links
                document.addEventListener('contextmenu', function(e) {
                    var target = e.target;
                    while (target && target.tagName !== 'A') {
                        target = target.parentElement;
                    }
                    if (target && target.tagName === 'A') {
                        e.preventDefault();
                    }
                });
            })();
        """.trimIndent(), null)
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
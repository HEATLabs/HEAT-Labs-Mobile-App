package com.heatalabs.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // Hide status bar by default
        hideStatusBar()

        webView = findViewById(R.id.webview)

        // Configure WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportMultipleWindows(false)

        // Set WebViewClient to handle link clicks
        webView.webViewClient = object : WebViewClient() {
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject JavaScript to handle link clicks
                injectLinkHandler()
            }
        }

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
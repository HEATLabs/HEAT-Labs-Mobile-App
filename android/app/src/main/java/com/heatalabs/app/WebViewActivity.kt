package com.heatalabs.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class WebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var splashOverlay: RelativeLayout
    private lateinit var splashLogo: ImageView
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var loadingSpinner: ProgressBar
    private var isPageLoaded = false
    private var isSplashMinTimePassed = false
    private var isFirstLoad = true
    private var hasNetworkError = false
    private var currentErrorCode: String? = null
    private val okHttpClient = OkHttpClient()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        // Get splash overlay views
        splashOverlay = findViewById(R.id.splash_overlay)
        splashLogo = findViewById(R.id.splash_logo)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingSpinner = findViewById(R.id.loading_spinner)

        // Hide status bar by default
        hideStatusBar()

        // Set appropriate logo based on theme
        setLogoBasedOnTheme()

        // Start fade in animation for logo
        splashLogo.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.splash_fade_in)
        )

        // Check network connectivity before loading
        if (!isNetworkAvailable()) {
            currentErrorCode = "NET-001"
            showNoInternetPage()
            return
        }

        // Load tracking pixel only on first load
        if (isFirstLoad) {
            loadTrackingPixel()
        }

        webView = findViewById(R.id.webview)

        // Configure WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setSupportMultipleWindows(false)

        // Set WebViewClient to handle link clicks, page loading, and errors
        webView.webViewClient =
            object : WebViewClient() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    isPageLoaded = false
                    hasNetworkError = false
                    currentErrorCode = null

                    // Show loading spinner for navigation
                    if (!isFirstLoad) {
                        showLoadingSpinner()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // Double-check network before considering page loaded
                    if (
                        !isNetworkAvailable() &&
                        url != null &&
                        !url.contains("file:///android_asset/")
                    ) {
                        currentErrorCode = "NET-002"
                        showNoInternetPage()
                        return
                    }

                    // Only proceed if no error occurred
                    if (!hasNetworkError) {
                        isPageLoaded = true
                        isFirstLoad = false

                        if (isSplashMinTimePassed) {
                            hideSplashAndLoading()
                        } else {
                            // If splash time hasn't passed yet, just hide loading spinner
                            hideLoadingSpinner()
                        }

                        // Inject JavaScript to handle link clicks
                        injectLinkHandler()
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    handleWebViewError(error?.errorCode ?: -1)
                }

                // Keep deprecated method for older Android versions
                @SuppressLint("ObsoleteSdkInt")
                @Deprecated(
                    "Deprecated in API 23",
                    ReplaceWith("onReceivedError(WebView, WebResourceRequest, WebResourceError)")
                )
                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    // Only use this method on older Android versions
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        handleWebViewError(errorCode)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    return handleUrlLoading(url)
                }

                // Keep deprecated method for older Android versions
                @SuppressLint("ObsoleteSdkInt")
                @Deprecated(
                    "Deprecated in API 24",
                    ReplaceWith("shouldOverrideUrlLoading(WebView, WebResourceRequest)")
                )
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Only use this method on older Android versions
                    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        handleUrlLoading(url ?: "")
                    } else {
                        super.shouldOverrideUrlLoading(view, url)
                    }
                }

                private fun handleUrlLoading(url: String): Boolean {
                    // Check network connectivity before loading any URL
                    if (!isNetworkAvailable()) {
                        currentErrorCode = "NET-003"
                        showNoInternetPage()
                        return true
                    }

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
                        url.startsWith("mailto:") ||
                                url.startsWith("tel:") ||
                                url.startsWith("sms:") ||
                                url.startsWith("intent:") -> {
                            // Hide loading spinner if shown
                            hideLoadingSpinner()
                            // Open in appropriate app
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            true
                        }
                        // External links
                        else -> {
                            // Hide loading spinner if shown
                            hideLoadingSpinner()
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
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
        Handler(Looper.getMainLooper())
            .postDelayed(
                {
                    isSplashMinTimePassed = true
                    checkAndHideSplash()
                },
                2000
            )

        // Set up back button handling
        val callback =
            object : OnBackPressedCallback(true) {
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

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // For Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities =
                    connectivityManager.getNetworkCapabilities(network) ?: return false

                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                // For older Android versions
                @Suppress("DEPRECATION") val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION") return networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun showNoInternetPage() {
        runOnUiThread {
            hideLoadingSpinner()
            val errorCode = currentErrorCode ?: "NET-000"
            webView.loadUrl("file:///android_asset/no_internet.html?error=$errorCode")
            hasNetworkError = true
            isPageLoaded = true

            // Still hide splash after minimum time
            if (isSplashMinTimePassed) {
                hideSplashAndLoading()
            }
        }
    }

    private fun showErrorPage(errorCode: String, webViewErrorCode: Int? = null) {
        runOnUiThread {
            hideLoadingSpinner()

            // Load appropriate error
            val finalErrorCode = currentErrorCode ?: errorCode
            when (webViewErrorCode) {
                ERROR_HOST_LOOKUP,
                ERROR_CONNECT,
                ERROR_TIMEOUT -> {
                    webView.loadUrl("file:///android_asset/no_internet.html?error=$finalErrorCode")
                }
                ERROR_FILE_NOT_FOUND -> {
                    webView.loadUrl(
                        "file:///android_asset/page_not_found.html?error=$finalErrorCode"
                    )
                }
                else -> {
                    webView.loadUrl(
                        "file:///android_asset/generic_error.html?error=$finalErrorCode"
                    )
                }
            }

            hasNetworkError = true
            isPageLoaded = true

            // Still hide splash after minimum time
            if (isSplashMinTimePassed) {
                hideSplashAndLoading()
            }
        }
    }

    private fun handleWebViewError(errorCode: Int) {
        hasNetworkError = true

        // Determine error type and code
        when (errorCode) {
            ERROR_HOST_LOOKUP -> {
                currentErrorCode = "WEB-001"
                // Network connectivity issues
                showNoInternetPage()
            }
            ERROR_CONNECT -> {
                currentErrorCode = "WEB-002"
                // Network connectivity issues
                showNoInternetPage()
            }
            ERROR_TIMEOUT -> {
                currentErrorCode = "WEB-003"
                // Network connectivity issues
                showNoInternetPage()
            }
            ERROR_FILE_NOT_FOUND -> {
                currentErrorCode = "WEB-404"
                // Page not found
                showErrorPage("WEB-404", errorCode)
            }
            ERROR_TOO_MANY_REQUESTS -> {
                currentErrorCode = "WEB-429"
                showErrorPage("WEB-429", errorCode)
            }
            ERROR_INTERNAL_SERVER -> {
                currentErrorCode = "WEB-500"
                showErrorPage("WEB-500", errorCode)
            }
            ERROR_BAD_GATEWAY -> {
                currentErrorCode = "WEB-502"
                showErrorPage("WEB-502", errorCode)
            }
            ERROR_SERVICE_UNAVAILABLE -> {
                currentErrorCode = "WEB-503"
                showErrorPage("WEB-503", errorCode)
            }
            else -> {
                currentErrorCode = "WEB-999"
                // Generic error
                showErrorPage("WEB-999", errorCode)
            }
        }
    }

    // WebView error constants
    companion object {
        private const val ERROR_HOST_LOOKUP = -2
        private const val ERROR_CONNECT = -6
        private const val ERROR_TIMEOUT = -8
        private const val ERROR_FILE_NOT_FOUND = -12
        private const val ERROR_TOO_MANY_REQUESTS = -15
        private const val ERROR_INTERNAL_SERVER = -16
        private const val ERROR_BAD_GATEWAY = -17
        private const val ERROR_SERVICE_UNAVAILABLE = -18
    }

    private fun loadTrackingPixel() {
        // Only load tracking pixel if network is available
        if (!isNetworkAvailable()) {
            return
        }

        val trackingUrl =
            "https://views.heatlabs.net/api/track/pcwstats-tracker-pixel-android-app.png"

        val request =
            Request.Builder()
                .url(trackingUrl)
                .header("User-Agent", "HEAT Labs Android App")
                .get()
                .build()

        okHttpClient
            .newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        // Silent failure
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // Tracking pixel loaded successfully
                        response.close()
                    }
                }
            )
    }

    private fun setLogoBasedOnTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> {
                splashLogo.setImageResource(R.drawable.logo_dark)
                loadingOverlay.setBackgroundResource(R.drawable.loading_background_dark)
            }
            Configuration.UI_MODE_NIGHT_NO -> {
                splashLogo.setImageResource(R.drawable.logo_light)
                loadingOverlay.setBackgroundResource(R.drawable.loading_background_light)
            }
            else -> {
                splashLogo.setImageResource(R.drawable.logo_light)
                loadingOverlay.setBackgroundResource(R.drawable.loading_background_light)
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
        val fadeOut =
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.splash_fade_out)
        splashOverlay.startAnimation(fadeOut)
        splashLogo.startAnimation(fadeOut)

        fadeOut.setAnimationListener(
            object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}

                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    // Animation ended, hide splash overlay
                    splashOverlay.visibility = View.GONE
                    isFirstLoad = false
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            }
        )
    }

    private fun showLoadingSpinner() {
        runOnUiThread {
            if (loadingOverlay.visibility != View.VISIBLE) {
                loadingOverlay.visibility = View.VISIBLE
                loadingSpinner.visibility = View.VISIBLE
                // Fade in animation
                loadingOverlay.startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(
                        this,
                        R.anim.loading_fade_in
                    )
                )
            }
        }
    }

    private fun hideLoadingSpinner() {
        runOnUiThread {
            if (loadingOverlay.isVisible) {
                // Fade out animation
                val fadeOut =
                    android.view.animation.AnimationUtils.loadAnimation(
                        this,
                        R.anim.loading_fade_out
                    )
                loadingOverlay.startAnimation(fadeOut)
                fadeOut.setAnimationListener(
                    object : android.view.animation.Animation.AnimationListener {
                        override fun onAnimationStart(
                            animation: android.view.animation.Animation?
                        ) {}

                        override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                            loadingOverlay.visibility = View.GONE
                            loadingSpinner.visibility = View.GONE
                        }

                        override fun onAnimationRepeat(
                            animation: android.view.animation.Animation?
                        ) {}
                    }
                )
            }
        }
    }

    private fun hideSplashAndLoading() {
        runOnUiThread {
            // Hide splash if it's still visible
            if (splashOverlay.isVisible) {
                fadeOutSplash()
            }
            // Hide loading spinner if it's visible
            hideLoadingSpinner()
        }
    }

    private fun injectLinkHandler() {
        webView.evaluateJavascript(
            """
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
        """
                .trimIndent(),
            null
        )
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()

        // Check if we're on error page and retry if connection is restored
        if (hasNetworkError && isNetworkAvailable()) {
            hasNetworkError = false
            currentErrorCode = null
            webView.loadUrl("https://heatlabs.net")
            showLoadingSpinner()
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        okHttpClient.dispatcher.executorService.shutdown()
    }
}

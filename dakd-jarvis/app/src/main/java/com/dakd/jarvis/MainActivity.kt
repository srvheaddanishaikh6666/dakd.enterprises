package com.dakd.jarvis

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import com.example.R

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var appLauncher: AppLauncher
    private lateinit var contactManager: ContactManager
    private lateinit var whatsAppManager: WhatsAppManager
    private lateinit var torchManager: TorchManager
    private lateinit var fileManager: FileManager
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var aiClient: AIClient
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var bridge: JarvisBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers
        settingsManager = SettingsManager(this)
        permissionManager = PermissionManager(this)
        appLauncher = AppLauncher(this)
        contactManager = ContactManager(this)
        whatsAppManager = WhatsAppManager(this, appLauncher, contactManager)
        torchManager = TorchManager(this)
        fileManager = FileManager(this)
        ttsManager = TTSManager(this, settingsManager)
        voiceManager = VoiceManager(this, permissionManager)
        aiClient = AIClient(settingsManager)
        commandExecutor = CommandExecutor(
            this,
            appLauncher,
            whatsAppManager,
            contactManager,
            torchManager,
            fileManager,
            permissionManager,
            settingsManager,
            ttsManager
        )

        // Setup WebView
        webView = WebView(this)
        setContentView(webView)

        setupWebView()

        // Handle Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.onAndroidBack ? window.onAndroidBack() : false") { value ->
                    if (value != "true") {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })

        // Start optional foreground service
        if (settingsManager.isForegroundNotificationEnabled()) {
            try {
                JarvisForegroundService.start(this)
            } catch (e: Exception) {
                // ignore
            }
        }

        // Proactively check audio permission
        if (!permissionManager.hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        bridge = JarvisBridge(
            this,
            webView,
            aiClient,
            commandExecutor,
            voiceManager,
            ttsManager,
            torchManager,
            appLauncher,
            settingsManager,
            permissionManager
        )

        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("DAKD_JARVIS_WEB", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Notify frontend that bridge is connected
                bridge.sendEvent("bridge_ready", org.json.JSONObject().apply {
                    put("status", "connected")
                })
            }
        }

        // Load asset index.html
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permStatus = permissionManager.getPermissionsStatus()
        bridge.sendEvent("permissions_update", permStatus)
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) {
            bridge.sendEvent("accessibility_update", org.json.JSONObject().apply {
                put("enabled", JarvisAccessibilityService.isServiceRunning())
            })
            bridge.sendEvent("permissions_update", permissionManager.getPermissionsStatus())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
        voiceManager.destroy()
    }
}

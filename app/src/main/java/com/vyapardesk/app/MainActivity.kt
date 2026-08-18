package com.vyapardesk.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.allowFileAccessFromFileURLs = true
        ws.allowUniversalAccessFromFileURLs = true
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ws.useWideViewPort = true
        ws.loadWithOverviewMode = true
        ws.builtInZoomControls = false
        ws.displayZoomControls = false
        ws.mediaPlaybackRequiresUserGesture = false

        WebView.setWebContentsDebuggingEnabled(true)

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: ActivityNotFoundException) {
                        return false
                    }
                }
                // Keep all file:///android_asset and http(s) inside WebView
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Ensure Android bridge is ready - injection is also via android-bridge.js file
                view?.evaluateJavascript(
                    "(function(){ if(window.Android && !window.__vyaparAndroidPatched){ var s=document.createElement('script'); s.src='android-bridge.js'; document.head.appendChild(s);} })();",
                    null
                )
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                if (url.startsWith("blob:")) {
                    Toast.makeText(this, "Preparing file…", Toast.LENGTH_SHORT).show()
                    // Blob handling is done via JS bridge (android-bridge.js intercepts anchor clicks)
                    // Fallback: inject JS to handle this specific blob url if bridge missed it
                    webView.evaluateJavascript(
                        """
                        (function(){
                          fetch("$url").then(r=>r.blob()).then(b=>{
                            var r=new FileReader();
                            r.onload=function(){
                              var b64=r.result.split(',')[1];
                              Android.saveBase64File(b64, "${URLUtil.guessFileName(url, contentDisposition, mimeType)}", "$mimeType");
                            };
                            r.readAsDataURL(b);
                          });
                        })();
                        """.trimIndent(), null
                    )
                    return@setDownloadListener
                }
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading $fileName")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // Load VyaparDesk PWA from assets
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    inner class WebAppInterface {

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun shareText(title: String, text: String) {
            runOnUiThread {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TITLE, title)
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, title))
            }
        }

        /**
         * Called from JS when a blob download is intercepted.
         * Saves base64 file to cache and triggers share sheet + also saves to Downloads.
         */
        @JavascriptInterface
        fun saveBase64File(base64Data: String, fileName: String, mimeType: String) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    // Save to cache for sharing
                    val cacheFile = File(cacheDir, fileName)
                    FileOutputStream(cacheFile).use { it.write(bytes) }

                    // Also try to save to Downloads via MediaStore-like path (external files)
                    try {
                        val downloadsFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        downloadsFile.parentFile?.mkdirs()
                        FileOutputStream(downloadsFile).use { it.write(bytes) }
                    } catch (_: Exception) {}

                    // Share via FileProvider
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        cacheFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType.ifEmpty { "application/octet-stream" }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // Also offer save to Downloads notification
                    Toast.makeText(this@MainActivity, "Saved $fileName — sharing…", Toast.LENGTH_SHORT).show()
                    startActivity(Intent.createChooser(shareIntent, "Share $fileName"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "File save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun shareBase64File(base64Data: String, fileName: String, mimeType: String) {
            saveBase64File(base64Data, fileName, mimeType)
        }

        @JavascriptInterface
        fun downloadBase64File(base64Data: String, fileName: String, mimeType: String) {
            saveBase64File(base64Data, fileName, mimeType)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

package com.higa.resultados

import android.annotation.SuppressLint
import android.os.Bundle
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebChromeClient

import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var pdfWeb: WebView
    private lateinit var closePdfButton: Button
    private lateinit var dni: EditText
    private lateinit var from: EditText
    private lateinit var to: EditText
    private lateinit var status: TextView
    private lateinit var nextButton: Button

    private var listaDni: List<String> = emptyList()
    private var indiceActual = 0

    private val url =
        "http://higamdp.ddns.net:8070/intranet/higa/index.php?action=laboratorio&subaction=consulta-resultados"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
        pdfWeb = findViewById(R.id.pdfWebView)
        closePdfButton = findViewById(R.id.closePdfButton)
        pdfWeb.visibility = android.view.View.GONE
        closePdfButton.visibility = android.view.View.GONE
        dni = findViewById(R.id.dniInput)
        from = findViewById(R.id.fromDate)
        to = findViewById(R.id.toDate)
        status = findViewById(R.id.status)
        nextButton = findViewById(R.id.nextButton)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(true)

        CookieManager.getInstance().setAcceptCookie(true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(
                view: WebView?,
                pageUrl: String?
            ) {
                status.text = "Página HIGA cargada"
            }
        }

        pdfWeb.settings.javaScriptEnabled = true
        pdfWeb.settings.domStorageEnabled = true

        val descargaPdf = android.webkit.DownloadListener {
            descargaUrl, userAgent, contentDisposition, mimeType, contentLength ->

            try {
                val nombre = URLUtil.guessFileName(
                    descargaUrl,
                    contentDisposition,
                    mimeType
                )

                val solicitud = DownloadManager.Request(Uri.parse(descargaUrl))
                val cookies = CookieManager.getInstance().getCookie(descargaUrl)

                if (!cookies.isNullOrEmpty()) {
                    solicitud.addRequestHeader("Cookie", cookies)
                }

                if (!userAgent.isNullOrEmpty()) {
                    solicitud.addRequestHeader("User-Agent", userAgent)
                }

                solicitud.setMimeType(mimeType ?: "application/pdf")
                solicitud.setTitle(nombre)
                solicitud.setDescription("Descargando resultado de laboratorio")
                solicitud.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                solicitud.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    nombre
                )

                val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                gestor.enqueue(solicitud)

                Toast.makeText(
                    this,
                    "Descarga iniciada. Revisá Descargas.",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "No se pudo iniciar la descarga: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        web.setDownloadListener(descargaPdf)
        pdfWeb.setDownloadListener(descargaPdf)


        pdfWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                status.text = "Documento abierto"
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val mensaje = resultMsg ?: return false
                val transporte =
                    mensaje.obj as? WebView.WebViewTransport ?: return false

                pdfWeb.stopLoading()
                pdfWeb.loadUrl("about:blank")

                web.visibility = android.view.View.GONE
                pdfWeb.visibility = android.view.View.VISIBLE
                closePdfButton.visibility = android.view.View.VISIBLE

                transporte.webView = pdfWeb
                mensaje.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                pdfWeb.visibility = android.view.View.GONE
                closePdfButton.visibility = android.view.View.GONE
                web.visibility = android.view.View.VISIBLE
            }
        }

        closePdfButton.setOnClickListener {
            pdfWeb.loadUrl("about:blank")
            pdfWeb.visibility = android.view.View.GONE
            closePdfButton.visibility = android.view.View.GONE
            web.visibility = android.view.View.VISIBLE
        }

        web.loadUrl(url)

        findViewById<Button>(R.id.searchButton).setOnClickListener {
            iniciarConsultas()
        }

        nextButton.setOnClickListener {
            siguienteDni()
        }

        findViewById<Button>(R.id.pdfListButton).setOnClickListener {
            web.visibility = android.view.View.GONE
            pdfWeb.visibility = android.view.View.GONE
            findViewById<android.view.View>(R.id.pdfListContainer).visibility =
                android.view.View.VISIBLE
        }

        findViewById<Button>(R.id.closePdfListButton).setOnClickListener {
            findViewById<android.view.View>(R.id.pdfListContainer).visibility =
                android.view.View.GONE
            web.visibility = android.view.View.VISIBLE
        }
    }

    private fun iniciarConsultas() {from.setText("01/01/${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}")
to.setText(
    String.format(
        "%02d/%02d/%04d",
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
        java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    )
)

        listaDni = dni.text.toString()
            .lines()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                it.all { c -> c.isDigit() }
            }

        if (listaDni.isEmpty()) {
            status.text = "Ingresá al menos un DNI"
            return
        }

        indiceActual = 0
        nextButton.isEnabled = false

        consultarActual()
    }

    private fun siguienteDni() {

        indiceActual++

        if (indiceActual >= listaDni.size) {
            status.text = "Consultas terminadas: ${listaDni.size}"
            nextButton.isEnabled = false
            return
        }

        nextButton.isEnabled = false
        consultarActual()
    }

    private fun consultarActual() {

        val documento = listaDni[indiceActual]
        val fechaDesde = from.text.toString().trim()
        val fechaHasta = to.text.toString().trim()

        status.text =
            "Consultando $documento (${indiceActual + 1}/${listaDni.size})"

        val javascript = """
            (function() {

                function setValue(element, value) {
                    if (!element) return;

                    var setter =
                        Object.getOwnPropertyDescriptor(
                            HTMLInputElement.prototype,
                            'value'
                        );

                    if (setter && setter.set) {
                        setter.set.call(element, value);
                    } else {
                        element.value = value;
                    }

                    element.dispatchEvent(
                        new Event('input', {bubbles:true})
                    );

                    element.dispatchEvent(
                        new Event('change', {bubbles:true})
                    );
                }

                var selects =
                    [...document.querySelectorAll('select')];

                var documentSelect =
                    selects.find(function(select) {
                        return /D\.?N\.?I\.?|DNI/i.test(
                            select.innerText || ''
                        );
                    });

                if (documentSelect) {

                    var option =
                        [...documentSelect.options].find(
                            function(o) {
                                return /D\.?N\.?I\.?|DNI/i.test(o.text);
                            }
                        );

                    if (option) {

                        documentSelect.value =
                            option.value;

                        documentSelect.dispatchEvent(
                            new Event('change', {
                                bubbles:true
                            })
                        );
                    }
                }

                var inputs =
                    [...document.querySelectorAll('input')]
                    .filter(function(x) {
                        return x.offsetParent !== null;
                    });

                var documentInput =
                    inputs.find(function(x) {

                        return /dni|document|documento/i.test(
                            (x.name || '') + ' ' +
                            (x.id || '') + ' ' +
                            (x.placeholder || '')
                        );
                    });

                if (!documentInput && inputs.length > 0) {
                    documentInput = inputs[0];
                }

                setValue(
                    documentInput,
                    '$documento'
                );

                var dateInputs =
                    inputs.filter(function(x) {

                        return /date|fecha/i.test(
                            (x.name || '') + ' ' +
                            (x.id || '') + ' ' +
                            (x.placeholder || '')
                        );
                    });

                if (dateInputs[0] && '$fechaDesde') {
                    setValue(
                        dateInputs[0],
                        '$fechaDesde'
                    );
                }

                if (dateInputs[1] && '$fechaHasta') {
                    setValue(
                        dateInputs[1],
                        '$fechaHasta'
                    );
                }

                setTimeout(function() {

                    var buttons =
                        [...document.querySelectorAll(
                            'button,input,a'
                        )];

                    var buscar =
                        buttons.find(function(x) {

                            return /buscar/i.test(
                                (x.innerText ||
                                 x.value ||
                                 '').trim()
                            );
                        });

                    if (buscar) {
                        buscar.click();
                    }

                }, 700);

            })();
        """.trimIndent()

        web.evaluateJavascript(javascript, null)

        nextButton.isEnabled = true
    }
}

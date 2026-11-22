package com.higa.resultados

import android.annotation.SuppressLint
import android.os.Bundle
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

        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transporte =
                    resultMsg?.obj as? WebView.WebViewTransport ?: return false

                transporte.webView = web
                resultMsg.sendToTarget()
                return true
            }
        }

        web.loadUrl(url)

        findViewById<Button>(R.id.searchButton).setOnClickListener {
            iniciarConsultas()
        }

        nextButton.setOnClickListener {
            siguienteDni()
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

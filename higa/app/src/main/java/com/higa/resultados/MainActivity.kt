package com.higa.resultados

import android.annotation.SuppressLint
import android.os.Bundle
import android.content.Intent
import android.content.SharedPreferences
import android.view.Gravity
import org.json.JSONArray
import org.json.JSONObject
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
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
    private var pdfWeb: WebView? = null
    private lateinit var pdfContainer: android.widget.FrameLayout
    private lateinit var closePdfButton: Button
    private lateinit var dni: EditText
    private lateinit var from: EditText
    private lateinit var to: EditText
    private lateinit var status: TextView
    private lateinit var nextButton: Button
    private lateinit var pdfTable: TableLayout
    private lateinit var preferencias: SharedPreferences

    private var listaDni: List<String> = emptyList()
    private var indiceActual = 0

    private val url =
        "http://higamdp.ddns.net:8070/intranet/higa/index.php?action=laboratorio&subaction=consulta-resultados"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
       pdfContainer = findViewById(R.id.pdfContainer)
closePdfButton = findViewById(R.id.closePdfButton)
pdfContainer.visibility = android.view.View.GONE
closePdfButton.visibility = android.view.View.GONE
dni = findViewById(R.id.dniInput)
        from = findViewById(R.id.fromDate)
        to = findViewById(R.id.toDate)
        status = findViewById(R.id.status)
        nextButton = findViewById(R.id.nextButton)
        pdfTable = findViewById(R.id.pdfTable)
        preferencias = getSharedPreferences("pdf_descargados", MODE_PRIVATE)

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
                val downloadId = gestor.enqueue(solicitud)
                guardarPdfRegistro(downloadId, nombre)

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

        val nuevoPdfWeb = WebView(this@MainActivity)

        nuevoPdfWeb.settings.javaScriptEnabled = true
        nuevoPdfWeb.settings.domStorageEnabled = true

        nuevoPdfWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                status.text = "Documento abierto"
            }
        }

        nuevoPdfWeb.setDownloadListener(descargaPdf)

        pdfContainer.removeAllViews()
        pdfContainer.addView(
            nuevoPdfWeb,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        pdfWeb = nuevoPdfWeb

        web.visibility = android.view.View.GONE
        pdfContainer.visibility = android.view.View.VISIBLE
        closePdfButton.visibility = android.view.View.VISIBLE

        transporte.webView = nuevoPdfWeb
        mensaje.sendToTarget()

        return true
    }

    override fun onCloseWindow(window: WebView?) {
        pdfWeb?.destroy()
        pdfWeb = null
        pdfContainer.removeAllViews()

        pdfContainer.visibility = android.view.View.GONE
        closePdfButton.visibility = android.view.View.GONE
        web.visibility = android.view.View.VISIBLE
    }
}

        closePdfButton.setOnClickListener {

    pdfWeb?.stopLoading()
    pdfWeb?.destroy()
    pdfWeb = null

    pdfContainer.removeAllViews()
    pdfContainer.visibility = android.view.View.GONE

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
    actualizarTablaPdf()
    web.visibility = android.view.View.GONE
    pdfWeb?.visibility = android.view.View.GONE
    pdfContainer.visibility = android.view.View.GONE
    findViewById<android.view.View>(R.id.pdfListContainer).visibility =
        android.view.View.VISIBLE

        }

        findViewById<Button>(R.id.closePdfListButton).setOnClickListener {
            findViewById<android.view.View>(R.id.pdfListContainer).visibility =
                android.view.View.GONE
            web.visibility = android.view.View.VISIBLE
        }
    }


    private fun guardarPdfRegistro(downloadId: Long, nombre: String) {
        val registros = JSONArray(preferencias.getString("lista", "[]"))
        val registro = JSONObject()

        registro.put("id", downloadId)
        registro.put(
            "dni",
            listaDni.getOrNull(indiceActual) ?: dni.text.toString().trim()
        )
        registro.put("nombre", nombre)
        registro.put(
            "fecha",
            java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
        )

        registros.put(registro)
        preferencias.edit().putString("lista", registros.toString()).apply()

        runOnUiThread {
            actualizarTablaPdf()
        }
    }

    private fun actualizarTablaPdf() {
        pdfTable.removeAllViews()

        val encabezado = TableRow(this)
        listOf("DNI", "Fecha", "Archivo", "Acción").forEach {
            val celda = TextView(this)
            celda.text = it
            celda.setPadding(8, 12, 8, 12)
            celda.gravity = Gravity.CENTER
            celda.textSize = 14f
            encabezado.addView(celda)
        }
        pdfTable.addView(encabezado)

        val registros = JSONArray(preferencias.getString("lista", "[]"))

        for (i in 0 until registros.length()) {
            val registro = registros.getJSONObject(i)
            val id = registro.getLong("id")
            val fila = TableRow(this)

            fun agregarTexto(valor: String) {
                val texto = TextView(this)
                texto.text = valor
                texto.setPadding(8, 12, 8, 12)
                texto.gravity = Gravity.CENTER
                texto.textSize = 12f
                fila.addView(texto)
            }

            agregarTexto(registro.optString("dni"))
            agregarTexto(registro.optString("fecha"))
            agregarTexto(registro.optString("nombre"))

            val acciones = android.widget.LinearLayout(this)
            acciones.orientation = android.widget.LinearLayout.VERTICAL

            val abrir = Button(this)
            abrir.text = "Abrir"
            abrir.setOnClickListener {
                try {
                    val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val uri = gestor.getUriForDownloadedFile(id)

                    if (uri == null) {
                        Toast.makeText(
                            this,
                            "El PDF todavía no está disponible.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "No se pudo abrir el PDF.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val eliminar = Button(this)
            eliminar.text = "Eliminar"
            eliminar.setOnClickListener {
                val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                gestor.remove(id)

                val nuevos = JSONArray()
                for (j in 0 until registros.length()) {
                    val elemento = registros.getJSONObject(j)
                    if (elemento.getLong("id") != id) {
                        nuevos.put(elemento)
                    }
                }

                preferencias.edit()
                    .putString("lista", nuevos.toString())
                    .apply()

                actualizarTablaPdf()
            }

            acciones.addView(abrir)
            acciones.addView(eliminar)
            fila.addView(acciones)
            pdfTable.addView(fila)
        }

        if (registros.length() == 0) {
            val fila = TableRow(this)
            val texto = TextView(this)
            texto.text = "Todavía no hay PDF registrados."
            texto.setPadding(12, 20, 12, 20)
            fila.addView(texto)
            pdfTable.addView(fila)
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

        web.evaluateJavascript(javascript) { resultado ->
            runOnUiThread {
                nextButton.isEnabled = true
            }
        }
    }
}

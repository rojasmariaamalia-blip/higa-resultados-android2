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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
class MainActivity : ComponentActivity() {
    companion object {
        private var limpiezaSesionHecha = false
    }


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
    private lateinit var resultsTable: TableLayout

    private lateinit var determinationsTable: TableLayout
    private lateinit var preferencias: SharedPreferences

    private var listaDni: List<String> = emptyList()
    private var indiceActual = 0

    private var receptorDescargasRegistrado = false

    private val receptorDescargas = object : android.content.BroadcastReceiver() {
        override fun onReceive(
            context: android.content.Context?,
            intent: Intent?
        ) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val downloadId = intent.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID,
                -1L
            )
            if (downloadId == -1L) return

            val pdfs = JSONArray(preferencias.getString("lista", "[]"))
            var perteneceALaApp = false

            for (i in 0 until pdfs.length()) {
                if (pdfs.getJSONObject(i).optLong("id") == downloadId) {
                    perteneceALaApp = true
                    break
                }
            }

            if (!perteneceALaApp) return

            val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val consulta = DownloadManager.Query().setFilterById(downloadId)

            gestor.query(consulta)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columna = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (columna >= 0 &&
                        cursor.getInt(columna) == DownloadManager.STATUS_SUCCESSFUL
                    ) {
                        val procesados = preferencias.getStringSet(
                            "pdf_procesados_auto",
                            emptySet()
                        ) ?: emptySet()

                        if (!procesados.contains(downloadId.toString())) {
                            leerTextoPdfAutomatico(downloadId)
                        }
                    }
                }
            }
        }
    }

    private val url =
        "http://higamdp.ddns.net:8070/intranet/higa/index.php?action=laboratorio&subaction=consulta-resultados"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
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
        resultsTable = findViewById(R.id.resultsTable)
        determinationsTable = findViewById(R.id.determinationsTable)

        findViewById<Button>(R.id.downloadResultsButton)
            .setOnClickListener {
                descargarResultadosCsv()
            }

        preferencias = getSharedPreferences("pdf_descargados", MODE_PRIVATE)

        if (!limpiezaSesionHecha) {
            limpiarDatosSesion()
            limpiezaSesionHecha = true
        }


        val filtroDescargas = android.content.IntentFilter(
            DownloadManager.ACTION_DOWNLOAD_COMPLETE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receptorDescargas,
                filtroDescargas,
                android.content.Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receptorDescargas, filtroDescargas)
        }

        receptorDescargasRegistrado = true

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
                esperarYLeerPdfAutomaticamente(downloadId)

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

        view?.evaluateJavascript("""
            (function() {
                return JSON.stringify(
                    [...document.querySelectorAll(
                        'a, button, input, [role="button"]'
                    )].map(function(e) {
                        return {
                            tag: e.tagName,
                            text: (e.innerText || e.value || '').trim(),
                            href: e.href || '',
                            type: e.type || '',
                            id: e.id || '',
                            className: e.className || ''
                        };
                    })
                );
            })();
        """.trimIndent()) { resultado ->
            android.util.Log.d(
                "HIGA_PDF_DOM",
                "URL=$pageUrl ELEMENTOS=$resultado"
            )
            }
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

            val leer = Button(this)
            leer.text = "Leer texto"
            leer.setOnClickListener {
                leerTextoPdf(id)
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
            acciones.addView(leer)
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

        actualizarTablaResultados()
    }


    private fun actualizarTablaResultados() {
        resultsTable.removeAllViews()

        val encabezado = TableRow(this)
        listOf("DNI", "Fecha", "Archivo", "Estado", "Acción").forEach {
            val celda = TextView(this)
            celda.text = it
            celda.setPadding(8, 12, 8, 12)
            celda.gravity = Gravity.CENTER
            celda.textSize = 13f
            celda.setTypeface(null, android.graphics.Typeface.BOLD)
            encabezado.addView(celda)
        }
        resultsTable.addView(encabezado)

        val resultados = JSONArray(
            preferencias.getString("resultados", "[]")
        )

        for (i in 0 until resultados.length()) {
            val registro = resultados.getJSONObject(i)
            val id = registro.optLong("downloadId")
            val fila = TableRow(this)

            fun agregarTexto(valor: String) {
                val celda = TextView(this)
                celda.text = valor
                celda.setPadding(8, 12, 8, 12)
                celda.gravity = Gravity.CENTER
                celda.textSize = 11f
                fila.addView(celda)
            }

            agregarTexto(registro.optString("dni"))
            agregarTexto(registro.optString("fecha"))
            agregarTexto(registro.optString("archivo"))
            agregarTexto(registro.optString("estado"))

            val acciones = android.widget.LinearLayout(this)
            acciones.orientation = android.widget.LinearLayout.VERTICAL

            val verTexto = Button(this)
            verTexto.text = "Ver texto"
            verTexto.setOnClickListener {
                val textoOriginal = registro.optString("textoOriginal")
                val vista = TextView(this).apply {
                    text = textoOriginal
                    setTextIsSelectable(true)
                    setPadding(32, 24, 32, 24)
                }
                val scroll = android.widget.ScrollView(this).apply {
                    addView(vista)
                }

                android.app.AlertDialog.Builder(this)
                    .setTitle("Texto extraído")
                    .setView(scroll)
                    .setPositiveButton("Cerrar", null)
                    .show()
            }

            val abrirPdf = Button(this)
            abrirPdf.text = "Abrir PDF"
            abrirPdf.setOnClickListener {
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

            acciones.addView(verTexto)
            acciones.addView(abrirPdf)
            fila.addView(acciones)
            resultsTable.addView(fila)
        }

        if (resultados.length() == 0) {
            val fila = TableRow(this)
            val texto = TextView(this)
            texto.text = "Todavía no hay textos extraídos."
            texto.setPadding(12, 20, 12, 20)
            fila.addView(texto)
            resultsTable.addView(fila)
        }

        actualizarTablaDeterminaciones()
    }


    private fun leerTextoPdf(downloadId: Long) {
        val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri = gestor.getUriForDownloadedFile(downloadId)

        if (uri == null) {
            Toast.makeText(this, "El PDF todavía no está disponible.", Toast.LENGTH_LONG).show()
            return
        }

        Thread {
            val texto = try {
                contentResolver.openInputStream(uri)?.use { entrada ->
                    PDDocument.load(entrada).use { documento ->
                        PDFTextStripper().getText(documento)
                    }
                } ?: ""
            } catch (e: Exception) {
                null
            }

            runOnUiThread {
                if (texto.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "No se pudo extraer texto del PDF.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    guardarResultadosExtraidos(downloadId, texto)

                    val vista = TextView(this).apply {
                        text = texto
                        setTextIsSelectable(true)
                        setPadding(32, 24, 32, 24)
                    }

                    val scroll = android.widget.ScrollView(this).apply {
                        addView(vista)
                    }

                    android.app.AlertDialog.Builder(this)
                        .setTitle("Texto del PDF")
                        .setView(scroll)
                        .setPositiveButton("Cerrar", null)
                        .show()
                }
            }
        }.start()
    }


    private fun esperarYLeerPdfAutomaticamente(downloadId: Long) {
        val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        var intentos = 0
        val maxIntentos = 120

        val comprobar = object : Runnable {
            override fun run() {
                intentos++

                try {
                    val procesados = preferencias.getStringSet(
                        "pdf_procesados_auto",
                        emptySet()
                    ) ?: emptySet()

                    if (procesados.contains(downloadId.toString())) {
                        return
                    }

                    val consulta = DownloadManager.Query()
                        .setFilterById(downloadId)

                    gestor.query(consulta)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnaEstado =
                                cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                            if (columnaEstado >= 0) {
                                when (cursor.getInt(columnaEstado)) {

                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        leerTextoPdfAutomatico(downloadId)
                                        return
                                    }

                                    DownloadManager.STATUS_FAILED -> {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No se pudo descargar el PDF.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (intentos < maxIntentos) {
                    handler.postDelayed(this, 1000)
                }
            }
        }

        handler.post(comprobar)
    }


    private fun leerTextoPdfAutomatico(downloadId: Long) {
        val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri = gestor.getUriForDownloadedFile(downloadId)

        if (uri == null) {
            preferencias.edit()
                .putStringSet(
                    "pdf_procesados_auto",
                    (preferencias.getStringSet("pdf_procesados_auto", emptySet())
                        ?: emptySet()) - downloadId.toString()
                )
                .apply()
            return
        }

        Thread {
            val texto = try {
                contentResolver.openInputStream(uri)?.use { entrada ->
                    PDDocument.load(entrada).use { documento ->
                        PDFTextStripper().getText(documento)
                    }
                } ?: ""
            } catch (e: Exception) {
                ""
            }

            runOnUiThread {
                if (texto.isBlank()) {
                    val procesados = preferencias.getStringSet(
                        "pdf_procesados_auto",
                        emptySet()
                    ) ?: emptySet()

                    preferencias.edit()
                        .putStringSet(
                            "pdf_procesados_auto",
                            procesados - downloadId.toString()
                        )
                        .apply()

                    Toast.makeText(
                        this,
                        "No se pudo leer el PDF. Podés usar Leer texto.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    guardarResultadosExtraidos(downloadId, texto)

                    val procesados = preferencias.getStringSet(
                        "pdf_procesados_auto",
                        emptySet()
                    ) ?: emptySet()

                    preferencias.edit()
                        .putStringSet(
                            "pdf_procesados_auto",
                            procesados + downloadId.toString()
                        )
                        .apply()
                    actualizarTablaPdf()
                    actualizarTablaResultados()

                    Toast.makeText(
                        this,
                        "PDF procesado automáticamente y CSV actualizado.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Cuando el PDF terminó de procesarse,
                    // pasa automáticamente al siguiente DNI.
                    siguienteDni()
                }
            }
        }.start()
    }


    private fun guardarResultadosExtraidos(
        downloadId: Long,
        texto: String
    ) {
        val pdfs = JSONArray(preferencias.getString("lista", "[]"))
        var dniPaciente = ""
        var fechaInforme = ""
        var nombreArchivo = ""

        for (i in 0 until pdfs.length()) {
            val pdf = pdfs.getJSONObject(i)
            if (pdf.optLong("id") == downloadId) {
                dniPaciente = pdf.optString("dni")
                fechaInforme = pdf.optString("fecha")
                nombreArchivo = pdf.optString("nombre")
                break
            }
        }

        val resultados = JSONArray(
            preferencias.getString("resultados", "[]")
        )

        val registro = JSONObject().apply {
            put("downloadId", downloadId)
            put("dni", dniPaciente)
            put("fecha", fechaInforme)
            put("archivo", nombreArchivo)
            put("textoOriginal", texto)
            put("estado", "pendiente de clasificacion")
        }

        val resultadosActualizados = JSONArray()

        for (i in 0 until resultados.length()) {
            val anterior = resultados.getJSONObject(i)
            if (anterior.optLong("downloadId") != downloadId) {
                resultadosActualizados.put(anterior)
            }
        }

        resultadosActualizados.put(registro)

        preferencias.edit()
            .putString("resultados", resultadosActualizados.toString())
            .apply()

        guardarDeterminacionesExtraidas(
            downloadId,
            dniPaciente,
            fechaInforme,
            nombreArchivo,
            texto
        )
        actualizarTablaDeterminaciones()

        Toast.makeText(
            this,
            "Texto guardado para clasificar",
            Toast.LENGTH_LONG
        ).show()
    }


    private fun guardarDeterminacionesExtraidas(
        downloadId: Long,
        dniPaciente: String,
        fechaInforme: String,
        nombreArchivo: String,
        texto: String
    ) {
        val determinaciones = JSONArray(
            preferencias.getString("determinaciones", "[]")
        )

        val actualizadas = JSONArray()

        // Conserva los registros de los demás PDF.
        for (i in 0 until determinaciones.length()) {
            val anterior = determinaciones.getJSONObject(i)
            if (anterior.optLong("downloadId") != downloadId) {
                actualizadas.put(anterior)
            }
        }

        val lineas = texto.lines().map { it.trim() }
        val normalizado = texto.replace('\r', '\n')

        // Fecha y hora que figuran al comienzo del informe.
        val fechaHora = Regex(
            """(\d{2}/\d{2}/\d{2,4}\s+\d{2}:\d{2}:\d{2})"""
        ).find(texto)?.groupValues?.get(1).orEmpty()

        fun agregar(
            analisis: String,
            determinacion: String,
            resultado: String,
            unidad: String,
            referencia: String,
            interpretacion: String = ""
        ) {
            val fila = JSONObject().apply {
                put("downloadId", downloadId)
                put("dni", dniPaciente)
                put("fecha", fechaInforme)
                put("fechaHora", fechaHora)
                put("archivo", nombreArchivo)
                put("analisis", analisis)
                put("determinacion", determinacion)
                put("resultado", resultado)
                put("unidad", unidad)
                put("referencia", referencia)
                put("interpretacion", interpretacion)
            }
            actualizadas.put(fila)
        }

        // HIV de cuarta generación.
        val indiceHiv = lineas.indexOfFirst {
            it.contains("HIV - Quimioluminiscencia", ignoreCase = true)
        }

        if (indiceHiv >= 0) {
            val resultadoHiv = lineas
                .drop(indiceHiv + 1)
                .firstOrNull {
                    it.isNotBlank() &&
                    !it.startsWith("Validado", ignoreCase = true) &&
                    !it.startsWith("Método", ignoreCase = true)
                }.orEmpty()

            agregar(
                "HIV",
                "HIV - Quimioluminiscencia 4ta generación",
                resultadoHiv,
                "",
                ""
            )
        }

        // HIV carga viral.
        if (texto.contains("HIV CARGA VIRAL", ignoreCase = true)) {
            val patronResultado = Regex(
                """COPIAS\s*/\s*ml\s*([0-9][0-9.,]*)""",
                RegexOption.IGNORE_CASE
            )

            val resultadoCarga = patronResultado
                .find(normalizado)
                ?.groupValues?.get(1)
                .orEmpty()

            val patronRango = Regex(
                """Rango de Cuantificación\s*([0-9][0-9.]*\s*[-–]\s*[0-9][0-9.]*\s*copias\s*/\s*ml)""",
                RegexOption.IGNORE_CASE
            )

            val rango = patronRango
                .find(normalizado)
                ?.groupValues?.get(1)
                ?.trim()
                .orEmpty()

            val interpretacion = lineas
                .dropWhile {
                    !it.contains("Interpretación", ignoreCase = true)
                }
                .drop(1)
                .takeWhile {
                    !it.contains("cd4", ignoreCase = true)
                }
                .filter { it.isNotBlank() }
                .joinToString(" | ")

            agregar(
                "HIV",
                "HIV CARGA VIRAL",
                resultadoCarga,
                "COPIAS / ml",
                rango,
                interpretacion
            )
        }

        // Perfil linfocitario CD3/CD4/CD8.
        val tienePerfil = texto.contains(
            "CD3/CD4/CD8",
            ignoreCase = true
        ) || Regex(
            """cd4\s+Resultado\s+Unidad""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(texto)

        if (tienePerfil) {
            val patrones = listOf(
                Triple(
                    "LINFOCITOS TOTALES",
                    Regex("""LINFOCITOS\s+TOTALES\s+([0-9.,]+)\s+cel/ul""", RegexOption.IGNORE_CASE),
                    "cel/ul"
                ),
                Triple(
                    "CD3",
                    Regex("""(?m)^CD3\s+([0-9.,]+)\s*%\s*([0-9.,]+\s*[-–]\s*[0-9.,]+\s*%)?\s*$""", RegexOption.IGNORE_CASE),
                    "%"
                ),
                Triple(
                    "RECUENTO CD3",
                    Regex("""RECUENTO\s+CD3\s+([0-9.,]+)\s+cel/ul""", RegexOption.IGNORE_CASE),
                    "cel/ul"
                ),
                Triple(
                    "CD4",
                    Regex("""(?m)^CD4\s+([0-9.,]+)\s*%\s*([0-9.,]+\s*[-–]\s*[0-9.,]+\s*%)?\s*$""", RegexOption.IGNORE_CASE),
                    "%"
                ),
                Triple(
                    "RECUENTO CD4",
                    Regex("""RECUENTO\s+CD4\s+([0-9.,]+)\s+cel/ul""", RegexOption.IGNORE_CASE),
                    "cel/ul"
                ),
                Triple(
                    "CD8",
                    Regex("""(?m)^CD8\s+([0-9.,]+)\s*%\s*([0-9.,]+\s*[-–]\s*[0-9.,]+\s*%)?\s*$""", RegexOption.IGNORE_CASE),
                    "%"
                ),
                Triple(
                    "RECUENTO CD8",
                    Regex("""RECUENTO\s+CD8\s+([0-9.,]+)\s+cel/ul""", RegexOption.IGNORE_CASE),
                    "cel/ul"
                ),
                Triple(
                    "RELACION CD4/CD8",
                    Regex("""RELACION\s+CD4/CD8\s+([0-9.,]+)""", RegexOption.IGNORE_CASE),
                    ""
                )
            )

            for ((nombre, patron, unidad) in patrones) {
                val coincidencia = patron.find(normalizado)
                if (coincidencia != null) {
                    val resultado = coincidencia.groupValues[1].trim()
                    val referencia = coincidencia.groupValues
                        .getOrNull(2)
                        ?.trim()
                        .orEmpty()

                    agregar(
                        "CD3/CD4/CD8",
                        nombre,
                        resultado,
                        unidad,
                        referencia
                    )
                }
            }
        }

        preferencias.edit()
            .putString("determinaciones", actualizadas.toString())
            .apply()

        descargarResultadosCsv()
    }


    private fun descargarResultadosCsv() {
        val determinaciones = JSONArray(
            preferencias.getString("determinaciones", "[]")
        )

        if (determinaciones.length() == 0) {
            Toast.makeText(
                this,
                "No hay resultados para descargar",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val nombreArchivo = "resultados_higa.csv"

        val contenido = buildString {
            append("DNI;Fecha;Fecha y hora;Análisis;Determinación;Resultado;Unidad;Intervalo de referencia;Interpretación\n")

            for (i in 0 until determinaciones.length()) {
                val fila = determinaciones.getJSONObject(i)

                fun campo(nombre: String): String {
                    return fila.optString(nombre, "")
                        .replace("\"", "\"\"")
                        .replace("\n", " ")
                        .replace("\r", " ")
                }

                append("\"${campo("dni")}\";")
                append("\"${campo("fecha")}\";")
                append("\"${campo("fechaHora")}\";")
                append("\"${campo("analisis")}\";")
                append("\"${campo("determinacion")}\";")
                append("\"${campo("resultado")}\";")
                append("\"${campo("unidad")}\";")
                append("\"${campo("referencia")}\";")
                append("\"${campo("interpretacion")}\"\n")
            }
        }

        try {
            val valores = android.content.ContentValues().apply {
                put(
                    android.provider.MediaStore.Downloads.DISPLAY_NAME,
                    nombreArchivo
                )
                put(
                    android.provider.MediaStore.Downloads.MIME_TYPE,
                    "text/csv"
                )
                put(
                    android.provider.MediaStore.Downloads.IS_PENDING,
                    1
                )
            }

            val resolver = contentResolver

            // Reemplaza la versión anterior del CSV en Descargas.
            resolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(nombreArchivo)
            )

            val uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                valores
            )

            if (uri == null) {
                Toast.makeText(
                    this,
                    "No se pudo crear el archivo",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            resolver.openOutputStream(uri)?.use { salida ->
                salida.write(
                    ("\uFEFF" + contenido).toByteArray(Charsets.UTF_8)
                )
            }

            valores.clear()
            valores.put(
                android.provider.MediaStore.Downloads.IS_PENDING,
                0
            )
            resolver.update(uri, valores, null, null)

            Toast.makeText(
                this,
                "Resultados descargados en Descargas/$nombreArchivo",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error al descargar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun actualizarTablaDeterminaciones() {
        determinationsTable.removeAllViews()

        val encabezado = TableRow(this)

        listOf(
            "DNI",
            "Fecha",
            "Análisis",
            "Determinación",
            "Resultado",
            "Unidad",
            "Referencia"
        ).forEach {
            val celda = TextView(this)
            celda.text = it
            celda.setPadding(8, 12, 8, 12)
            celda.gravity = Gravity.CENTER
            celda.textSize = 12f
            celda.setTypeface(null, android.graphics.Typeface.BOLD)
            encabezado.addView(celda)
        }

        determinationsTable.addView(encabezado)

        val determinaciones = JSONArray(
            preferencias.getString("determinaciones", "[]")
        )

        for (i in 0 until determinaciones.length()) {
            val registro = determinaciones.getJSONObject(i)
            val fila = TableRow(this)

            listOf(
                "dni",
                "fecha",
                "analisis",
                "determinacion",
                "resultado",
                "unidad",
                "referencia"
            ).forEach { campo ->
                val celda = TextView(this)
                celda.text = registro.optString(campo)
                celda.setPadding(8, 10, 8, 10)
                celda.gravity = Gravity.CENTER
                celda.textSize = 11f
                fila.addView(celda)
            }

            determinationsTable.addView(fila)
        }

        if (determinaciones.length() == 0) {
            val fila = TableRow(this)
            val celda = TextView(this)
            celda.text = "Todavía no hay determinaciones clasificadas."
            celda.setPadding(12, 20, 12, 20)
            fila.addView(celda)
            determinationsTable.addView(fila)
        }
    }


    private fun iniciarConsultas() {
        from.setText("01/01/${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}")
to.setText(
    String.format(
        "%02d/%02d/%04d",
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
        java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    )
)

        val dniPegados = dni.text.toString()
            .split(Regex("[\\r\\n\\t ,;|]+"))
            .map { token -> token.filter { it.isDigit() } }
            .filter { it.length in 7..8 }
            .distinct()

        listaDni = dniPegados

        // Muestra los DNI normalizados, uno por línea.
        if (listaDni.isNotEmpty()) {
            val dniEnLineas = listaDni.joinToString("\n")
            if (dni.text.toString() != dniEnLineas) {
                dni.setText(dniEnLineas)
                dni.setSelection(dni.text.length)
            }
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


    private fun limpiarDatosSesion() {
        try {
            val pdfs = JSONArray(preferencias.getString("lista", "[]"))
            val ids = mutableListOf<Long>()

            for (i in 0 until pdfs.length()) {
                val id = pdfs.optJSONObject(i)?.optLong("id", -1L) ?: -1L
                if (id > 0) ids.add(id)
            }

            if (ids.isNotEmpty()) {
                val gestor = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                gestor.remove(*ids.toLongArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            contentResolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf("resultados_higa.csv")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        preferencias.edit()
            .remove("lista")
            .remove("resultados")
            .remove("determinaciones")
            .remove("pdf_procesados_auto")
            .apply()
    }

    override fun onDestroy() {
        if (receptorDescargasRegistrado) {
            unregisterReceiver(receptorDescargas)
            receptorDescargasRegistrado = false
        }
        if (isFinishing) {
            limpiarDatosSesion()
        }
        super.onDestroy()
    }

}

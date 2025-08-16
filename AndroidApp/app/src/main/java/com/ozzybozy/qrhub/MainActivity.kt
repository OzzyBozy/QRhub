/*
 * Copyright (c) 2025 OzzyBozy
 * Custom Non‑Commercial Open Source License — see LICENSE.txt
 */
package com.ozzybozy.qrhub

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val qrList = mutableListOf<QRItem>()
    private lateinit var qrContainer: LinearLayout
    private lateinit var filterSpinner: Spinner

    private enum class FilterType {
        DATE, FAVORITE
    }
    private var currentFilterType = FilterType.DATE

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchScanner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        qrContainer = findViewById(R.id.qrContainer)
        filterSpinner = findViewById(R.id.filterSpinner)

        val filterOptions = arrayOf("Sort by Date", "Show Favorites")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_no_text, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFilterType = when (position) {
                    0 -> FilterType.DATE
                    1 -> FilterType.FAVORITE
                    else -> FilterType.DATE
                }
                refreshQrDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val scanButton = findViewById<Button>(R.id.scanButton)
        scanButton.setOnClickListener {
            checkCameraPermissionAndScan()
        }

        qrList.clear()
        qrList.addAll(QRStorageHelper.loadQRList(this))
        refreshQrDisplay()
    }

    private fun refreshQrDisplay() {
        qrContainer.removeAllViews()

        val displayList = when (currentFilterType) {
            FilterType.DATE -> qrList
            FilterType.FAVORITE -> qrList.filter { it.favorite }
        }

        for (item in displayList) {

            val view = LayoutInflater.from(this).inflate(R.layout.qr_item, qrContainer, false)
            val nameEditText = view.findViewById<EditText>(R.id.qrText)
            val dateTextView = view.findViewById<TextView>(R.id.dateText)
            val qrImageView = view.findViewById<ImageView>(R.id.qrImage)
            val favButton = view.findViewById<CheckBox>(R.id.favButton)

            nameEditText.isSaveEnabled = false
            favButton.id = View.NO_ID

            nameEditText.setText(item.text)

            val displayDateOnly: String = try {
                val fullDateObject = dateTimeFormat.parse(item.dateTime)
                if (fullDateObject != null) {
                    displayDateFormat.format(fullDateObject)
                } else {
                    item.dateTime.split(" ")[0]
                }
            } catch (e: ParseException) {
                item.dateTime.split(" ")[0]
            }
            dateTextView.text = displayDateOnly

            item.faviconPath?.let { path ->
                if (path.isNotEmpty()) {
                    val faviconFile = File(path)
                    if (faviconFile.exists()) {
                        qrImageView.setImageURI(Uri.fromFile(faviconFile))
                    } else {
                         qrImageView.setImageDrawable(null)
                    }
                } else {
                     qrImageView.setImageDrawable(null)
                }
            } ?: run {
                qrImageView.setImageDrawable(null)
            }

            val itemId = item.id

            nameEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newText = s.toString()
                    val itemToUpdate = qrList.find { it.id == itemId }
                    if (itemToUpdate != null && itemToUpdate.text != newText) {
                        itemToUpdate.text = newText
                        QRStorageHelper.saveQRList(this@MainActivity, qrList)
                    }
                }
            })

            favButton.setOnCheckedChangeListener(null)
            favButton.isChecked = item.favorite
            favButton.setOnCheckedChangeListener { _, isChecked ->
                val itemToUpdate = qrList.find { it.id == itemId }
                if (itemToUpdate != null && itemToUpdate.favorite != isChecked) {
                    itemToUpdate.favorite = isChecked
                    QRStorageHelper.saveQRList(this@MainActivity, qrList)
                    if (currentFilterType == FilterType.FAVORITE) {
                        refreshQrDisplay()
                    }
                }
            }

            view.setOnClickListener {
                if (item.url.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, "No app can handle this URL", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No URL available for this item", Toast.LENGTH_SHORT).show()
                }
            }
            qrContainer.addView(view)
            view.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Item")
                    .setMessage("Do you want to delete this item?")
                    .setPositiveButton("Yes") { _, _ ->
                        val itemToRemove = qrList.find { it.id == itemId }
                        if (itemToRemove != null) {
                            qrList.remove(itemToRemove)
                            QRStorageHelper.saveQRList(this@MainActivity, qrList)
                            refreshQrDisplay()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
                true
            }
        }
    }

    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchScanner()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivityForResult(intent, 101)
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val scannedText = data?.getStringExtra("scanned_code")
            scannedText?.let {
                val currentDateTime = dateTimeFormat.format(Date())
                val newItem = QRItem(text = it, url = it, dateTime = currentDateTime, favorite = false, faviconPath = null)
                qrList.add(0, newItem)
                QRStorageHelper.saveQRList(this, qrList)
                refreshQrDisplay()
                lifecycleScope.launch {
                    downloadFaviconForQRItem(newItem.id, newItem.url)
                }
            }
        }
    }

    data class QRItem(
        val id: String = UUID.randomUUID().toString(),
        var text: String,
        val url: String,
        val dateTime: String,
        var favorite: Boolean = false,
        var faviconPath: String? = null
    )

    object QRStorageHelper {
        private const val PREFS_NAME = "qr_prefs"
        private const val KEY_QR_LIST = "qr_list"
        private val gson = Gson()
        private val storageDateTimeFormat = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault())

        fun saveQRList(context: Context, list: List<QRItem>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_QR_LIST, json).apply()
        }

        fun loadQRList(context: Context): MutableList<QRItem> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_QR_LIST, null)
            return if (json != null) {
                try {
                    val type = object : TypeToken<MutableList<QRItem>>() {}.type
                    val loadedList: MutableList<QRItem> = gson.fromJson(json, type)
                    loadedList.sortWith(compareByDescending { item ->
                        try {
                            storageDateTimeFormat.parse(item.dateTime)
                        } catch (e: ParseException) {
                            null
                        }
                    })
                    loadedList
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
        }
    }

   private suspend fun downloadFaviconForQRItem(itemPassedId: String, itemUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                if (itemUrl.isEmpty()) {
                    return@withContext
                }

                val originalUri = try {
                    Uri.parse(itemUrl)
                } catch (e: Exception) {
                    return@withContext
                }

                val hostname = originalUri.host
                if (hostname.isNullOrEmpty()) {
                    return@withContext
                }

                val faviconUrlStrings = listOf(
                    "https://www.google.com/s2/favicons?domain=$hostname&sz=128",
                    "https://$hostname/favicon.ico",
                    "http://$hostname/favicon.ico",
                    "https://$hostname/apple-touch-icon.png"
                )

                var downloadedFaviconPath: String? = null

                for (faviconUrlString in faviconUrlStrings) {
                    var connection: HttpURLConnection? = null
                    var inputStream: InputStream? = null
                    var outputStream: FileOutputStream? = null
                    try {
                        val url = URL(faviconUrlString)
                        connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.instanceFollowRedirects = true
                        connection.connect()

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val faviconsDir = File(filesDir, "favicons")
                            if (!faviconsDir.exists()) {
                                faviconsDir.mkdirs()
                            }

                            val safeHostname = hostname.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                            val extension = ".png"
                            val fileName = "$safeHostname$extension"
                            val faviconFile = File(faviconsDir, fileName)

                            inputStream = connection.inputStream
                            outputStream = FileOutputStream(faviconFile)
                            inputStream.copyTo(outputStream)

                            downloadedFaviconPath = faviconFile.absolutePath
                            break 
                        }
                    } catch (e: Exception) {
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                        connection?.disconnect()
                    }
                }

                downloadedFaviconPath?.let { path ->
                    val itemToUpdate = qrList.find { it.id == itemPassedId }
                    if (itemToUpdate != null) {
                        if (itemToUpdate.url == itemUrl) {
                            itemToUpdate.faviconPath = path
                            QRStorageHelper.saveQRList(this@MainActivity, qrList)
                            withContext(Dispatchers.Main) {
                                refreshQrDisplay()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
}

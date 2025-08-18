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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ozzybozy.qrhub.databinding.ActivityMainBinding
import com.ozzybozy.qrhub.databinding.QrItemBinding
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
    private lateinit var binding: ActivityMainBinding

    private enum class FilterType {
        DATE, ALPHABETICAL, FAVORITE, RENAMED
    }
    private var currentFilterType = FilterType.DATE
    private var isAscending = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchScanner()
        }
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedText = result.data?.getStringExtra("scanned_code")
            scannedText?.let {
                val currentDateTime = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date())
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

    companion object {
        private const val KEY_SETTINGS_MENU_VISIBLE = "settings_menu_visible"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePrefsOnCreate = getSharedPreferences(ThemeUtils.PREFS_NAME, MODE_PRIVATE)
        val currentTheme = themePrefsOnCreate.getString(ThemeUtils.KEY_THEME, ThemeUtils.THEME_LIGHT) ?: ThemeUtils.THEME_LIGHT
        ThemeUtils.applyTheme(currentTheme)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filterOptions = resources.getStringArray(R.array.filter_options_array)
        val adapter = ArrayAdapter(this, R.layout.spinner_item_no_text, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterSpinner.adapter = adapter

        binding.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFilterType = when (position) {
                    0 -> FilterType.DATE
                    1 -> FilterType.ALPHABETICAL
                    2 -> FilterType.FAVORITE
                    3 -> FilterType.RENAMED
                    else -> FilterType.DATE
                }
                isAscending = true
                binding.sortButton.isChecked = true
                updateSortButtonIcon()
                refreshQrDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.sortButton.setOnCheckedChangeListener { _, isChecked ->
            isAscending = isChecked
            updateSortButtonIcon()
            refreshQrDisplay()
        }

        binding.scanButton.setOnClickListener {
            checkCameraPermissionAndScan()
        }

        val settingsLayout = binding.settingsMenu
        val settingsButton = binding.settingsButton
        val settingsExitButton = binding.settingsExitButton
        val sharedPrefs = getSharedPreferences(ThemeUtils.PREFS_NAME, MODE_PRIVATE)


        settingsButton.setOnClickListener {
            settingsLayout.visibility = View.VISIBLE
            sharedPrefs.edit { putBoolean(KEY_SETTINGS_MENU_VISIBLE, true) }
        }
        settingsExitButton.setOnClickListener {
            settingsLayout.visibility = View.GONE
            sharedPrefs.edit { putBoolean(KEY_SETTINGS_MENU_VISIBLE, false) }
        }

        val settingsMenuVisible = sharedPrefs.getBoolean(KEY_SETTINGS_MENU_VISIBLE, false)
        if (settingsMenuVisible) {
            settingsLayout.visibility = View.VISIBLE
        }


        binding.darkModeSwitch.isChecked = currentTheme == ThemeUtils.THEME_DARK
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val newThemePreference = if (isChecked) {
                ThemeUtils.THEME_DARK
            } else {
                ThemeUtils.THEME_LIGHT
            }
            sharedPrefs.edit { putString(ThemeUtils.KEY_THEME, newThemePreference) }
            ThemeUtils.applyThemeChangeAndRecreate(this)
        }

        val languageCodes = listOf("en", "it", "de", "fr", "tr", "ar", "es", "hi", "ja", "ko", "ru", "zh")
        val languages = resources.getStringArray(R.array.language_display_names_array).toList()
        val savedLang = sharedPrefs.getString("app_language", "en") ?: "en"

        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.languageSpinner.adapter = langAdapter

        val savedIndex = languageCodes.indexOf(savedLang)
        if (savedIndex != -1) {
            binding.languageSpinner.setSelection(savedIndex)
        }

        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedLangCode = languageCodes[position]
                val currentSavedLang = sharedPrefs.getString("app_language", "en")
                if (selectedLangCode != currentSavedLang) {
                    sharedPrefs.edit { putString("app_language", selectedLangCode) }
                    LocaleUtils.setLocale(this@MainActivity, selectedLangCode)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        qrList.clear()
        qrList.addAll(QRStorageHelper.loadQRList(this))
        updateSortButtonIcon()
        refreshQrDisplay()
    }

    private fun updateSortButtonIcon() {
        val iconRes = when (currentFilterType) {
            FilterType.DATE, FilterType.ALPHABETICAL -> {
                if (isAscending) R.drawable.ic_ascending else R.drawable.ic_descending
            }
            FilterType.FAVORITE -> {
                if (isAscending) R.drawable.ic_star_blue else R.drawable.ic_star_border_blue
            }
            FilterType.RENAMED -> {
                if (isAscending) R.drawable.ic_tick else R.drawable.ic_cross
            }
        }
        binding.sortButton.setBackgroundResource(iconRes)
    }

    private fun refreshQrDisplay() {
        binding.qrContainer.removeAllViews()

        val displayList = when (currentFilterType) {
            FilterType.DATE -> {
                val comparator = compareBy<QRItem> {
                    try {
                        SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).parse(it.dateTime)
                    } catch (_: ParseException) { null }
                }
                if (isAscending) qrList.sortedWith(comparator.reversed()) else qrList.sortedWith(comparator)
            }
            FilterType.ALPHABETICAL -> {
                if (isAscending) qrList.sortedBy { it.text.lowercase(Locale.getDefault()) }
                else qrList.sortedByDescending { it.text.lowercase(Locale.getDefault()) }
            }
            FilterType.FAVORITE -> {
                qrList.filter { it.favorite == isAscending }.sortedByDescending {
                    try { SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).parse(it.dateTime) } catch (_: ParseException) { null }
                }
            }
            FilterType.RENAMED -> {
                qrList.filter { (it.text != it.url) == isAscending }.sortedByDescending {
                    try { SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).parse(it.dateTime) } catch (_: ParseException) { null }
                }
            }
        }

        for (item in displayList) {
            val itemBinding = QrItemBinding.inflate(LayoutInflater.from(this), binding.qrContainer, false)
            
            itemBinding.qrText.isSaveEnabled = false
            itemBinding.favButton.id = View.NO_ID

            itemBinding.qrText.setText(item.text)

            val displayDateOnly: String = try {
                val fullDateObject = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).parse(item.dateTime)
                if (fullDateObject != null) {
                    SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(fullDateObject)
                } else {
                    item.dateTime.split(" ")[0]
                }
            } catch (_: ParseException) {
                item.dateTime.split(" ")[0]
            }
            itemBinding.dateText.text = displayDateOnly

            item.faviconPath?.let { path ->
                if (path.isNotEmpty()) {
                    val faviconFile = File(path)
                    if (faviconFile.exists()) {
                        itemBinding.qrImage.setImageURI(Uri.fromFile(faviconFile))
                    } else {
                        itemBinding.qrImage.setImageDrawable(null)
                    }
                } else {
                    itemBinding.qrImage.setImageDrawable(null)
                }
            } ?: run {
                itemBinding.qrImage.setImageDrawable(null)
            }

            val itemId = item.id

            itemBinding.qrText.addTextChangedListener(object : TextWatcher {
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

            itemBinding.favButton.setOnCheckedChangeListener(null)
            itemBinding.favButton.isChecked = item.favorite
            itemBinding.favButton.setOnCheckedChangeListener { _, isChecked ->
                val itemToUpdate = qrList.find { it.id == itemId }
                if (itemToUpdate != null && itemToUpdate.favorite != isChecked) {
                    itemToUpdate.favorite = isChecked
                    QRStorageHelper.saveQRList(this@MainActivity, qrList)
                    if (currentFilterType == FilterType.FAVORITE) {
                        refreshQrDisplay()
                    }
                }
            }

            itemBinding.root.setOnClickListener {
                if (item.url.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, item.url.toUri())
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "No app can handle this URL", Toast.LENGTH_SHORT).show() // TODO: String resource
                    } catch (_: Exception) {
                        Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show() // TODO: String resource
                    }
                } else {
                    Toast.makeText(this, "No URL available for this item", Toast.LENGTH_SHORT).show() // TODO: String resource
                }
            }
            binding.qrContainer.addView(itemBinding.root)
            itemBinding.root.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Item") // TODO: String resource
                    .setMessage("Do you want to delete this item?") // TODO: String resource
                    .setPositiveButton("Yes") { _, _ -> // TODO: String resource
                        val itemToRemove = qrList.find { it.id == itemId }
                        if (itemToRemove != null) {
                            qrList.remove(itemToRemove)
                            QRStorageHelper.saveQRList(this@MainActivity, qrList)
                            refreshQrDisplay()
                        }
                    }
                    .setNegativeButton("No", null) // TODO: String resource
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
        scannerLauncher.launch(intent)
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

        fun saveQRList(context: Context, list: List<QRItem>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val json = gson.toJson(list)
            prefs.edit {
                putString(KEY_QR_LIST, json)
            }
        }

        fun loadQRList(context: Context): MutableList<QRItem> {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_QR_LIST, null)
            return if (json != null) {
                try {
                    val type = object : TypeToken<MutableList<QRItem>>() {}.type
                    val loadedList: MutableList<QRItem> = gson.fromJson(json, type)
                    loadedList.sortWith(compareByDescending { item ->
                        try {
                            SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).parse(item.dateTime)
                        } catch (_: ParseException) {
                            null
                        }
                    })
                    loadedList
                } catch (_: Exception) {
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
                    itemUrl.toUri()
                } catch (_: Exception) {
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
                    } catch (_: Exception) {
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
            } catch (_: Exception) {
            }
        }
    }
}

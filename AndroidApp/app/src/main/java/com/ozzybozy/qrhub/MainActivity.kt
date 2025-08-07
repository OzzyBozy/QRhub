package com.ozzybozy.qrhub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val qrList = mutableListOf<QRItem>()
    private lateinit var qrContainer: LinearLayout

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

        for (item in qrList) {
            val view = LayoutInflater.from(this).inflate(R.layout.qr_item, qrContainer, false)
            val nameEditText = view.findViewById<EditText>(R.id.qrText)
            val dateTextView = view.findViewById<TextView>(R.id.dateText)

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

            nameEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val newText = s.toString()
                    if (item.text != newText) {
                        item.text = newText
                        QRStorageHelper.saveQRList(this@MainActivity, qrList)
                    }
                }
            })
            qrContainer.addView(view)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val scannedText = data?.getStringExtra("scanned_code")
            scannedText?.let {
                val currentDateTime = dateTimeFormat.format(Date())
                val newItem = QRItem(text = it, url = it, dateTime = currentDateTime, favorite = false)

                qrList.add(0, newItem)
                QRStorageHelper.saveQRList(this, qrList)
                refreshQrDisplay()
            }
        }
    }

    data class QRItem(
        var text: String,
        val url: String,
        val dateTime: String,
        var favorite: Boolean = false
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
}

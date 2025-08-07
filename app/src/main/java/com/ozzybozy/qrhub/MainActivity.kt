package com.ozzybozy.qrhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private val qrList = mutableListOf<QRItem>()
    private lateinit var qrContainer: LinearLayout

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

        qrList.addAll(QRStorageHelper.loadQRList(this))
        qrList.forEach { addQRBox(it) }
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
        startActivityForResult(intent,101)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val scannedText = data?.getStringExtra("scanned_code")
            scannedText?.let {
                val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
                val item = QRItem(text = it, date = date)

                qrList.add(0, item)
                QRStorageHelper.saveQRList(this, qrList)
                addQRBox(item)
            }
        }
    }


    private fun addQRBox(item: QRItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.qr_item, qrContainer, false)
        val textView = view.findViewById<TextView>(R.id.qrText)
        val dateText = view.findViewById<TextView>(R.id.dateText)

        textView.text = item.text
        dateText.text = item.date
        qrContainer.addView(view, 0)
    }


    data class QRItem(
        val text: String,
        val date: String
    )

    object QRStorageHelper {
        private const val PREFS_NAME = "qr_prefs"
        private const val KEY_QR_LIST = "qr_list"

        private val gson = Gson()

        fun saveQRList(context: Context, list: List<QRItem>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_QR_LIST, json).apply()
        }

        fun loadQRList(context: Context): MutableList<QRItem> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_QR_LIST, null)
            return if (json != null) {
                val type = object : TypeToken<MutableList<QRItem>>() {}.type
                gson.fromJson(json, type)
            } else {
                mutableListOf()
            }
        }
    }
}
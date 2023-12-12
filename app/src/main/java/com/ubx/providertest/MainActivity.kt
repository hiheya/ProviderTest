package com.ubx.providertest

import android.annotation.SuppressLint
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.content.contentValuesOf
import com.ubx.providertest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var mainBinding: ActivityMainBinding
    private var bookId: String? = null
    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        mainBinding.addData.setOnClickListener {
            // add Data
            val uri = Uri.parse("content://work.icu007.databasetest.provider/book")
            val values = contentValuesOf("name" to "剑来", "author" to "烽火戏诸侯", "pages" to 60000,
                "price" to 50.99)
            val newUri = contentResolver.insert(uri, values)
            bookId = newUri?.pathSegments?.get(1)
            Log.d(TAG, "addData: bookId : $bookId ")
        }

        mainBinding.queryData.setOnClickListener {
            // query Data
            val uri = Uri.parse("content://work.icu007.databasetest.provider/book")
            contentResolver.query(uri, null, null, null, null)?.apply {
                while (moveToNext()){
                    val name = getString(getColumnIndex("name"))
                    val author = getString(getColumnIndex("author"))
                    val pages = getInt(getColumnIndex("pages"))
                    val price = getDouble(getColumnIndex("price"))
                    Log.d(TAG, "onCreate: book name is $name;\nbook author is $author;\n" +
                            "book pages is $pages;\nbook price is $price.")
                }
                close()
            }
        }

        mainBinding.updateData.setOnClickListener {
            // update Data
            bookId?.let {
                val uri = Uri.parse("content://work.icu007.databasetest.provider/book/$it")
                val values = contentValuesOf("name" to "雪中悍刀行", "pages" to 50000, "price" to 49.99)
                contentResolver.update(uri, values, null, null)
            }
        }

        mainBinding.deleteData.setOnClickListener {
            // delete data
            Log.d(TAG, "deleteData: bookId : $bookId")
            bookId?.let {
                val uri = Uri.parse("content://work.icu007.databasetest.provider/book/$it")
                contentResolver.delete(uri, null, null)
                Log.d(TAG, "LCR deleteData: delete $it")
            }
        }
    }
}
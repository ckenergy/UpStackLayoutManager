package com.example.stacklayout

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_first.*

class FirstActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first)
        bt_1.setOnClickListener { startActivity(Intent(this, ExpandActivity::class.java)) }
        bt_2.setOnClickListener {
            startActivity(Intent(this, UpwardActivity::class.java)
                .putExtra(UpwardActivity.PARAM, 3))
        }
        bt_3.setOnClickListener {
            startActivity(Intent(this, UpwardActivity::class.java)
                .putExtra(UpwardActivity.PARAM, 15))
        }
    }
}

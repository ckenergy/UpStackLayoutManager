package com.example.stacklayout

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.stacklayout.stacklayout.UpStackLayoutManager
import kotlinx.android.synthetic.main.activity_upward.*

class ExpandActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expand)

        val stackLayoutManager =
            UpStackLayoutManager()
        recyler_view.layoutManager = stackLayoutManager
        recyler_view.adapter = StackAdapter(this, 20)
    }
}

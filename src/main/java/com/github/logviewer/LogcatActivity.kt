package com.github.logviewer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.util.regex.Pattern

class LogcatActivity : AppCompatActivity() {

    companion object {
        @JvmOverloads
        fun start(context: Context, excludeList: List<Pattern> = emptyList()) {
            val list = ArrayList<String>()
            for (pattern in excludeList) {
                list.add(pattern.pattern())
            }
            val starter = getIntent(context, list)
            context.startActivity(starter)
        }

        private fun getIntent(context: Context?, list: ArrayList<String>?): Intent {
            return Intent(context, LogcatActivity::class.java)
                .putStringArrayListExtra("exclude_list", list)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val excludeList: MutableList<Pattern> = ArrayList()
        intent.getStringArrayListExtra("exclude_list")?.let {
            for (pattern in it) {
                excludeList.add(Pattern.compile(pattern))
            }
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LogcatFragment.newInstance(excludeList))
                .commit()
        }
    }
}

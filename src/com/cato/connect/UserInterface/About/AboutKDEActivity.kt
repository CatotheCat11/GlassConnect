/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.connect.UserInterface.About

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import com.cato.connect.UserInterface.ThemeUtil
import com.cato.connect.R
/*import com.cato.connect.databinding.ActivityAboutKdeBinding

class AboutKDEActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.setUserPreferredTheme(this)
        super.onCreate(savedInstanceState)

        val binding = ActivityAboutKdeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        binding.aboutTextView.text = fromHtml(resources.getString(R.string.about_kde_about))
        binding.reportBugsOrWishesTextView.text = fromHtml(resources.getString(R.string.about_kde_report_bugs_or_wishes))
        binding.joinKdeTextView.text = fromHtml(resources.getString(R.string.about_kde_join_kde))
        binding.supportKdeTextView.text = fromHtml(resources.getString(R.string.about_kde_support_kde))

        binding.aboutTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.reportBugsOrWishesTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.joinKdeTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.supportKdeTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun fromHtml(html: String): Spanned {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(html)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        super.onBackPressed()
        return true
    }
}*/
/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package com.cato.kdeconnect.UserInterface.List

import android.view.LayoutInflater
import android.view.View

open class EntryItem protected constructor(protected val title: String, protected val subtitle: String?) : ListAdapter.Item {

    override fun inflateView(layoutInflater: LayoutInflater): View {
        throw UnsupportedOperationException("This should not have been called")
    }
}
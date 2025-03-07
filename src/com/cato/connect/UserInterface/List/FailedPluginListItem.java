/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package com.cato.connect.UserInterface.List;

import com.cato.connect.Plugins.Plugin;

public class FailedPluginListItem extends SmallEntryItem {

    public interface Action {
        void action(Plugin plugin);
    }

    public FailedPluginListItem(Plugin plugin, Action action) {
        super(plugin.getDisplayName(),  (view) -> action.action(plugin));
    }
}
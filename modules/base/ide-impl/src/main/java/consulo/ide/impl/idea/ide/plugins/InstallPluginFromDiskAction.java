/*
 * Copyright 2013-2024 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.ide.impl.idea.ide.plugins;

import consulo.application.CommonBundle;
import consulo.container.impl.PluginValidator;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginManager;
import consulo.fileChooser.FileChooser;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.ide.impl.idea.ide.startup.StartupActionScriptManager;
import consulo.ide.impl.idea.openapi.updateSettings.impl.PluginDownloader;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

class InstallPluginFromDiskAction extends DumbAwareAction {
  private InstalledPluginsManagerMain myInstalledPluginsManagerMain;

  InstallPluginFromDiskAction(InstalledPluginsManagerMain installedPluginsManagerMain) {
    super("Install plugin from disk...", null, PlatformIconGroup.actionsInstall());
    myInstalledPluginsManagerMain = installedPluginsManagerMain;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false) {
      @RequiredUIAccess
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return Objects.equals(file.getExtension(), PluginManager.CONSULO_PLUGIN_EXTENSION);
      }
    };
    descriptor.setTitle("Choose Plugin File");
    descriptor.setDescription("'consulo-plugin' files are accepted");

    FileChooser.chooseFile(descriptor, null, myInstalledPluginsManagerMain.getMainPanel(), null).doWhenDone(virtualFile -> {
      final File file = VfsUtilCore.virtualToIoFile(virtualFile);
      try {
        final PluginDescriptor pluginDescriptor = InstalledPluginsManagerMain.loadDescriptorFromArchive(file);
        if (pluginDescriptor == null) {
          Messages.showErrorDialog("Fail to load plugin descriptor from file " + file.getName(), CommonBundle.getErrorTitle());
          return;
        }
        if (PluginValidator.isIncompatible(pluginDescriptor)) {
          Messages.showErrorDialog("Plugin " + pluginDescriptor.getName() + " is incompatible with current installation",
                                   CommonBundle.getErrorTitle());
          return;
        }
        final PluginDescriptor alreadyInstalledPlugin = PluginManager.findPlugin(pluginDescriptor.getPluginId());
        if (alreadyInstalledPlugin != null) {
          final File oldFile = alreadyInstalledPlugin.getPath();
          if (oldFile != null) {
            StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.DeleteCommand(oldFile));
          }
        }
        if (((InstalledPluginsTableModel)myInstalledPluginsManagerMain.myPluginsModel).appendOrUpdateDescriptor(pluginDescriptor)) {
          PluginDownloader.install(file, file.getName(), false);
          myInstalledPluginsManagerMain.select(pluginDescriptor.getPluginId());
          myInstalledPluginsManagerMain.checkInstalledPluginDependencies(pluginDescriptor);
          myInstalledPluginsManagerMain.setRequireShutdown(true);
        }
        else {
          Messages.showInfoMessage(myInstalledPluginsManagerMain.getMainPanel(),
                                   "Plugin " + pluginDescriptor.getName() + " was already installed",
                                   CommonBundle.getWarningTitle());
        }
      }
      catch (IOException ex) {
        Messages.showErrorDialog(ex.getMessage(), CommonBundle.getErrorTitle());
      }
    });
  }
}

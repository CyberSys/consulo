/*
 * Copyright 2013-2018 consulo.io
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
package consulo.desktop.impl.ide;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;
import com.intellij.Patches;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.AboutAction;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.ApplicationStarter;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.ThrowableRunnable;
import com.sun.jna.Callback;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author VISTALL
 * @since 2018-09-01
 */
@Singleton
public class MacOSDefaultMenuInitializer {
  private static final Logger LOGGER = Logger.getInstance(MacOSDefaultMenuInitializer.class);

  private static class PreJava9Worker implements ThrowableRunnable<Throwable> {
    @Override
    @SuppressWarnings("deprecation")
    public void run() throws Throwable {
      Application application = new Application();
      application.addApplicationListener(new ApplicationAdapter() {
        @Override
        public void handleAbout(ApplicationEvent applicationEvent) {
          AboutAction.showAbout();
          applicationEvent.setHandled(true);
        }

        @Override
        public void handlePreferences(ApplicationEvent applicationEvent) {
          final Project project = getNotNullProject();
          final ShowSettingsUtil showSettingsUtil  =ShowSettingsUtil.getInstance();
          if (!showSettingsUtil.isAlreadyShown()) {
            TransactionGuard.submitTransaction(project, () -> showSettingsUtil.showSettingsDialog(project));
          }
          applicationEvent.setHandled(true);
        }

        @Override
        public void handleQuit(ApplicationEvent applicationEvent) {
          final com.intellij.openapi.application.Application app = com.intellij.openapi.application.Application.get();
          TransactionGuard.submitTransaction(app, app::exit);
        }

        @Override
        public void handleOpenFile(final ApplicationEvent applicationEvent) {
          final Project project = getProject();
          final String filename = applicationEvent.getFilename();
          if (filename == null) return;

          TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
            File file = new File(filename);
            if (ProjectUtil.open(file.getAbsolutePath(), project, true) != null) {
              ApplicationStarter.getInstance().setPerformProjectLoad(false);
              return;
            }
            if (project != null && file.exists()) {
              OpenFileAction.openFile(filename, project);
              applicationEvent.setHandled(true);
            }
          });
        }
      });

      application.addAboutMenuItem();
      application.addPreferencesMenuItem();
      application.setEnabledAboutMenu(true);
      application.setEnabledPreferencesMenu(true);
    }
  }

  private static class Java9Worker implements ThrowableRunnable<Throwable> {
    @Override
    public void run() throws Throwable {
      Desktop desktop = Desktop.getDesktop();
      ClassLoader classLoader = MacOSDefaultMenuInitializer.class.getClassLoader();

      Class<?> aboutHandler = Class.forName("java.awt.desktop.AboutHandler");
      Class<?> preferencesHandler = Class.forName("java.awt.desktop.PreferencesHandler");
      Class<?> quitHandler = Class.forName("java.awt.desktop.QuitHandler");
      Class<?> openFilesHandler = Class.forName("java.awt.desktop.OpenFilesHandler");

      Class<?> filesEvent = Class.forName("java.awt.desktop.FilesEvent");
      Method getFiles = filesEvent.getDeclaredMethod("getFiles");

      Class<? extends Desktop> desktopClass = desktop.getClass();
      Method setAboutHandler = desktopClass.getDeclaredMethod("setAboutHandler", aboutHandler);
      setAboutHandler.invoke(desktop, Proxy.newProxyInstance(classLoader, new Class[]{aboutHandler}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          AboutAction.showAbout();
          return null;
        }
      }));

      Method setPreferencesHandler = desktopClass.getDeclaredMethod("setPreferencesHandler", preferencesHandler);
      setPreferencesHandler.invoke(desktop, Proxy.newProxyInstance(classLoader, new Class[]{preferencesHandler}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          final Project project = getNotNullProject();
          final ShowSettingsUtil showSettingsUtil = ShowSettingsUtil.getInstance();
          if (!showSettingsUtil.isAlreadyShown()) {
            TransactionGuard.submitTransaction(project, () -> showSettingsUtil.showSettingsDialog(project));
          }
          return null;
        }
      }));

      Method setQuitHandler = desktopClass.getDeclaredMethod("setQuitHandler", quitHandler);
      setQuitHandler.invoke(desktop, Proxy.newProxyInstance(classLoader, new Class[]{quitHandler}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          final com.intellij.openapi.application.Application app = com.intellij.openapi.application.Application.get();
          TransactionGuard.submitTransaction(app, app::exit);
          return null;
        }
      }));

      Method setOpenFileHandler = desktopClass.getDeclaredMethod("setOpenFileHandler", openFilesHandler);
      setOpenFileHandler.invoke(desktop, Proxy.newProxyInstance(classLoader, new Class[]{openFilesHandler}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          Object eventObject = args[0];
          try {
            List files = (List)getFiles.invoke(eventObject);
            final Project project = getProject();

            if(files != null) {
              File file = (File)files.get(0);
              TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
                if (ProjectUtil.open(file.getAbsolutePath(), project, true) != null) {
                  ApplicationStarter.getInstance().setPerformProjectLoad(false);
                  return;
                }
                if (project != null && file.exists()) {
                  OpenFileAction.openFile(file.getPath(), project);
                }
              });
            }
          }
          catch (Exception ignored) {
          }
          return null;
        }
      }));
    }
  }

  private static final Callback IMPL = new Callback() {
    @SuppressWarnings("unused")
    public void callback(ID self, String selector) {
      SwingUtilities.invokeLater(() -> {
        ActionManager am = ActionManager.getInstance();
        MouseEvent me = new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
        am.tryToExecute(am.getAction("CheckForUpdate"), me, null, null, false);
      });
    }
  };

  public MacOSDefaultMenuInitializer() {
    if (SystemInfo.isMac) {
      try {
        assert Patches.USE_REFLECTION_TO_ACCESS_JDK9;

        ThrowableRunnable<Throwable> task = SystemInfo.isJavaVersionAtLeast(9, 0, 0) ? new Java9Worker() : new PreJava9Worker();

        task.run();

        installAutoUpdateMenu();
      }
      catch (Throwable t) {
        LOGGER.warn(t);
      }
    }
  }

  @Nonnull
  private static Project getNotNullProject() {
    Project project = getProject();
    return project == null ? ProjectManager.getInstance().getDefaultProject() : project;
  }

  @SuppressWarnings("deprecation")
  private static Project getProject() {
    return DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT);
  }

  private static void installAutoUpdateMenu() {
    ID pool = Foundation.invoke("NSAutoreleasePool", "new");

    ID app = Foundation.invoke("NSApplication", "sharedApplication");
    ID menu = Foundation.invoke(app, Foundation.createSelector("menu"));
    ID item = Foundation.invoke(menu, Foundation.createSelector("itemAtIndex:"), 0);
    ID appMenu = Foundation.invoke(item, Foundation.createSelector("submenu"));

    ID checkForUpdatesClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSMenuItem"), "NSCheckForUpdates");
    Foundation.addMethod(checkForUpdatesClass, Foundation.createSelector("checkForUpdates"), IMPL, "v");

    Foundation.registerObjcClassPair(checkForUpdatesClass);

    ID checkForUpdates = Foundation.invoke("NSCheckForUpdates", "alloc");
    Foundation.invoke(checkForUpdates, Foundation.createSelector("initWithTitle:action:keyEquivalent:"), Foundation.nsString("Check for Updates..."), Foundation.createSelector("checkForUpdates"),
                      Foundation.nsString(""));
    Foundation.invoke(checkForUpdates, Foundation.createSelector("setTarget:"), checkForUpdates);

    Foundation.invoke(appMenu, Foundation.createSelector("insertItem:atIndex:"), checkForUpdates, 1);
    Foundation.invoke(checkForUpdates, Foundation.createSelector("release"));

    Foundation.invoke(pool, Foundation.createSelector("release"));
  }
}

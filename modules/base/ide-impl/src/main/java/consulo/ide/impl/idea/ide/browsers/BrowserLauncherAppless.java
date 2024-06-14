/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ide.browsers;

import consulo.application.ApplicationManager;
import consulo.application.CommonBundle;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.Patches;
import consulo.application.util.SystemInfo;
import consulo.container.boot.ContainerPathManager;
import consulo.ide.impl.idea.ide.BrowserUtil;
import consulo.ide.impl.idea.ide.GeneralSettings;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtilRt;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.util.io.ZipUtil;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.base.localize.CommonLocalize;
import consulo.platform.base.localize.IdeLocalize;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ExecUtil;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.OptionsDialog;
import consulo.ui.ex.awt.internal.GuiUtils;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.io.URLUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.webBrowser.BrowserLauncher;
import consulo.webBrowser.BrowserSpecificSettings;
import consulo.webBrowser.UrlOpener;
import consulo.webBrowser.WebBrowser;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BrowserLauncherAppless extends BrowserLauncher {
  static final Logger LOG = Logger.getInstance(BrowserLauncherAppless.class);
  public static BrowserLauncherAppless INSTANCE = new BrowserLauncherAppless();

  private static boolean isDesktopActionSupported(Desktop.Action action) {
    return !Patches.SUN_BUG_ID_6457572 && !Patches.SUN_BUG_ID_6486393 && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
  }

  public static boolean canUseSystemDefaultBrowserPolicy() {
    return isDesktopActionSupported(Desktop.Action.BROWSE)
      || Platform.current().os().isMac()
      || Platform.current().os().isWindows()
      || Platform.current().os().isUnix() && SystemInfo.hasXdgOpen();
  }

  private static GeneralSettings getGeneralSettingsInstance() {
    if (ApplicationManager.getApplication() != null) {
      return GeneralSettings.getInstance();
    }

    return new GeneralSettings();
  }

  @Nullable
  private static List<String> getDefaultBrowserCommand() {
    if (Platform.current().os().isWindows()) {
      return Arrays.asList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""));
    }
    else if (Platform.current().os().isMac()) {
      return Collections.singletonList(ExecUtil.getOpenCommandPath());
    }
    else if (Platform.current().os().isUnix() && SystemInfo.hasXdgOpen()) {
      return Collections.singletonList("xdg-open");
    }
    else {
      return null;
    }
  }

  @Override
  public void open(@Nonnull String url) {
    openOrBrowse(url, false);
  }

  @Override
  public void browse(@Nonnull File file) {
    browse(VfsUtil.toUri(file));
  }

  @Override
  public void browse(@Nonnull URI uri) {
    LOG.debug("Launch browser: [" + uri + "]");

    GeneralSettings settings = getGeneralSettingsInstance();
    if (settings.isUseDefaultBrowser()) {
      if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
        try {
          Desktop.getDesktop().browse(uri);
          LOG.debug("Browser launched using JDK 1.6 API");
          return;
        }
        catch (Exception e) {
          LOG.warn("Error while using Desktop API, fallback to CLI", e);
        }
      }

      List<String> command = getDefaultBrowserCommand();
      if (command != null) {
        doLaunch(uri.toString(), command, null, null, ArrayUtil.EMPTY_STRING_ARRAY, null);
        return;
      }
    }

    browseUsingPath(uri.toString(), settings.getBrowserPathOrDefault(), null, null, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void openOrBrowse(@Nonnull String url, boolean browse) {
    url = url.trim();

    if (url.startsWith("jar:")) {
      String files = extractFiles(url);
      if (files == null) {
        return;
      }
      url = files;
    }

    URI uri;
    if (BrowserUtil.isAbsoluteURL(url)) {
      uri = VfsUtil.toUri(url);
    }
    else {
      File file = new File(url);
      if (!browse && isDesktopActionSupported(Desktop.Action.OPEN)) {
        if (!file.exists()) {
          doShowError(IdeLocalize.errorFileDoesNotExist(file.getPath()).get(), null, null, null, null);
          return;
        }

        try {
          Desktop.getDesktop().open(file);
          return;
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }

      browse(file);
      return;
    }

    if (uri == null) {
      doShowError(IdeLocalize.errorMalformedUrl(url).get(), null, null, null, null);
    }
    else {
      browse(uri);
    }
  }

  @Nullable
  protected WebBrowser getEffectiveBrowser(@Nullable WebBrowser browser) {
    return browser;
  }

  @Nullable
  private static String extractFiles(String url) {
    try {
      int sharpPos = url.indexOf('#');
      String anchor = "";
      if (sharpPos != -1) {
        anchor = url.substring(sharpPos);
        url = url.substring(0, sharpPos);
      }

      Pair<String, String> pair = URLUtil.splitJarUrl(url);
      if (pair == null) return null;

      File jarFile = new File(FileUtil.toSystemDependentName(pair.first));
      if (!jarFile.canRead()) return null;

      String jarUrl = StandardFileSystems.FILE_PROTOCOL_PREFIX + FileUtil.toSystemIndependentName(jarFile.getPath());
      String jarLocationHash = jarFile.getName() + "." + Integer.toHexString(jarUrl.hashCode());
      final File outputDir = new File(getExtractedFilesDir(), jarLocationHash);

      final String currentTimestamp = String.valueOf(new File(jarFile.getPath()).lastModified());
      final File timestampFile = new File(outputDir, ".idea.timestamp");

      String previousTimestamp = null;
      if (timestampFile.exists()) {
        previousTimestamp = FileUtilRt.loadFile(timestampFile);
      }

      if (!currentTimestamp.equals(previousTimestamp)) {
        final Ref<Boolean> extract = new Ref<>();
        Runnable r = () -> {
          final ConfirmExtractDialog dialog = new ConfirmExtractDialog();
          if (dialog.isToBeShown()) {
            dialog.show();
            extract.set(dialog.isOK());
          }
          else {
            dialog.close(DialogWrapper.OK_EXIT_CODE);
            extract.set(true);
          }
        };

        try {
          GuiUtils.runOrInvokeAndWait(r);
        }
        catch (InvocationTargetException | InterruptedException ignored) {
          extract.set(false);
        }

        if (!extract.get()) {
          return null;
        }

        boolean closeZip = true;
        final ZipFile zipFile = new ZipFile(jarFile);
        try {
          ZipEntry entry = zipFile.getEntry(pair.second);
          if (entry == null) {
            return null;
          }
          InputStream is = zipFile.getInputStream(entry);
          ZipUtil.extractEntry(entry, is, outputDir);
          closeZip = false;
        }
        finally {
          if (closeZip) {
            zipFile.close();
          }
        }

        ApplicationManager.getApplication().invokeLater(() -> new Task.Backgroundable(null, "Extracting files...", true) {
          @Override
          public void run(@Nonnull final ProgressIndicator indicator) {
            final int size = zipFile.size();
            final int[] counter = new int[]{0};

            class MyFilter implements FilenameFilter {
              private final Set<File> myImportantDirs = ContainerUtil.newHashSet(outputDir, new File(outputDir, "resources"));
              private final boolean myImportantOnly;

              private MyFilter(boolean importantOnly) {
                myImportantOnly = importantOnly;
              }

              @Override
              public boolean accept(@Nonnull File dir, @Nonnull String name) {
                indicator.checkCanceled();
                boolean result = myImportantOnly == myImportantDirs.contains(dir);
                if (result) {
                  indicator.setFraction(((double)counter[0]) / size);
                  counter[0]++;
                }
                return result;
              }
            }

            try {
              try {
                ZipUtil.extract(zipFile, outputDir, new MyFilter(true));
                ZipUtil.extract(zipFile, outputDir, new MyFilter(false));
                FileUtil.writeToFile(timestampFile, currentTimestamp);
              }
              finally {
                zipFile.close();
              }
            }
            catch (IOException ignore) {
            }
          }
        }.queue());
      }

      return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(new File(outputDir, pair.second).getPath())) + anchor;
    }
    catch (IOException e) {
      LOG.warn(e);
      Messages.showErrorDialog("Cannot extract files: " + e.getMessage(), "Error");
      return null;
    }
  }

  private static File getExtractedFilesDir() {
    return new File(ContainerPathManager.get().getSystemPath(), "ExtractedFiles");
  }

  public static void clearExtractedFiles() {
    FileUtil.delete(getExtractedFilesDir());
  }

  private static class ConfirmExtractDialog extends OptionsDialog {
    private ConfirmExtractDialog() {
      super(null);
      setTitle("Confirmation");
      init();
    }

    @Override
    protected boolean isToBeShown() {
      return getGeneralSettingsInstance().isConfirmExtractFiles();
    }

    @Override
    protected void setToBeShown(boolean value, boolean onOk) {
      getGeneralSettingsInstance().setConfirmExtractFiles(value);
    }

    @Override
    protected boolean shouldSaveOptionsOnCancel() {
      return true;
    }

    @Override
    @Nonnull
    protected Action[] createActions() {
      setOKButtonText(CommonLocalize.buttonYes().get());
      return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      String message = "The files are inside an archive, do you want them to be extracted?";
      JLabel label = new JLabel(message);

      label.setIconTextGap(10);
      label.setIcon(TargetAWT.to(Messages.getQuestionIcon()));

      panel.add(label, BorderLayout.CENTER);
      panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

      return panel;
    }
  }

  @Override
  public void browse(@Nonnull String url, @Nullable WebBrowser browser) {
    browse(url, browser, null);
  }

  @Override
  public void browse(@Nonnull String url, @Nullable WebBrowser browser, @Nullable Project project) {
    WebBrowser effectiveBrowser = getEffectiveBrowser(browser);

    if (effectiveBrowser == null) {
      openOrBrowse(url, true);
    }
    else {
      for (UrlOpener urlOpener : UrlOpener.EP_NAME.getExtensionList()) {
        if (urlOpener.openUrl(effectiveBrowser, url, project)) {
          return;
        }
      }
    }
  }

  @Override
  public boolean browseUsingPath(@Nullable final String url,
                                 @Nullable String browserPath,
                                 @Nullable final WebBrowser browser,
                                 @Nullable final Project project,
                                 @Nonnull final String[] additionalParameters) {
    Runnable launchTask = null;
    if (browserPath == null && browser != null) {
      browserPath = PathUtil.toSystemDependentName(browser.getPath());
      launchTask = new Runnable() {
        @Override
        public void run() {
          browseUsingPath(url, null, browser, project, additionalParameters);
        }
      };
    }
    return doLaunch(url, browserPath, browser, project, additionalParameters, launchTask);
  }

  private boolean doLaunch(@Nullable String url,
                           @Nullable String browserPath,
                           @Nullable WebBrowser browser,
                           @Nullable Project project,
                           @Nonnull String[] additionalParameters,
                           @Nullable Runnable launchTask) {
    if (!checkPath(browserPath, browser, project, launchTask)) {
      return false;
    }
    return doLaunch(url, BrowserUtil.getOpenBrowserCommand(browserPath, false), browser, project, additionalParameters, launchTask);
  }

  @Contract("null, _, _, _ -> false")
  public boolean checkPath(@Nullable String browserPath, @Nullable WebBrowser browser, @Nullable Project project, @Nullable Runnable launchTask) {
    if (!StringUtil.isEmptyOrSpaces(browserPath)) {
      return true;
    }

    String message = browser != null
      ? browser.getBrowserNotFoundMessage()
      : IdeLocalize.errorPleaseSpecifyPathToWebBrowser(CommonBundle.settingsActionPath()).get();
    doShowError(message, browser, project, IdeLocalize.titleBrowserNotFound().get(), launchTask);
    return false;
  }

  private boolean doLaunch(
    @Nullable String url,
    @Nonnull List<String> command,
    @Nullable final WebBrowser browser,
    @Nullable final Project project,
    @Nonnull String[] additionalParameters,
    @Nullable Runnable launchTask
  ) {
    if (url != null && url.startsWith("jar:")) {
      String files = extractFiles(url);
      if (files == null) {
        return false;
      }
      url = files;
    }

    List<String> commandWithUrl = new ArrayList<>(command);
    if (url != null) {
      if (browser != null) {
        browser.addOpenUrlParameter(commandWithUrl, url);
      }
      else {
        commandWithUrl.add(url);
      }
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(commandWithUrl);
    addArgs(commandLine, browser == null ? null : browser.getSpecificSettings(), additionalParameters);
    try {
      Process process = commandLine.createProcess();
      checkCreatedProcess(browser, project, commandLine, process, launchTask);
      return true;
    }
    catch (ExecutionException e) {
      doShowError(e.getMessage(), browser, project, null, null);
      return false;
    }
  }

  protected void checkCreatedProcess(@Nullable WebBrowser browser, @Nullable Project project, @Nonnull GeneralCommandLine commandLine, @Nonnull Process process, @Nullable Runnable launchTask) {
  }

  protected void doShowError(@Nullable String error, @Nullable WebBrowser browser, @Nullable Project project, String title, @Nullable Runnable launchTask) {
    // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    LOG.warn(error);
  }

  private static void addArgs(@Nonnull GeneralCommandLine command, @Nullable BrowserSpecificSettings settings, @Nonnull String[] additional) {
    List<String> specific = settings == null ? Collections.<String>emptyList() : settings.getAdditionalParameters();
    if (specific.size() + additional.length > 0) {
      if (isOpenCommandUsed(command)) {
        if (BrowserUtil.isOpenCommandSupportArgs()) {
          command.addParameter("--args");
        }
        else {
          LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " + StringUtil.join(specific, ", ") + " " + Arrays.toString(additional));
          return;
        }
      }

      command.addParameters(specific);
      command.addParameters(additional);
    }
  }

  public static boolean isOpenCommandUsed(@Nonnull GeneralCommandLine command) {
    return Platform.current().os().isMac() && ExecUtil.getOpenCommandPath().equals(command.getExePath());
  }
}
/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide;

import consulo.process.cmd.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.BrowserLauncherAppless;
import consulo.application.ApplicationManager;
import consulo.application.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

public class BrowserUtil {
  // The pattern for 'scheme' mainly according to RFC1738.
  // We have to violate the RFC since we need to distinguish
  // real schemes from local Windows paths; The only difference
  // with RFC is that we do not allow schemes with length=1 (in other case
  // local paths like "C:/temp/index.html" would be erroneously interpreted as
  // external URLs.)
  private static final Pattern ourExternalPrefix = Pattern.compile("^[\\w\\+\\.\\-]{2,}:");
  private static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");

  private BrowserUtil() {
  }

  public static boolean isAbsoluteURL(String url) {
    return ourExternalPrefix.matcher(url.toLowerCase()).find();
  }

  public static String getDocURL(String url) {
    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);
    return anchorMatcher.find() ? anchorMatcher.reset().replaceAll("") : url;
  }

  @Nullable
  public static URL getURL(String url) throws MalformedURLException {
    return isAbsoluteURL(url) ? VfsUtil.convertToURL(url) : new URL("file", "", url);
  }

  public static void browse(@Nonnull VirtualFile file) {
    browse(VfsUtil.toUri(file));
  }

  public static void browse(@Nonnull File file) {
    getBrowserLauncher().browse(file);
  }

  public static void browse(@Nonnull URL url) {
    browse(url.toExternalForm());
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated Use {@link #browse(String)}
   */
  public static void launchBrowser(@Nonnull @NonNls String url) {
    browse(url);
  }

  public static void browse(@Nonnull @NonNls String url) {
    getBrowserLauncher().browse(url, null);
  }

  private static BrowserLauncher getBrowserLauncher() {
    BrowserLauncher launcher = ApplicationManager.getApplication() == null ? null : BrowserLauncher.getInstance();
    return launcher == null ? BrowserLauncherAppless.INSTANCE : launcher;
  }

  public static void open(@Nonnull @NonNls String url) {
    getBrowserLauncher().open(url);
  }

  /**
   * Main method: tries to launch a browser using every possible way
   */
  public static void browse(@Nonnull URI uri) {
    getBrowserLauncher().browse(uri);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nonnull
  @Deprecated
  public static List<String> getOpenBrowserCommand(@NonNls @Nonnull String browserPathOrName) {
    return getOpenBrowserCommand(browserPathOrName, false);
  }

  @Nonnull
  public static List<String> getOpenBrowserCommand(@NonNls @Nonnull String browserPathOrName, boolean newWindowIfPossible) {
    if (new File(browserPathOrName).isFile()) {
      return Collections.singletonList(browserPathOrName);
    }
    else if (SystemInfo.isMac) {
      List<String> command = newArrayList(ExecUtil.getOpenCommandPath(), "-a", browserPathOrName);
      if (newWindowIfPossible) {
        command.add("-n");
      }
      return command;
    }
    else if (SystemInfo.isWindows) {
      return Arrays.asList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""), browserPathOrName);
    }
    else {
      return Collections.singletonList(browserPathOrName);
    }
  }

  public static boolean isOpenCommandSupportArgs() {
    return SystemInfo.isMacOSSnowLeopard;
  }

  @Nonnull
  public static String getDefaultAlternativeBrowserPath() {
    if (SystemInfo.isWindows) {
      return "C:\\Program Files\\Internet Explorer\\IExplore.exe";
    }
    else if (SystemInfo.isMac) {
      return "open";
    }
    else {
      return "";
    }
  }
}

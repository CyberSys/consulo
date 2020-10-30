/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.event.MouseEvent;

public abstract class DvcsStatusWidget<T extends Repository> extends EditorBasedWidget
        implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe
{
  protected static final Logger LOG = Logger.getInstance(DvcsStatusWidget.class);
  private static final String MAX_STRING = "VCS: Rebasing feature-12345";

  @Nonnull
  private final String myVcsName;

  @Nullable
  private String myText;
  @Nullable
  private String myTooltip;
  @Nullable
  private Image myIcon;

  protected DvcsStatusWidget(@Nonnull Project project, @Nonnull String vcsName) {
    super(project);
    myVcsName = vcsName;
  }

  @Nullable
  protected abstract T guessCurrentRepository(@Nonnull Project project);

  @Nonnull
  protected abstract String getFullBranchName(@Nonnull T repository);

  protected abstract boolean isMultiRoot(@Nonnull Project project);

  @Nonnull
  protected abstract ListPopup getPopup(@Nonnull Project project, @Nonnull T repository);

  protected abstract void subscribeToRepoChangeEvents(@Nonnull Project project);

  protected abstract void rememberRecentRoot(@Nonnull String path);

  public void activate() {
    Project project = getProject();
    if (project != null) {
      installWidgetToStatusBar(project, this);
    }
  }

  public void deactivate() {
    Project project = getProject();
    if (project != null) {
      removeWidgetFromStatusBar(project, this);
    }
  }

  @Override
  public void dispose() {
    deactivate();
    super.dispose();
  }

  @Override
  @Nullable
  public Image getIcon() {
    return myIcon;
  }

  @Nonnull
  @Override
  public String ID() {
    return getClass().getName();
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
    LOG.debug("selection changed");
    update();
  }

  @Override
  public void fileOpened(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
    LOG.debug("file opened");
    update();
  }

  @Override
  public void fileClosed(@Nonnull FileEditorManager source, @Nonnull VirtualFile file) {
    LOG.debug("file closed");
    update();
  }

  @RequiredUIAccess
  @Nullable
  @Override
  public String getSelectedValue() {
    return StringUtil.defaultIfEmpty(myText, "");
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Nullable
  @Override
  public ListPopup getPopupStep() {
    Project project = getProject();
    if (project == null || project.isDisposed()) return null;
    T repository = guessCurrentRepository(project);
    if (repository == null) return null;

    return getPopup(project, repository);
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    // has no effect since the click opens a list popup, and the consumer is not called for the MultipleTextValuesPresentation
    return null;
  }

  protected void updateLater() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        LOG.debug("update after repository change");
        update();
      }
    });
  }

  @RequiredUIAccess
  private void update() {
    myText = null;
    myTooltip = null;
    myIcon = null;

    Project project = getProject();
    if (project == null || project.isDisposed()) return;
    T repository = guessCurrentRepository(project);
    if (repository == null) return;

    int maxLength = MAX_STRING.length() - 1; // -1, because there are arrows indicating that it is a popup
    myText = StringUtil.shortenTextWithEllipsis(getFullBranchName(repository), maxLength, 5);
    myTooltip = getToolTip(project);
    myIcon = getIcon(repository);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    rememberRecentRoot(repository.getRoot().getPath());
  }

  @Nullable
  protected Image getIcon(@Nonnull T repository) {
    if (repository.getState() != Repository.State.NORMAL) return AllIcons.General.Warning;
    return PlatformIconGroup.vcsBranch();
  }

  @Nullable
  private String getToolTip(@Nonnull Project project) {
    T currentRepository = guessCurrentRepository(project);
    if (currentRepository == null) return null;
    String branchName = getFullBranchName(currentRepository);
    if (isMultiRoot(project)) {
      return branchName + "\n" + "Root: " + currentRepository.getRoot().getName();
    }
    return branchName;
  }

  private void installWidgetToStatusBar(@Nonnull final Project project, @Nonnull final StatusBarWidget widget) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null && !isDisposed()) {
          statusBar.addWidget(widget, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), project);
          subscribeToMappingChanged();
          subscribeToRepoChangeEvents(project);
          update();
        }
      }
    });
  }

  private void removeWidgetFromStatusBar(@Nonnull final Project project, @Nonnull final StatusBarWidget widget) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null && !isDisposed()) {
          statusBar.removeWidget(widget.ID());
        }
      }
    });
  }

  private void subscribeToMappingChanged() {
    myProject.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, new VcsRepositoryMappingListener() {
      @Override
      public void mappingChanged() {
        LOG.debug("repository mappings changed");
        updateLater();
      }
    });
  }
}
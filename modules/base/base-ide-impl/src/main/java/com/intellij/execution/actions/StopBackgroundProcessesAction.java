// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import consulo.dataContext.DataContext;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.TaskInfo;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Pair;
import consulo.project.ui.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.ContainerUtil;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StopBackgroundProcessesAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  @Override
  public void update(@Nonnull AnActionEvent e) {
    e.getPresentation().setEnabled(!getCancellableProcesses(e.getProject()).isEmpty());
  }

  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    List<StopAction.HandlerItem> handlerItems = getItemsList(getCancellableProcesses(project));

    if (handlerItems.isEmpty()) {
      return;
    }

    final JBList<StopAction.HandlerItem> list = new JBList<>(handlerItems);
    list.setCellRenderer(new GroupedItemsListRenderer<>(new ListItemDescriptorAdapter<StopAction.HandlerItem>() {
      @Nullable
      @Override
      public String getTextFor(StopAction.HandlerItem item) {
        return item.displayName;
      }

      @Nullable
      @Override
      public Image getIconFor(StopAction.HandlerItem item) {
        return item.icon;
      }

      @Override
      public boolean hasSeparatorAboveOf(StopAction.HandlerItem item) {
        return item.hasSeparator;
      }
    }));

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list).setMovable(true)
            .setTitle(handlerItems.size() == 1 ? ExecutionBundle.message("confirm.background.process.stop") : ExecutionBundle.message("stop.background.process"))
            .setNamerForFiltering(o -> o.displayName).setItemChoosenCallback(() -> {
              List valuesList = list.getSelectedValuesList();
              for (Object o : valuesList) {
                if (o instanceof StopAction.HandlerItem) ((StopAction.HandlerItem)o).stop();
              }
            }).setRequestFocus(true).createPopup();

    InputEvent inputEvent = e.getInputEvent();
    Component component = inputEvent != null ? inputEvent.getComponent() : null;
    if (component != null && (ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace()) || ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(e.getPlace()))) {
      popup.showUnderneathOf(component);
    }
    else if (project == null) {
      popup.showInBestPositionFor(dataContext);
    }
    else {
      popup.showCenteredInCurrentWindow(project);
    }

  }

  @Nonnull
  private static List<Pair<TaskInfo, ProgressIndicator>> getCancellableProcesses(@Nullable Project project) {
    IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) return Collections.emptyList();

    return ContainerUtil.findAll(statusBar.getBackgroundProcesses(), pair -> pair.first.isCancellable() && !pair.second.isCanceled());
  }

  @Nonnull
  private static List<StopAction.HandlerItem> getItemsList(@Nonnull List<? extends Pair<TaskInfo, ProgressIndicator>> tasks) {
    List<StopAction.HandlerItem> items = new ArrayList<>(tasks.size());
    for (final Pair<TaskInfo, ProgressIndicator> eachPair : tasks) {
      items.add(new StopAction.HandlerItem(eachPair.first.getTitle(), AllIcons.Process.Step_passive, false) {
        @Override
        void stop() {
          eachPair.second.cancel();
        }
      });
    }
    return items;
  }
}

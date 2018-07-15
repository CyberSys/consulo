/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.accessibility.Accessible;
import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.awt.event.KeyEvent.VK_SPACE;

public class ActionButton extends JButton implements ActionButtonComponent, AnActionHolder, Accessible {

  private PropertyChangeListener myPresentationListener;
  protected final Presentation myPresentation;
  protected final AnAction myAction;
  protected final String myPlace;

  private boolean myNoIconsInPopup = false;

  private boolean myMinimalMode;
  private boolean myDecorateButtons;

  public ActionButton(AnAction action, Presentation presentation, String place, @Nonnull Dimension minimumSize) {
    myAction = action;
    myPresentation = presentation;
    myPlace = place;

    // Pressing the SPACE key is the same as clicking the button
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getModifiers() == 0 && e.getKeyCode() == VK_SPACE) {
          click();
        }
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        performAction(e);
      }
    });

    putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, Boolean.TRUE);
  }

  public void setMinimalMode(boolean minimalMode) {
    myMinimalMode = minimalMode;
  }

  public void setDecorateButtons(boolean decorateButtons) {
    myDecorateButtons = decorateButtons;
  }

  public void setNoIconsInPopup(boolean noIconsInPopup) {
    myNoIconsInPopup = noIconsInPopup;
  }

  public void setMinimumButtonSize(@Nonnull Dimension size) {
  }

  @Override
  public int getPopState() {
    return 0;
  }

  public Presentation getPresentation() {
    return myPresentation;
  }

  public boolean isButtonEnabled() {
    return isEnabled();
  }

  public void click() {
    performAction(new MouseEvent(this, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
  }

  private void performAction(MouseEvent e) {
    AnActionEvent event = AnActionEvent.createFromInputEvent(e, myPlace, myPresentation, getDataContext(), false, true);
    if (!ActionUtil.lastUpdateAndCheckDumb(myAction, event, false)) {
      return;
    }

    if (isButtonEnabled()) {
      final ActionManagerEx manager = ActionManagerEx.getInstanceEx();
      final DataContext dataContext = event.getDataContext();
      manager.fireBeforeActionPerformed(myAction, dataContext, event);
      Component component = dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      if (component != null && !component.isShowing()) {
        return;
      }
      actionPerformed(event);
      manager.queueActionPerformedEvent(myAction, dataContext, event);
      if (event.getInputEvent() instanceof MouseEvent) {
        //FIXME [VISTALL] we need that ?ToolbarClicksCollector.record(myAction, myPlace);
      }
    }
  }

  protected DataContext getDataContext() {
    ActionToolbar actionToolbar = UIUtil.getParentOfType(ActionToolbar.class, this);
    return actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext();
  }

  private void actionPerformed(final AnActionEvent event) {
    if (myAction instanceof ActionGroup && !(myAction instanceof CustomComponentAction) && ((ActionGroup)myAction).isPopup() && !((ActionGroup)myAction).canBePerformed(event.getDataContext())) {
      final ActionManagerImpl am = (ActionManagerImpl)ActionManager.getInstance();
      ActionPopupMenuImpl popupMenu = (ActionPopupMenuImpl)am.createActionPopupMenu(event.getPlace(), (ActionGroup)myAction, new MenuItemPresentationFactory() {
        @Override
        protected void processPresentation(Presentation presentation) {
          if (myNoIconsInPopup) {
            presentation.setIcon(null);
            presentation.setHoveredIcon(null);
          }
        }
      });
      popupMenu.setDataContextProvider(this::getDataContext);
      if (event.isFromActionToolbar()) {
        popupMenu.getComponent().show(this, 0, getHeight());
      }
      else {
        popupMenu.getComponent().show(this, getWidth(), 0);
      }

    }
    else {
      ActionUtil.performActionDumbAware(myAction, event);
    }
  }

  @Override
  public void removeNotify() {
    if (myPresentationListener != null) {
      myPresentation.removePropertyChangeListener(myPresentationListener);
      myPresentationListener = null;
    }
    super.removeNotify();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myPresentationListener == null) {
      myPresentation.addPropertyChangeListener(myPresentationListener = this::presentationPropertyChanded);
    }
    AnActionEvent e = AnActionEvent.createFromInputEvent(null, myPlace, myPresentation, getDataContext(), false, true);
    ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), myAction, e, false);
    updateToolTipText();
    updateIcon();
    updateEnabled();
  }

  @Override
  public void setToolTipText(String s) {
    String tooltipText = KeymapUtil.createTooltipText(s, myAction);
    super.setToolTipText(tooltipText.length() > 0 ? tooltipText : null);
  }

  public void updateEnabled() {
    setEnabled(myPresentation.isEnabled());
  }

  public void updateIcon() {
    Icon icon = myPresentation.getIcon();
    setIcon(icon);
    if (myPresentation.getDisabledIcon() != null) { // set disabled icon if it is specified
      setDisabledIcon(myPresentation.getDisabledIcon());
    }
    else {
      setDisabledIcon(IconLoader.getDisabledIcon(icon));
    }
  }

  void updateToolTipText() {
    String text = myPresentation.getText();
    setToolTipText(text == null ? myPresentation.getDescription() : text);
  }

  @Override
  public AnAction getAnAction() {
    return myAction;
  }

  protected void presentationPropertyChanded(PropertyChangeEvent e) {
    String propertyName = e.getPropertyName();
    if (Presentation.PROP_TEXT.equals(propertyName)) {
      updateToolTipText();
    }
    else if (Presentation.PROP_ENABLED.equals(propertyName)) {
      updateEnabled();
      updateIcon();
    }
    else if (Presentation.PROP_ICON.equals(propertyName)) {
      updateIcon();
    }
    else if (Presentation.PROP_DISABLED_ICON.equals(propertyName)) {
      setDisabledIcon(myPresentation.getDisabledIcon());
    }
    else if (Presentation.PROP_VISIBLE.equals(propertyName)) {
    }
    else if ("selected".equals(propertyName)) {
    }
  }
}

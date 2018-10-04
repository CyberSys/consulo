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
package consulo.actionSystem.ex;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import consulo.ui.SwingUIDecorator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Constructor;

/**
 * @author VISTALL
 * @since 2018-07-12
 * <p>
 * Component which looks like ComboBox but will show action popup.
 * How it works:
 * 1. UI painter hacked. We get laf ui instance - then, override popup field
 * 2. Adding items dont supported. There only one item, which used for rendering
 */
public final class ComboBoxButtonImpl extends JComboBox<Object> implements ComboBoxButton {
  private final ComboBoxAction myComboBoxAction;
  private final Presentation myPresentation;

  private Runnable myCurrentPopupCanceler;
  private PropertyChangeListener myButtonSynchronizer;

  private boolean myLikeButton;
  private Runnable myOnClickListener;

  public ComboBoxButtonImpl(ComboBoxAction comboBoxAction, Presentation presentation) {
    myComboBoxAction = comboBoxAction;
    myPresentation = presentation;

    setRenderer(new ColoredListCellRenderer<Object>() {
      @Override
      protected void customizeCellRenderer(@Nonnull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
        append(StringUtil.notNullize(myPresentation.getText()));
        setIcon(myPresentation.getIcon());
      }
    });

    // add and select one value
    revalidateValue();
    updateSize();
    updateTooltipText(presentation.getDescription());
  }

  private void revalidateValue() {
    Object oldValue = getSelectedItem();

    Object value = new Object();
    addItem(value);
    setSelectedItem(value);

    if (oldValue != null) {
      removeItem(oldValue);
    }
  }

  protected void hidePopup0() {
    if (myCurrentPopupCanceler != null) {
      myCurrentPopupCanceler.run();
      myCurrentPopupCanceler = null;
    }
  }

  protected void showPopup0() {
    hidePopup0();

    if(myLikeButton && myOnClickListener != null) {
      myOnClickListener.run();

      myCurrentPopupCanceler = null;
      return;
    }

    JBPopup popup = createPopup(() -> {
      myCurrentPopupCanceler = null;

      updateSize();
    });
    popup.showUnderneathOf(this);

    myCurrentPopupCanceler = popup::cancel;
  }

  protected boolean isPopupVisible0() {
    return myCurrentPopupCanceler != null;
  }

  private JBPopup createPopup(Runnable onDispose) {
    return myComboBoxAction.createPopup(getDataContext(), onDispose);
  }

  protected void updateSize() {
    revalidateValue();
    setSize(getPreferredSize());

    invalidate();
    repaint();
  }

  protected DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(this);
  }

  @Override
  public void removeNotify() {
    if (myButtonSynchronizer != null) {
      myPresentation.removePropertyChangeListener(myButtonSynchronizer);
      myButtonSynchronizer = null;
    }
    super.removeNotify();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myButtonSynchronizer == null) {
      myButtonSynchronizer = new MyButtonSynchronizer();
      myPresentation.addPropertyChangeListener(myButtonSynchronizer);
    }
  }

  private class MyButtonSynchronizer implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      String propertyName = evt.getPropertyName();
      if (Presentation.PROP_TEXT.equals(propertyName)) {
        updateSize();
      }
      else if (Presentation.PROP_DESCRIPTION.equals(propertyName)) {
        updateTooltipText((String)evt.getNewValue());
      }
      else if (Presentation.PROP_ICON.equals(propertyName)) {
        updateSize();
      }
      else if (Presentation.PROP_ENABLED.equals(propertyName)) {
        setEnabled(((Boolean)evt.getNewValue()).booleanValue());
      }
      else if (ComboBoxButton.LIKE_BUTTON.equals(propertyName)) {
        setLikeButton(true, (Runnable)evt.getNewValue());
      }
    }
  }

  private void updateTooltipText(String description) {
    String tooltip = KeymapUtil.createTooltipText(description, myComboBoxAction);
    setToolTipText(!tooltip.isEmpty() ? tooltip : null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void updateUI() {
    try {
      ComboBoxUI comboBoxUI = (ComboBoxUI)UIManager.getUI(this);

      Class uiHelper = (Class)UIManager.get("ComboBoxButtonUIHelper");
      if(uiHelper != null) {
        Constructor constructor = uiHelper.getConstructor(ComboBoxUI.class);
        setUI((ComboBoxUI)constructor.newInstance(comboBoxUI));
      }
      else {
        setUI(new ComboBoxButtonUI(comboBoxUI));
      }

      // refresh state
      setLikeButton(myLikeButton, myOnClickListener);
    }
    catch (Exception e) {
      // ignore if component check ui instance
      super.updateUI();
    }

    SwingUIDecorator.apply(SwingUIDecorator::decorateToolbarComboBox, this);
  }

  @Nonnull
  @Override
  public ComboBoxAction getComboBoxAction() {
    return myComboBoxAction;
  }

  @Override
  public void setLikeButton(boolean value, @Nullable Runnable onClick) {
    myLikeButton = value;
    myOnClickListener = onClick;
  }
}

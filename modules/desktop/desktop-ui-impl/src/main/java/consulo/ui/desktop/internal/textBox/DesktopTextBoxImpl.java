/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ui.desktop.internal.textBox;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import consulo.awt.impl.FromSwingComponentWrapper;
import consulo.disposer.Disposable;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.TextBox;
import consulo.ui.desktop.internal.validableComponent.DocumentSwingValidator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * @author VISTALL
 * @since 19-Nov-16.
 */
public class DesktopTextBoxImpl extends DocumentSwingValidator<String, JBTextField> implements TextBox, TextBoxWithTextField {
  private static class Listener extends DocumentAdapter {
    private DesktopTextBoxImpl myTextField;

    public Listener(DesktopTextBoxImpl textField) {
      myTextField = textField;
    }

    @Override
    @RequiredUIAccess
    protected void textChanged(DocumentEvent e) {
      myTextField.valueChanged();
    }
  }

  class MyJBTextField extends JBTextField implements FromSwingComponentWrapper {

    @Nonnull
    @Override
    public Component toUIComponent() {
      return DesktopTextBoxImpl.this;
    }
  }

  @RequiredUIAccess
  public DesktopTextBoxImpl(String text) {
    MyJBTextField field = new MyJBTextField();
    TextFieldPlaceholderFunction.install(field);
    initialize(field);
    addDocumentListenerForValidator(field.getDocument());

    field.getDocument().addDocumentListener(new Listener(this));
    setValue(text);
  }

  @Nonnull
  @Override
  public JTextField getTextField() {
    return toAWTComponent();
  }

  @Override
  public void setPlaceholder(@Nullable String text) {
    toAWTComponent().getEmptyText().setText(text);
  }

  @Override
  public void setVisibleLength(int columns) {
    toAWTComponent().setColumns(columns);
  }

  @Override
  public void selectAll() {
    toAWTComponent().selectAll();
  }

  @Override
  public void setEditable(boolean editable) {
    toAWTComponent().setEditable(editable);
  }

  @Override
  public boolean isEditable() {
    return toAWTComponent().isEditable();
  }

  @SuppressWarnings("unchecked")
  @RequiredUIAccess
  private void valueChanged() {
    dataObject().getDispatcher(ValueListener.class).valueChanged(new ValueEvent(this, getValue()));
  }

  @Nonnull
  @Override
  public Disposable addValueListener(@Nonnull ValueListener<String> valueListener) {
    return dataObject().addListener(ValueListener.class, valueListener);
  }

  @Override
  public String getValue() {
    return StringUtil.nullize(toAWTComponent().getText());
  }

  @RequiredUIAccess
  @Override
  public void setValue(String value, boolean fireListeners) {
    toAWTComponent().setText(value);
  }
}

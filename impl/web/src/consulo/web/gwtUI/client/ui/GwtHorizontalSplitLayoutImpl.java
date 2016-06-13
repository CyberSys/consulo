/*
 * Copyright 2013-2016 must-be.org
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
package consulo.web.gwtUI.client.ui;

import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.user.client.ui.HorizontalSplitPanel;
import com.google.gwt.user.client.ui.Widget;
import consulo.web.gwtUI.client.UIConverter;
import consulo.web.gwtUI.client.WebSocketProxy;
import consulo.web.gwtUI.shared.UIComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author VISTALL
 * @since 13-Jun-16
 */
public class GwtHorizontalSplitLayoutImpl implements InternalGwtComponent {
  private HorizontalSplitPanel myPanel = new HorizontalSplitPanel();

  public GwtHorizontalSplitLayoutImpl() {

  }

  @Override
  public void updateState(@NotNull Map<String, String> map) {
    myPanel.setSplitPosition(map.get("proportion") + "%");
  }

  @Override
  public void addChildren(WebSocketProxy proxy, UIComponent.Child child) {
    boolean first = Boolean.parseBoolean(child.getVariables().get("position"));

    final Widget component = UIConverter.create(proxy, child.getComponent()).asWidget();

    if (first) {
      component.addAttachHandler(new AttachEvent.Handler() {
        @Override
        public void onAttachOrDetach(AttachEvent event) {
          myPanel.setHeight(component.getElement().getClientHeight() + "px");
        }
      });
      myPanel.setLeftWidget(component);
    }
    else {
      myPanel.setRightWidget(component);
    }
  }

  @Override
  public Widget asWidget() {
    return myPanel;
  }
}

/*
 * Copyright 2013-2020 consulo.io
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
package consulo.sandboxPlugin.ui;

import consulo.ide.ui.FileChooserTextBoxBuilder;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.*;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.app.WindowWrapper;
import consulo.ui.cursor.StandardCursors;
import consulo.ui.image.Image;
import consulo.ui.layout.*;
import consulo.ui.model.TableModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2020-05-29
 */
public class UITester {
  private static class MyWindowWrapper extends WindowWrapper {

    public MyWindowWrapper() {
      super("UI Tester");
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createCenterComponent() {
      TabbedLayout tabbedLayout = TabbedLayout.create();

      tabbedLayout.addTab("Layouts", layouts()).setCloseHandler((tab, component) -> {
      });
      tabbedLayout.addTab("Components", components());
      tabbedLayout.addTab("Components > Table", table());
      tabbedLayout.addTab("Components > Tree", tree());
      tabbedLayout.addTab("Alerts", alerts());

      return tabbedLayout;
    }

    @RequiredUIAccess
    private Component layouts() {
      TabbedLayout tabbedLayout = TabbedLayout.create();

      VerticalLayout fold = VerticalLayout.create();
      fold.add(Label.create("Some label"));
      fold.add(Button.create("Some Button", (e) -> Alerts.okError("Clicked!").showAsync()));

      FoldoutLayout layout = FoldoutLayout.create(LocalizeValue.localizeTODO("Show Me"), fold);
      layout.addStateListener(state -> Alerts.okInfo("State " + state).showAsync());

      tabbedLayout.addTab("FoldoutLayout", layout);

      TwoComponentSplitLayout splitLayout = TwoComponentSplitLayout.create(SplitLayoutPosition.HORIZONTAL);
      splitLayout.setFirstComponent(DockLayout.create().center(Button.create("Left")));
      splitLayout.setSecondComponent(DockLayout.create().center(Button.create("Second")));

      tabbedLayout.addTab("SplitLayout", splitLayout);

      SwipeLayout swipeLayout = SwipeLayout.create();

      swipeLayout.register("left", () -> swipeChildLayout("Right", () -> swipeLayout.swipeRightTo("right")));
      swipeLayout.register("right", () -> swipeChildLayout("Left", () -> swipeLayout.swipeLeftTo("left")));

      tabbedLayout.addTab("SwipeLayout", swipeLayout);

      VerticalLayout borderLayout = VerticalLayout.create();
      DockLayout dockLayout = DockLayout.create();
      Button centerBtn = Button.create(LocalizeValue.localizeTODO("Center"));
      centerBtn.addClickListener(event -> {
        dockLayout.center(HorizontalLayout.create().add(Label.create(LocalizeValue.of(LocalDateTime.now().toString()))));
      }) ;
      
      borderLayout.add(centerBtn).add(dockLayout);

      borderLayout.add(centerBtn);

      tabbedLayout.addTab("DockLayout", borderLayout);

      return tabbedLayout;
    }

    @RequiredUIAccess
    private Layout swipeChildLayout(String text, @RequiredUIAccess Runnable runnable) {
      DockLayout dockLayout = DockLayout.create();

      dockLayout.center(HorizontalLayout.create().add(Button.create(text, e -> runnable.run())));

      return dockLayout;
    }

    @RequiredUIAccess
    private Component components() {
      VerticalLayout layout = VerticalLayout.create();

      FileChooserTextBoxBuilder builder = FileChooserTextBoxBuilder.create(null);
      layout.add(builder.build());

      ToggleSwitch toggleSwitch = ToggleSwitch.create(true);
      toggleSwitch.addValueListener(event -> Alerts.okInfo("toggle").showAsync());

      CheckBox checkBox = CheckBox.create("Check box");
      checkBox.addValueListener(event -> Alerts.okInfo("checkBox").showAsync());

      layout.add(HorizontalLayout.create().add(Label.create("Toggle Switch")).add(toggleSwitch).add(checkBox));

      layout.add(HorizontalLayout.create().add(Label.create("Password")).add(PasswordBox.create()));

      IntSlider intSlider = IntSlider.create(3);
      intSlider.addValueListener(event -> Alerts.okInfo("intSlider " + event.getValue()).showAsync());
      layout.add(HorizontalLayout.create().add(Label.create("IntSlider")).add(intSlider));

      layout.add(Hyperlink.create("Some Link", (e) -> Alerts.okInfo(LocalizeValue.localizeTODO("Clicked!!!")).showAsync()));
      return layout;
    }

    @RequiredUIAccess
    private Component table() {
      DockLayout layout = DockLayout.create();
      Map<String, String> map = new TreeMap<>();
      map.put("test1", "1");
      map.put("test2", "3");
      map.put("test3", "5");

      List<TableColumn<?, Map.Entry<String, String>>> columns = new ArrayList<>();
      columns.add(TableColumn.<String, Map.Entry<String, String>>create("Column 1", Map.Entry::getKey).build());
      columns.add(TableColumn.<String, Map.Entry<String, String>>create("Column 2", Map.Entry::getValue).build());

      TableModel<Map.Entry<String, String>> model = TableModel.of(map.entrySet());

      layout.center(ScrollableLayout.create(Table.create(columns, model)));

      return layout;
    }

    @RequiredUIAccess
    private Component tree() {
      Tree<String> tree = Tree.create(new TreeModel<String>() {
        @Override
        public void buildChildren(@Nonnull Function<String, TreeNode<String>> nodeFactory, @Nullable String parentValue) {
          if (parentValue == null) {
            for (int i = 0; i < 50; i++) {
              TreeNode<String> node = nodeFactory.apply("First Child = " + i);

              List<Image> icons =
                      List.of(PlatformIconGroup.nodesClass(), PlatformIconGroup.nodesEnum(), PlatformIconGroup.nodesStruct(), PlatformIconGroup.nodesInterface(), PlatformIconGroup.nodesAttribute());
              int r = new Random().nextInt(icons.size());

              node.setRender((s, textItemPresentation) -> {
                textItemPresentation.append(s);
                textItemPresentation.withIcon(icons.get(r));
              });
            }
          }
          else {
            for (int i = 0; i < 10; i++) {
              nodeFactory.apply(parentValue + ", second child = " + i);
            }
          }
        }
      });

      return ScrollableLayout.create(tree);
    }

    @RequiredUIAccess
    private Component alerts() {
      VerticalLayout layout = VerticalLayout.create();
      layout.add(Button.create(LocalizeValue.localizeTODO("Info. Hand Cursor"), event -> {
        Alerts.okInfo(LocalizeValue.localizeTODO("This is INFO")).showAsync();
      }).withCursor(StandardCursors.HAND));
      layout.add(Button.create(LocalizeValue.localizeTODO("Warning"), event -> {
        Alerts.okWarning(LocalizeValue.localizeTODO("This is WARN")).showAsync();
      }));
      layout.add(Button.create(LocalizeValue.localizeTODO("Error. Wait Cursor"), event -> {
        Alerts.okError(LocalizeValue.localizeTODO("This is ERROR")).showAsync();
      }).withCursor(StandardCursors.WAIT));
      layout.add(Button.create(LocalizeValue.localizeTODO("Question"), event -> {
        Alerts.okQuestion(LocalizeValue.localizeTODO("This is QUESTION")).showAsync();
      }));
      return layout;
    }

    @Nullable
    @Override
    protected Size getDefaultSize() {
      return new Size(500, 500);
    }
  }

  @RequiredUIAccess
  public static void show() {
    new MyWindowWrapper().showAsync();
  }
}

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
package consulo.ui;

import com.intellij.util.ObjectUtil;
import com.intellij.util.containers.ContainerUtil;
import consulo.ui.laf.MorphValue;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2018-07-23
 */
public interface UIDecorator {
  MorphValue<UIDecorator> ourDecorator = MorphValue.of(() -> {
    List<UIDecorator> list = new ArrayList<>();
    ContainerUtil.addAll(list, ServiceLoader.load(UIDecorator.class, UIDecorator.class.getClassLoader()));
    ContainerUtil.weightSort(list, UIDecorator::getWeight);
    for (UIDecorator uiDecorator : list) {
      if (uiDecorator.isAvaliable()) {
        return uiDecorator;
      }
    }
    return list.get(list.size() - 1);
  });

  static <ARG, U extends UIDecorator> void apply(BiPredicate<U, ARG> predicate, ARG arg, Class<U> clazz) {
    U u = ObjectUtil.tryCast(ourDecorator.getValue(), clazz);
    if (u == null) {
      return;
    }

    predicate.test(u, arg);
  }

  static <ARG, U extends UIDecorator> void apply(BiConsumer<U, ARG> predicate, ARG arg, Class<U> clazz) {
    U u = ObjectUtil.tryCast(ourDecorator.getValue(), clazz);
    if (u == null) {
      return;
    }

    predicate.accept(u, arg);
  }

  @Nonnull
  static <R, U extends UIDecorator> R get(Function<U, R> supplier, Class<U> clazz) {
    U u = ObjectUtil.tryCast(ourDecorator.getValue(), clazz);
    if (u == null) {
      throw new IllegalArgumentException("null value");
    }

    R fun = supplier.apply(u);
    if (fun != null) {
      return fun;
    }

    throw new IllegalArgumentException("null value");
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  static <U extends UIDecorator> U get() {
    return (U)ourDecorator.getValue();
  }

  boolean isAvaliable();

  default boolean isDark() {
    return false;
  }

  default int getWeight() {
    return 0;
  }
}

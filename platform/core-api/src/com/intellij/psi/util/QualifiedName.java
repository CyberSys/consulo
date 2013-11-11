package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class QualifiedName {
  @NotNull private final List<String> myComponents;

  private QualifiedName(int count) {
    myComponents = new ArrayList<String>(count);
  }

  public static QualifiedName fromComponents(Collection<String> components) {
    QualifiedName qName = new QualifiedName(components.size());
    qName.myComponents.addAll(components);
    return qName;
  }

  public static QualifiedName fromComponents(String... components) {
    QualifiedName result = new QualifiedName(components.length);
    Collections.addAll(result.myComponents, components);
    return result;
  }

  public QualifiedName append(String name) {
    QualifiedName result = new QualifiedName(myComponents.size()+1);
    result.myComponents.addAll(myComponents);
    result.myComponents.add(name);
    return result;
  }

  public QualifiedName append(QualifiedName qName) {
    QualifiedName result = new QualifiedName(myComponents.size()+qName.getComponentCount());
    result.myComponents.addAll(myComponents);
    result.myComponents.addAll(qName.getComponents());
    return result;
  }

  @NotNull
  public QualifiedName removeLastComponent() {
    return removeTail(1);
  }

  @NotNull
  public QualifiedName removeTail(int count) {
    int size = myComponents.size();
    QualifiedName result = new QualifiedName(size);
    result.myComponents.addAll(myComponents);
    for (int i = 0; i < count && result.myComponents.size() > 0; i++) {
      result.myComponents.remove(result.myComponents.size()-1);
    }
    return result;
  }

  @NotNull
  public QualifiedName removeHead(int count) {
    int size = myComponents.size();
    QualifiedName result = new QualifiedName(size);
    result.myComponents.addAll(myComponents);
    for (int i = 0; i < count && result.myComponents.size() > 0; i++) {
      result.myComponents.remove(0);
    }
    return result;
  }

  @NotNull
  public List<String> getComponents() {
    return myComponents;
  }

  public int getComponentCount() {
    return myComponents.size();
  }

  public boolean matches(String... components) {
    if (myComponents.size() != components.length) {
      return false;
    }
    for (int i = 0; i < myComponents.size(); i++) {
      if (!myComponents.get(i).equals(components[i])) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesPrefix(QualifiedName prefix) {
    if (getComponentCount() < prefix.getComponentCount()) {
      return false;
    }
    for (int i = 0; i < prefix.getComponentCount(); i++) {
      final String component = getComponents().get(i);
      if (component == null || !component.equals(prefix.getComponents().get(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean endsWith(@NotNull String suffix) {
    return suffix.equals(getLastComponent());
  }

  public static void serialize(@Nullable QualifiedName qName, StubOutputStream dataStream) throws IOException {
    if (qName == null) {
      dataStream.writeVarInt(0);
    }
    else {
      dataStream.writeVarInt(qName.getComponentCount());
      for (String s : qName.myComponents) {
        dataStream.writeName(s);
      }
    }
  }

  @Nullable
  public static QualifiedName deserialize(StubInputStream dataStream) throws IOException {
    QualifiedName qName;
    int size = dataStream.readVarInt();
    if (size == 0) {
      qName = null;
    }
    else {
      qName = new QualifiedName(size);
      for (int i = 0; i < size; i++) {
        final StringRef name = dataStream.readName();
        qName.myComponents.add(name == null ? null : name.getString());
      }
    }
    return qName;
  }

  @Nullable
  public String getLastComponent() {
    if (myComponents.size() == 0) {
      return null;
    }
    return myComponents.get(myComponents.size()-1);
  }

  @Override
  public String toString() {
    return join(".");
  }

  public String join(final String separator) {
    return StringUtil.join(myComponents, separator);
  }

  public static QualifiedName fromDottedString(@NotNull String refName) {
    return fromComponents(refName.split("\\."));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QualifiedName that = (QualifiedName)o;
    return myComponents.equals(that.myComponents);
  }

  @Override
  public int hashCode() {
    return myComponents.hashCode();
  }
}
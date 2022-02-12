/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.ui;

import consulo.application.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.packaging.impl.elements.ExtractedDirectoryPackagingElement;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import consulo.ui.ex.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;

/**
 * @author nik
 */
public class ExtractedDirectoryPresentation extends PackagingElementPresentation {
  private final String myJarPath;
  private final String myPathInJar;
  private final VirtualFile myFile;

  public ExtractedDirectoryPresentation(ExtractedDirectoryPackagingElement element) {
    myFile = element.findFile();
    myJarPath = element.getFilePath();
    myPathInJar = element.getPathInJar();
  }

  public String getPresentableName() {
    return PathUtil.getFileName(myJarPath) + myPathInJar;
  }

  public void render(@Nonnull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    presentationData.setIcon(AllIcons.Nodes.ExtractedFolder);
    final String parentPath = PathUtil.getParentPath(myJarPath);
    if (myFile == null || !myFile.isDirectory()) {
      mainAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      final VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(parentPath);
      if (parentFile == null) {
        commentAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      }
    }
    presentationData.addText("Extracted '" + PathUtil.getFileName(myJarPath) + myPathInJar + "'", mainAttributes);
    presentationData.addText(" (" + parentPath + ")", commentAttributes);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.EXTRACTED_DIRECTORY;
  }
}

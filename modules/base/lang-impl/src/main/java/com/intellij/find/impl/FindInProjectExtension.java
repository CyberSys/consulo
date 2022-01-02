/*
 * Copyright 2013-2021 consulo.io
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
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import consulo.extensions.StrictExtensionPointName;

/**
 * @author VISTALL
 * @since 12/12/2021
 */
public interface FindInProjectExtension {
  public static final StrictExtensionPointName<Application, FindInProjectExtension> EP_NAME = StrictExtensionPointName.create(Application.class, "com.intellij.findInProjectExtension");

  public boolean initModelFromContext(FindModel model, DataContext dataContext);
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.mustbe.consulo.DeprecationInfo;

import java.io.IOException;

/**
 * @author max
 */
@Deprecated
@DeprecationInfo(value = "Please regenerate lexer with new skeleton, avoid using IDEA jflex skeleton", until = "2.0")
public interface FlexLexer {
  void yybegin(int state);
  int yystate();
  int getTokenStart();
  int getTokenEnd();
  IElementType advance() throws IOException;
  void reset(CharSequence buf, int start, int end, int initialState);
}

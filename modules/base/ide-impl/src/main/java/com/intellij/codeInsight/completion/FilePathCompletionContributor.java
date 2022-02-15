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

package com.intellij.codeInsight.completion;

import consulo.language.editor.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import consulo.language.impl.psi.reference.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.util.ArrayUtil;
import consulo.application.progress.ProcessCanceledException;
import consulo.application.progress.ProgressManager;
import consulo.codeInsight.completion.CompletionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReference;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileNameMatcher;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author spleaner
 */
public class FilePathCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(FilePathCompletionContributor.class);

  public FilePathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(), new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result) {
        final PsiReference psiReference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (getReference(psiReference) != null && parameters.getInvocationCount() == 1) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CODE_COMPLETION);
          result.addLookupAdvertisement(CodeInsightBundle.message("class.completion.file.path", shortcut));
        }
      }
    });

    CompletionProvider provider = new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull final CompletionParameters parameters, ProcessingContext context, @Nonnull final CompletionResultSet _result) {
        if (!parameters.isExtendedCompletion()) {
          return;
        }

        @Nonnull final CompletionResultSet result = _result.caseInsensitive();
        final PsiElement e = parameters.getPosition();
        final Project project = e.getProject();

        final PsiReference psiReference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());

        final Pair<FileReference, Boolean> fileReferencePair = getReference(psiReference);
        if (fileReferencePair != null) {
          final FileReference first = fileReferencePair.getFirst();
          if (first == null) return;

          final FileReferenceSet set = first.getFileReferenceSet();
          String prefix = set.getPathString().substring(0, parameters.getOffset() - set.getElement().getTextRange().getStartOffset() - set.getStartInElement());

          List<String> pathPrefixParts = null;
          int lastSlashIndex;
          if ((lastSlashIndex = prefix.lastIndexOf('/')) != -1) {
            pathPrefixParts = StringUtil.split(prefix.substring(0, lastSlashIndex), "/");
            prefix = prefix.substring(lastSlashIndex + 1);
          }

          final CompletionResultSet __result = result.withPrefixMatcher(prefix).caseInsensitive();

          final PsiFile originalFile = parameters.getOriginalFile();
          final VirtualFile contextFile = originalFile.getVirtualFile();
          if (contextFile != null) {
            final String[] fileNames = getAllNames(project);
            final Set<String> resultNames = new TreeSet<>();
            for (String fileName : fileNames) {
              if (filenameMatchesPrefixOrType(fileName, prefix, set.getSuitableFileTypes(), parameters.getInvocationCount())) {
                resultNames.add(fileName);
              }
            }

            final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

            final Module contextModule = index.getModuleForFile(contextFile);
            if (contextModule != null) {
              final List<FileReferenceHelper> helpers = FileReferenceHelperRegistrar.getHelpers(originalFile);

              final ProjectAwareSearchScope scope = ProjectScopes.getProjectScope(project);
              for (final String name : resultNames) {
                ProgressManager.checkCanceled();

                final PsiFile[] files = FilenameIndex.getFilesByName(project, name, scope);

                if (files.length <= 0) {
                  continue;
                }
                for (final PsiFile file : files) {
                  ProgressManager.checkCanceled();

                  final VirtualFile virtualFile = file.getVirtualFile();
                  if (virtualFile == null || !virtualFile.isValid() || Comparing.equal(virtualFile, contextFile)) {
                    continue;
                  }
                  List<FileReferenceHelper> helperList = new ArrayList<>();
                  for (FileReferenceHelper contextHelper : helpers) {
                    ProgressManager.checkCanceled();

                    if (contextHelper.isMine(project, virtualFile)) {
                      if (pathPrefixParts == null || fileMatchesPathPrefix(contextHelper.getPsiFileSystemItem(project, virtualFile), pathPrefixParts)) {
                        helperList.add(contextHelper);
                      }
                    }
                  }
                  if (!helperList.isEmpty()) {
                    __result.addElement(new FilePathLookupItem(file, helperList));
                  }
                }
              }
            }
          }

          if (set.getSuitableFileTypes().length > 0 && parameters.getInvocationCount() == 1) {
            final String shortcut = getActionShortcut(IdeActions.ACTION_CODE_COMPLETION);
            result.addLookupAdvertisement(CodeInsightBundle.message("class.completion.file.path.all.variants", shortcut));
          }

          if (fileReferencePair.getSecond()) result.stopHere();
        }
      }
    };
    extend(CompletionType.BASIC, psiElement(), provider);
  }

  private static boolean filenameMatchesPrefixOrType(final String fileName,
                                                     final String prefix,
                                                     final FileType[] suitableFileTypes,
                                                     final int invocationCount) {
    final boolean prefixMatched = prefix.length() == 0 || StringUtil.startsWithIgnoreCase(fileName, prefix);
    if (prefixMatched && (suitableFileTypes.length == 0 || invocationCount > 2)) return true;

    if (prefixMatched) {
      final String extension = FileUtilRt.getExtension(fileName);
      if (extension.length() == 0) return false;

      for (final FileType fileType : suitableFileTypes) {
        for (final FileNameMatcher matcher : FileTypeManager.getInstance().getAssociations(fileType)) {
          if (matcher.accept(fileName)) return true;
        }
      }
    }

    return false;
  }

  private static boolean fileMatchesPathPrefix(@Nullable final PsiFileSystemItem file, @Nonnull final List<String> pathPrefix) {
    if (file == null) return false;

    final List<String> contextParts = new ArrayList<>();
    PsiFileSystemItem parentFile = file;
    PsiFileSystemItem parent;
    while ((parent = parentFile.getParent()) != null) {
      if (parent.getName().length() > 0) contextParts.add(0, parent.getName().toLowerCase());
      parentFile = parent;
    }

    final String path = StringUtil.join(contextParts, "/");

    int nextIndex = 0;
    for (@NonNls final String s : pathPrefix) {
      if ((nextIndex = path.indexOf(s.toLowerCase(), nextIndex)) == -1) return false;
    }

    return true;
  }

  private static String[] getAllNames(@Nonnull final Project project) {
    Set<String> names = new HashSet<>();
    final ChooseByNameContributor[] nameContributors = ChooseByNameContributor.FILE_EP_NAME.getExtensions();
    for (final ChooseByNameContributor contributor : nameContributors) {
      try {
        names.addAll(Arrays.asList(contributor.getNames(project, false)));
      }
      catch (ProcessCanceledException ex) {
        // index corruption detected, ignore
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }

    return ArrayUtil.toStringArray(names);
  }

  @Nullable
  private static Pair<FileReference, Boolean> getReference(final PsiReference original) {
    if (original == null) {
      return null;
    }

    if (original instanceof PsiMultiReference) {
      final PsiMultiReference multiReference = (PsiMultiReference)original;
      for (PsiReference reference : multiReference.getReferences()) {
        if (reference instanceof FileReference) {
          if (((FileReference)reference).getFileReferenceSet().supportsExtendedCompletion()) {
            return Pair.create((FileReference)reference, false);
          }
        }
      }
    }
    else if (original instanceof FileReferenceOwner) {
      final PsiFileReference fileReference = ((FileReferenceOwner)original).getLastFileReference();
      if (fileReference instanceof FileReference) {
        if (((FileReference)fileReference).getFileReferenceSet().supportsExtendedCompletion()) {
          return Pair.create((FileReference)fileReference, true);
        }
      }
    }

    return null;
  }

  public static class FilePathLookupItem extends LookupElement {
    private final String myName;
    private final String myPath;
    private final String myInfo;
    private final Image myIcon;
    private final PsiFile myFile;
    private final List<FileReferenceHelper> myHelpers;

    public FilePathLookupItem(@Nonnull final PsiFile file, @Nonnull final List<FileReferenceHelper> helpers) {
      myName = file.getName();
      myPath = file.getVirtualFile().getPath();

      myHelpers = helpers;

      myInfo = FileInfoManager.getFileAdditionalInfo(file);
      myIcon = file.getFileType().getIcon();

      myFile = file;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    @Override
    public String toString() {
      return String.format("%s%s", myName, myInfo == null ? "" : " (" + myInfo + ")");
    }

    @Nonnull
    @Override
    public Object getObject() {
      return myFile;
    }

    @Override
    @Nonnull
    public String getLookupString() {
      return myName;
    }

    @Override
    public void handleInsert(InsertionContext context) {
      context.commitDocument();
      if (myFile.isValid()) {
        final PsiReference psiReference = context.getFile().findReferenceAt(context.getStartOffset());
        final Pair<FileReference, Boolean> fileReferencePair = getReference(psiReference);
        if (fileReferencePair != null) {
          FileReference ref = fileReferencePair.getFirst();
          context.setTailOffset(ref.getRangeInElement().getEndOffset() + ref.getElement().getTextRange().getStartOffset());
          ref.bindToElement(myFile);
        }
      }
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      final String relativePath = getRelativePath();

      final StringBuilder sb = new StringBuilder();
      if (myInfo != null) {
        sb.append(" (").append(myInfo);
      }

      if (relativePath != null && !relativePath.equals(myName)) {
        if (myInfo != null) {
          sb.append(", ");
        }
        else {
          sb.append(" (");
        }

        sb.append(relativePath);
      }

      if (sb.length() > 0) {
        sb.append(')');
      }

      presentation.setItemText(myName);

      if (sb.length() > 0) {
        presentation.setTailText(sb.toString(), true);
      }

      presentation.setIcon(myIcon);
    }

    @Nullable
    private String getRelativePath() {
      final VirtualFile virtualFile = myFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      for (FileReferenceHelper helper : myHelpers) {
        final PsiFileSystemItem root = helper.findRoot(myFile.getProject(), virtualFile);
        String path = PsiFileSystemItemUtil.getRelativePath(root, helper.getPsiFileSystemItem(myFile.getProject(), virtualFile));
        if (path != null) return path;
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FilePathLookupItem that = (FilePathLookupItem)o;

      if (!myName.equals(that.myName)) return false;
      if (!myPath.equals(that.myPath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + myPath.hashCode();
      return result;
    }
  }
}
package org.consulo.compiler.server.compiler;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TranslatingCompilerFilesMonitorImpl extends TranslatingCompilerFilesMonitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TranslatingCompilerFilesMonitor");
  private static final boolean ourDebugMode = false;

  private final Object myDataLock = new Object();

  @Override
  public void suspendProject(Project project) {
  }

  @Override
  public void watchProject(Project project) {
  }

  @Override
  public boolean isSuspended(Project project) {
    return false;
  }

  @Override
  public boolean isSuspended(int projectId) {
    return false;
  }

  @Override
  public void collectFiles(CompileContext context,
                           TranslatingCompiler compiler,
                           Iterator<VirtualFile> scopeSrcIterator,
                           boolean forceCompile,
                           boolean isRebuild,
                           Collection<VirtualFile> toCompile,
                           Collection<Trinity<File, String, Boolean>> toDelete) {
    final Project project = context.getProject();

    final CompilerManager configuration = CompilerManager.getInstance(project);
    final boolean _forceCompile = forceCompile || isRebuild;
    //final Set<VirtualFile> selectedForRecompilation = new HashSet<VirtualFile>();
    synchronized (myDataLock) {
      //final TIntHashSet pathsToRecompile = mySourcesToRecompile.get(projectId);
      if (_forceCompile) {
        if (ourDebugMode) {
          System.out.println("Analysing potentially recompilable files for " + compiler.getDescription());
        }
        while (scopeSrcIterator.hasNext()) {
          final VirtualFile file = scopeSrcIterator.next();
          if (!file.isValid()) {
            if (LOG.isDebugEnabled() || ourDebugMode) {
              LOG.debug("Skipping invalid file " + file.getPresentableUrl());
              if (ourDebugMode) {
                System.out.println("\t SKIPPED(INVALID) " + file.getPresentableUrl());
              }
            }
            continue;
          }

          if (_forceCompile) {
            if (compiler.isCompilableFile(file, context) && !configuration.isExcludedFromCompilation(file)) {
              toCompile.add(file);
              if (ourDebugMode) {
                System.out.println("\t INCLUDED " + file.getPresentableUrl());
              }
              //selectedForRecompilation.add(file);
            }
            else {
              if (ourDebugMode) {
                System.out.println("\t NOT COMPILABLE OR EXCLUDED " + file.getPresentableUrl());
              }
            }
          }
          else
            if (compiler.isCompilableFile(file, context) && !configuration.isExcludedFromCompilation(file)) {
              toCompile.add(file);
              if (ourDebugMode) {
                System.out.println("\t INCLUDED " + file.getPresentableUrl());
              }
             // selectedForRecompilation.add(file);
            }
            else {
              if (ourDebugMode) {
                System.out.println("\t NOT COMPILABLE OR EXCLUDED " + file.getPresentableUrl());
              }
            }
          }
        }
      }
  }

  @Override
  public void update(CompileContext context,
                     @Nullable String outputRoot,
                     Collection<TranslatingCompiler.OutputItem> successfullyCompiled,
                     VirtualFile[] filesToRecompile) throws IOException {
  }

  @Override
  public void updateOutputRootsLayout(Project project) {
  }

  @Override
  public List<String> getCompiledClassNames(VirtualFile srcFile, Project project) {
    return Collections.emptyList();
  }

  @Override
  public void scanSourceContent(com.intellij.compiler.impl.TranslatingCompilerFilesMonitorImpl.ProjectRef projRef,
                                Collection<VirtualFile> roots,
                                int totalRootCount,
                                boolean isNewRoots) {
  }

  @Override
  public void ensureInitializationCompleted(Project project, ProgressIndicator indicator) {
  }

  @Override
  public void scanSourcesForCompilableFiles(Project project) {
  }

  @Override
  public boolean isMarkedForCompilation(Project project, VirtualFile file) {
    return false;
  }
}
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
package consulo.ide.impl.idea.openapi.vfs.impl.http;

import consulo.application.ApplicationManager;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.document.util.FileContentUtilCore;
import consulo.virtualFileSystem.http.HttpVirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class VirtualFileImpl extends HttpVirtualFile {
  private final HttpFileSystemBase myFileSystem;
  private final @Nullable
  RemoteFileInfoImpl myFileInfo;
  private FileType myInitialFileType;
  private final String myPath;
  private final String myParentPath;
  private final String myName;

  VirtualFileImpl(HttpFileSystemBase fileSystem, String path, final @Nullable RemoteFileInfoImpl fileInfo) {
    myFileSystem = fileSystem;
    myPath = path;
    myFileInfo = fileInfo;
    if (myFileInfo != null) {
      myFileInfo.addDownloadingListener(new FileDownloadingAdapter() {
        @Override
        public void fileDownloaded(final VirtualFile localFile) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              VirtualFileImpl file = VirtualFileImpl.this;
              FileDocumentManager.getInstance().reloadFiles(file);
              if (!localFile.getFileType().equals(myInitialFileType)) {
                FileContentUtilCore.reparseFiles(file);
              }
            }
          });
        }
      });

      int end = path.indexOf("?");
      if (end != -1) {
        path = path.substring(0, end);
      }
      path = StringUtil.trimEnd(path, "/");
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash == -1) {
        myParentPath = null;
        myName = path;
      }
      else {
        myParentPath = path.substring(0, lastSlash);
        myName = path.substring(lastSlash + 1);
      }
    }
    else {
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash == path.length() - 1) {
        myParentPath = null;
        myName = path;
      }
      else {
        int prevSlash = path.lastIndexOf('/', lastSlash - 1);
        if (prevSlash < 0) {
          myParentPath = path.substring(0, lastSlash + 1);
          myName = path.substring(lastSlash + 1);
        }
        else {
          myParentPath = path.substring(0, lastSlash);
          myName = path.substring(lastSlash + 1);
        }
      }
    }
  }

  @Override
  @Nullable
  public RemoteFileInfoImpl getFileInfo() {
    return myFileInfo;
  }

  @Override
  @Nonnull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "HttpVirtualFile:" + myPath + ", info=" + myFileInfo;
  }

  @Override
  public VirtualFile getParent() {
    if (myParentPath == null) return null;
    return myFileSystem.findFileByPath(myParentPath, true);
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isDirectory() {
    return myFileInfo == null;
  }

  @Override
  public VirtualFile[] getChildren() {
    if (myFileInfo == null) {
      return EMPTY_ARRAY;
    }
    throw new UnsupportedOperationException();
  }

  @Override
  @Nonnull
  public FileType getFileType() {
    if (myFileInfo == null) {
      return super.getFileType();
    }

    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.getFileType();
    }
    FileType fileType = super.getFileType();
    if (myInitialFileType == null) {
      myInitialFileType = fileType;
    }
    return fileType;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getInputStream();
      }
    }
    throw new UnsupportedOperationException();
  }

  @Override
  @Nonnull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    if (myFileInfo != null) {
      VirtualFile localFile = myFileInfo.getLocalFile();
      if (localFile != null) {
        return localFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
      }
    }
    throw new UnsupportedOperationException();
  }

  @Override
  @Nonnull
  public byte[] contentsToByteArray() throws IOException {
    if (myFileInfo == null) {
      throw new UnsupportedOperationException();
    }

    VirtualFile localFile = myFileInfo.getLocalFile();
    if (localFile != null) {
      return localFile.contentsToByteArray();
    }
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public long getLength() {
    return -1;
  }

  @Override
  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    if (myFileInfo != null) {
      myFileInfo.refresh(postRunnable);
    }
    else if (postRunnable != null) {
      postRunnable.run();
    }
  }
}

package com.maplecloudy.avro.mapreduce;

import java.io.Closeable;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.avro.file.SeekableInput;

/** Adapt an {@link FSDataInputStream} to {@link SeekableInput}. */
public class FsInput implements Closeable, SeekableInput {
  private final FSDataInputStream stream;
  private final long len;

  /** Construct given a path and a configuration. */
  public FsInput(Path path, Configuration conf) throws IOException {
    this.stream = path.getFileSystem(conf).open(path);
    this.len = path.getFileSystem(conf).getFileStatus(path).getLen();
  }
  
  /** Construct given a path and a fs. */
  public FsInput(Path path, FileSystem fs) throws IOException {
    this.stream = fs.open(path);
    this.len = fs.getFileStatus(path).getLen();
  }

  public long length() {
    return len;
  }

  public int read(byte[] b, int off, int len) throws IOException {
    return stream.read(b, off, len);
  }

  public void seek(long p) throws IOException {
    stream.seek(p);
  }

  public long tell() throws IOException {
    return stream.getPos();
  }

  public void close() throws IOException {
    stream.close();
  }
}

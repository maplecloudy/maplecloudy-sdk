package com.maplecloudy.avro.mapreduce.output;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.hadoop.util.ReflectionUtils;

import com.maplecloudy.avro.mapreduce.output.MultipleOutputs.WrappedStatusReporter;

@SuppressWarnings({"rawtypes"})
public class RollOutputs<K,V> {
  
  public static final Log LOG = LogFactory.getLog(RollOutputs.class);
  private TaskAttemptContext context;
  private int rollSize = 0;
  private RecordWriter<K,V> recordWriter;
  private int currentNum = 0;
  private String currentOutName = null;
  private String baseName;
  
  Class<? extends ExtFileOutputFormat> outputFormatClass;
  
  public void setRollSize(int rollSize) {
    this.rollSize = rollSize;
  }
  
  public RollOutputs(TaskAttemptContext context,
      Class<? extends ExtFileOutputFormat> outputFormatClass,
      Class<K> keyClass, Class<V> valueClass, String baseName)
      throws IOException {
    this.baseName = baseName;
    this.outputFormatClass = outputFormatClass;
    Job job;
    try {
      
      job = new Job(context.getConfiguration());
      
      job.setOutputFormatClass(outputFormatClass);
      job.setOutputKeyClass(keyClass);
      job.setOutputValueClass(valueClass);
      this.context = new TaskAttemptContextImpl(job.getConfiguration(),
          context.getTaskAttemptID(),new WrappedStatusReporter(context));
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    }
    
  }
  
  public void write(K key, V value) throws IOException, InterruptedException {
    currentNum += 1;
    getRecordWriter(context).write(key, value);
  }
  
  private String getCurrentOutName() {
    if (rollSize <= 0) return baseName;
    int curRoll = (currentNum - 1) / rollSize;
    return (baseName + "-" + curRoll);
    
  }
  
  @SuppressWarnings({"unchecked"})
  private synchronized RecordWriter<K,V> getRecordWriter(
      TaskAttemptContext taskContext) throws IOException, InterruptedException {
    String curName = getCurrentOutName();
    
    if (curName.equals(currentOutName)) {
      return recordWriter;
    } else {
      currentOutName = curName;
      if (LOG.isInfoEnabled()) LOG.info("current roll out name is : ["
          + curName + "]");
      
      // close old output file
      close();
      ExtFileOutputFormat.setOutputName(taskContext, curName);
      try {
        recordWriter = ((OutputFormat<K,V>) ReflectionUtils.newInstance(
            taskContext.getOutputFormatClass(), taskContext.getConfiguration()))
            .getRecordWriter(taskContext);
      } catch (ClassNotFoundException e) {
        throw new IOException(e);
      }
      return recordWriter;
    }
    
  }
  
  public void close() throws IOException, InterruptedException {
    if (recordWriter != null) recordWriter.close(context);
  }
}

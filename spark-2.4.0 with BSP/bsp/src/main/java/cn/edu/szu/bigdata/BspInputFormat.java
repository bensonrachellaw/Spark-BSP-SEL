package cn.edu.szu.bigdata;

import com.google.common.base.Charsets;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.mapred.*;

import java.io.IOException;

/**
 * Created by longhao on 2020/11/17
 */


@InterfaceAudience.Public
@InterfaceStability.Stable
public class BspInputFormat extends FileInputFormat<LongWritable, Text>
        implements JobConfigurable {

    private CompressionCodecFactory compressionCodecs = null;

    public void configure(JobConf conf) {
        compressionCodecs = new CompressionCodecFactory(conf);
    }

    protected boolean isSplitable(FileSystem fs, Path file) {
        return false;
    }

    public RecordReader<LongWritable, Text> getRecordReader(
            InputSplit genericSplit, JobConf job,
            Reporter reporter)
            throws IOException {

        reporter.setStatus(genericSplit.toString());
        String delimiter = job.get("textinputformat.record.delimiter");
        byte[] recordDelimiterBytes = null;
        if (null != delimiter) {
            recordDelimiterBytes = delimiter.getBytes(Charsets.UTF_8);
        }
        return new LineRecordReader(job, (FileSplit) genericSplit,
                recordDelimiterBytes);
    }
}

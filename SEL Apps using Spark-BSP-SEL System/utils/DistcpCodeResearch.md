## 宏观分析

1. 首先，当在命令行使用distcp命令时，脚本会指向DistCp类下的main方法，main方法会进入到DistCp类的run方法中，run方法会先进行命令行的参数进行解析，并把参数封装到环境上下文context中，然后检测一下拷贝的目标路径是否存在，把结果的布尔值放进context和配置类conf中。接着，会进入到execute方法的createAndSubmitJob方法中，首先会创建一个org.apache.hadoop.fs.Path的metaFolder，实际上是后续工作的基本目录，后面会进入createJob中，这个方法是设置MR任务的基本信息，包括Mapper的输入输出类型，以及切分数据的形式（第2点），这个方法出来之后会进入prepareFileListing方法，这个方法是用于准备待复制列表的，首先会创建一个空的seq文件，然后判断是否有通配符，如果有会把所有匹配的路径都记录下来，对于这些路径，再对其进行遍历，获得源路径下所有目录和文件，把每个文件的相对路径和它的CopyListingFileStatus【这个对象是由FileStatus转换来的，记录的信息类似】以KV键值对的形式写入上述的seq文件中。准备完成之后，任务被正式提交。第2点是关于源文件如何切片，第3点是关于mapper的处理逻辑。

2. 文件是怎么切分成split传给map方法的：distcp里有两种策略可以选，分别是UniformSizeInputFormat均匀分配和DynamicInputFormat动态分配两种。UniformSizeInputFormat获得到的切片，每个切片字节数大致相同，计算的方法是通过根据用户设定的map数和拷贝总数据量，均匀封装成FileSplit，也就是切片，但并不存储数据本身，而是只记录了要处理的数据的位置和长度。当切片送到mapper之后，mapper会根据切片的内容，再去读取seq文件中对应范围的数据，得到该mapper要处理的路径和对应的FileStatus，参考mapper的签名，第一个Text就是路径，第二个CopyListingFileStatus就是路径对应的FileStatus，第三个和第四个Text是mapper的输出kv，k置null，v则是处理过程的简单一些信息（因为没有reducer）

   ```
   public class CopyMapper extends Mapper<Text, CopyListingFileStatus, Text, Text>
   ```

3. 得到要处理的路径和对应的FileStatus（一个mapper可能要处理多个，由切片内容决定）之后，后面就是具体怎么复制过去了，mapper里首先会依次判断，源文件是否存在，目标路径是否存在，源文件是否是文件夹类型，如果是，则会在目标路径也创建文件夹；接着会判断这次的动作是否是更新动作（跟命令行参数有关，如果复制到空文件夹不用管），后面开始复制时，先获取到目标路径和源路径的FileSystem对象，先进行一些校验，后面先通过目标路径的FileSystem对象包装一个输出流BufferedOutputStream，然后通过源路径的FileSystem对象包装一个输入流ThrottledInputStream（里面包装了FSDataInputStream），之后就是普通Java的IO流操作了。

   示意图：

   ![New Wireframe 1](https://typora-itsujin.oss-cn-shenzhen.aliyuncs.com/New%20Wireframe%201.png)

![img](https://typora-itsujin.oss-cn-shenzhen.aliyuncs.com/7352164-272ca3ee9410ea10.jpg)

![img](https://typora-itsujin.oss-cn-shenzhen.aliyuncs.com/7352164-acdcc44eb26f2604.jpg)

## 源码

DistCp主要有以下三个组件组成:

- DistCp Driver
- Copy-listing generator
- Input-formats 和 Map-Reduce components

#### main方法（shell入口）

```java
  /**
   * Main function of the DistCp program. Parses the input arguments (via OptionsParser),
   * and invokes the DistCp::run() method, via the ToolRunner.
   * @param argv Command-line arguments sent to DistCp.
   */
  public static void main(String argv[]) {
    int exitCode;
    try {
      DistCp distCp = new DistCp();  //构造
      Cleanup CLEANUP = new Cleanup(distCp);   //如果distcp实例未提交任务，则删除metaFolder，并令metaFolder = null。

      ShutdownHookManager.get().addShutdownHook(CLEANUP,
        SHUTDOWN_HOOK_PRIORITY);
      exitCode = ToolRunner.run(getDefaultConf(), distCp, argv);  //正式运行任务===============》
    }
    catch (Exception e) {
      LOG.error("Couldn't complete DistCp operation: ", e);
      exitCode = DistCpConstants.UNKNOWN_ERROR;
    }
    System.exit(exitCode);
  }
```

#### DistCp Driver

**构造器**

```java
  /**
   * Public Constructor. Creates DistCp object with specified input-parameters.
   * (E.g. source-paths, target-location, etc.)
   * @param configuration configuration against which the Copy-mapper must run
   * @param inputOptions Immutable options
   * @throws Exception
   */
// 构造方法，根据输入的参数创建DistCp
  public DistCp(Configuration configuration, DistCpOptions inputOptions)
      throws Exception {
    Configuration config = new Configuration(configuration);
    config.addResource(DISTCP_DEFAULT_XML);
    config.addResource(DISTCP_SITE_XML);
    setConf(config);
    if (inputOptions != null) {
      this.context = new DistCpContext(inputOptions);
    }
    this.metaFolder   = createMetaFolderPath();
  }
```



#### **run方法**

Distcp是一个Tool、ToolRunner应用，Tool应用要求实现run方法。

```java
  /**
   * 实现 Tool::run(). 组织源文件拷贝到目标位置
   *  1. 创建将要拷贝到目标的文件列表
   *  2. 运行仅包含Map的任务. (委托给 execute().)
   * @param argv List of arguments passed to DistCp, from the ToolRunner.
   * @return On success, it returns 0. Else, -1.
   */  
	@Override
  public int run(String[] argv) {
    if (argv.length < 1) {
      OptionsParser.usage();
      return DistCpConstants.INVALID_ARGUMENT; //参数不合法
    }
    
    try { 
      //OptionsParser类是distcp单独实现的参数解析工具类。
      //将输入命令行参数解析成DistCpOptions inputOptions类型。如常见的参数overwrite = false等等
      //括号里包括的parse返回的是一个 DistCpOptions 类，里面包装了参数，然后放到了context(DistCpContext)上下文中
      context = new DistCpContext(OptionsParser.parse(argv)); 
      checkSplitLargeFile();  //里面参数默认是0，对应false，进去判断就返回了，不用管
      setTargetPathExists();  //把（目标路径是否存在【就是利用FileSysten去看看】）这个boolean量放进 context 和 conf中
      LOG.info("Input Options: " + context);
    } catch (Throwable e) {
      LOG.error("Invalid arguments: ", e);
      System.err.println("Invalid arguments: " + e.getMessage());
      OptionsParser.usage();      
      return DistCpConstants.INVALID_ARGUMENT;
    }
    
    try {
      execute(); //从这里进去==========================
    } catch (InvalidInputException e) {
        .......
      //一些异常的捕获
    }
    return DistCpConstants.SUCCESS;
  }

```

#### execute();

调用`createAndSubmitJob()`创建MR任务，准备数据，设定数据输入格式，并把任务提交到hadoop集群运行，最后等待任务执行完毕。

```java
/**
 * Implements the core-execution. Creates the file-list for copy,
 * and launches the Hadoop-job, to do the copy.
 * @return Job handle
 * @throws Exception
 */
public Job execute() throws Exception {
  Preconditions.checkState(context != null,
      "The DistCpContext should have been created before running DistCp!");   //上下文没创建报错
  Job job = createAndSubmitJob();   //创建并提交任务，这里进去==========================

  if (context.shouldBlock()) {
    waitForJobCompletion(job);   //等待任务结束
  }
  return job;
}
```

#### createAndSubmitJob();

```java
  /**
   * Create and submit the mapreduce job.
   * @return The mapreduce job object that has been submitted
   */
  public Job createAndSubmitJob() throws Exception {
    assert context != null;
    assert getConf() != null;
    Job job = null;
    try {
      synchronized(this) {
        //Don't cleanup while we are setting up.
        //metaFolder：path类型
        //其存放着distcp工具需要的元数据信息，也就是所有需要拷贝的源目录/文件信息列表。
        //这些数据在一个fileList.seq文件中以Key/Value结构进行保存，
        //Key是源文件的Text格式的相对路径，
        //Value则记录源文件的FileStatus格式的org.apache.hadoop.fs.FileStatus信息，
        //这里FileStatus是hadoop已经封装好了的描述HDFS文件信息的类，
        //metafolder目录中的fileList.seq最终会作为参数传递给MR任务中的Mapper。
        metaFolder = createMetaFolderPath();
         //该任务工作目录下的FileSystem对象
        jobFS = metaFolder.getFileSystem(getConf());
        job = createJob();
      }
      prepareFileListing(job);   //创建列表
      job.submit(); //进入这里，提交任务===============
      submitted = true;
    } finally {
      if (!submitted) {
        cleanup();
      }
    }

    String jobID = job.getJobID().toString();
    job.getConfiguration().set(DistCpConstants.CONF_LABEL_DISTCP_JOB_ID,
        jobID);
    LOG.info("DistCp job-id: " + jobID);

    return job;
  }
```

##### createMetaFolderPath();

* ```java
  /**
   * Create a default working folder for the job, under the
   * job staging directory
   *
   * @return Returns the working folder information
   * @throws Exception - Exception if any
   */
  private Path createMetaFolderPath() throws Exception {
    Configuration configuration = getConf();
    //getStagingDir：创建工作的目录
    // new Cluster () :传入核心配置文件，创建集群
    Path stagingDir = JobSubmissionFiles.getStagingDir(
            new Cluster(configuration), configuration);    
    Path metaFolderPath = new Path(stagingDir, PREFIX + String.valueOf(rand.nextInt()));  // 工作目录_distcp+随机数
    if (LOG.isDebugEnabled())
      LOG.debug("Meta folder location: " + metaFolderPath);
    configuration.set(DistCpConstants.CONF_LABEL_META_FOLDER, metaFolderPath.toString());    
    return metaFolderPath;
  }
  ```

##### createJob()

* ```java
    /**
     * Create Job object for submitting it, with all the configuration
     *
     * @return Reference to job object.
     * @throws IOException - Exception if any
     */
    private Job createJob() throws IOException {
      String jobName = "distcp";
      String userChosenName = getConf().get(JobContext.JOB_NAME);
      if (userChosenName != null)
        jobName += ": " + userChosenName;
      Job job = Job.getInstance(getConf());
      job.setJobName(jobName);
      //主要是设定job中任务的分配策略，分为UniformSizeInputFormat和DynamicInputFormat两种，
      //UniformSizeInputFormat表示均衡分配任务，也就是设定的map中，每个map分配同样的任务数(默认)
      //DynamicInputFormat表示动态分为任务书，也就是动态的根据每个map运行的速度来分为具体的任务数；
      job.setInputFormatClass(DistCpUtils.getStrategy(getConf(), context));
      job.setJarByClass(CopyMapper.class);
      configureOutputFormat(job);
    
      job.setMapperClass(CopyMapper.class);  //关键类 map任务逻辑
      job.setNumReduceTasks(0);
      job.setMapOutputKeyClass(Text.class);
      job.setMapOutputValueClass(Text.class);
      job.setOutputFormatClass(CopyOutputFormat.class);
      job.getConfiguration().set(JobContext.MAP_SPECULATIVE, "false");
      job.getConfiguration().set(JobContext.NUM_MAPS,
                    String.valueOf(context.getMaxMaps()));
    
      context.appendToConf(job.getConfiguration());
      return job;  //在这里最终将创建好的job返回给execute()
    }
  ```

> UniformSizeInputFormat继承了InputFormat并实现了数据读入格式，它会读取metafolder中fileList.seq序列化文件的内容，并根据用户设定的map数和拷贝总数据量进行分片，计算出分多少片，最终提供“K-V”对供Mapper使用。

* prepareFileListing(job)

  ```java
    private void prepareFileListing(Job job) throws Exception {
      if (context.shouldUseSnapshotDiff()) {   
        //如果命令行有参数 -diff <arg>（用snapshot diff报告来标识源和目标之间的差异),这里不管，直接看else
        // When "-diff" or "-rdiff" is passed, do sync() first, then
        // create copyListing based on snapshot diff.
        DistCpSync distCpSync = new DistCpSync(context, getConf());
        if (distCpSync.sync()) {
          createInputFileListingWithDiff(job, distCpSync);
        } else {
          throw new Exception("DistCp sync failed, input options: " + context);
        }
      } else {
        // When no "-diff" or "-rdiff" is passed, create copyListing
        // in regular way.
        createInputFileListing(job);//进入==========
      }
    }
  ```

  *  createInputFileListing(job)

    ```java
      /**
       * Create input listing by invoking an appropriate copy listing
       * implementation. Also add delegation tokens for each path
       * to job's credential store
       *
       * @param job - Handle to job
       * @return Returns the path where the copy listing is created
       * @throws IOException - If any
       */
      protected Path createInputFileListing(Job job) throws IOException {
        Path fileListingPath = getFileListingPath(); //创建一个空的seq文件
        CopyListing copyListing = CopyListing.getCopyListing(job.getConfiguration(),
            job.getCredentials(), context);
        //buildListing（）方法往这个seq文件写入数据，
        // 数据写入的整体逻辑就是遍历源路径下所有目录和文件，
        // 把每个文件的相对路径和它的CopyListingFileStatus
        // 以“K-V”对的形式写入fileList.seq每行中，最终就得到Mapper的输入了。
        copyListing.buildListing(fileListingPath, context);
        return fileListingPath;
      }
    ```

    * buildListing(fileListingPath, context);

      

#### submit();

```java
  /**
   * Submit the job to the cluster and return immediately.
   * @throws IOException
   */
  public void submit() 
         throws IOException, InterruptedException, ClassNotFoundException {
    ensureState(JobState.DEFINE);
    setUseNewAPI();
    connect();
    final JobSubmitter submitter = 
        getJobSubmitter(cluster.getFileSystem(), cluster.getClient());
    status = ugi.doAs(new PrivilegedExceptionAction<JobStatus>() {
      public JobStatus run() throws IOException, InterruptedException, 
      ClassNotFoundException {
        return submitter.submitJobInternal(Job.this, cluster);
      }
    });
    state = JobState.RUNNING;
    LOG.info("The url to track the job: " + getTrackingURL());
   }
  
```

任务提交之后，重点就看mapper逻辑了

### Mapper

#### setup()

一些准备工作

```java
/**
 * Implementation of the Mapper::setup() method. This extracts the DistCp-
 * options specified in the Job's configuration, to set up the Job.
 * @param context Mapper's context.
 * @throws IOException On IO failure.
 * @throws InterruptedException If the job is interrupted.
 */
@Override
public void setup(Context context) throws IOException, InterruptedException {
  conf = context.getConfiguration();

  syncFolders = conf.getBoolean(DistCpOptionSwitch.SYNC_FOLDERS.getConfigLabel(), false);
  ignoreFailures = conf.getBoolean(DistCpOptionSwitch.IGNORE_FAILURES.getConfigLabel(), false);
  skipCrc = conf.getBoolean(DistCpOptionSwitch.SKIP_CRC.getConfigLabel(), false);
  overWrite = conf.getBoolean(DistCpOptionSwitch.OVERWRITE.getConfigLabel(), false);
  append = conf.getBoolean(DistCpOptionSwitch.APPEND.getConfigLabel(), false);
  verboseLog = conf.getBoolean(
      DistCpOptionSwitch.VERBOSE_LOG.getConfigLabel(), false);
  preserve = DistCpUtils.unpackAttributes(conf.get(DistCpOptionSwitch.
      PRESERVE_STATUS.getConfigLabel()));

  targetWorkPath = new Path(conf.get(DistCpConstants.CONF_LABEL_TARGET_WORK_PATH));
  Path targetFinalPath = new Path(conf.get(
          DistCpConstants.CONF_LABEL_TARGET_FINAL_PATH));
  targetFS = targetFinalPath.getFileSystem(conf);

  try {
    overWrite = overWrite || targetFS.getFileStatus(targetFinalPath).isFile();
  } catch (FileNotFoundException ignored) {
  }

  startEpoch = System.currentTimeMillis();
}
```

#### map()逻辑

```java
  /**
   * Implementation of the Mapper::map(). Does the copy.
   * @param relPath The target path.
   * @param sourceFileStatus The source path.
   * @throws IOException
   * @throws InterruptedException
   */
  @Override
  public void map(Text relPath, CopyListingFileStatus sourceFileStatus,
          Context context) throws IOException, InterruptedException {
    Path sourcePath = sourceFileStatus.getPath();

    if (LOG.isDebugEnabled())
      LOG.debug("DistCpMapper::map(): Received " + sourcePath + ", " + relPath);

    Path target = new Path(targetWorkPath.makeQualified(targetFS.getUri(),
                          targetFS.getWorkingDirectory()) + relPath.toString());

    EnumSet<DistCpOptions.FileAttribute> fileAttributes
            = getFileAttributeSettings(context);
    final boolean preserveRawXattrs = context.getConfiguration().getBoolean(
        DistCpConstants.CONF_LABEL_PRESERVE_RAWXATTRS, false);

    final String description = "Copying " + sourcePath + " to " + target;
    context.setStatus(description);

    LOG.info(description);

    try {
      CopyListingFileStatus sourceCurrStatus;
      FileSystem sourceFS;
      try {
        sourceFS = sourcePath.getFileSystem(conf);
        final boolean preserveXAttrs =
            fileAttributes.contains(FileAttribute.XATTR);
        sourceCurrStatus = DistCpUtils.toCopyListingFileStatusHelper(sourceFS,
            sourceFS.getFileStatus(sourcePath),
            fileAttributes.contains(FileAttribute.ACL),
            preserveXAttrs, preserveRawXattrs,
            sourceFileStatus.getChunkOffset(),
            sourceFileStatus.getChunkLength());
      } catch (FileNotFoundException e) {
        throw new IOException(new RetriableFileCopyCommand.CopyReadException(e));
      }

      FileStatus targetStatus = null;

      try {
        targetStatus = targetFS.getFileStatus(target);
      } catch (FileNotFoundException ignore) {
        if (LOG.isDebugEnabled())
          LOG.debug("Path could not be found: " + target, ignore);
      }

      if (targetStatus != null &&
          (targetStatus.isDirectory() != sourceCurrStatus.isDirectory())) {
        throw new IOException("Can't replace " + target + ". Target is " +
            getFileType(targetStatus) + ", Source is " + getFileType(sourceCurrStatus));
      }

      if (sourceCurrStatus.isDirectory()) {
        createTargetDirsWithRetry(description, target, context);
        return;
      }

      FileAction action = checkUpdate(sourceFS, sourceCurrStatus, target,
          targetStatus);

      Path tmpTarget = target;
      if (action == FileAction.SKIP) {
        LOG.info("Skipping copy of " + sourceCurrStatus.getPath()
                 + " to " + target);
        updateSkipCounters(context, sourceCurrStatus);
        context.write(null, new Text("SKIP: " + sourceCurrStatus.getPath()));

        if (verboseLog) {
          context.write(null,
              new Text("FILE_SKIPPED: source=" + sourceFileStatus.getPath()
              + ", size=" + sourceFileStatus.getLen() + " --> "
              + "target=" + target + ", size=" + (targetStatus == null ?
                  0 : targetStatus.getLen())));
        }
      } else {
        if (sourceCurrStatus.isSplit()) {
          tmpTarget = DistCpUtils.getSplitChunkPath(target, sourceCurrStatus);
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("copying " + sourceCurrStatus + " " + tmpTarget);
        }
        copyFileWithRetry(description, sourceCurrStatus, tmpTarget,
            targetStatus, context, action, fileAttributes);  //这里进入
      }
      DistCpUtils.preserve(target.getFileSystem(conf), tmpTarget,
          sourceCurrStatus, fileAttributes, preserveRawXattrs);
    } catch (IOException exception) {
      handleFailures(exception, sourceFileStatus, target, context);
    }
  }
```

##### copyFileWithRetry

拷贝动作,这个函数最底层调用的是常用的Java输入输出流的方式，以此方式来完成点对点拷贝。即copyToFile里面的copyBytes(sourceFileStatus, sourceOffset, outStream, BUFFER_SIZE, context);方法。

```java
  private void copyFileWithRetry(String description,
      CopyListingFileStatus sourceFileStatus, Path target,
      FileStatus targrtFileStatus, Context context, FileAction action,
      EnumSet<DistCpOptions.FileAttribute> fileAttributes)
      throws IOException, InterruptedException {
    long bytesCopied;
    try {
      bytesCopied = (Long) new RetriableFileCopyCommand(skipCrc, description,
          action).execute(sourceFileStatus, target, context, fileAttributes);  //真正拷贝
    } catch (Exception e) {
      context.setStatus("Copy Failure: " + sourceFileStatus.getPath());
      throw new IOException("File copy failed: " + sourceFileStatus.getPath() +
          " --> " + target, e);
    }
    incrementCounter(context, Counter.BYTESEXPECTED, sourceFileStatus.getLen());
    incrementCounter(context, Counter.BYTESCOPIED, bytesCopied);
    incrementCounter(context, Counter.COPY, 1);
    totalBytesCopied += bytesCopied;

    if (verboseLog) {
      context.write(null,
          new Text("FILE_COPIED: source=" + sourceFileStatus.getPath() + ","
          + " size=" + sourceFileStatus.getLen() + " --> "
          + "target=" + target + ", size=" + (targrtFileStatus == null ?
              0 : targrtFileStatus.getLen())));
    }
  }
```

##### RetriableFileCopyCommand类的doExecute方法

```java
  /**
   * Implementation of RetriableCommand::doExecute().
   * This is the actual copy-implementation.
   * @param arguments Argument-list to the command.
   * @return Number of bytes copied.
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  @Override
  protected Object doExecute(Object... arguments) throws Exception {
    assert arguments.length == 4 : "Unexpected argument list.";
    CopyListingFileStatus source = (CopyListingFileStatus)arguments[0];
    assert !source.isDirectory() : "Unexpected file-status. Expected file.";
    Path target = (Path)arguments[1];
    Mapper.Context context = (Mapper.Context)arguments[2];
    EnumSet<FileAttribute> fileAttributes
            = (EnumSet<FileAttribute>)arguments[3];
    return doCopy(source, target, context, fileAttributes);   //进入下面的docopy
  }

  private long doCopy(CopyListingFileStatus source, Path target,
      Mapper.Context context, EnumSet<FileAttribute> fileAttributes)
      throws IOException {
    final boolean toAppend = action == FileAction.APPEND;
    Path targetPath = toAppend ? target : getTmpFile(target, context);
    final Configuration configuration = context.getConfiguration();
    FileSystem targetFS = target.getFileSystem(configuration);

    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copying " + source.getPath() + " to " + target);
        LOG.debug("Target file path: " + targetPath);
      }
      final Path sourcePath = source.getPath();
      final FileSystem sourceFS = sourcePath.getFileSystem(configuration);
      final FileChecksum sourceChecksum = fileAttributes
          .contains(FileAttribute.CHECKSUMTYPE) ? sourceFS
          .getFileChecksum(sourcePath) : null;

      long offset = (action == FileAction.APPEND) ?
          targetFS.getFileStatus(target).getLen() : source.getChunkOffset();
      long bytesRead = copyToFile(targetPath, targetFS, source,
          offset, context, fileAttributes, sourceChecksum);   //从这里进去

      if (!source.isSplit()) {
        compareFileLengths(source, targetPath, configuration, bytesRead
            + offset);
      }
      //At this point, src&dest lengths are same. if length==0, we skip checksum
      if ((bytesRead != 0) && (!skipCrc)) {
        if (!source.isSplit()) {
          compareCheckSums(sourceFS, source.getPath(), sourceChecksum,
              targetFS, targetPath);
        }
      }
      // it's not append case, thus we first write to a temporary file, rename
      // it to the target path.
      if (!toAppend) {
        promoteTmpToTarget(targetPath, target, targetFS);
      }
      return bytesRead;
    } finally {
      // note that for append case, it is possible that we append partial data
      // and then fail. In that case, for the next retry, we either reuse the
      // partial appended data if it is good or we overwrite the whole file
      if (!toAppend) {
        targetFS.delete(targetPath, false);
      }
    }
  }
```

##### copyToFile()

```java
 private long copyToFile(Path targetPath, FileSystem targetFS,
      CopyListingFileStatus source, long sourceOffset, Mapper.Context context,
      EnumSet<FileAttribute> fileAttributes, final FileChecksum sourceChecksum)
      throws IOException {
    FsPermission permission = FsPermission.getFileDefault().applyUMask(
        FsPermission.getUMask(targetFS.getConf()));
    int copyBufferSize = context.getConfiguration().getInt(
        DistCpOptionSwitch.COPY_BUFFER_SIZE.getConfigLabel(),
        DistCpConstants.COPY_BUFFER_SIZE_DEFAULT);
    final OutputStream outStream;
    if (action == FileAction.OVERWRITE) {
      // If there is an erasure coding policy set on the target directory,
      // files will be written to the target directory using the same EC policy.
      // The replication factor of the source file is ignored and not preserved.
      final short repl = getReplicationFactor(fileAttributes, source,
          targetFS, targetPath);
      final long blockSize = getBlockSize(fileAttributes, source,
          targetFS, targetPath);
      FSDataOutputStream out = targetFS.create(targetPath, permission,
          EnumSet.of(CreateFlag.CREATE, CreateFlag.OVERWRITE),
          copyBufferSize, repl, blockSize, context,
          getChecksumOpt(fileAttributes, sourceChecksum));
      outStream = new BufferedOutputStream(out);
    } else {
      outStream = new BufferedOutputStream(targetFS.append(targetPath,
          copyBufferSize));
    }
    return copyBytes(source, sourceOffset, outStream, copyBufferSize,
        context);   //这里进去
  }
```

##### copyBytes()

```java
  @VisibleForTesting
  long copyBytes(CopyListingFileStatus source2, long sourceOffset,
      OutputStream outStream, int bufferSize, Mapper.Context context)
      throws IOException {
    Path source = source2.getPath();
    byte buf[] = new byte[bufferSize];
    ThrottledInputStream inStream = null;
    long totalBytesRead = 0;

    long chunkLength = source2.getChunkLength();
    boolean finished = false;
    try {
      inStream = getInputStream(source, context.getConfiguration());
      int bytesRead = readBytes(inStream, buf, sourceOffset);
      while (bytesRead >= 0) {
        if (chunkLength > 0 &&
            (totalBytesRead + bytesRead) >= chunkLength) {
          bytesRead = (int)(chunkLength - totalBytesRead);
          finished = true;
        }
        totalBytesRead += bytesRead;
        if (action == FileAction.APPEND) {
          sourceOffset += bytesRead;
        }
        outStream.write(buf, 0, bytesRead);
        updateContextStatus(totalBytesRead, context, source2);
        if (finished) {
          break;
        }
        bytesRead = readBytes(inStream, buf, sourceOffset);
      }
      outStream.close();
      outStream = null;
    } finally {
      IOUtils.cleanup(LOG, outStream, inStream);
    }
    return totalBytesRead;
  }
```


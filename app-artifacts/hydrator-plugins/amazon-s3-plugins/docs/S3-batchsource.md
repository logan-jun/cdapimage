# Amazon S3 Batch Source


Description
-----------
This source is used whenever you need to read from Amazon S3.
For example, you may want to read in log files from S3 every hour and then store
the logs in a TimePartitionedFileSet.

Properties
----------
**Reference Name:** Name used to uniquely identify this source for lineage, annotating metadata, etc.

**Path:** Path to read from. For example, s3a://<bucket>/path/to/input

**Format:** Format of the data to read.
The format must be one of 'avro', 'blob', 'csv', 'delimited', 'json', 'parquet', 'text', or 'tsv'.
If the format is 'blob', every input file will be read into a separate record.
The 'blob' format also requires a schema that contains a field named 'body' of type 'bytes'.
If the format is 'text', the schema must contain a field named 'body' of type 'string'.

**Delimiter:** Delimiter to use when the format is 'delimited'. This will be ignored for other formats.

**Authentication Method:** Authentication method to access S3. The default value is Access Credentials.
IAM can only be used if the plugin is run in an AWS environment, such as on EMR.

**Access ID:** Amazon access ID required for authentication.

**Access Key:** Amazon access key required for authentication.

**Maximum Split Size:** Maximum size in bytes for each input partition.
Smaller partitions will increase the level of parallelism, but will require more resources and overhead.
The default value is 128MB.

**Path Field:** Output field to place the path of the file that the record was read from.
If not specified, the file path will not be included in output records.
If specified, the field must exist in the output schema as a string.

**Path Filename Only:** Whether to only use the filename instead of the URI of the file path when a path field is given.
The default value is false.

**Read Files Recursively:** Whether files are to be read recursively from the path. The default value is false.

**Allow Empty Input:** Whether to allow an input path that contains no data. When set to false, the plugin
will error when there is no data to read. When set to true, no error will be thrown and zero records will be read.

**File System Properties:** Additional properties to use with the InputFormat when reading the data.

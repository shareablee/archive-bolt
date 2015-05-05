# archive-bolt

Reusable Storm bolt for archiving data to file. Currently supports storing to s3. 

[![Build Status](https://magnum.travis-ci.com/shareablee/archive-bolt.svg?token=NU2eMZobEmxbYse4grEj&branch=master)](https://magnum.travis-ci.com/shareablee/archive-bolt)

## Change log

- v0.1.8
  - Writing objects to S3 no longer uses a temporary file.
- v0.1.7
  - Bumped storm version to 0.9.3.
- v0.1.6
  - `archive` bolt now takes a `"meta"` field to the input tuple which will be passed through to the output tuple
  - Tests now require environment variables. See the "Running Tests" section.
  - The S3 backend for archive-read now accepts extra options when calling list-objects.
- v0.1.5
  - Added `archive-read-filtered` bolt which must be initialized with a predicate function for filtering search results. The predicate function must be quoted with a backtick.
  - Use `log-debug` instead of `log-warn` if no results are found during archive read

## Usage

### `archive`

Takes a tuple of `["meta" "backend", "location", "content"]` where backend is where the content will be stored, location is the path the file should be saved to, and content is a string to be written to file (json).

Emits a bolt of `["meta" "result"]`.

#### Behavior

- Attempts to safely retry storing to the specified backend so the caller does not have to
- Emits a boolean if it was successful
- `acks!` the tuple if successful, `fail!` the tuple if unsuccessful

### `archive-read`

Takes a tuple of `["meta", "backend", "location"]` where backend is a string of the backend the content is stored in and location is the path the file should be read from.

For s3, if there are more than 1,000 results, it will automatically paginate to yield all results. 

The implementation of the S3 backend calls list-objects. By passing a map into the `meta` field of the tuple with the key `:archive-bolt.read.s3/list-objects-opts`, you can override the options except for `:bucket` and `:prefix`. A common option to set is `:delimiter`.

#### Behavior

- Emits a tuple of `["meta", "results"]`
- Results is a collection representing the value of all keys at the given location and meta data. Each item in the collection has keys `:meta` and `value` where `:meta` has `:location`, `:full-path`, and `:file-name` and `:value` is the string from the document corresponding to the location.
  
- `acks!` the tuple whether there were results or not
- Only emits if there are results returned

## Deployment

The library used to interact with s3, `amazonica`, uses a java library that requires `org.apache.httpcomponents/httpclient` version 4.2.5+. Storm 0.9.0.1 (this is updated in later versions)distribution package ships with an out of date version of this library and thus must be replaced on all servers in the cluster.

### Replacing Apache httpclient

From the server running a storm distribution:

```
# Go to where the jars live
cd storm-0.9.0.1/lib
# Get an updated version of httpclient from maven
wget http://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.2.5/httpclient-4.2.5.jar
# Backup the old package
cp httpclient-4.1.1.jar httpclient-4.1.1.jar.bak
# Delete it
rm httpclient-4.1.1.jar

```

Make sure to restart any storm running processes so the new classpath takes effect.

## Running Tests

Integration tests rely on environment variables being available for writing/reading from S3.

```
AWS_ACCESS_KEY_ID=<aws id> AWS_SECRET_ACCESS_KEY=<aws secret key> AWS_S3_REGION=<s3 region> S3_BUCKET=<bucket name> lein test
```

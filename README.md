# archive-bolt

Reusable Storm bolt for archiving data to file. Currently supports storing to s3. 

## Usage

Takes a tuple of `["backend", "location", "content"]` where backend is where the content will be stored, location is the path the file should be saved to, and content is a string to be written to file (json).

To use in another project, include the compiled jar generated by `lein uberjar` in the storm classpath.

### Behavior

- Attempts to safely retry storing to the specified backend so the caller does not have to
- Emits a boolean if it was successful
- `acks!` the tuple if successful, `fail!` the tuple if unsuccessful

## Deployment

The library used to interact with s3, `amazonica`, uses a java library that requires `org.apache.httpcomponents/httpclient` version 4.2.5+. Storm distribution package ships with an out of date version of this library and thus must be replaced on all servers in the cluster.

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

## License

Copyright © 2014 Shareablee

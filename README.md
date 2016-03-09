# Tika Page Extractor

This application set up a server that extracts content from PDFs and returns it as
JSON string, one entry per page.

-> [Download zipped Binary](https://www.auxnet.de/wp-content/uploads/2016/03/TikaPageExtractor.zip)

Start server using:

    java -jar TikaPageExtractor.jar

Adding `-h` to the line above will print command line options you can set, e.g.
the server's port and ip address.

By default, the server will listen to port 9090.

You can PUT pdf file contents to the server, similar to Solr. Using curl you can do
the following, for example:

    curl -X PUT -T file.pdf http://localhost:9090/

This will upload file.pdf to the server and return its content as JSON. The server will
return an array of strings, one entry per page.

The server was inspired by http://vteams.com/blog/apache-tika-per-page-content-extraction/. Thanks!

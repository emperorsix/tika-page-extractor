package de.auxnet;

/*
 * (C) Copyright 2016 Dr. Maximilian Kalus (https://auxnet.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Dr. Maximilian Kalus
 */

import com.google.gson.Gson;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

/**
 * Page Extractor main class - uses Spark to do most of the magic
 * <p>
 * Usage (CURL):
 * <p>
 * curl -X PUT -T file.pdf http://localhost:9090/
 */
public class TikaPageExtractor {
	/**
	 * logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(TikaPageExtractor.class);

	@Option(name = "-i", usage = "Hostname or ip address", aliases = "--host")
	private String ipAddress = null;

	@Option(name = "-p", usage = "Port to listen on", aliases = "--ip")
	private int port = 9090;

	@Option(name = "-c", usage = "Compress extracted text, removing extra whitespace and triming output", aliases = "--compress", handler = ExplicitBooleanOptionHandler.class)
	private boolean compressText = true;

	@Option(name = "-M", usage = "Extract and return raw meta data in JSON", aliases = "--raw-metadata", handler = ExplicitBooleanOptionHandler.class)
	private boolean returnRawMetaData = false;

	@Option(name = "-m", usage = "Extract, interpret and return meta data in JSON (unify meta data names somewhat to make parsing easier)", aliases = "--metadata", handler = ExplicitBooleanOptionHandler.class)
	private boolean returnMetaData = true;

	@Option(name = "-f", usage = "Return full text, too (only applies to PDFs).", aliases = "--fulltext", handler = ExplicitBooleanOptionHandler.class)
	private boolean fullText = true;

	@Option(name = "-l", usage = "Guess language if none detected in metadata (requires -m to be set).", depends = "--metadata", aliases = "--detect-language", handler = ExplicitBooleanOptionHandler.class)
	private boolean detectLanguage = true;

	@Option(name="-h", usage = "Print this help")
	private boolean help;

	/**
	 * Start server
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		TikaPageExtractor pageExtractor = new TikaPageExtractor();

		// parse arguments
		try {
			if (!pageExtractor.parseCommandLineArguments(args))
				System.exit(0); // some help displayed - do not start server
		} catch (IOException e) {
			System.err.println("Server threw IO exception: " + e.getMessage());
			System.exit(1); // non-0 exit code
		}

		// start server
		pageExtractor.startServer();
	}

	/**
	 * Parse arguments
	 * @param args command line arguments
	 * @throws IOException
	 */
	public boolean parseCommandLineArguments(String[] args) throws IOException {
		CmdLineParser parser = new CmdLineParser(this);

		try {
			parser.parseArgument(args);

			// just print help and exit
			if (this.help) {
				printHelp(System.out, parser);
				return false;
			}
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			printHelp(System.err, parser);

			return false;
		}

		return true;
	}

	/**
	 * helper to print usage information
	 * @param stream to print to
	 * @param parser used
	 */
	protected static void printHelp(PrintStream stream, CmdLineParser parser) {
		stream.println("java -jar TikaPageExtractor.jar [options...] arguments...");

		parser.printUsage(stream);
		stream.println();
	}

	/**
	 * start server
	 */
	public void startServer() {
		// set IP address if set
		if (ipAddress != null && !ipAddress.isEmpty()) {
			ipAddress(ipAddress);
			if (logger.isInfoEnabled())
				logger.info("Server address set to " + ipAddress);
		}
		if (port > 0) {
			port(port);
			if (logger.isInfoEnabled())
				logger.info("Server port set to " + port);
		}
		else port(9090);

		// initialize converter - should be thread save according to developers (and we do not use DateTime or the like)
		Gson gson = new Gson();

		// generic message at get
		get("/", (req, res) -> "This is a TikaPageExtractor instance - please put PDF files.");

		// put pdf file
		put("/", (request, response) -> "Please put a file - add file name as path parameter like \"http://localhost:9090/myfile.pdf\".");

		// put pdf file
		put("/:file", (request, response) -> {
			if (logger.isInfoEnabled())
				logger.info("Uploaded file: " + request.params(":file"));

			// create map that will include all the data
			Map<String, Object> content = new HashMap<>();
			content.put("filename", request.params(":file"));

			long startTime = System.currentTimeMillis();

			// get request bytes - cast to Tika Input Stream
			TikaInputStream is = TikaInputStream.get(request.bodyAsBytes());

			// detect mime type of file
			TikaConfig tika = new TikaConfig();
			MediaType mediaType = tika.getDetector().detect(is, new Metadata());
			content.put("mimetype", mediaType.toString());
			// rewind is
			is.reset();

			// get full text
			try {
				AutoDetectParser parser = new AutoDetectParser();
				Metadata metadata = new Metadata();
				ContentHandler handler = new ToTextContentHandler();

				parser.parse(is, handler, metadata);

				// if pdf, you have to set fullText to true in order to get it
				if (!mediaType.getSubtype().equals("pdf") || fullText) {
					// compact whitespace and trim string
					String contentText = handler.toString();
					if (compressText)
						contentText = contentText.replaceAll("\\s+", " ").trim();

					content.put("content", contentText);
				}

				// get raw meta data?
				if (returnRawMetaData) {
					content.put("rawmeta", metadata);
				}

				// get parsed meta data?
				if (returnMetaData) {
					content.put("meta", parseMetaData(metadata, handler));
				}
			} catch (Exception e) {
				logger.error("Exception thrown while parsing plain text: ", e);
				return "";
			}

			if (mediaType.getSubtype().equals("pdf")) {
				// rewind is
				is.reset();

				// create parser and handler
				Parser parser = new AutoDetectParser();
				PageContentHandler contentHandler = new PageContentHandler(compressText);

				parser.parse(is, contentHandler, new Metadata(), new ParseContext());

				content.put("pages", contentHandler.getPages());
			}

			// set response type
			response.type("application/json");

			long stopTime = System.currentTimeMillis();
			long elapsedTime = stopTime - startTime;
			if (logger.isInfoEnabled())
				logger.info("Elapsed time: " + elapsedTime + "ms");

			// return content
			return content;
		}, gson::toJson);
	}

	/**
	 * identify language of text
	 * @param text to check
	 * @return language detected
	 */
	protected String identifyLanguage(String text) {
		LanguageIdentifier identifier = new LanguageIdentifier(text);
		return identifier.getLanguage();
	}

	/**
	 * try to cleverly parse meta data to unified schema
	 * @param metadata extracted meta data
	 * @param handler content handler for full text
	 * @return map containing meta data
	 */
	protected Map<String, Object> parseMetaData(Metadata metadata, ContentHandler handler) {
		Map<String, Object> data = new HashMap<>();
		String value;

		// check title
		value = metadata.get("dc:title");
		if (value == null || value.isEmpty()) value = metadata.get("title");
		if (value != null && !value.isEmpty())
			data.put("title", value);

		// description
		value = metadata.get("description");
		if (value == null || value.isEmpty()) value = metadata.get("dc:subject");
		if (value == null || value.isEmpty()) value = metadata.get("subject");
		if (value != null && !value.isEmpty())
			data.put("description", value);

		// version
		value = metadata.get("editing-cycles");
		if (value == null || value.isEmpty()) value = metadata.get("pdf:PDFVersion");
		if (value == null || value.isEmpty()) value = metadata.get("Revision-Number");
		if (value == null || value.isEmpty()) value = metadata.get("cp:revision");
		if (value != null && !value.isEmpty())
			try {
				data.put("version", Integer.parseInt(value));
			} catch (NumberFormatException e) {
				data.put("version", value);
			}

		// page and word counts
		value = metadata.get("meta:page-count");
		if (value == null || value.isEmpty()) value = metadata.get("xmpTPg:NPages");
		if (value != null && !value.isEmpty())
			try {
				data.put("page_count", Integer.parseInt(value));
			} catch (NumberFormatException e) {
				data.put("page_count", value);
			}

		value = metadata.get("meta:word-count");
		if (value != null && !value.isEmpty())
			try {
				data.put("word_count", Integer.parseInt(value));
			} catch (NumberFormatException e) {
				data.put("word_count", value);
			}

		// creator/author
		value = metadata.get("meta:author");
		if (value == null || value.isEmpty()) value = metadata.get("creator");
		if (value != null && !value.isEmpty())
			data.put("creator", value);

		// width/height
		value = metadata.get("tiff:ImageLength");
		if (value != null && !value.isEmpty())
			try {
				data.put("height", Integer.parseInt(value));
			} catch (NumberFormatException e) {
				data.put("height", value);
			}
		value = metadata.get("tiff:ImageWidth");
		if (value != null && !value.isEmpty())
			try {
				data.put("width", Integer.parseInt(value));
			} catch (NumberFormatException e) {
				data.put("width", value);
			}

		// get language
		value = metadata.get("language");
		if (value != null && !value.isEmpty())
			data.put("language", value);
		else if (detectLanguage) {
			// try to detect language through content
			String contentText = handler.toString().replaceAll("\\s+", " ").trim();
			if (!contentText.isEmpty()) {
				if (logger.isDebugEnabled())
					logger.debug("Guessing language of document...");
				data.put("language", identifyLanguage(contentText));
			}
		}

		// get key words
		value = metadata.get("Keywords");
		if (value != null && !value.isEmpty())
			data.put("keywords", value);

		//TODO longitude, latitude

		// get created/modified
		value = metadata.get("meta:creation-date");
		if (value != null && !value.isEmpty())
			data.put("document_created", value.substring(0, 19));
		value = metadata.get("Last-Modified");
		if (value != null && !value.isEmpty())
			data.put("document_changed", value.substring(0, 19));

		return data;
	}
}

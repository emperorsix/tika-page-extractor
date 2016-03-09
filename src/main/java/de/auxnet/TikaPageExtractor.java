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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;

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
		put("/", (request, response) -> "Please put a file.");

		// put pdf file
		put("/:file", (request, response) -> {
			if (logger.isInfoEnabled())
				logger.info("Uploaded file: " + request.params(":file"));

			// get request bytes
			ByteArrayInputStream is = new ByteArrayInputStream(request.bodyAsBytes());

			// create parser and handler
			Parser parser = new AutoDetectParser();
			PageContentHandler contentHandler = new PageContentHandler(true);

			long startTime = System.currentTimeMillis();
			parser.parse(is, contentHandler, new Metadata(), new ParseContext());
			long stopTime = System.currentTimeMillis();
			long elapsedTime = stopTime - startTime;
			if (logger.isInfoEnabled())
				logger.info("Elapsed time: " + elapsedTime + "ms");

			// set response type
			response.type("application/json");

			// return page list
			return contentHandler.getPages();
		}, gson::toJson);
	}
}

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

import org.apache.tika.sax.ToTextContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Page Content Handler to parse Tika extracted content into a list containing pages
 * <p>
 * Inspired by http://vteams.com/blog/apache-tika-per-page-content-extraction/
 * Thanks!
 */
public class PageContentHandler extends ToTextContentHandler {
	final static private String pageTag = "div";
	final static private String pageClass = "page";

	/**
	 * StringBuilder of current page
	 */
	private StringBuilder builder;

	/**
	 * page counter
	 */
	private int pageNumber = 0;

	/**
	 * page map - setting the initial capacity to 500 will enhance speed by a tiny bit up to 500 bits, but will require
	 * more RAM
	 */
	private Map<Integer, String> pages = new HashMap<>(500);

	/**
	 * flag telling to compress text information by stripping whitespace?
	 */
	private final boolean compress;

	/**
	 * Default constructor
	 */
	public PageContentHandler() {
		this.compress = true;
	}

	/**
	 * Constructor
	 *
	 * @param compress text information by stripping whitespace?
	 */
	public PageContentHandler(boolean compress) {
		this.compress = compress;
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		if (pageTag.endsWith(qName) && pageClass.equals(atts.getValue("class")))
			startPage();
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (pageTag.endsWith(qName))
			endPage();
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		// append data
		if (length > 0 && builder != null) {
			builder.append(ch);
		}
	}

	protected void startPage() throws SAXException {
		builder = new StringBuilder();
		pageNumber++;
		System.out.println(pageNumber);
	}

	protected void endPage() throws SAXException {
		String page = builder.toString();

		// if compression has been turned on, compact whitespace and trim string
		if (compress)
			page = page.replaceAll("\\s+", " ").trim();

		// page number already exists?
		if (pages.containsKey(pageNumber)) {
			page = pages.get(pageNumber) + " " + page; // concatenate pages
			page = page.trim();
		}

		// add to page list
		pages.put(pageNumber, page);
		builder = new StringBuilder();
	}

	/**
	 * @return all extracted pages
	 */
	public List<String> getPages() {
		List<String> pagesReal = new ArrayList<>(pageNumber);

		// convert to list
		for (int i = 1; i <= pageNumber; i++) {
			String page = pages.get(i);
			if (page == null) page = "";

			pagesReal.add(page);
		}

		return pagesReal;
	}
}
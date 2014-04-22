package com.satimetry.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;

//import org.junit.Test;

import com.Ostermiller.util.CSVParser;
import com.satimetry.nudge.DroolsService;

public class DroolsServiceTest extends TestCase {

	private static String INPUT_FILE_NAME = "/Users/stefanopicozzi/TheNudgeMachine/kepler/workspace/Rdrools6/data/input.csv";
	private static String RULES_FILE_NAME = "/Users/stefanopicozzi/TheNudgeMachine/kepler/workspace/Rdrools6/data/rules.drl";

	private static String[][] RULES_OUTPUT = new String[][] { { "address", "subject", "body" },
		{ "johhny@school.edu", "Your grade in Math", "You got a 85 in Math, Johnny" },
		{ "johnny@school.edu", "Your grade in English", "You got a 45 in English, Johnny" },
		{ "johnny@school.edu", "Your grade in Science", "You got a 72 in Science, Johnny" },
		{ "johnny@school.edu", "Your grade in History", "You got a 91 in History, Johnny" },
		{ "johnny@school.edu", "Your grade in Spanish", "You got a 77 in Spanish, Johnny" },
		{ "james@school.edu", "Your grade in Math", "You got a 99 in Math, James" },
		{ "james@school.edu", "Your grade in English", "You got a 87 in English, James" },
		{ "james@school.edu", "Your grade in Science", "You got a 73 in Science, James" },
		{ "james@school.edu", "Your grade in History", "You got a 80 in History, James" },
		{ "james@school.edu", "Your grade in Spanish", "You got a 90 in Spanish, James" },
		{ "joseph@school.edu", "Your grade in Math", "You got a 25 in Math, Joseph" },
		{ "joseph@school.edu", "Your grade in English", "You got a 47 in English, Joseph" },
		{ "joseph@school.edu", "Your grade in Science", "You got a 33 in Science, Joseph" },
		{ "joseph@school.edu", "Your grade in History", "You got a 62 in History, Joseph" },
		{ "joseph@school.edu", "Your grade in Spanish", "You got a 54 in Spanish, Joseph" } };

	private DroolsService service;

	public DroolsServiceTest() {
	}

	public static final void main(String[] args) {
		try {

			if(args.length < 2) {
				System.out.println("Proper Usage is: java program RULES_FILE_NAME INPUT_FILE_NAME");
				System.exit(0);
			}

			if (args.length == 2) {
				RULES_FILE_NAME = args[0];
				INPUT_FILE_NAME = args[1];
			} else { // arg[0] becomes program name
				RULES_FILE_NAME = args[1];
				INPUT_FILE_NAME = args[2];	    		 		 
			}

			System.out.println("Classpath->" + System.getProperty("java.class.path"));
			System.out.println("Java->" + System.getProperty("java.version"));

			DroolsServiceTest dst = new DroolsServiceTest();

			dst.testDataSetRules();

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	//@Test
	public void testDataSetRules() {
		runRules(INPUT_FILE_NAME, RULES_FILE_NAME, "name, class, grade, email", "address, subject, body",
				RULES_OUTPUT);
	}

	public void runRules(String csvFile, String rulesFile, String expectedInputColumnsCSV,
			String outputColumnsCSV, String[][] expectedValues) {
		try {

			String input = readFileAsString(new FileInputStream(csvFile));
			String rules = readFileAsString(new FileInputStream(rulesFile));  

			service = new DroolsService(rules, expectedInputColumnsCSV, outputColumnsCSV);
			String outputCSV = service.execute(input);
			System.out.println(outputCSV);
			CSVParser parser = new CSVParser(new ByteArrayInputStream(outputCSV.getBytes()));
			String[] columns = parser.getLine();
			checkValues(expectedValues[0], columns);

			String[] values = null;
			int index = 0;
			while ((values = parser.getLine()) != null) {
				index += 1;
				//
				assertTrue("Too many resulting rows: reading line " + index, index < expectedValues.length);
				checkValues(expectedValues[index], values);
			}
			assertEquals("Too few rows", expectedValues.length - 1, index);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public String readFileAsString(InputStream inputStream) throws java.io.IOException {

		InputStreamReader inRead = new InputStreamReader(inputStream);
		StringBuffer fileData = new StringBuffer(1000);
		BufferedReader reader = new BufferedReader(inRead);
		char[] buf = new char[1024];
		int numRead = 0;
		while ((numRead = reader.read(buf)) != -1) {
			fileData.append(buf, 0, numRead);
		}
		reader.close();
		return fileData.toString();
	}

	public void checkValues(String[] expected, String[] actual) {
		if (expected == null) {
			assertNull(actual);
		} else {
			assertNotNull(actual);
			assertEquals(expected.length, actual.length);
			for (int index = 0; index < expected.length; index++) {
				assertEquals("Column at index " + index, expected[index], actual[index]);
			}
		}
	}
}

/**
 * The drools service
 */
package com.satimetry.nudge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.*;
import org.apache.commons.lang.NullArgumentException;
//import org.drools.core.time.SessionPseudoClock;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message.Level;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.event.rule.DebugAgendaEventListener;
import org.kie.api.event.rule.DebugRuleRuntimeEventListener;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.rule.*;
import org.kie.api.time.SessionClock;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.api.runtime.rule.EntryPoint;
// import org.kie.api.runtime.rule.SessionEntryPoint;
import org.drools.core.time.impl.*;

import com.Ostermiller.util.CSVParser;
import com.Ostermiller.util.CSVPrinter;

/**
 * @author Sms Chauhan A lot in this class has been taken from the Drools node present in the
 *         nexusBPM project. The class mostly relies on the contribution of
 *         the project members.
 */
public class DroolsService {

	private String[] inputColumns = null;

	private List<String> expectedInputColumns = null;

	private List<String> outputColumns = null;

	private String MODE = "STATELESS";
	private Integer MODE_INT = 0;

	private StatelessKieSession slSession = null;
	private KieSession sfSession = null;

	private EntryPoint entryPoint;
	
	public DroolsService(String mode, String rules, String expectedInputColumnsCSV, String outputColumnsCSV)
			throws Exception {
		this.expectedInputColumns = new ArrayList<String>();
		this.outputColumns = new ArrayList<String>();
		// validate all inputs
		validateInputs(rules, expectedInputColumnsCSV, expectedInputColumns, outputColumnsCSV,
				outputColumns);
		
		this.MODE = mode;
//		System.out.println("MODE->" + this.MODE);
		if (this.MODE.equals("STATELESS")) { this.MODE_INT = 0; }
		if (this.MODE.equals("STATEFUL")) { this.MODE_INT = 1; }
		if (this.MODE.equals("STREAM")) { this.MODE_INT = 2; }
		if (this.MODE.equals("CLOUD")) { this.MODE_INT = 3; }
//		System.out.println("MODE_INT->" + this.MODE_INT);
		// Switch on String requires Java7
		switch (this.MODE_INT) {
			case 0 :
				this.slSession = createStatelessSession(rules);
				break;
			case 1 :
				this.sfSession = createStatefulSession(rules);
				break;				
			case 2 :
				this.sfSession = createStreamSession(rules);
				break;
			case 3 :
				this.sfSession = createCloudSession(rules);
				break;
		}	
		
	}

	public String execute(String inputCSV) throws Exception {
		String outputCSV = null;
		ByteArrayOutputStream csvOutputStream = new ByteArrayOutputStream();
		// verify input CSV
		validateInputCSV(inputCSV);
		// read the inputs columns from the input CSV string
		InputStream inputCSVStream = new ByteArrayInputStream(inputCSV.getBytes());
		CSVParser parser = new CSVParser(inputCSVStream);
		this.inputColumns = parser.getLine();
		// verify input columns
		verifyInputColumns(this.expectedInputColumns, this.inputColumns);
		// open an output stream for the output CSV string and print the column names
		CSVPrinter printer = new CSVPrinter(csvOutputStream);
		printer.writeln(this.outputColumns.toArray(new String[this.outputColumns.size()]));
		// get a drools session from the rules file

		// run the rules and write the results to the output CSV
		switch (MODE_INT) {
			case 0 :
				runStatelessRules(this.inputColumns, this.outputColumns, parser, printer, this.slSession);
				break;
			case 1 :
				runStatefulRules(this.inputColumns, this.outputColumns, parser, printer, this.sfSession);
				break;				
			case 2 :
				runStreamRules(this.inputColumns, this.outputColumns, parser, printer, this.sfSession);
				break;
			case 3 :
				runStreamRules(this.inputColumns, this.outputColumns, parser, printer, this.sfSession);
				break;
		}	
		

		// convert the ByteArrayOutputStream to a string
		outputCSV = new String(csvOutputStream.toByteArray(), "UTF-8");
		return outputCSV;

	}

	protected void validateInputCSV(String inputCSV) throws Exception {
		if (inputCSV == null || inputCSV == "") {
			throw new NullArgumentException("The input dataset is emtpy!");
		}
	}

	protected void validateInputs(String rules, String expectedInputColumnsCSV,
			List<String> expectedInputColumns, String outputColumnsCSV, List<String> outputColumns)
					throws Exception

					{
		if (rules == null || rules == "") {
			throw new NullArgumentException("Empty rules file!");
		}
		StringTokenizer tokenizer = null;
		if (expectedInputColumnsCSV != null && expectedInputColumnsCSV != "") {

			tokenizer = new StringTokenizer(expectedInputColumnsCSV.replace(" ", ""), ",");

			while (tokenizer.hasMoreTokens()) {
				expectedInputColumns.add(tokenizer.nextToken());
			}
		} else {
			throw new NullArgumentException("No input columns found!");
		}
		if (expectedInputColumnsCSV != null && expectedInputColumnsCSV != "") {
			tokenizer = new StringTokenizer(outputColumnsCSV.replace(" ", ""), ",");

			while (tokenizer.hasMoreTokens()) {
				outputColumns.add(tokenizer.nextToken());
			}
		} else {
			throw new NullArgumentException("No output columns found!");
		}
					}

	protected void verifyInputColumns(List<String> expectedInputColumns, String[] inputColumns)
			throws Exception {
		for (String column : expectedInputColumns) {
			boolean found = false;
			for (int index = 0; index < inputColumns.length; index++) {
				if (inputColumns[index].equals(column)) {
					found = true;
					break;
				}
			}
			if (!found) {
				throw new IllegalArgumentException(
						"Error: The input CSV file does not contain the column '" + column
						+ "' which is required by the rules file!");
			}
		}
	}

	protected StatelessKieSession createStatelessSession(String rules) throws Exception {

		KieServices ks = KieServices.Factory.get();
		KieRepository kr = ks.getRepository();
		KieFileSystem kfs = ks.newKieFileSystem();

		kfs.write("src/main/resources/rules/Rules.drl", rules);
		KieBuilder kb = ks.newKieBuilder(kfs);

		kb.buildAll(); 
		// kieModule is automatically deployed to KieRepository if successfully built.

		if (kb.getResults().hasMessages(Level.ERROR)) {
			throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
		}

		KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
		StatelessKieSession slSession = kContainer.newStatelessKieSession();

//		slSession.addEventListener( new DebugAgendaEventListener() );
//		slSession.addEventListener( new DebugRuleRuntimeEventListener() );

		return slSession;
	}

	protected void runStatelessRules(String[] inputColumns, List<String> outputColumns, CSVParser parser,
			CSVPrinter printer, StatelessKieSession slSession) throws Exception {
		String[] outputRow = new String[outputColumns.size()];
		String[] inputRow;

		Map<String, String> inputMap = new HashMap<String, String>();
		Map<String, String> outputMap = new HashMap<String, String>();

		slSession.setGlobal("output", outputMap);

		while ((inputRow = parser.getLine()) != null) {
			// write the values for the input row
			for (int index = 0; index < inputColumns.length; index++) {
				inputMap.put(inputColumns[index], inputRow[index]);
			}
			if (outputMap.size() > 0) {
				outputMap.clear();
			}
			
			slSession.execute(inputMap);

			if (outputMap.size() > 0) {
				for (int index = 0; index < outputColumns.size(); index++) {
					outputRow[index] = outputMap.get(outputColumns.get(index));
				}
				printer.writeln(outputRow);
			}
		}
	}

	protected KieSession createStreamSession(String rules) throws Exception {

		KieServices ks = KieServices.Factory.get();
		KieRepository kr = ks.getRepository();
		KieFileSystem kfs = ks.newKieFileSystem();

		kfs.write("src/main/resources/rules/Rules.drl", rules);
		KieBuilder kb = ks.newKieBuilder(kfs);
		kb.buildAll(); 
		// kieModule is automatically deployed to KieRepository if successfully built.

		if (kb.getResults().hasMessages(Level.ERROR)) {
			throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
		}
    	
    	KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
		
    	KieBaseConfiguration kBaseConf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
    	kBaseConf.setOption( EventProcessingOption.STREAM );
    	KieBase kbase = kContainer.newKieBase(kBaseConf);

    	KieSessionConfiguration ksessionConfig = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();    	
    	ksessionConfig.setOption( ClockTypeOption.get("realtime") );
    	sfSession = kbase.newKieSession(ksessionConfig, null);
    	
        this.entryPoint = (EntryPoint) sfSession.getEntryPoint( "DEFAULT" );
//        System.out.println("EntryPoint-->" + sfSession.getEntryPointId());

//		sfSession.addEventListener( new DebugAgendaEventListener() );
//		sfSession.addEventListener( new DebugRuleRuntimeEventListener() );

		return sfSession;
	}

	protected void runStreamRules(String[] inputColumns, List<String> outputColumns, CSVParser parser,
			CSVPrinter printer, KieSession sfSession) throws Exception {
		String[] outputRow = new String[outputColumns.size()];
		String[] inputRow;

		Map<String, String> inputMap = new HashMap<String, String>();
		Map<String, String> outputMap = new HashMap<String, String>();
		java.util.List<JSONObject> outputJSONList = new ArrayList<JSONObject>();
        SessionClock clock = sfSession.getSessionClock();
        
//        System.out.println("clockc-->" + clock.toString()  );
//        System.out.println("clocks-->" + sfSession.getSessionClock()  );
        
		while ((inputRow = parser.getLine()) != null) {
			// write the values for the input row
			for (int index = 0; index < inputColumns.length; index++) {
				inputMap.put(inputColumns[index], inputRow[index]);
			}
			JSONObject jobject = new JSONObject(inputMap);

//	        clock.advanceTime(1, TimeUnit.MINUTES );
//			sfSession.insert(jobject);
	        this.entryPoint.insert(jobject);

		}
		// TODO add clock stepping routine
		// get timestamp form input as stepping time variable
		sfSession.fireAllRules();

        for ( FactHandle f : sfSession.getFactHandles() ) {
        	Object o = sfSession.getObject( f );
        	if ( o.getClass().toString().toLowerCase().contains("Output".toLowerCase()) ) {
    		    JSONObject outputJSON = new JSONObject(o.toString());
    		    outputJSONList.add(outputJSON);
        	}
        }
                
		Iterator it = outputJSONList.iterator(); 
		while ( it.hasNext() ) {
			JSONObject outputJSON = (JSONObject) it.next();
			HashMap<String, String> outputHashMap = new HashMap();
			Integer index = 0;
			for(String colName: outputColumns){
				outputHashMap.put( colName, outputJSON.get(colName).toString() );
				outputRow[index] = outputHashMap.get(outputColumns.get(index));
				index++;
			}
			printer.writeln(outputRow);
		}
		sfSession.dispose();        
	}
	
	protected KieSession createCloudSession(String rules) throws Exception {

		KieServices ks = KieServices.Factory.get();
		KieRepository kr = ks.getRepository();
		KieFileSystem kfs = ks.newKieFileSystem();

		kfs.write("src/main/resources/rules/Rules.drl", rules);
		KieBuilder kb = ks.newKieBuilder(kfs);
		kb.buildAll(); 
		// kieModule is automatically deployed to KieRepository if successfully built.

		if (kb.getResults().hasMessages(Level.ERROR)) {
			throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
		}
    	
    	KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());

    	KieBaseConfiguration kBaseConf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
    	kBaseConf.setOption( EventProcessingOption.CLOUD );
// CLOUD mode throws error atb this point which maybe BUG https://bugzilla.redhat.com/show_bug.cgi?id=1005165
// has been set to run in CLOUD mode but requires features only available in STREAM mode
// Basically foget CLOUD mode until few months from 16-DEC
    	KieBase kbase = kContainer.newKieBase(kBaseConf);

    	KieSessionConfiguration ksessionConfig = KnowledgeBaseFactory.newKnowledgeSessionConfiguration();    	
    	ksessionConfig.setOption( ClockTypeOption.get("realtime") );
    	sfSession = kbase.newKieSession(ksessionConfig, null);
    	
        this.entryPoint = (EntryPoint) sfSession.getEntryPoint( "DEFAULT" );
        System.out.println("EntryPoint-->" + sfSession.getEntryPointId());

//		sfSession.addEventListener( new DebugAgendaEventListener() );
//		sfSession.addEventListener( new DebugRuleRuntimeEventListener() );

		return sfSession;
	}

	protected void runCloudRules(String[] inputColumns, List<String> outputColumns, CSVParser parser,
			CSVPrinter printer, KieSession sfSession) throws Exception {
		String[] outputRow = new String[outputColumns.size()];
		String[] inputRow;

		Map<String, String> inputMap = new HashMap<String, String>();
		Map<String, String> outputMap = new HashMap<String, String>();
		java.util.List<JSONObject> outputJSONList = new ArrayList<JSONObject>();
        SessionClock clock = sfSession.getSessionClock();
        
        System.out.println("clockc-->" + clock.toString()  );
        System.out.println("clocks-->" + sfSession.getSessionClock()  );
        
		while ((inputRow = parser.getLine()) != null) {
			// write the values for the input row
			for (int index = 0; index < inputColumns.length; index++) {
				inputMap.put(inputColumns[index], inputRow[index]);
			}
			JSONObject jobject = new JSONObject(inputMap);

//	        clock.advanceTime(1, TimeUnit.MINUTES );
//			sfSession.insert(jobject);
	        this.entryPoint.insert(jobject);

		}
		// TODO add clock stepping routine
		// get timestamp form input as stepping time variable
		sfSession.fireAllRules();

        for ( FactHandle f : sfSession.getFactHandles() ) {
        	Object o = sfSession.getObject( f );
        	if ( o.getClass().toString().toLowerCase().contains("Output".toLowerCase()) ) {
    		    JSONObject outputJSON = new JSONObject(o.toString());
    		    outputJSONList.add(outputJSON);
        	}
        }
                
		Iterator it = outputJSONList.iterator(); 
		while ( it.hasNext() ) {
			JSONObject outputJSON = (JSONObject) it.next();
			HashMap<String, String> outputHashMap = new HashMap();
			Integer index = 0;
			for(String colName: outputColumns){
				outputHashMap.put( colName, outputJSON.get(colName).toString() );
				outputRow[index] = outputHashMap.get(outputColumns.get(index));
				index++;
			}
			printer.writeln(outputRow);
		}
		sfSession.dispose();        
	}
	
	protected KieSession createStatefulSession(String rules) throws Exception {

		KieServices ks = KieServices.Factory.get();
		KieRepository kr = ks.getRepository();
		KieFileSystem kfs = ks.newKieFileSystem();

		kfs.write("src/main/resources/rules/Rules.drl", rules);
		KieBuilder kb = ks.newKieBuilder(kfs);

		kb.buildAll(); 
		// kieModule is automatically deployed to KieRepository if successfully built.

		if (kb.getResults().hasMessages(Level.ERROR)) {
			throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
		}

		KieContainer kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
		KieSession sfSession = kContainer.newKieSession();

//		sfSession.addEventListener( new DebugAgendaEventListener() );
//		sfSession.addEventListener( new DebugRuleRuntimeEventListener() );

		return sfSession;
	}

	protected void runStatefulRules(String[] inputColumns, List<String> outputColumns, CSVParser parser,
			CSVPrinter printer, KieSession sfSession) throws Exception {
		String[] outputRow = new String[outputColumns.size()];
		String[] inputRow;

		Map<String, String> inputMap = new HashMap<String, String>();
		Map<String, String> outputMap = new HashMap<String, String>();
		java.util.List<JSONObject> outputJSONList = new ArrayList<JSONObject>();
				
		while ((inputRow = parser.getLine()) != null) {
			// write the values for the input row
			for (int index = 0; index < inputColumns.length; index++) {
				inputMap.put(inputColumns[index], inputRow[index]);
			}
			sfSession.insert(new JSONObject(inputMap));
		}	
		sfSession.fireAllRules();

        for ( FactHandle f : sfSession.getFactHandles() ) {
        	Object o = sfSession.getObject( f );
        	if ( o.getClass().toString().toLowerCase().contains("Output".toLowerCase()) ) {
    		    JSONObject outputJSON = new JSONObject(o.toString());
    		    outputJSONList.add(outputJSON);
        	}
        }
            		
		/*
		// Could use ObjectFilter instead Google around for examples
		QueryResults results = sfSession.getQueryResults( "getOutput" ); 
		for ( QueryResultsRow row : results ) {
		    Object output = ( Object ) row.get( "$output" );
		    String soutput = output.toString();
		    String[] split1;
		    String[] split2;		    
		    split1 = soutput.split("outputMap=");
		    split2 = split1[1].split("} ");
		    String joutput = split2[0] +"}";
		    JSONObject outputJSON = new JSONObject(joutput);
		    outputJSONList.add(outputJSON);		
		}
		*/
		
		Iterator it = outputJSONList.iterator(); 
		while ( it.hasNext() ) {
			JSONObject outputJSON = (JSONObject) it.next();
			HashMap<String, String> outputHashMap = new HashMap();
			Integer index = 0;
			for(String colName: outputColumns){
				outputHashMap.put( colName, outputJSON.get(colName).toString() );
				outputRow[index] = outputHashMap.get(outputColumns.get(index));
				index++;
			}
			printer.writeln(outputRow);
		}
		
		sfSession.dispose();
	}
	
}

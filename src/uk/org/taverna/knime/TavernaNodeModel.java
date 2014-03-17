package uk.org.taverna.knime;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.util.FileUtil;

import uk.org.taverna.scufl2.api.container.WorkflowBundle;
import uk.org.taverna.scufl2.api.io.ReaderException;
import uk.org.taverna.scufl2.api.io.WorkflowBundleIO;
import uk.org.taverna.scufl2.api.port.InputWorkflowPort;
import uk.org.taverna.scufl2.api.port.Port;


/**
 * This is the model implementation of Taverna.
 * Execute Taverna workflow
 *
 * @author myGrid
 */
public class TavernaNodeModel extends NodeModel {
    
	private static final int SLEEP_MILLISECONDS = 300;

	protected static final int TAVERNA_TIMEOUT_S_DEFAULT = 60;

	/** Sort strings by first comparing their prefixed integer (if any), then the remaining characters.
	 *  Only non-negative {@link Integer}s are supported for the numeric comparison.
	 *  
	 * e.g.:
	 * <pre>
	 * "13.txt" < "2.txt" < "23.txt" < "123.txt"
	 * "13" < "13.csv" < "13.txt"
	 * "0.txt" < "2147483647.txt" < "-1" < "hello.txt"
	 * </pre>
	 */
    private final class NumericComparator implements Comparator<String> {
		@Override
		public int compare(String arg0, String arg1) {
			int diff = intFrom(arg0) - intFrom(arg1);
			if (diff != 0) {
				return diff;
			}
			return arg0.compareTo(arg1);
		}
	}

	protected static final String TAVERNA_WORKFLOW_PATH = "taverna_workflow_path";
	
	protected static final String TAVERNA_EXECUTEWORKFLOW_PATH = "taverna_executeworkflow_path";

	protected static final String TAVERNA_TIMEOUT_S = "taverna_timeout_s";
	
	protected static final String TAVERNA_INPUT_DELIMITER = "taverna_input_delimiter";

	// the logger instance
    private static final NodeLogger logger = NodeLogger
            .getLogger(TavernaNodeModel.class);
        
    private final SettingsModelString m_taverna_workflow_path = 
    		new SettingsModelString(TAVERNA_WORKFLOW_PATH, "");

    private final SettingsModelString m_taverna_executeworkflow_path = 
    		new SettingsModelString(TAVERNA_EXECUTEWORKFLOW_PATH, "executeworkflow");
    
    // Alternatives:
    // "\u0007"; // infamous BEL character
    // "\t" // TAB
    // "\n" // LF
    private final String m_taverna_input_delimiter = "\n"; 
    
    private final SettingsModelIntegerBounded m_taverna_timeout_s = 
    		new SettingsModelIntegerBounded(TAVERNA_TIMEOUT_S, TAVERNA_TIMEOUT_S_DEFAULT, 0, Integer.MAX_VALUE);
    

    

    /**
     * Constructor for the node model.
     */
    protected TavernaNodeModel() {
    	// As we have to decide on ports at construction time in Knime, then 
    	// the best we can say before having seen the .t2flow file is
		// simply one input and one output tables - each containing a column per
		// Taverna workflow port
        super(1, 1);        
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {

    	// Should not really be needed.. just to be sure wfbundle has been loaded
    	loadTavernaWorkflow();
    	
    	ProcessBuilder proc = new ProcessBuilder(m_taverna_executeworkflow_path.getStringValue());
    	
    	
        //CommandLine cmdLine = CommandLine.parse("executeworkflow");
        String workflowPath = m_taverna_workflow_path.getStringValue();
		proc.command().add(workflowPath);
        
        File tmpDir = FileUtil.createTempDir("taverna_" + wfbundle.getName()+"_output_");
        // Note - directory for -outputdir must NOT pre-exist
        tmpDir.delete();
        logger.debug("Saving Taverna workflow output to temporary dir: " + tmpDir);
        proc.command().add("-outputdir");
        proc.command().add(tmpDir.getAbsolutePath());
        
        
        DataRow firstRow = null;
       	// Prepare inputs
    	if (! wfbundle.getMainWorkflow().getInputPorts().isEmpty()) {
    		if (inData == null || inData[0] == null || inData[0].getRowCount() < 0) {
    			throw new IllegalArgumentException("Inputs missing");
    		}
    		firstRow = firstRow(inData);    		
    	}
    	for (InputWorkflowPort p : wfbundle.getMainWorkflow().getInputPorts()) {
    		/**  findColumnIndex should be safe here - if the port was missing
    		 * it should have been picked up in {@link #configure(DataTableSpec[])}
    		 */
    		int columnIndex = inData[0].getSpec().findColumnIndex(p.getName());
    		if (p.getDepth() == null || p.getDepth() == 0) {
    			// Simple input - pick from firstRow
				DataCell cell = firstRow.getCell(columnIndex);
    			proc.command().add("-inputvalue");
    			proc.command().add(p.getName());
    			proc.command().add(cell.toString());
    			
    			// Fixme: Should 'big' or 'multiple lines' values go to a file?    			
    		} else {
    			// List - we'll need to join the cells
    			proc.command().add("-inputdelimiter");
    			proc.command().add(p.getName());
    			String delimiter = m_taverna_input_delimiter;
    			// TODO: Check if delimiter works as expected on Windows
    			proc.command().add(delimiter);
    			
    			// As the list might be big, we'll use a temporary file
    			File tempFile = FileUtil.createTempFile("taverna_" + wfbundle.getName()+"_input_" + p.getName() + "_", ".txt");
    			
    			BufferedWriter writer = new BufferedWriter(new FileWriterWithEncoding(tempFile, "UTF-8"));    			
    			try { 
    				CloseableRowIterator it = inData[0].iterator();
    				while (it.hasNext()) {
	    				DataCell cell = it.next().getCell(columnIndex);
	    				if (! cell.isMissing()) {
	    					writer.append(cell.toString());
	    				}
						writer.append(delimiter);
	    			}
    			} finally {
    				writer.close();
    			}    			
    			proc.command().add("-inputfile");
    			proc.command().add(p.getName());
    			proc.command().add(tempFile.getAbsolutePath());    			
    		}
    	}
        
    	logger.debug("Running Taverna workflow " + wfbundle.getName());
    	
    	// Execute
        
        logger.info("Executing Taverna: " + proc.command());
        System.out.println(proc.command());
        
        // TODO: Pass-through stdout to knime logger
        proc.inheritIO();
        Process process = proc.start();
        

        
        long until = Long.MAX_VALUE;
        if (m_taverna_timeout_s.getIntValue() > 0) {
        	until = new Date().getTime() + m_taverna_timeout_s.getIntValue() * 1000;
        }
        int exitValue = -1;
        while (until > new Date().getTime()) {        	
        	try { 
        		exec.checkCanceled();
        		exitValue = process.exitValue();
        		break;
        	} catch (CanceledExecutionException ex) {
        		process.destroy();
        		throw ex;
        	} catch (IllegalThreadStateException e) {
        		Thread.sleep(SLEEP_MILLISECONDS);
        	}        	
        }
        
        if (exitValue == -1) {
        	throw new Exception("Process did not return");
        }
        if (exitValue > 0) {
        	throw new Exception("Error in process");
        }
        
        //process.exitValue();
        
        
        //int exitValue = executor.execute(cmdLine);
        
 
        // Process outputs
        
        DataTableSpec outputSpec = generateOutputSpecs();
    	// We'll need to gather the Taverna ports into a single table
    	// as KNIME does not support dynamic changing of the number
    	// of ports of a Node (we don't know the number of Taverna
    	// output ports before the t2flow has been selected)
    	
    	Map<Port, List<DataCell>> cellsPerPort = new HashMap<>();
    	// Note: .getOutputPorts() is always in predictable alphabetical order
		for (Port p : wfbundle.getMainWorkflow().getOutputPorts()) {
			List<DataCell> rows = new ArrayList<>();
			cellsPerPort.put(p, rows);
			exec.checkCanceled();
			File file = getFileForPort(tmpDir, p);
			// Might recurse to handle lists or lists-of-lists
			addRows(exec, rows, file);
		}

		int numRows = 0;
		for (List<DataCell> list : cellsPerPort.values()) {
			numRows = Math.max(numRows, list.size());
		}

		// OK, create the table
    	BufferedDataContainer container = exec.createDataContainer(outputSpec);
    	for (int i=0; i < numRows ; i++) {
    		List<DataCell> row = new ArrayList<>();
    		for (Port p : wfbundle.getMainWorkflow().getOutputPorts()) {
    	    	// Note: .getOutputPorts() is always in predictable alphabetical order
    			List<DataCell> portCells = cellsPerPort.get(p);
    			if (portCells.size() <= i) { 
    				row.add(DataType.getMissingCell());
//    				row.add(null);
    			} else {
    				row.add(portCells.get(i));
    			}
    		}
			container.addRowToTable(new DefaultRow(Integer.toString(i+1), row));
    	}    	
    	container.close();
    	
    	return new BufferedDataTable[]{container.getTable()};
    }



	private DataRow firstRow(final BufferedDataTable[] inData) {
		CloseableRowIterator iterator = inData[0].iterator();
		try { 
			return iterator.next();
		} finally { 
			iterator.close();
		}
	}



	private WorkflowBundle loadTavernaWorkflow(String workflowPath) throws InvalidSettingsException {
     	
		File workflow_file = new File(workflowPath);
    	if (workflow_file.exists()) {
    		try {
				return new WorkflowBundleIO().readBundle(workflow_file, "application/vnd.taverna.t2flow+xml");
			} catch (ReaderException | IOException e) {
				throw new InvalidSettingsException("Can't read Taverna workflow file " + workflow_file, e);
			}
    	}
    	if (workflowPath.contains(":")) { 
    		// Remote URL to workflow
			try {
				URL workflow_url = new URL(workflowPath);
				return new WorkflowBundleIO().readBundle(workflow_url, "application/vnd.taverna.t2flow+xml");
			} catch (ReaderException | IOException e) {
				throw new InvalidSettingsException("Can't read Taverna workflow URL " + workflowPath, e);
			}
    	}
		throw new InvalidSettingsException("Can't find Taverna workflow " + workflowPath);    	
	}

	private void addRows(final ExecutionContext exec, List<DataCell> rows,
			File file) throws IOException, CanceledExecutionException {
		if (file == null) {
			return;
			// No output / error e.g. no rows for this port
		} else if (file.isFile()) {
			rows.add(fileAsCell(file));
		} else if (file.isDirectory()) {
			// Make sure we iterate over the children in numeric order
			List<String> children = numericallyListedChildren(file);
			for (String child : children) {
				exec.checkCanceled();
				// Recurse to add file or subdirectory
				addRows(exec, rows, new File(file, child));
			}
		}
	}

	private List<String> numericallyListedChildren(File file) {
		List<String> children = Arrays.asList(file.list());
		Collections.sort(children, new NumericComparator());
		return children;
	}

	final Pattern startNumber = Pattern.compile("^[0-9]+");

	protected WorkflowBundle wfbundle;
    	
	private int intFrom(String str) {
		Matcher matcher = startNumber.matcher(str);
		if (! matcher.find()) {
			// Ensure non-numeric are sorted last
			return Integer.MAX_VALUE;
		}
		return Integer.parseInt(matcher.group());
	};


	private DataCell fileAsCell(File file) throws IOException {
		if (file == null || ! file.isFile() || file.getPath().endsWith(".error")) {
			// as far as we know - Knime has no way to represents errors mid-table
			// beyond 'missing cell'
			return DataType.getMissingCell();
		}
		
		byte[] bytes = Files.readAllBytes(file.toPath());
		// FIXME: What if it is a binary not parseable as a UTF8 string??
		String str = new String(bytes, Charset.forName("UTF-8"));
		// TODO: Optional fancier parsing of multi-line inputs etc? 
		// Automagic or per config?    			
		return new StringCell(str);
	} 

    private File getFileForPort(File tmpDir, Port p) {
    	final String portName = p.getName();
		File directMatch = new File(tmpDir, portName);
    	if (directMatch.exists()) {
    		// Note: Might be a directory!
    		return directMatch;
    	}
    	// First file with any extension
    	File[] files = tmpDir.listFiles(new FilenameFilter() {			
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(portName + ".") && ! name.endsWith(".error");
			}
		});
		if (files.length == 0) { 
			return null;
		}
		// Naive pick up first match
		return files[0];		
	}

	/**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // TODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        
    	// Ensure we have a wfbundle loaded
    	loadTavernaWorkflow();
    	
    	if (inSpecs != null && inSpecs.length > 0 && inSpecs[0] != null) {
			DataTableSpec inSpec = inSpecs[0];
			for (InputWorkflowPort p : wfbundle.getMainWorkflow().getInputPorts()) {
				if (! inSpec.containsName(p.getName())) { 
					throw new InvalidSettingsException("Missing input column: " + p.getName());
				}
				if (p.getDepth() != null && p.getDepth() > 1) {
					logger.warn("Unsupported list-of-list input column: " + p.getName());
				}
			}
    	}

        return new DataTableSpec[]{generateOutputSpecs()};
    }



	private DataTableSpec generateOutputSpecs() {
		// Genere our output spec
        ArrayList<DataColumnSpec> oututColumnSpecs = new ArrayList<DataColumnSpec>();        
        // One column per Taverna output port
    	for (Port p : wfbundle.getMainWorkflow().getOutputPorts()) {
            // FIXME: Cell type should not be fixed - user configurable?
        	oututColumnSpecs.add(new DataColumnSpecCreator(p.getName(), StringCell.TYPE).createSpec());   
    	}
    	DataTableSpec outputSpec = new DataTableSpec(oututColumnSpecs.toArray(new DataColumnSpec[0]));
		return outputSpec;
	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {

    	m_taverna_workflow_path.saveSettingsTo(settings);
    	m_taverna_timeout_s.saveSettingsTo(settings);    	
    	m_taverna_executeworkflow_path.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
            
    	m_taverna_workflow_path.loadSettingsFrom(settings);
    	m_taverna_timeout_s.loadSettingsFrom(settings);
    	m_taverna_executeworkflow_path.loadSettingsFrom(settings);
    	loadTavernaWorkflow();
    }

    private void loadTavernaWorkflow() throws InvalidSettingsException {
    	wfbundle = null;
    	wfbundle = loadTavernaWorkflow(m_taverna_workflow_path.getStringValue());
	}



	/**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    	m_taverna_workflow_path.validateSettings(settings);
    	m_taverna_timeout_s.validateSettings(settings);
    	m_taverna_executeworkflow_path.validateSettings(settings);
    	loadTavernaWorkflow(settings.getString(TAVERNA_WORKFLOW_PATH));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        
        // TODO load internal data. 
        // Everything handed to output ports is loaded automatically (data
        // returned by the execute method, models loaded in loadModelContent,
        // and user settings set through loadSettingsFrom - is all taken care 
        // of). Load here only the other internals that need to be restored
        // (e.g. data used by the views).

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
       
        // TODO save internal models. 
        // Everything written to output ports is saved automatically (data
        // returned by the execute method, models saved in the saveModelContent,
        // and user settings saved through saveSettingsTo - is all taken care 
        // of). Save here only the other internals that need to be preserved
        // (e.g. data used by the views).

    }

}


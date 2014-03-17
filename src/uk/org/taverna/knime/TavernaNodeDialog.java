package uk.org.taverna.knime;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentFileChooser;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "Taverna" Node. Execute Taverna workflow
 * 
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more
 * complex dialog please derive directly from
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author myGrid
 */
public class TavernaNodeDialog extends DefaultNodeSettingsPane {

	/**
	 * New pane for configuring Taverna node dialog. This is just a suggestion
	 * to demonstrate possible default dialog components.
	 */
	protected TavernaNodeDialog() {
		super();
		DialogComponentFileChooser fileChooser = new DialogComponentFileChooser(
				new SettingsModelString(TavernaNodeModel.TAVERNA_WORKFLOW_PATH,
						""), TavernaNodeModel.TAVERNA_WORKFLOW_PATH, ".t2flow");
		fileChooser.setBorderTitle("Taverna workflow");
		addDialogComponent(fileChooser);

		SettingsModelIntegerBounded tavernaTimeoutM = new SettingsModelIntegerBounded(
				TavernaNodeModel.TAVERNA_TIMEOUT_S,
				TavernaNodeModel.TAVERNA_TIMEOUT_S_DEFAULT, 0,
				Integer.MAX_VALUE);
		
		
		addDialogComponent(new DialogComponentNumber(tavernaTimeoutM,
				"Timeout (seconds)", 5));
		
		DialogComponentFileChooser executeWorkflowChoser = new DialogComponentFileChooser(
				new SettingsModelString(TavernaNodeModel.TAVERNA_EXECUTEWORKFLOW_PATH,
						""), TavernaNodeModel.TAVERNA_EXECUTEWORKFLOW_PATH);
		executeWorkflowChoser.setBorderTitle("executeworkflow binary");
		addDialogComponent(executeWorkflowChoser);


	}
}

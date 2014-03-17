package uk.org.taverna.knime;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

import org.knime.core.node.NodeView;

import uk.org.taverna.scufl2.api.port.InputWorkflowPort;

/**
 * <code>NodeView</code> for the "Taverna" Node. Execute Taverna workflow
 * 
 * @author myGrid
 */
public class TavernaNodeView extends NodeView<TavernaNodeModel> {

	/**
	 * Creates a new view.
	 * 
	 * @param nodeModel
	 *            The model (class: {@link TavernaNodeModel})
	 */
	protected TavernaNodeView(final TavernaNodeModel nodeModel) {
		super(nodeModel);
		setShowNODATALabel(false);		
		modelChanged();
		// TODO instantiate the components of the view here.

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void modelChanged() {

		// TODO: Redo layout so it is usable
		TavernaNodeModel nodeModel = (TavernaNodeModel) getNodeModel();
		assert nodeModel != null;

		JPanel comp = new JPanel();
		comp.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.anchor = GridBagConstraints.BASELINE_LEADING;
		
		if (nodeModel.wfbundle == null) {
			comp.add(new JLabel("Can't load workflow"), gbc);
			setComponent(comp);
			return;
		}
			
		if (nodeModel.wfbundle.getMainWorkflow().getInputPorts().isEmpty()) {
			comp.add(new JLabel("No input columns"), gbc);
		} else {
			comp.add(new JLabel("Input columns"), gbc);
			List<String> ports = new ArrayList<>();
			for (InputWorkflowPort p : nodeModel.wfbundle.getMainWorkflow()
					.getInputPorts()) {
				String portLabel = p.getName();
				if (p.getDepth() == null || p.getDepth() == 0) {
					portLabel += " (single)";
				}
				ports.add(portLabel);
			}
			comp.add(new JList(ports.toArray()), gbc);
		}
		if (nodeModel.wfbundle.getMainWorkflow().getOutputPorts().isEmpty()) {
			comp.add(new JLabel("No output columns"), gbc);
		} else {
			comp.add(new JLabel("Output columns"), gbc);
			comp.add(new JList(nodeModel.wfbundle.getMainWorkflow()
					.getOutputPorts().getNames().toArray()), gbc);
		}

		this.setComponent(comp);


	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onClose() {

		// TODO things to do when closing the view
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onOpen() {
		
		// TODO things to do when opening the view
	}

}

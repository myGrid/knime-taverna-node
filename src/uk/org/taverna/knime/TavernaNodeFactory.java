package uk.org.taverna.knime;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "Taverna" Node.
 * Execute Taverna workflow
 *
 * @author myGrid
 */
public class TavernaNodeFactory 
        extends NodeFactory<TavernaNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public TavernaNodeModel createNodeModel() {
        return new TavernaNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<TavernaNodeModel> createNodeView(final int viewIndex,
            final TavernaNodeModel nodeModel) {
        return new TavernaNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new TavernaNodeDialog();
    }

}


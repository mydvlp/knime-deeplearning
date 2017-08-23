/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 *
 * History
 *   Sep 25, 2014 (Patrick Winter): created
 */
package org.knime.dl.python.base.node.predictor;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.core.node.workflow.NodeContext;
import org.knime.dl.base.portobjects.DLNetworkPortObject;
import org.knime.dl.python.core.DLPythonNetwork;
import org.knime.dl.python.core.DLPythonNetworkHandle;
import org.knime.dl.python.core.DLPythonNetworkSpec;
import org.knime.python2.config.PythonSourceCodeOptionsPanel;
import org.knime.python2.config.WorkspacePreparer;
import org.knime.python2.kernel.FlowVariableOptions;
import org.knime.python2.kernel.PythonKernel;
import org.knime.python2.port.PickledObject;

/**
 * Shamelessly copied and pasted from python predictor.
 *
 * @author Christian Dietz, KNIME
 */
class DLPythonPredictorNodeDialog extends DataAwareNodeDialogPane {

	DLPythonSourceCodePanel m_sourceCodePanel;

	PythonSourceCodeOptionsPanel m_sourceCodeOptionsPanel;

	/**
	 * Create the dialog for this node.
	 */
	protected DLPythonPredictorNodeDialog() {
		m_sourceCodePanel = new DLPythonSourceCodePanel(DLPythonPredictorNodeConfig.getVariableNames(),
				FlowVariableOptions.parse(getAvailableFlowVariables()));
		m_sourceCodeOptionsPanel = new PythonSourceCodeOptionsPanel(m_sourceCodePanel);
		addTab("Script", m_sourceCodePanel, false);
		addTab("Options", m_sourceCodeOptionsPanel, true);
	}

	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
		final DLPythonPredictorNodeConfig config = new DLPythonPredictorNodeConfig();
		m_sourceCodePanel.saveSettingsTo(config);
		m_sourceCodeOptionsPanel.saveSettingsTo(config);
		config.saveTo(settings);
	}

	@Override
	protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
			throws NotConfigurableException {
		final DLPythonPredictorNodeConfig config = new DLPythonPredictorNodeConfig();
		config.loadFromInDialog(settings);
		m_sourceCodePanel.loadSettingsFrom(config, specs);
		m_sourceCodePanel.updateFlowVariables(
				getAvailableFlowVariables().values().toArray(new FlowVariable[getAvailableFlowVariables().size()]));
		m_sourceCodeOptionsPanel.loadSettingsFrom(config);
		m_sourceCodePanel.updateData(new BufferedDataTable[] { null }, new PickledObject[] { null });
	}

	@Override
	protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObject[] input)
			throws NotConfigurableException {
		final PortObjectSpec[] specs = new PortObjectSpec[input.length];
		for (int i = 0; i < specs.length; i++) {
			specs[i] = input[i] == null ? null : input[i].getSpec();
		}
		loadSettingsFrom(settings, specs);
		final DLNetworkPortObject portObject = (DLNetworkPortObject) input[0];
		if (portObject != null && portObject.getNetwork() instanceof DLPythonNetwork) {
			m_sourceCodePanel.registerWorkspacePreparer(new WorkspacePreparer() {
				@Override
				public void prepareWorkspace(final PythonKernel kernel) {
					try {
						NodeContext.pushContext(DLPythonPredictorNodeDialog.this.getNodeContext());
						final DLPythonNetwork<? extends DLPythonNetworkSpec> network =
								(DLPythonNetwork<? extends DLPythonNetworkSpec>) portObject.getNetwork();
						final DLPythonNetworkHandle networkHandle =
								network.getSpec().getLoader().load(network.getSource(), kernel);
						final String name = networkHandle.getIdentifier();
						kernel.execute("global input_network\ninput_network=" + name + "\ndel globals()['" + name
								+ "']\ndel locals()['" + name + "']");
						// TODO: clean workspace (remove model's load path etc.)
					} catch (final Exception e) {
						m_sourceCodePanel.errorToConsole(
								"Deep Learning network could not be loaded. Try again by pressing the \"Reset workspace\" button.");
					}
				}
			});
		} else {
			throw new NotConfigurableException(
					"Deep Learning network can't be handled by KNIME Deep Learning - Python Backend.");
		}

		m_sourceCodePanel.updateData(new BufferedDataTable[] { (BufferedDataTable) input[1] }, new PickledObject[] {});
	}

	@Override
	public boolean closeOnESC() {
		return false;
	}

	@Override
	public void onOpen() {
		m_sourceCodePanel.open();
	}

	@Override
	public void onClose() {
		m_sourceCodePanel.close();
	}
}
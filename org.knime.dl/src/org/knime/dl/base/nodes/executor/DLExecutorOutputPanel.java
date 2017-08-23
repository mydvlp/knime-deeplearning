/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 * History
 *   Jul 10, 2017 (marcel): created
 */
package org.knime.dl.base.nodes.executor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DataCell;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentString;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.dl.core.DLLayerDataSpec;
import org.knime.dl.core.data.convert.DLLayerDataToDataCellConverterFactory;
import org.knime.dl.core.data.convert.DLLayerDataToDataCellConverterRegistry;
import org.knime.dl.core.execution.DLExecutionContext;
import org.knime.dl.core.execution.DLExecutionContextRegistry;

/**
 * @author Marcel Wiedenmann, KNIME, Konstanz, Germany
 * @author Christian Dietz, KNIME, Konstanz, Germany
 */
final class DLExecutorOutputPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final DLExecutorOutputConfig m_cfg;

	private final DLLayerDataSpec m_outputDataSpec;

	private final DialogComponentIdFromPrettyStringSelection m_dcConverter;

	private final CopyOnWriteArrayList<ChangeListener> m_removeListeners;

	DLExecutorOutputPanel(final DLExecutorOutputConfig cfg, final DLLayerDataSpec outputDataSpec)
			throws NotConfigurableException {
		super(new GridBagLayout());
		m_cfg = cfg;
		m_outputDataSpec = outputDataSpec;
		m_removeListeners = new CopyOnWriteArrayList<>();

		// construct panel:

		setBorder(BorderFactory.createTitledBorder("Output: " + m_outputDataSpec.getName()));
		final GridBagConstraints constr = new GridBagConstraints();
		constr.gridx = 0;
		constr.gridy = 0;
		constr.weightx = 1;
		constr.anchor = GridBagConstraints.WEST;
		constr.fill = GridBagConstraints.VERTICAL;
		// meta information
		final JPanel shape = new JPanel();
		final GridBagConstraints shapeConstr = new GridBagConstraints();
		shapeConstr.insets = new Insets(5, 0, 5, 0);
		shape.add(new JLabel("Shape: " + m_outputDataSpec.getShape().toString()), shapeConstr);
		add(shape, constr);
		// 'remove' button, see bottom for click event handling
		final JButton outputRemoveBtn = new JButton("remove");
		final GridBagConstraints outputRemoveBtnConstr = (GridBagConstraints) constr.clone();
		outputRemoveBtnConstr.weightx = 1;
		outputRemoveBtnConstr.anchor = GridBagConstraints.EAST;
		outputRemoveBtnConstr.fill = GridBagConstraints.NONE;
		outputRemoveBtnConstr.insets = new Insets(0, 0, 0, 5);
		add(outputRemoveBtn, outputRemoveBtnConstr);
		constr.gridy++;
		// converter selection
		m_dcConverter = new DialogComponentIdFromPrettyStringSelection(m_cfg.getConverterModel(), "Conversion", (e) -> {
			m_cfg.getConverterModel()
					.setStringArrayValue(((DialogComponentIdFromPrettyStringSelection) e.getSource()).getSelection());
		});
		add(m_dcConverter.getComponentPanel(), constr);
		constr.gridy++;
		// prefix text input
		final DialogComponentString dcPrefix =
				new DialogComponentString(m_cfg.getPrefixModel(), "Output columns prefix");
		add(dcPrefix.getComponentPanel(), constr);
		constr.gridy++;
		// 'remove' button click event: remove output
		outputRemoveBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(final ActionEvent e) {
				onRemove();
			}
		});

		m_cfg.getGeneralConfig().addExecutionContextChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(final ChangeEvent e) {
				try {
					refreshAvailableConverters();
				} catch (final NotConfigurableException ex) {
					throw new IllegalStateException(ex.getMessage(), ex);
				}
			}
		});

		refreshAvailableConverters();
	}

	DLExecutorOutputConfig getConfig() {
		return m_cfg;
	}

	void addRemoveListener(final ChangeListener l) {
		if (!m_removeListeners.contains(l)) {
			m_removeListeners.add(l);
		}
	}

	void removeRemoveListener(final ChangeListener l) {
		m_removeListeners.remove(l);
	}

	void loadFromSettings(final NodeSettingsRO settings, final PortObjectSpec[] specs) throws NotConfigurableException {
		refreshAvailableConverters();

		try {
			m_cfg.loadFromSettings(settings);
		} catch (final InvalidSettingsException e) {
			// ignore
		}
	}

	void saveToSettings(final NodeSettingsWO settings) {
		m_cfg.saveToSettings(settings);
	}

	private void refreshAvailableConverters() throws NotConfigurableException {
		final DLExecutionContext<?> executionContext =
				DLExecutionContextRegistry.getInstance()
						.getExecutionContext(m_cfg.getGeneralConfig().getExecutionContext()[1])
						.orElseThrow(() -> new NotConfigurableException("Execution back end '"
								+ m_cfg.getGeneralConfig().getExecutionContext()[0] + " ("
								+ m_cfg.getGeneralConfig().getExecutionContext()[1] + ")' could not be found."));
		final List<DLLayerDataToDataCellConverterFactory<?, ? extends DataCell>> converterFactories =
				DLLayerDataToDataCellConverterRegistry.getInstance().getPreferredFactoriesForSourceType(
						executionContext.getLayerDataFactory().getReadableBufferType(m_outputDataSpec),
						m_outputDataSpec);
		converterFactories.sort(Comparator.comparing(DLLayerDataToDataCellConverterFactory::getName));
		final String[] names = new String[converterFactories.size()];
		final String[] ids = new String[converterFactories.size()];
		for (int i = 0; i < converterFactories.size(); i++) {
			final DLLayerDataToDataCellConverterFactory<?, ? extends DataCell> converter = converterFactories.get(i);
			names[i] = "To " + converter.getName();
			ids[i] = converter.getIdentifier();
		}
		if (names.length == 0) {
			throw new NotConfigurableException(
					"No converters available for output '" + m_outputDataSpec.getName() + "'.");
		}
		m_dcConverter.replaceListItems(names, ids, null);
	}

	private void onRemove() {
		for (final ChangeListener l : m_removeListeners) {
			l.stateChanged(new ChangeEvent(this));
		}
	}
}
/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 *   May 31, 2017 (marcel): created
 */
package org.knime.dl.keras.base.nodes.learner;

import org.knime.core.data.DataValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterConfiguration;
import org.knime.dl.base.settings.AbstractStandardConfigEntry;
import org.knime.dl.base.settings.DLAbstractInputConfig;
import org.knime.dl.base.settings.DLDataTypeColumnFilter;
import org.knime.dl.core.DLTensorId;
import org.knime.dl.core.data.convert.DLDataValueToTensorConverterFactory;
import org.knime.dl.core.data.convert.DLDataValueToTensorConverterRegistry;

/**
 * @author Marcel Wiedenmann, KNIME GmbH, Konstanz, Germany
 * @author Christian Dietz, KNIME GmbH, Konstanz, Germany
 */
final class DLKerasLearnerInputConfig extends DLAbstractInputConfig<DLKerasLearnerGeneralConfig> {


    @SuppressWarnings("rawtypes") // Java limitation
    DLKerasLearnerInputConfig(final DLTensorId inputTensorId, final String inputTensorName,
        final DLKerasLearnerGeneralConfig generalCfg) {
        super(inputTensorId, inputTensorName, generalCfg);
		put(new AbstractStandardConfigEntry<DLDataValueToTensorConverterFactory>(CFG_KEY_CONVERTER,
				DLDataValueToTensorConverterFactory.class) {

			@Override
			protected void saveEntry(final NodeSettingsWO settings)
					throws InvalidSettingsException {
				final String converterIdentifier = m_value != null //
						? m_value.getIdentifier()
						: CFG_VALUE_NULL_CONVERTER;
				settings.addString(getEntryKey(), converterIdentifier);
			}

			@Override
			protected void loadEntry(final NodeSettingsRO settings)
					throws InvalidSettingsException {
				final String converterIdentifier = settings.getString(getEntryKey());
				if (CFG_VALUE_NULL_CONVERTER.equals(converterIdentifier)) {
					throw new InvalidSettingsException(
							"No training data converter available for network input '" + getTensorNameOrId() + "'.");
				}
				m_value = DLDataValueToTensorConverterRegistry.getInstance().getConverterFactory(converterIdentifier)
						.orElseThrow(() -> new InvalidSettingsException("Training data converter '"
								+ converterIdentifier + "' of network input '" + getTensorNameOrId()
								+ "' could not be found. Are you missing a KNIME extension?"));
			}
		});
		put(new AbstractStandardConfigEntry<DataColumnSpecFilterConfiguration>(CFG_KEY_INPUT_COL,
				DataColumnSpecFilterConfiguration.class,
				new DataColumnSpecFilterConfiguration(CFG_KEY_INPUT_COL, new DLDataTypeColumnFilter(DataValue.class))) {

			@Override
			protected void saveEntry(final NodeSettingsWO settings)
					throws InvalidSettingsException {
				final NodeSettingsWO subSettings = settings.addNodeSettings(CFG_KEY_INPUT_COL);
				m_value.saveConfiguration(subSettings);
			}

			@Override
			protected void loadEntry(final NodeSettingsRO settings)
					throws InvalidSettingsException {
				// no op. Separate routines for loading in model and dialog required. See super class.
			}
		});
	}

}

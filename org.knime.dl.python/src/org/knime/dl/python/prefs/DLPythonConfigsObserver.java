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
 */
package org.knime.dl.python.prefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.python2.Conda;
import org.knime.python2.PythonCommand;
import org.knime.python2.PythonKernelTester;
import org.knime.python2.PythonKernelTester.PythonKernelTestResult;
import org.knime.python2.PythonModuleSpec;
import org.knime.python2.PythonVersion;
import org.knime.python2.config.AbstractCondaEnvironmentsPanel;
import org.knime.python2.config.AbstractPythonConfigsObserver;
import org.knime.python2.config.CondaEnvironmentCreationObserver;
import org.knime.python2.config.PythonConfigsObserver;
import org.knime.python2.config.PythonEnvironmentConfig;
import org.knime.python2.config.PythonEnvironmentType;
import org.knime.python2.config.PythonEnvironmentTypeConfig;
import org.knime.python2.config.SerializerConfig;
import org.knime.python2.extensions.serializationlibrary.SerializationLibraryExtensions;

/**
 * TODO merge with {@link PythonConfigsObserver}?
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 */
final class DLPythonConfigsObserver extends AbstractPythonConfigsObserver {

    private final DLPythonConfigSelectionConfig m_configSelectionConfig;

    private final PythonEnvironmentTypeConfig m_environmentTypeConfig;

    private final DLCondaEnvironmentConfig m_condaEnvironmentConfig;

    private final CondaEnvironmentCreationObserver m_pythonEnvironmentCreator;

    private final DLManualEnvironmentConfig m_manualEnvironmentConfig;

    private final SerializerConfig m_serializerConfig;

    DLPythonConfigsObserver(final DLPythonConfigSelectionConfig configSelectionConfig,
        final PythonEnvironmentTypeConfig environmentTypeConfig, final DLCondaEnvironmentConfig condaEnvironmentConfig,
        final CondaEnvironmentCreationObserver pythonEnvironmentCreator,
        final DLManualEnvironmentConfig manualEnvironmentConfig, final SerializerConfig serializerConfig) {
        m_configSelectionConfig = configSelectionConfig;
        m_environmentTypeConfig = environmentTypeConfig;
        m_condaEnvironmentConfig = condaEnvironmentConfig;
        m_pythonEnvironmentCreator = pythonEnvironmentCreator;
        m_manualEnvironmentConfig = manualEnvironmentConfig;
        m_serializerConfig = serializerConfig;

        // TODO default environment? I think this isn't needed right?

        // TODO Observe config selection

        environmentTypeConfig.getEnvironmentType().addChangeListener(e -> testCurrentPreferences());

        // Refresh and test entire Conda config on Conda directory change
        condaEnvironmentConfig.getCondaDirectoryPath().addChangeListener(e -> refreshAndTestCondaConfig());

        // Test Conda environment on change
        condaEnvironmentConfig.getEnvironmentName().addChangeListener(e -> testPythonEnvironment(true));

        // Disable Conda environment creation by default. Updated when Conda installation is tested
        pythonEnvironmentCreator.getIsEnvironmentCreationEnabled().setBooleanValue(false);

        // TODO handle finished conda environment creation

        // Test manual config on change
        manualEnvironmentConfig.getExecutablePath().addChangeListener(e -> testPythonEnvironment(false));

        // Test everything if the serializer changes: C
        m_serializerConfig.getSerializer().addChangeListener(e -> testCurrentPreferences());
    }

    public void testCurrentPreferences() {
        final DLPythonConfigSelection configSelection =
            DLPythonConfigSelection.fromId(m_configSelectionConfig.getConfigSelection().getStringValue());

        if (DLPythonConfigSelection.PYTHON.equals(configSelection)) {
            //
            // Using the python config
            //
            // TODO check the python config
        } else if (DLPythonConfigSelection.DL.equals(configSelection)) {
            //
            // Using the special DL config
            //
            final PythonEnvironmentType environmentType =
                PythonEnvironmentType.fromId(m_environmentTypeConfig.getEnvironmentType().getStringValue());
            if (PythonEnvironmentType.CONDA.equals(environmentType)) {
                // CONDA
                refreshAndTestCondaConfig();
            } else if (PythonEnvironmentType.MANUAL.equals(environmentType)) {
                // MANUAL
                testPythonEnvironment(false);
            } else {
                throw new IllegalStateException("Selected environment type '" + environmentType.getName()
                    + "' is neither " + "conda nor manual. This is an implementation error.");
            }
        } else {
            throw new IllegalStateException("Selected config'" + configSelection.getName() + "' is neither "
                + "python nor deep learning. This is an implementation error.");
        }
    }

    private void refreshAndTestCondaConfig() {
        new Thread(() -> {
            // Test the conda installation
            final Conda conda;
            try {
                conda = testCondaInstallation();
            } catch (final Exception ex) {
                return;
            }
            // Get the available environments
            final List<String> availableEnvironments;
            try {
                availableEnvironments = getAvailableCondaEnvironments(conda, true);
            } catch (final Exception ex) {
                return;
            }

            // Test the configuration
            try {
                setAvailableCondaEnvironments(availableEnvironments);
                testPythonEnvironment(true);
            } catch (Exception ex) {
                // Ignore, we still want to configure and test the second environment.
            }
        }).start();
    }

    private void testPythonEnvironment(final boolean isConda) {
        final String environmentCreationInfo = "\nNote: You can create a new Python Conda environment that "
            + "contains all packages\nrequired by the KNIME Deep Learning integration by clicking the '"
            + AbstractCondaEnvironmentsPanel.CREATE_NEW_ENVIRONMENT_BUTTON_TEXT + "' button\nabove.";

        // Conda or manual
        final PythonEnvironmentType environmentType;
        final PythonEnvironmentConfig environmentConfig;
        if (isConda) {
            environmentType = PythonEnvironmentType.CONDA;
            environmentConfig = m_condaEnvironmentConfig;
            if (isPlaceholderEnvironmentSelected()) {
                // If the placeholder is selected we ask the user to create a new environment
                // And test nothing
                m_condaEnvironmentConfig.getPythonInstallationInfo().setStringValue("");
                m_condaEnvironmentConfig.getPythonInstallationError().setStringValue(
                    "No environment avaiable. Please create a new one to be able to use the Deep Learning integration."
                        + environmentCreationInfo);
                return;
            }
        } else {
            environmentType = PythonEnvironmentType.MANUAL;
            environmentConfig = m_manualEnvironmentConfig;
        }

        final Collection<PythonModuleSpec> additionalModules = new ArrayList<>();

        // Serializer modules
        additionalModules.addAll(SerializationLibraryExtensions
            .getSerializationLibraryFactory(m_serializerConfig.getSerializer().getStringValue())
            .getRequiredExternalModules());

        // Deep learning modules
        // TODO add versions
        // TODO ...
        additionalModules.add(new PythonModuleSpec("keras"));
        additionalModules.add(new PythonModuleSpec("tensorflow"));
        additionalModules.add(new PythonModuleSpec("onnx"));
        additionalModules.add(new PythonModuleSpec("onnx_tf"));

        // Start the installation test in a new thread
        new Thread(() -> {
            onEnvironmentInstallationTestStarting(environmentType, PythonVersion.PYTHON3);
            final PythonCommand pythonCommand = environmentConfig.getPythonCommand();
            final PythonKernelTestResult testResult =
                PythonKernelTester.testPython3Installation(pythonCommand, additionalModules, true);
            environmentConfig.getPythonInstallationInfo().setStringValue(testResult.getVersion());
            String errorLog = testResult.getErrorLog();
            if (errorLog != null && !errorLog.isEmpty()) {
                errorLog += environmentCreationInfo;
            }
            environmentConfig.getPythonInstallationError().setStringValue(errorLog);
            onEnvironmentInstallationTestFinished(environmentType, PythonVersion.PYTHON3, testResult);
        }).start();
    }

    private boolean isPlaceholderEnvironmentSelected() {
        return m_condaEnvironmentConfig.getEnvironmentName().getStringValue()
            .equals(DLCondaEnvironmentConfig.DEFAULT_ENVIRONMENT_NAME);
    }

    private Conda testCondaInstallation() throws Exception {
        final SettingsModelString condaInfoMessage = m_condaEnvironmentConfig.getCondaInstallationInfo();
        final SettingsModelString condaErrorMessage = m_condaEnvironmentConfig.getCondaInstallationError();
        try {
            condaInfoMessage.setStringValue("Testing Conda installation...");
            condaErrorMessage.setStringValue("");
            onCondaInstallationTestStarting();
            final Conda conda = new Conda(m_condaEnvironmentConfig.getCondaDirectoryPath().getStringValue());
            String condaVersionString = conda.getVersionString();
            try {
                condaVersionString =
                    "Conda version: " + Conda.condaVersionStringToVersion(condaVersionString).toString();
            } catch (final IllegalArgumentException ex) {
                // Ignore and use raw version string.
            }
            condaInfoMessage.setStringValue(condaVersionString);
            condaErrorMessage.setStringValue("");
            m_pythonEnvironmentCreator.getIsEnvironmentCreationEnabled().setBooleanValue(true);
            onCondaInstallationTestFinished("");
            return conda;
        } catch (final Exception ex) {
            condaInfoMessage.setStringValue("");
            condaErrorMessage.setStringValue(ex.getMessage());
            clearAvailableCondaEnvironments();
            setCondaEnvironmentStatusMessages("", "");
            m_pythonEnvironmentCreator.getIsEnvironmentCreationEnabled().setBooleanValue(false);
            onCondaInstallationTestFinished(ex.getMessage());
            throw ex;
        }
    }

    private void clearAvailableCondaEnvironments() {
        final String placeholderEnvironmentName = DLCondaEnvironmentConfig.DEFAULT_ENVIRONMENT_NAME;
        m_condaEnvironmentConfig.getEnvironmentName().setStringValue(placeholderEnvironmentName);
        m_condaEnvironmentConfig.getAvailableEnvironmentNames()
            .setStringArrayValue(new String[]{placeholderEnvironmentName});
    }

    private void setCondaEnvironmentStatusMessages(final String infoMessage, final String errorMessage) {
        m_condaEnvironmentConfig.getPythonInstallationInfo().setStringValue(infoMessage);
        m_condaEnvironmentConfig.getPythonInstallationError().setStringValue(errorMessage);
    }

    private List<String> getAvailableCondaEnvironments(final Conda conda, final boolean updatePythonStatusMessage)
        throws Exception {
        try {
            if (updatePythonStatusMessage) {
                setCondaEnvironmentStatusMessages("Collecting available environments...", "");
            }
            return conda.getEnvironments();
        } catch (final Exception ex) {
            m_condaEnvironmentConfig.getCondaInstallationError().setStringValue(ex.getMessage());
            final String environmentsNotDetectedMessage = "Available environments could not be detected.";
            clearAvailableCondaEnvironments();
            setCondaEnvironmentStatusMessages("", environmentsNotDetectedMessage);
            throw ex;
        }
    }

    private void setAvailableCondaEnvironments(List<String> availableEnvironments) {
        if (availableEnvironments.isEmpty()) {
            availableEnvironments = Arrays.asList(DLCondaEnvironmentConfig.DEFAULT_ENVIRONMENT_NAME);
        }
        m_condaEnvironmentConfig.getAvailableEnvironmentNames()
            .setStringArrayValue(availableEnvironments.toArray(new String[0]));
        if (!availableEnvironments.contains(m_condaEnvironmentConfig.getEnvironmentName().getStringValue())) {
            m_condaEnvironmentConfig.getEnvironmentName().setStringValue(availableEnvironments.get(0));
        }
    }

}

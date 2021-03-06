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
package org.knime.dl.keras.core.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.knime.core.util.FileUtil;
import org.knime.dl.core.export.DLAbstractNetworkExporter;
import org.knime.dl.keras.core.DLKerasNetwork;

/**
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 */
public class DLKerasH5NetworkExporter extends DLAbstractNetworkExporter<DLKerasNetwork> {

    private static final String NAME = "Keras H5 Exporter";

    private static final String[] VALID_EXTENSIONS = {"h5"};

    /**
     * Creates a new Keras h5 exporter.
     */
    public DLKerasH5NetworkExporter() {
        super(DLKerasNetwork.class, NAME, VALID_EXTENSIONS);
    }

    @Override
    public void exportNetwork(final DLKerasNetwork network, final URL path, final boolean overwrite)
        throws IOException {
        // TODO make more general for remote destination files
        final File dest = FileUtil.getFileFromURL(path);
        if (dest.exists() && !overwrite) {
            throw new IOException("The destination file already exists.");
        }
        try (final InputStream sourceStream = FileUtil.openStreamWithTimeout(network.getSource().getURI().toURL());
                final OutputStream destStream = new FileOutputStream(dest)) {
            FileUtil.copy(sourceStream, destStream);
        }
    }

}

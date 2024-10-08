// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.Test;
import org.openstreetmap.gui.jmapviewer.FeatureAdapter;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import org.openstreetmap.josm.gui.layer.WMSLayer;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.OsmApi;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Unit tests for class {@link AddImageryLayerAction}.
 */
@BasicPreferences
@BasicWiremock
@OsmApi(OsmApi.APIType.FAKE)
@Projection
final class AddImageryLayerActionTest {
    /**
     * Unit test of {@link AddImageryLayerAction#updateEnabledState}.
     */
    @Test
    void testEnabledState() {
        assertTrue(new AddImageryLayerAction(new ImageryInfo("foo")).isEnabled());
        assertTrue(new AddImageryLayerAction(new ImageryInfo("foo_tms", "http://bar", "tms", null, null)).isEnabled());
        assertTrue(new AddImageryLayerAction(new ImageryInfo("foo_bing", "http://bar", "bing", null, null)).isEnabled());
        assertTrue(new AddImageryLayerAction(new ImageryInfo("foo_scanex", "http://bar", "scanex", null, null)).isEnabled());
        assertTrue(new AddImageryLayerAction(new ImageryInfo("foo_wms_endpoint", "http://bar", "wms_endpoint", null, null)).isEnabled());
    }

    /**
     * Unit test of {@link AddImageryLayerAction#actionPerformed} - Enabled cases for TMS.
     */
    @Test
    void testActionPerformedEnabledTms() {
        assertTrue(MainApplication.getLayerManager().getLayersOfType(TMSLayer.class).isEmpty());
        new AddImageryLayerAction(new ImageryInfo("foo_tms", "http://bar", "tms", null, null)).actionPerformed(null);
        List<TMSLayer> tmsLayers = MainApplication.getLayerManager().getLayersOfType(TMSLayer.class);
        assertEquals(1, tmsLayers.size());
        MainApplication.getLayerManager().removeLayer(tmsLayers.get(0));
    }

    /**
     * Unit test of {@link AddImageryLayerAction#actionPerformed} - Enabled cases for WMS.
     */
    @Test
    void testActionPerformedEnabledWms(WireMockRuntimeInfo wireMockRuntimeInfo) {
        wireMockRuntimeInfo.getWireMock().register(get(urlEqualTo("/wms?apikey=random_key&SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.1.1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBodyFile("imagery/wms-capabilities.xml")));
        wireMockRuntimeInfo.getWireMock().register(get(urlEqualTo("/wms?apikey=random_key&SERVICE=WMS&REQUEST=GetCapabilities"))
                .willReturn(aResponse()
                        .withStatus(404)));
        wireMockRuntimeInfo.getWireMock().register(get(urlEqualTo("/wms?apikey=random_key&SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0"))
                .willReturn(aResponse()
                        .withStatus(404)));

        try {
            FeatureAdapter.registerApiKeyAdapter(id -> "random_key");
            final ImageryInfo imageryInfo = new ImageryInfo("localhost", wireMockRuntimeInfo.getHttpBaseUrl() + "/wms?apikey={apikey}",
                    "wms_endpoint", null, null);
            imageryInfo.setId("testActionPerformedEnabledWms");
            new AddImageryLayerAction(imageryInfo).actionPerformed(null);
            List<WMSLayer> wmsLayers = MainApplication.getLayerManager().getLayersOfType(WMSLayer.class);
            assertEquals(1, wmsLayers.size());

            MainApplication.getLayerManager().removeLayer(wmsLayers.get(0));
        } finally {
            FeatureAdapter.registerApiKeyAdapter(new FeatureAdapter.DefaultApiKeyAdapter());
        }
    }

    /**
     * Unit test of {@link AddImageryLayerAction#actionPerformed} - disabled case.
     */
    @Test
    void testActionPerformedDisabled() {
        assertTrue(MainApplication.getLayerManager().getLayersOfType(TMSLayer.class).isEmpty());
        try {
            new AddImageryLayerAction(new ImageryInfo("foo")).actionPerformed(null);
        } catch (IllegalArgumentException expected) {
            assertEquals("Parameter 'info.url' must not be null", expected.getMessage());
        }
        assertTrue(MainApplication.getLayerManager().getLayersOfType(TMSLayer.class).isEmpty());
    }
}

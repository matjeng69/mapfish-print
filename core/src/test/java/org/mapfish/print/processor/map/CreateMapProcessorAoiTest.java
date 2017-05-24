package org.mapfish.print.processor.map;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.io.Files;

import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinTask;
import org.apache.batik.transcoder.TranscoderException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mapfish.print.AbstractMapfishSpringTest;
import org.mapfish.print.TestHttpClientFactory;
import org.mapfish.print.attribute.map.AreaOfInterest;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.ConfigurationFactory;
import org.mapfish.print.config.Template;
import org.mapfish.print.output.Values;
import org.mapfish.print.parser.MapfishParser;
import org.mapfish.print.test.util.ImageSimilarity;
import org.mapfish.print.wrapper.json.PJsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mapfish.print.attribute.map.AreaOfInterest.AoiDisplay.RENDER;

/**
 * Basic test of the Map processor.
 * <p></p>
 * Created by Jesse on 3/26/14.
 */
public class CreateMapProcessorAoiTest extends AbstractMapfishSpringTest {
    public static final String BASE_DIR = "center_wms1_0_0_flexiblescale_area_of_interest/";

    @Autowired
    private ConfigurationFactory configurationFactory;
    @Autowired
    private TestHttpClientFactory requestFactory;
    @Autowired
    private MapfishParser parser;
    @Autowired
    private ForkJoinPool forkJoinPool;

    @Test
    @DirtiesContext
    public void testExecute() throws Exception {
        final String host = "center_wms1_0_0_flexiblescale";
        requestFactory.registerHandler(
                new Predicate<URI>() {
                    @Override
                    public boolean apply(URI input) {
                        return (("" + input.getHost()).contains(host + ".wms")) || input.getAuthority().contains(host + ".wms");
                    }
                }, new TestHttpClientFactory.Handler() {
                    @Override
                    public MockClientHttpRequest handleRequest(URI uri, HttpMethod httpMethod) throws Exception {
                        try {
                            byte[] bytes = Files.toByteArray(getFile("/map-data/zoomed-in-ny-tiger.tif"));
                            return ok(uri, bytes, httpMethod);
                        } catch (AssertionError e) {
                            return error404(uri, httpMethod);
                        }
                    }
                }
        );
        requestFactory.registerHandler(
                new Predicate<URI>() {
                    @Override
                    public boolean apply(URI input) {
                        return (("" + input.getHost()).contains(host + ".json")) || input.getAuthority().contains(host + ".json");
                    }
                }, new TestHttpClientFactory.Handler() {
                    @Override
                    public MockClientHttpRequest handleRequest(URI uri, HttpMethod httpMethod) throws Exception {
                        try {
                            byte[] bytes = Files.toByteArray(getFile("/map-data" + uri.getPath()));
                            return ok(uri, bytes, httpMethod);
                        } catch (AssertionError e) {
                            return error404(uri, httpMethod);
                        }
                    }
                }
        );
        final Configuration config = configurationFactory.getConfig(getFile(BASE_DIR + "config.yaml"));
        final Template template = config.getTemplate("main");

        createMap(template, "expectedSimpleImage-default.png", RENDER, null, false, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-default.png", RENDER, null, false, true, null);
        createMap(template, "expectedSimpleImage-render-thinline.png", RENDER, "file://thinline.sld", false, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-render-thinline.png", RENDER, "file://thinline.sld", false, true, null);
        createMap(template, "expectedSimpleImage-render-jsonStyle.png", RENDER, createJsonStyle().toString(), false, null);
        createMap(template, "expectedSimpleImage-render-polygon.png", RENDER, "polygon",
                false, null);
        createMap(template, "expectedSimpleImage-none.png", AreaOfInterest.AoiDisplay.NONE, null, false, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-none.png", AreaOfInterest.AoiDisplay.NONE, null, false, true, null);
        createMap(template, "expectedSimpleImage-clip.png", AreaOfInterest.AoiDisplay.CLIP, null, false, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-clip.png", AreaOfInterest.AoiDisplay.CLIP, null, false, true, null);

        // Test when SVG is used for vector layers
        createMap(template, "expectedSimpleImage-render-polygon-svg.png", RENDER, createJsonStyle().toString(), true, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-render-polygon-svg.png", RENDER, createJsonStyle().toString(), true, true, null);
        createMap(template, "expectedSimpleImage-clip-svg.png", AreaOfInterest.AoiDisplay.CLIP, null, true, null);
        /* jpeg */ createMap(template, "expectedSimpleImage-clip-svg.png", AreaOfInterest.AoiDisplay.CLIP, null, true, true, null);
        Function<PJsonObject, Void> setRotationUpdater = new Function<PJsonObject, Void>() {

            @Nullable
            @Override
            public Void apply(@Nonnull PJsonObject input) {
                try {
                    getMapAttributes(input).getInternalObj().put("rotation", 90);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        };
        createMap(template, "expectedSimpleImage-rotate-clip-svg.png", AreaOfInterest.AoiDisplay.CLIP, null, true, setRotationUpdater);
        createMap(template, "expectedSimpleImage-rotate-render-svg.png", AreaOfInterest.AoiDisplay.RENDER, null, true, setRotationUpdater);
    }

    private JSONObject createJsonStyle() throws JSONException {
        JSONObject jsonStyle = new JSONObject();
        jsonStyle.put("version", "2");
        final JSONObject polygonSymb = new JSONObject();
        polygonSymb.put("type", "polygon");
        polygonSymb.put("fillColor", "green");
        polygonSymb.put("fillOpacity", ".8");
        polygonSymb.put("strokeColor", "black");
        JSONArray symbs = new JSONArray();
        symbs.put(polygonSymb);

        JSONObject rule = new JSONObject();
        rule.put("symbolizers", symbs);
        jsonStyle.put("*", rule);
        return jsonStyle;
    }

    private void createMap(Template template, String expectedImageName, AreaOfInterest.AoiDisplay
            aoiDisplay, String styleRef, boolean useSVG, Function<PJsonObject, Void> requestUpdater) throws
            IOException, JSONException, TranscoderException, ExecutionException, InterruptedException {
        createMap(template, expectedImageName, aoiDisplay, styleRef, useSVG, false, requestUpdater);
    }

    private void createMap(Template template, String expectedImageName, AreaOfInterest.AoiDisplay
            aoiDisplay, String styleRef, boolean useSVG, boolean useJPEG, Function<PJsonObject, Void>
            requestUpdater) throws IOException, JSONException, TranscoderException, ExecutionException,
            InterruptedException {
        PJsonObject requestData = loadJsonRequestData();
        final PJsonObject mapAttribute = getMapAttributes(requestData);
        mapAttribute.getJSONArray("layers").getJSONObject(0).getInternalObj().put("renderAsSvg", useSVG);
        if (useJPEG) {
            mapAttribute.getJSONArray("layers").getJSONObject(1).getInternalObj().put("imageFormat", "jpeg");
        }

        final PJsonObject areaOfInterest = mapAttribute.getJSONObject("areaOfInterest");
        areaOfInterest.getInternalObj().put("display", aoiDisplay.name().toLowerCase()); // doesn't have to be lowercase,
        // this is to make things more interesting
        areaOfInterest.getInternalObj().put("style", styleRef);

        if (requestUpdater != null) {
            requestUpdater.apply(requestData);
        }

        Values values = new Values(requestData, template, this.parser, getTaskDirectory(), this.requestFactory, new File("."));

        final ForkJoinTask<Values> taskFuture = this.forkJoinPool.submit(
                template.getProcessorGraph().createTask(values));
        taskFuture.get();

        @SuppressWarnings("unchecked")
        List<URI> layerGraphics = (List<URI>) values.getObject("layerGraphics", List.class);
        int expectedNumberOfLayers = useSVG ? (aoiDisplay == RENDER ? 3 : 2) : (useJPEG ? 2 : 1);
        assertEquals(expectedNumberOfLayers, layerGraphics.size());
        if (useJPEG) {
            assertTrue(layerGraphics.get(0).getPath().endsWith(".jpeg"));
        }

        final BufferedImage actualImage = ImageSimilarity.mergeImages(layerGraphics, 630, 294);
        File expectedImage = getFile(BASE_DIR + "/output/" + expectedImageName);
        new ImageSimilarity(actualImage).assertSimilarity(expectedImage, 55);

    }


    private PJsonObject getMapAttributes(PJsonObject requestData) {
        return requestData.getJSONObject("attributes").getJSONObject("map");
    }

    private static PJsonObject loadJsonRequestData() throws IOException {
        return parseJSONObjectFromFile(CreateMapProcessorAoiTest.class, BASE_DIR + "requestData.json");
    }
}

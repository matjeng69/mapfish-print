package org.mapfish.print.processor.map;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinTask;
import org.junit.Test;
import org.mapfish.print.AbstractMapfishSpringTest;
import org.mapfish.print.TestHttpClientFactory;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.ConfigurationFactory;
import org.mapfish.print.config.Template;
import org.mapfish.print.output.Values;
import org.mapfish.print.parser.MapfishParser;
import org.mapfish.print.test.util.ImageSimilarity;
import org.mapfish.print.wrapper.json.PJsonObject;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Basic test of the Map processor.
 * <p></p>
 * Created by Jesse on 3/26/14.
 */
public class CreateMapProcessorCenterGeoJsonZoomToTest extends AbstractMapfishSpringTest {
    private static final String BASE_DIR ="center_geojson_zoomTo/";

    @Autowired
    private ConfigurationFactory configurationFactory;
    @Autowired
    private MapfishParser parser;
    @Autowired
    private TestHttpClientFactory httpRequestFactory;
    @Autowired
    private ForkJoinPool forkJoinPool;

    @Test
    public void testExecute() throws Exception {
        final Configuration config = configurationFactory.getConfig(getFile(BASE_DIR + "config.yaml"));
        final Template template = config.getTemplate("main");
        PJsonObject requestData = loadJsonRequestData();
        Values values = new Values(requestData, template, this.parser, getTaskDirectory(), this.httpRequestFactory, new File("."));

        final ForkJoinTask<Values> taskFuture = this.forkJoinPool.submit(
                template.getProcessorGraph().createTask(values));
        taskFuture.get();

        @SuppressWarnings("unchecked")
        List<URI> layerGraphics = (List<URI>) values.getObject("layerGraphics", List.class);
        assertEquals(1, layerGraphics.size());

        final BufferedImage referenceImage = ImageSimilarity.convertFromSvg(layerGraphics.get(0), 500, 400);
        new ImageSimilarity(referenceImage).assertSimilarity(getFile(BASE_DIR + "expectedSimpleImage.tiff"), 0);
    }

    public static PJsonObject loadJsonRequestData() throws IOException {
        return parseJSONObjectFromFile(CreateMapProcessorCenterGeoJsonZoomToTest.class, BASE_DIR + "requestData.json");
    }
}

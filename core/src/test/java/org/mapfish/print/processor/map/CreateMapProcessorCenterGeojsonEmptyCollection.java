package org.mapfish.print.processor.map;

import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinTask;
import org.json.JSONException;
import org.junit.Test;
import org.mapfish.print.AbstractMapfishSpringTest;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.ConfigurationFactory;
import org.mapfish.print.config.Template;
import org.mapfish.print.http.MfClientHttpRequestFactoryImpl;
import org.mapfish.print.output.Values;
import org.mapfish.print.parser.MapfishParser;
import org.mapfish.print.test.util.ImageSimilarity;
import org.mapfish.print.wrapper.json.PJsonObject;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;

import static org.junit.Assert.assertEquals;

/**
 * Tests a GeoJSON layer with an empty FeatureCollection.
 */
public class CreateMapProcessorCenterGeojsonEmptyCollection extends AbstractMapfishSpringTest {
    public static final String BASE_DIR ="center_geojson_empty_collection/";

    @Autowired
    private ConfigurationFactory configurationFactory;
    @Autowired
    private MapfishParser parser;
    @Autowired
    private MfClientHttpRequestFactoryImpl httpRequestFactory;
    @Autowired
    private ForkJoinPool forkJoinPool;

    @Test
    public void testExecute() throws Exception {
        PJsonObject requestData = loadJsonRequestData();
        doTest(requestData);
    }

    private void doTest(PJsonObject requestData) throws IOException, JSONException, ExecutionException,
            InterruptedException {
        final Configuration config = configurationFactory.getConfig(getFile(BASE_DIR + "config.yaml"));
        final Template template = config.getTemplate("main");
        Values values = new Values(requestData, template, parser, getTaskDirectory(), this.httpRequestFactory, new File("."));

        final ForkJoinTask<Values> taskFuture = this.forkJoinPool.submit(
                template.getProcessorGraph().createTask(values));
        taskFuture.get();

        @SuppressWarnings("unchecked")
        List<URI> layerGraphics = (List<URI>) values.getObject("layerGraphics", List.class);
        assertEquals(1, layerGraphics.size());

        final BufferedImage img = ImageIO.read(new File(layerGraphics.get(0)));
//        ImageIO.write(img, "tiff", new File("/tmp/expectedSimpleImage.tiff"));
        new ImageSimilarity(img, 2).assertSimilarity(getFile(BASE_DIR + "expectedSimpleImage.tiff"), 30);
    }

    public static PJsonObject loadJsonRequestData() throws IOException {
        return parseJSONObjectFromFile(CreateMapProcessorCenterGeojsonEmptyCollection.class, BASE_DIR + "requestData.json");
    }
}

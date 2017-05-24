package org.mapfish.print.processor.map;

import com.google.common.base.Predicate;
import com.google.common.io.Files;

import java.awt.image.BufferedImage;
import java.io.File;

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
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;

import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;

import static org.junit.Assert.assertEquals;

import java.util.List;

/**
 * Test of the CreateOverviewMap processor with a custom style for the bbox rectangle.
 */
public class CreateOverviewMapProcessorStyleTest extends AbstractMapfishSpringTest {
    public static final String BASE_DIR = "overview_map_style/";

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
        final String host = "overview_map_style";
        requestFactory.registerHandler(
                new Predicate<URI>() {
                    @Override
                    public boolean apply(URI input) {
                        return (("" + input.getHost()).contains(host + ".osm")) || input.getAuthority().contains(host + ".osm");
                    }
                }, new TestHttpClientFactory.Handler() {
                    @Override
                    public MockClientHttpRequest handleRequest(URI uri, HttpMethod httpMethod) throws Exception {
                        try {
                            byte[] bytes = Files.toByteArray(getFile("/map-data/osm" + uri.getPath()));
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
        PJsonObject requestData = loadJsonRequestData();
        Values values = new Values(requestData, template, this.parser, getTaskDirectory(), this.requestFactory, new File("."));

        final ForkJoinTask<Values> taskFuture = this.forkJoinPool.submit(
                template.getProcessorGraph().createTask(values));
        taskFuture.get();

        @SuppressWarnings("unchecked")
        List<URI> layerGraphics = (List<URI>) values.getObject("overviewMapLayerGraphics", List.class);
        assertEquals(2, layerGraphics.size());

//        Files.copy(new File(layerGraphics.get(0)), new File("/tmp/0_ov_"+getClass().getSimpleName()+".tiff"));
//        Files.copy(new File(layerGraphics.get(1)), new File("/tmp/1_ov_"+getClass().getSimpleName()+".tiff"));
//        Files.copy(new File(layerGraphics.get(2)), new File("/tmp/2_ov_"+getClass().getSimpleName()+".tiff"));

        final BufferedImage referenceImage = ImageSimilarity.mergeImages(layerGraphics, 300, 200);
        new ImageSimilarity(referenceImage)
                .assertSimilarity(getFile(BASE_DIR + "expectedSimpleImage.png"), 50);
    }

    private static PJsonObject loadJsonRequestData() throws IOException {
        return parseJSONObjectFromFile(CreateOverviewMapProcessorStyleTest.class, BASE_DIR + "requestData.json");
    }
}

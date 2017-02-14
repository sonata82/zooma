package uk.ac.ebi.spot.zooma;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import uk.ac.ebi.spot.zooma.controller.AnnotationController;
import uk.ac.ebi.spot.zooma.model.*;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class ZoomaLoadAnnotationsApplicationTests {

	private MockMvc mockMvc;


	private MediaType contentType = MediaType.APPLICATION_JSON;

	private MockRestServiceServer mockServer;

	private Annotation annotation = new Annotation();

	@Before
	public void setup() throws IOException {

		final AnnotationController controller = new AnnotationController();
		final RestTemplate restTemplate = new RestTemplate();
		this.mockServer = MockRestServiceServer.createServer(restTemplate);

		ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);

		setAnnotationTemplate();

		this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	public void createMongoAnnotation() throws Exception {
		setPostResponse();
		mockMvc.perform(post("/annotations").contentType(contentType).content(this.annotation.toString()))
				.andExpect(status().isCreated());
	}

	@Test
	public void getMongoAnnotations() throws Exception{
		setGetResponse();
		mockMvc.perform(get("/annotations"))
				.andExpect(status().isOk());
	}

	private void setPostResponse() throws IOException{
		HttpHeaders mockHeader = new HttpHeaders();
		mockHeader.setLocation(URI.create("URI"));
		this.mockServer.expect(requestTo("http://localhost:8080/annotations"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess().headers(mockHeader));
	}

	private void setGetResponse() throws IOException {
		this.mockServer.expect(ExpectedCount.manyTimes(), requestTo("http://localhost:8080/annotations"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"_embedded\": {\n" +
						"\"annotations\": [\n" +
						 annotation.toString() +
						"]}}", MediaType.APPLICATION_JSON));
	}

	private void setAnnotationTemplate(){
		BiologicalEntity be = new BiologicalEntity("GSE10927GSM277147", new Study("E-GEOD-10927"));
		DatabaseAnnotationSource source = new DatabaseAnnotationSource("www.ebi.ac.uk/sysmicro", "atlas", "Phenotypes");

		AnnotationProvenance
                provenance = new AnnotationProvenance(source, uk.ac.ebi.spot.zooma.model.api.AnnotationProvenance.Evidence.MANUAL_CURATED,
                                                      uk.ac.ebi.spot.zooma.model.api.AnnotationProvenance.Accuracy.PRECISE,
                                                      "generator",
                                                      "annotator",
                                                      LocalDateTime.now());

		this.annotation.setBiologicalEntities(be);
		this.annotation.setProvenance(provenance);
		Collection st = new ArrayList();
		st.add("http://www.ebi.ac.uk/efo/EFO_0001658");
		this.annotation.setSemanticTag(st);
		TypedProperty property = new TypedProperty("biopsy site", "left");
		this.annotation.setProperty(property);
		this.annotation.setBatchLoad(false);
	}

}
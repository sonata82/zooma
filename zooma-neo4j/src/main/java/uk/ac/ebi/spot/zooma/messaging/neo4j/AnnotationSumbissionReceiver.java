package uk.ac.ebi.spot.zooma.messaging.neo4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.spot.zooma.model.neo4j.*;
import uk.ac.ebi.spot.zooma.service.neo4j.AnnotationService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by olgavrou on 24/02/2017.
 */
@Component
public class AnnotationSumbissionReceiver {

    @Autowired
    AnnotationService annotationService;

    @Autowired
    ObjectMapper objectMapper;


    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    @RabbitListener(queues = "annotation.save.neo.queue")
    public void handleAnnotationSubmission(Message message) throws IOException {

        //read the message byte stream and convert to a HashMap
        Map<String, Object> propertiesMap = objectMapper.readValue(message.getBody(), new TypeReference<HashMap<String,Object>>() {});

        Collection<SemanticTag> semanticTags = new ArrayList();
        ArrayList<String> smTags = (ArrayList<String>) propertiesMap.get("semanticTag");
        for (String st: smTags){
            SemanticTag semanticTag = new SemanticTag();
            semanticTag.setSemanticTag(st);
            semanticTags.add(semanticTag);
        }

        Study study = new Study();
        study.setStudy((String) propertiesMap.get("study"));

        BiologicalEntity biologicalEntity = new BiologicalEntity();
        biologicalEntity.setBioEntity((String) propertiesMap.get("bioEntity"));
        biologicalEntity.setStudy(study);

        Property property = new Property();
        property.setPropertyType((String) propertiesMap.get("propertyType"));
        property.setPropertyValue((String) propertiesMap.get("propertyValue"));
        property.setBiologicalEntity(biologicalEntity);

        Source source = new Source();
        source.setName((String) propertiesMap.get("sourceName"));
        source.setTopic((String) propertiesMap.get("sourceTopic"));
        source.setType((String) propertiesMap.get("sourceType"));
        source.setUri((String) propertiesMap.get("sourceUri"));

        AnnotationProvenance provenance = new AnnotationProvenance();
        provenance.setAccuracy((String) propertiesMap.get("accuracy"));
        provenance.setEvidence((String) propertiesMap.get("evidence"));
        provenance.setAnnotator((String) propertiesMap.get("annotator"));
        provenance.setAnnotatedDate((String) propertiesMap.get("annotatedDate"));
        provenance.setGeneratedDate((String) propertiesMap.get("generatedDate"));
        provenance.setGenerator((String) propertiesMap.get("generator"));
        provenance.setSource(source);

        Annotation neoAnnotation = new Annotation();
        neoAnnotation.setBiologicalEntity(biologicalEntity);
        neoAnnotation.setProperty(property);
        neoAnnotation.setSemanticTag(semanticTags);
        neoAnnotation.setProvenance(provenance);
        neoAnnotation.setQuality((float) ((double)propertiesMap.get("quality")));
        neoAnnotation.setMongoId((String) propertiesMap.get("id"));

        annotationService.save(neoAnnotation);

        getLog().info("Neo4j Queue: We have saved the annotation into Neo4j! " + propertiesMap.get("id"));
    }
}
package uk.ac.ebi.spot.zooma.model.mongo;

import lombok.Data;
import lombok.NonNull;
import org.springframework.data.annotation.Id;
import uk.ac.ebi.spot.zooma.model.api.AnnotationSource;

/**
 * Created by olgavrou on 05/08/2016.
 */
@Data
public class OntologyAnnotationSource implements AnnotationSource {

    @Id
    String id;
    @NonNull
    private String uri;
    @NonNull
    private String name;

    private final Type type = Type.ONTOLOGY;
    @NonNull
    private String topic;

}

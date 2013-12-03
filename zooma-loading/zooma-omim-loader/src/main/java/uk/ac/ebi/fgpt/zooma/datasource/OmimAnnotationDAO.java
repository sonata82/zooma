package uk.ac.ebi.fgpt.zooma.datasource;

import org.springframework.core.io.Resource;
import uk.ac.ebi.fgpt.zooma.Initializable;
import uk.ac.ebi.fgpt.zooma.exception.NoSuchResourceException;
import uk.ac.ebi.fgpt.zooma.exception.ResourceAlreadyExistsException;
import uk.ac.ebi.fgpt.zooma.model.Annotation;
import uk.ac.ebi.fgpt.zooma.model.BiologicalEntity;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.model.Study;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 02/12/13
 */
public class OmimAnnotationDAO extends Initializable implements AnnotationDAO {
    private final AnnotationFactory annotationFactory;

    private Resource omimResource;
    private boolean isGzipped = false;

    private boolean resourceSupportsMark;

    private OmimPhenotypeEntryReader reader;

    public OmimAnnotationDAO() {
        this(new OmimAnnotationFactory(new OmimLoadingSession()));
    }

    public OmimAnnotationDAO(AnnotationFactory annotationFactory) {
        this.annotationFactory = annotationFactory;
    }

    public Resource getOmimResource() {
        return omimResource;
    }

    public void setOmimResource(Resource omimResource) {
        this.omimResource = omimResource;
    }

    public boolean isGzipped() {
        return isGzipped;
    }

    public void setGzipped(boolean gzipped) {
        isGzipped = gzipped;
    }

    @Override public Collection<Annotation> readByStudy(Study study) {
        throw new UnsupportedOperationException("Cannot query OMIM by study");
    }

    @Override public Collection<Annotation> readByBiologicalEntity(BiologicalEntity biologicalEntity) {
        throw new UnsupportedOperationException("Cannot query OMIM by biological entity");
    }

    @Override public Collection<Annotation> readByProperty(Property property) {
        throw new UnsupportedOperationException("Property lookup not yet implemented");
    }

    @Override public Collection<Annotation> readBySemanticTag(URI semanticTagURI) {
        throw new UnsupportedOperationException("Semantic tag lookup not yet implemented");
    }

    @Override public String getDatasourceName() {
        return "omim";
    }

    @Override public int count() {
        try {
            initOrWait();
        }
        catch (InterruptedException e) {
            getLog().warn("Interrupted whilst waiting for initialization");
        }

        try {
            return readEntries().size();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read annotations from OMIM resource", e);
        }
    }

    @Override public void create(Annotation identifiable) throws ResourceAlreadyExistsException {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a read-only annotation DAO, creation not supported");
    }

    @Override public Collection<Annotation> read() {
        try {
            initOrWait();
        }
        catch (InterruptedException e) {
            getLog().warn("Interrupted whilst waiting for initialization");
        }

        try {
            Collection<Annotation> results = new HashSet<>();
            for (OmimPhenotypeEntry entry : readEntries()) {
                results.addAll(convertEntry(entry));
            }
            return results;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read annotations from OMIM resource", e);
        }
    }

    @Override public List<Annotation> read(int size, int start) {
        try {
            initOrWait();
        }
        catch (InterruptedException e) {
            getLog().warn("Interrupted whilst waiting for initialization");
        }

        try {
            List<Annotation> results = new ArrayList<>();
            List<OmimPhenotypeEntry> allEntries = readEntries();
            int end = start + size > allEntries.size() ? allEntries.size() : start + size;
            List<OmimPhenotypeEntry> entries = allEntries.subList(start, end);
            for (OmimPhenotypeEntry entry : entries) {
                results.addAll(convertEntry(entry));
            }
            return results;
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to read annotations from OMIM resource", e);
        }
    }

    @Override public Annotation read(URI uri) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support URI based lookups");
    }

    @Override public void update(Annotation object) throws NoSuchResourceException {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a read-only annotation DAO, updates not supported");
    }

    @Override public void delete(Annotation object) throws NoSuchResourceException {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a read-only annotation DAO, deletions not supported");
    }

    @Override protected void doInitialization() throws Exception {
        // create an OMIM entry reader from the omim resource
        this.resourceSupportsMark = omimResource.getInputStream().markSupported();
        resetReader();
    }

    @Override protected void doTermination() throws Exception {
        reader.close();
    }

    private void resetReader() throws IOException {
        boolean recreate = false;
        if (resourceSupportsMark) {
            if (reader == null) {
                reader = createReader();
                try {
                    reader.mark(-1);
                }
                catch (IOException e) {
                    getLog().error("Failed to mark OMIM stream with a read-ahead of -1; " +
                                           "marking will be disabled (" + e.getMessage() + ")");
                    resourceSupportsMark = false;
                }
            }
            else {
                try {
                    reader.reset();
                }
                catch (IOException e) {
                    getLog().error("Failed to reset OMIM stream; reader will be recreated", e);
                    recreate = true;
                }
            }
        }
        else {
            recreate = true;
        }

        if (recreate) {
            try {
                if (reader != null) {
                    reader.close();
                }
            }
            catch (IOException e) {
                // ignore
                getLog().warn("Closing existing OMIMEntryReader failed; this may cause a memory leak", e);
            }
            reader = createReader();
        }
    }

    private OmimPhenotypeEntryReader createReader() throws IOException {
        InputStream in;
        if (isGzipped()) {
            in = new GZIPInputStream(omimResource.getInputStream());
        }
        else {
            in = omimResource.getInputStream();
        }
        return new OmimPhenotypeEntryReader(in);
    }

    private List<OmimPhenotypeEntry> readEntries() throws IOException {
        List<OmimPhenotypeEntry> entries = new ArrayList<>();
        OmimPhenotypeEntry entry;
        while ((entry = reader.readEntry()) != null) {
            entries.add(entry);
        }
        return entries;
    }

    private Collection<Annotation> convertEntry(OmimPhenotypeEntry entry) {
        Collection<Annotation> results = new HashSet<>();

        results.add(annotationFactory.createAnnotation(null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       null,
                                                       "OMIM phenotype",
                                                       entry.getPreferredTitle(),
                                                       null,
                                                       null,
                                                       convertToSemanticTag(entry.getOmimID()),
                                                       entry.getLastAnnotator(),
                                                       entry.getLastAnnotationDate()));
        for (String altTitle : entry.getAlternativeTitles()) {
            results.add(annotationFactory.createAnnotation(null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           null,
                                                           "OMIM phenotype",
                                                           altTitle,
                                                           null,
                                                           null,
                                                           convertToSemanticTag(entry.getOmimID()),
                                                           entry.getLastAnnotator(),
                                                           entry.getLastAnnotationDate()));
        }
        return results;
    }

    private URI convertToSemanticTag(String omimID) {
        return URI.create("http://omim.org/entry/" + omimID);
    }
}

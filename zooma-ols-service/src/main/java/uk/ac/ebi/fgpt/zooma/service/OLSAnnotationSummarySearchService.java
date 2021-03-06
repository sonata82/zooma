package uk.ac.ebi.fgpt.zooma.service;

import uk.ac.ebi.fgpt.zooma.Initializable;
import uk.ac.ebi.fgpt.zooma.exception.SearchResourcesUnavailableException;
import uk.ac.ebi.fgpt.zooma.model.*;
import uk.ac.ebi.fgpt.zooma.util.SearchStringProcessorProvider;
import uk.ac.ebi.pride.utilities.ols.web.service.model.Term;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * Implements an {@link AnnotationSummarySearchService} using the Ontology Lookup Service.
 * Each search calls the doSearch method that uses the {@link OLSSearchService} to
 * lookup the query in OLS. It then uses the {@link OLSAnnotationSummaryMapper} to convert
 * the {@link Term}s into  {@link AnnotationSummary}s.
 *
 *
 * Created by olgavrou on 19/05/2016.
 */
public class OLSAnnotationSummarySearchService extends Initializable implements AnnotationSummarySearchService  {

    private SearchStringProcessorProvider searchStringProcessorProvider;

    private OLSAnnotationSummaryMapper mapper;

    private OLSSearchService olsSearchService;

    public OLSAnnotationSummaryMapper getMapper() {
        return mapper;
    }

    public void setOlsSearchService(OLSSearchService olsSearchService) {
        this.olsSearchService = olsSearchService;
    }


    @Override
    public Collection<AnnotationSummary> search(String propertyValuePattern, URI[] sources, URI... ontologySources) {
        try {
            initOrWait();

            if (ontologySources != null && ontologySources.length > 0){
                return doSearch(getMapper(), propertyValuePattern, ontologySources);
            }
            return doSearch(getMapper(), propertyValuePattern);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }

    @Override
    public Collection<AnnotationSummary> search(String propertyType, String propertyValuePattern, URI[] sources, URI[] ontologySources) {
        try {
            initOrWait();
            if (ontologySources != null && ontologySources.length > 0){
                //search with provided sources
                return  doSearch(getMapper(), propertyType, propertyValuePattern, ontologySources);
            }
            return doSearch(getMapper(), propertyType, propertyValuePattern);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }


    @Override
    public Collection<AnnotationSummary> searchByPrefix(String propertyValuePrefix, URI[] sources, URI[] ontologySources) {
        try {
            initOrWait();
            return doSearch(getMapper(), propertyValuePrefix, ontologySources);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }

    @Override
    public Collection<AnnotationSummary> searchByPrefix(String propertyType, String propertyValuePrefix, URI[] sources, URI[] ontologySources) {
        try {
            initOrWait();
            return doSearch(getMapper(), propertyType, propertyValuePrefix, ontologySources);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }

    @Override
    public Collection<AnnotationSummary> searchBySemanticTags(String... semanticTagShortnames) {
        return new ArrayList<>(); ///TODO: what should be the implementation of this one?
    }

    @Override
    public Collection<AnnotationSummary> searchBySemanticTags(URI... semanticTags) {
        return new ArrayList<>(); ///TODO: what should be the implementation of this one?
    }

    @Override
    public Collection<AnnotationSummary> searchByPreferredSources(String propertyValuePattern, List<URI> preferredSources, URI[] requiredSources, URI[] ontologySources) {
        try {
            initOrWait();

            return this.search(propertyValuePattern, requiredSources, ontologySources);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }

    @Override
    public Collection<AnnotationSummary> searchByPreferredSources(String propertyType, String propertyValuePattern, List<URI> preferredSources, URI[] requiredSources, URI[] ontologySources) {
        try {
            initOrWait();

            return this.search( propertyType, propertyValuePattern, requiredSources, ontologySources);
        }
        catch (InterruptedException e) {
            throw new SearchResourcesUnavailableException("Failed to perform query - indexing process was interrupted",
                    e);
        }
    }

    @Override
    protected void doInitialization() throws Exception {
        this.mapper = new OLSAnnotationSummaryMapper();

    }

    @Override
    protected void doTermination() throws Exception {
    }

    /*
     *  Uses the provided olsSearchService to do a simple OLS Search, and returns the Term-s as AnnotationSummary-s
     */
    protected Collection<AnnotationSummary> doSearch(OLSAnnotationSummaryMapper mapper,
                                                     String propertyValuePattern) throws InterruptedException {

        Collection<AnnotationSummary> annotationSummaries = new ArrayList<>();
        List <Term> terms = new ArrayList<>();

        terms = olsSearchService.getTermsByName(propertyValuePattern);

       for (Term term : terms){
           annotationSummaries.add((AnnotationSummary) mapper.mapOLSTermToAnnotation(term));
       }

        return annotationSummaries;
    }

    /*
     *  Uses the provided olsSearchService to first query OLS for the given propertyType, and then
     *  use the found terms as parents when querying the propertyValuePattern in OLS.
     *  Returns the final Term-s as AnnotationSummary-s
     */
    protected Collection<AnnotationSummary> doSearch(OLSAnnotationSummaryMapper mapper, String propertyType, String propertyValuePattern) {
        Collection<AnnotationSummary> annotationSummaries = new ArrayList<>();
        List <Term> terms = new ArrayList<>();

        //get the parent uris from propertyType
        List<Term> parentTerms = olsSearchService.getTermsByName(propertyType);
        if (parentTerms != null && !parentTerms.isEmpty()) {
            StringBuilder childrenOf = new StringBuilder();
            for (Term parent : parentTerms) {
                if (!childrenOf.toString().contains(parent.getIri().getIdentifier())) {
                    childrenOf.append(parent.getIri().getIdentifier() + ",");
                }
            }
            terms = olsSearchService.getTermsByNameFromParent(propertyValuePattern, childrenOf.toString());

            for (Term term : terms) {
                annotationSummaries.add((AnnotationSummary) mapper.mapOLSTermToAnnotation(term));
            }
        }

        return annotationSummaries;
    }

    /******************* doSearch-es including the sources *****************************/
    /**********************************************************************************/


     /*
      *  Uses the provided olsSearchService to do a simple OLS Search. When given sources, pass them to OLS
      *  in order to do a ontology-specific search.
      *  Returns the Term-s as AnnotationSummary-s
      *
      */
    protected Collection<AnnotationSummary> doSearch(OLSAnnotationSummaryMapper mapper,
                                                     String propertyValuePattern,
                                                     URI... sources) throws InterruptedException {

        Collection<AnnotationSummary> annotationSummaries = new ArrayList<>();
        List <Term> terms = new ArrayList<>();

        if (sources != null && !(sources.length == 0) ){
            terms = olsSearchService.getTermsByName(propertyValuePattern, cleanSources(sources));
        }

        for (Term term : terms){
            annotationSummaries.add((AnnotationSummary) mapper.mapOLSTermToAnnotation(term));
        }

        return annotationSummaries;
    }

    /*
    *  Uses the provided olsSearchService to first query OLS for the given propertyType, and then
    *  use the found terms as parents when querying the propertyValuePattern in OLS.
    *  When given sources, pass them to OLS in order to do a ontology-specific search.
    *  Returns the final Term-s as AnnotationSummary-s
    */
    protected Collection<AnnotationSummary> doSearch(OLSAnnotationSummaryMapper mapper, String propertyType, String propertyValuePattern, URI[] sources) {
        Collection<AnnotationSummary> annotationSummaries = new ArrayList<>();
        List <Term> terms = new ArrayList<>();

        if (sources != null && !(sources.length == 0) ) {
            //clean the sources
            ArrayList<String> cleanSources = cleanSources(sources);
            //get the parent uris from propertyType
            List<Term> parentTerms = olsSearchService.getTermsByName(propertyType, cleanSources);
            if (parentTerms != null && !parentTerms.isEmpty()) {

                StringBuilder childrenOf = new StringBuilder();
                for (Term parent : parentTerms) {
                    if (!childrenOf.toString().contains(parent.getIri().getIdentifier())) {
                        childrenOf.append(parent.getIri().getIdentifier());
                    }
                }

                terms = olsSearchService.getTermsByNameFromParent(propertyValuePattern, cleanSources, childrenOf.toString());

                for (Term term : terms) {
                    annotationSummaries.add((AnnotationSummary) mapper.mapOLSTermToAnnotation(term));
                }
            }
        }

        return annotationSummaries;
    }

    /*
     * Used to clean a source from e.g.: "http://www.berkeleybop.org/ontologies/po/po.owl"
     * to "po", so to be added in the ols query as: ontology=po
     */
    private ArrayList<String> cleanSources(URI[] sources){

        ArrayList<String> cleanSources = new ArrayList<>();

        for (URI source : sources){
            String namespace = olsSearchService.getOntologyNamespaceFromId(source.toString());
            if (namespace != null) {
                cleanSources.add(namespace);
            }
        }

        return cleanSources;
    }

    }

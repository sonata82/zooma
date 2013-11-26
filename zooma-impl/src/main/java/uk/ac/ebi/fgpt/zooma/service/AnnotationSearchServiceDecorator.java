package uk.ac.ebi.fgpt.zooma.service;

import uk.ac.ebi.fgpt.zooma.model.Annotation;

import java.util.Collection;
import java.util.Map;

/**
 * An abstract decorator of a {@link AnnotationSearchService}.  You should subclass this decorator to create different
 * decorations that add functionality to annotation searches.
 *
 * @author Tony Burdett
 * @date 02/08/13
 * @see AnnotationSearchService
 */
public abstract class AnnotationSearchServiceDecorator implements AnnotationSearchService {
    private final AnnotationSearchService _annotationSearchService;

    protected AnnotationSearchServiceDecorator(AnnotationSearchService annotationSearchService) {
        this._annotationSearchService = annotationSearchService;
    }

    @Override public Collection<Annotation> search(String propertyValuePattern) {
        return _annotationSearchService.search(propertyValuePattern);
    }

    @Override public Collection<Annotation> search(String propertyType, String propertyValuePattern) {
        return _annotationSearchService.search(propertyType, propertyValuePattern);
    }

    @Override public Collection<Annotation> searchPrefix(String propertyValuePrefix) {
        return _annotationSearchService.searchPrefix(propertyValuePrefix);
    }

    @Override public Collection<Annotation> searchPrefix(String propertyType, String propertyValuePrefix) {
        return _annotationSearchService.searchPrefix(propertyType, propertyValuePrefix);
    }

    @Override public Map<Annotation, Float> searchAndScore(String propertyValuePattern) {
        return _annotationSearchService.searchAndScore(propertyValuePattern);
    }

    @Override public Map<Annotation, Float> searchAndScore(String propertyType, String propertyValuePattern) {
        return _annotationSearchService.searchAndScore(propertyType, propertyValuePattern);
    }

    @Override public Map<Annotation, Float> searchAndScoreByPrefix(String propertyValuePrefix) {
        return _annotationSearchService.searchAndScoreByPrefix(propertyValuePrefix);
    }

    @Override public Map<Annotation, Float> searchAndScoreByPrefix(String propertyType, String propertyValuePrefix) {
        return _annotationSearchService.searchAndScoreByPrefix(propertyType, propertyValuePrefix);
    }
}
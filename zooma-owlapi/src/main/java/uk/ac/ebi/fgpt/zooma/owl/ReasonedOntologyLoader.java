package uk.ac.ebi.fgpt.zooma.owl;

import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.ConsoleProgressMonitor;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import uk.ac.ebi.fgpt.zooma.util.URIUtils;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Loads an ontology using the OWLAPI and a HermiT reasoner to classify the ontology.  This allows for richer typing
 * information on each class to be provided
 *
 * @author Tony Burdett
 * @date 03/06/13
 */
public class ReasonedOntologyLoader extends AbstractOntologyLoader {
    protected void loadOntology() throws OWLOntologyCreationException {
        getLog().debug("Loading ontology...");
        OWLOntology ontology = getManager().loadOntology(IRI.create(getOntologyURI()));
        IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI();
        setOntologyIRI(ontologyIRI);
        getLog().debug("Successfully loaded ontology " + ontologyIRI);

        getLog().debug("Trying to create a reasoner over ontology '" + getOntologyURI() + "'");
        OWLReasonerFactory factory = new Reasoner.ReasonerFactory();
        ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
        OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
        OWLReasoner reasoner = factory.createReasoner(ontology, config);

        getLog().debug("Precomputing inferences...");
        reasoner.precomputeInferences();

        getLog().debug("Checking ontology consistency...");
        reasoner.isConsistent();

        getLog().debug("Checking for unsatisfiable classes...");
        if (reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().size() > 0) {
            throw new OWLOntologyCreationException(
                    "Once classified, unsatisfiable classes were detected in '" + ontologyIRI + "'");
        }
        else {
            getLog().debug("Reasoning complete! ");
        }

        Set<OWLClass> allClasses = ontology.getClassesInSignature();
        Set<URI> allKnownNamespaces = new HashSet<>();

        // remove excluded classes from allClasses by subclass
        if (getExclusionClassURI() != null) {
            OWLClass excludeClass = getFactory().getOWLClass(IRI.create(getExclusionClassURI()));
            Set<OWLClass> subclasses = reasoner.getSubClasses(excludeClass, false).getFlattened();
            for (OWLClass subclass : subclasses) {
                allClasses.remove(subclass);
            }
        }

        // remove excluded classes from allClasses by annotation property
        if (getExclusionAnnotationURI() != null) {
            OWLAnnotationProperty excludeAnnotation =
                    getFactory().getOWLAnnotationProperty(IRI.create(getExclusionAnnotationURI()));
            Iterator<OWLClass> allClassesIt = allClasses.iterator();
            while (allClassesIt.hasNext()) {
                OWLClass owlClass = allClassesIt.next();
                if (!owlClass.getAnnotations(ontology, excludeAnnotation).isEmpty()) {
                    allClassesIt.remove();
                }
            }
        }

        OWLAnnotationProperty rdfsLabel = getFactory().getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        OWLAnnotationProperty synonym = null;
        if (getSynonymURI() != null) {
            synonym = getFactory().getOWLAnnotationProperty(IRI.create(getSynonymURI()));
        }

        int labelCount = 0;
        int labelledClassCount = 0;
        int synonymCount = 0;
        int synonymedClassCount = 0;
        getLog().debug("Loading labels...");
        for (OWLClass ontologyClass : allClasses) {
            IRI clsIri = ontologyClass.getIRI();

            // get namespace for this IRI
            URI namespace = URIUtils.extractNamespace(clsIri.toURI());
            if (!URIUtils.isNamespaceKnown(namespace)) {
                getLog().warn(
                        "Namespace <" + namespace + "> (present in ontology " + ontologyIRI.toString() + ") " +
                                "is not known - you should register this namespace in zooma/prefix.properties " +
                                "to ensure ZOOMA can correctly shorten URIs in this namespace");
            }
            else {
                allKnownNamespaces.add(namespace);
            }

            // get label annotations
            Set<String> labels = getStringLiteralAnnotationValues(ontology, ontologyClass, rdfsLabel);
            String label = null;
            if (labels.isEmpty()) {
                getLog().warn("OWLClass " + ontologyClass + " contains no label. " +
                                      "No labels for this class will be loaded.");
            }
            else {
                if (labels.size() > 1) {
                    getLog().warn("OWLClass " + ontologyClass + " contains more than one label " +
                                          "(including '" + labels.iterator().next() + "'). " +
                                          "No labels for this class will be loaded.");
                }
                else {
                    label = labels.iterator().next();
                    addClassLabel(clsIri, label);
                    labelledClassCount++;
                    labelCount++;
                }
            }

            // get types
            getLog().debug("Loading types...");
            Set<String> ontologyTypeLabelSet = new HashSet<>();
            Set<OWLClass> parents = reasoner.getSuperClasses(ontologyClass, false).getFlattened();
            for (OWLClass parentClass : parents) {
                if (allClasses.contains(parentClass)) {
                    // only add type if the parent isn't excluded
                    getLog().debug("Next parent of " + label + ": " + parentClass);
                    Set<String> typeVals = getStringLiteralAnnotationValues(ontology, parentClass, rdfsLabel);
                    ontologyTypeLabelSet.addAll(typeVals);
                }
            }
            addClassTypes(clsIri, ontologyTypeLabelSet);

            // get all synonym annotations
            getLog().debug("Loading synonyms...");
            if (synonym != null) {
                Set<String> synonymVals = getStringLiteralAnnotationValues(ontology, ontologyClass, synonym);
                if (synonymVals.isEmpty()) {
                    getLog().trace("OWLClass " + ontologyClass + " contains no synonyms. " +
                                           "No synonyms for this class will be loaded.");
                }
                else {
                    addSynonyms(clsIri, synonymVals);
                    synonymCount += synonymVals.size();
                    synonymedClassCount++;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (URI namespace : allKnownNamespaces) {
            sb.append("\t").append(namespace.toString()).append("\n");
        }

        getLog().debug("Loaded classes with " + allKnownNamespaces.size() + " different namespaces " +
                               "from " + ontologyIRI.toString() + ". Those namespaces are...\n" + sb.toString());

        getLog().debug("Successfully loaded " + labelCount + " labels on " + labelledClassCount + " classes and " +
                               synonymCount + " synonyms on " + synonymedClassCount + " classes!");
    }
}
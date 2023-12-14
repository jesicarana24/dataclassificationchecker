// Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package com.amazon.checkerframework.checker.data_classification;

import com.amazon.checkerframework.checker.data_classification.qual.AnyConfidentiality;
import com.amazon.checkerframework.checker.data_classification.qual.Confidential;
import com.amazon.checkerframework.checker.data_classification.qual.Critical;
import com.amazon.checkerframework.checker.data_classification.qual.HighlyConfidential;
import com.amazon.checkerframework.checker.data_classification.qual.NonConfidential;
import com.amazon.checkerframework.checker.data_classification.qual.NonCritical;
import com.amazon.checkerframework.checker.data_classification.qual.NonHighlyConfidential;
import com.amazon.checkerframework.checker.data_classification.qual.NonRestricted;
import com.amazon.checkerframework.checker.data_classification.qual.PolyClassification;
import com.amazon.checkerframework.checker.data_classification.qual.Public;
import com.amazon.checkerframework.checker.data_classification.qual.Restricted;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.TypeSystemError;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.util.QualifierKind;
//import org.checkerframework.checker.nullness.qual;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.poly.QualifierPolymorphism;
//implementations for deprecated methods multigraph heirarchy 
import org.checkerframework.framework.type.MostlyNoElementQualifierHierarchy;
import org.checkerframework.framework.type.NoElementQualifierHierarchy;
import org.checkerframework.framework.type.ElementQualifierHierarchy;
//import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
//import org.checkerframework.framework.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.CollectionUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.framework.util.QualifierKind;

/**
 * An AnnotatedTypeFactory for DCC. It is responsible for aliasing annotations,
 * and for determining
 * the default qualifiers on classes from their members.
 */
public class DataClassificationAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    /** The canonical representations of the annotations supported by DCC. */
    private final AnnotationMirror critical = AnnotationBuilder.fromClass(elements, Critical.class),
            restricted = AnnotationBuilder.fromClass(elements, Restricted.class),
            highlyConfidential = AnnotationBuilder.fromClass(elements, HighlyConfidential.class),
            confidential = AnnotationBuilder.fromClass(elements, Confidential.class),
            publik = AnnotationBuilder.fromClass(elements, Public.class),
            poly = newPolyAnnotation(""),
            polyUse = newPolyAnnotation("use");

    /**
     * A boilerplate contructor. Follows the standard CF pattern. Also aliases
     * annotations.
     *
     * @param checker the type checker instatiating this ATF
     */
    public DataClassificationAnnotatedTypeFactory(final BaseTypeChecker checker) {
        super(checker);
        addAliasedTypeAnnotation(AnyConfidentiality.class, critical);
        addAliasedTypeAnnotation(NonCritical.class, restricted);
        addAliasedTypeAnnotation(NonRestricted.class, highlyConfidential);
        addAliasedTypeAnnotation(NonHighlyConfidential.class, confidential);
        addAliasedTypeAnnotation(NonConfidential.class, publik);
        this.postInit();
    }

    /**
     * Return the canonical version of the @Public annotation.
     *
     * <p>
     * Intended for use with AnnotatedTypeMirror#getAnnotationInHierarchy, to avoid
     * needing to
     * make the canonical fields above non-private.
     *
     * @return the canonical version of the @Public annotation
     */
    public AnnotationMirror getCanonicalPublicAnnotation() {
        return publik;
    }

    /** @return the canonical version of the @PolyClassification annotation. */
    public AnnotationMirror getPolyAnnotation() {
        return poly;
    }

    /**
     * @return the canonical version of the @PolyClassification("use") annotation.
     */
    public AnnotationMirror getPolyUseAnnotation() {
        return polyUse;
    }

    /**
     * Creates an AnnotationMirror for {@code @PolyClassification} with {@code arg}
     * as its value.
     *
     * @param arg the argument that will assigned to the value field of the created
     *            annotation
     * @return the created AnnotationMirror
     */
    private AnnotationMirror newPolyAnnotation(final String arg) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, PolyClassification.class);
        builder.setValue("value", arg);
        return builder.build();
    }

    @Override
    // protected QualifierHierarchy createQualifierPolymorphism() {
    // return new ClassificationPolymorphism(this.getSupportedTypeQualifiers(),
    // elements);
    // }

    protected QualifierPolymorphism createQualifierPolymorphism() {
        return new ClassificationPolymorphism(processingEnv, this);
    }

    /**
     * Need to explicitly state which qualifiers the checker actually supports
     * because the default
     * search procedure finds the aliases as well, and issues an error because the
     * aliases aren't
     * real annotations.
     */
    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return new LinkedHashSet<>(
                Arrays.asList(
                        Critical.class,
                        Restricted.class,
                        HighlyConfidential.class,
                        Confidential.class,
                        Public.class,
                        PolyClassification.class));
    }

    /**
     * The qualifier hierarchy has to be overridden so that poly with arguments can
     * have the right
     * subtyping relationship.
     */

    private class DataClassificationQualifierHierarchy extends MostlyNoElementQualifierHierarchy {
        // private final ProcessingEnvironment processingEnv;
        // protected final Map<QualifierKind, AnnotationMirror> kindToAnnotationMirror;
        // public SubtypeIsSubsetQualifierHierarchy(Collection<Class<? extends
        // Annotation>> qualifierClasses,ProcessingEnvironment
        // processingEnv,GenericAnnotatedTypeFactory<?, ?, ?, ?> atypeFactory) {
        // super(qualifierClasses, processingEnv.getElementUtils(), atypeFactory);
        // this.processingEnv = processingEnv;
        // }

        protected DataClassificationQualifierHierarchy(Collection<Class<? extends Annotation>> qualifierClasses,
                Elements elements) {
            super(qualifierClasses, elements, DataClassificationAnnotatedTypeFactory.this);
        }

        @Override
        public AnnotationMirror getPolymorphicAnnotation(AnnotationMirror start) {
            // Get the QualifierKind associated with the provided annotation
            QualifierKind polyKind = getQualifierKind(start).getPolymorphic();

            // Check if the QualifierKind has a polymorphic counterpart
            if (polyKind == null) {
                // If not, return null 
                return null;
            }

            // Retrieves the polymorphic annotation alignign to the QualifierKind kindtoelementlessqualifier is a method in elementqualifierhierarchy 
            AnnotationMirror poly = kindToElementlessQualifier.get(polyKind);

            // Check if the polymorphic annotation is not found
            if (poly == null) {
                // Throws an exception 
                throw new TypeSystemError(
                        "NEEDS TO GET FIXED ",
                        polyKind);
            }

            // Returns the found polymorphic annotation
            return poly;
        }
        // @Override

        // public AnnotationMirror getPolymorphicAnnotation(AnnotationMirror start) {
        // throw new BugInCF(
        // "GeneralQualifierHierarchy.getPolymorphicAnnotation() shouldn't be called.");
        // }


        // public @Nullable AnnotationMirror getPolymorphicAnnotation(AnnotationMirror
        // start) {
        // // Get the QualifierKind associated with the provided AnnotationMirror
        // QualifierKind startKind = getQualifierKind(start);
        // // Obtain the polymorphic kind from the retrieved QualifierKind
        // QualifierKind polymorphicKind = startKind.getPolymorphic();

        // // Check if there is no polymorphic kind
        // if (polymorphicKind == null) {
        // // If there is no polymorphic kind, return null
        // return null;
        // }

        // // Retrieve the polymorphic AnnotationMirror from the map
        // AnnotationMirror polymorphicAnnotation =
        // kindToElementlessQualifier.get(polymorphicKind);

        // // Check if the polymorphic annotation is not found
        // if (polymorphicAnnotation == null) {
        // // If not found, throw a TypeSystemError 
        // throw new TypeSystemError(
        // "Polymorphic kind %s has an element. Override
        // ElementQualifierHierarchy#getPolymorphicAnnotation.",
        // polymorphicKind);
        // }

        // // Return the found polymorphic annotation
        // return polymorphicAnnotation;
        // }





        // //@Override
        // public final @Nullable AnnotationMirror leastUpperBoundQualifiers(
        // AnnotationMirror a1, AnnotationMirror a2) {
        // QualifierKind qual1 = getQualifierKind(a1);
        // QualifierKind qual2 = getQualifierKind(a2);
        // QualifierKind lub = qualifierKindHierarchy.leastUpperBound(qual1, qual2);
        // if (lub == null) {
        // // Qualifiers are not in the same hierarchy.
        // return null;
        // }
        // if (lub.hasElements()) {
        // return leastUpperBoundWithElements(a1, qual1, a2, qual2, lub);
        // }
        // return kindToElementlessQualifier.get(lub);
        // }

        // //@Override
        // public final @Nullable AnnotationMirror greatestLowerBoundQualifiers(
        // AnnotationMirror a1, AnnotationMirror a2) {
        // QualifierKind qual1 = getQualifierKind(a1);
        // QualifierKind qual2 = getQualifierKind(a2);
        // QualifierKind glb = qualifierKindHierarchy.greatestLowerBound(qual1, qual2);
        // if (glb == null) {
        // // Qualifiers are not in the same hierarchy.
        // return null;
        // }
        // if (glb.hasElements()) {
        // return greatestLowerBoundWithElements(a1, qual1, a2, qual2, glb);
        // }
        // return kindToElementlessQualifier.get(glb);
        // }
        // /**
        // * Default constructor.
        // *
        // * @param f the multigraph factory
        // */
        // protected DataClassificationQualifierHierarchy(final
        // ElementQualifierHierarchy f) {
        // super(f);
        // }

        // protected DataClassificationQualifierHierarchy{
        // return new NoElementQualifierHierarchy(this.getSupportedTypeQualifiers(),
        // elements);
        // }
        // @Override
        // @Override
        // @Override
        // public @Nullable AnnotationMirror getPolymorphicAnnotation(AnnotationMirror
        // start) {
        // QualifierKind poly = getQualifierKind(start).getPolymorphic();
        // if (poly == null) {
        // return null;
        // }
        // return kindToAnnotationMirror.get(poly);
        // }

        @Override
        protected AnnotationMirror leastUpperBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind lubKind) {
            if (qualifierKind1 == qualifierKind2) {
                // Case: The qualifiers are of the same kind
                List<String> a1Values = valuesStringList(a1);
                List<String> a2Values = valuesStringList(a2);
                Set<String> set = new LinkedHashSet<>(a1Values);
                set.addAll(a2Values);
                return createAnnotationMirrorWithValue(lubKind, set);
            } else if (lubKind == qualifierKind1) {
                // Case: lubKind is the same as qualifierKind1
                return a1;
            } else if (lubKind == qualifierKind2) {
                // Case: lubKind is the same as qualifierKind2
                return a2;
            } else {
                // Case: Unexpected situation, this should not happen
                throw new BugInCF("Unexpected QualifierKinds %s %s", qualifierKind1, qualifierKind2, lubKind);
            }
        }

        @Override
        protected AnnotationMirror greatestLowerBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind glbKind) {
            if (qualifierKind1 == qualifierKind2) {
                List<String> a1Values = valuesStringList(a1);
                List<String> a2Values = valuesStringList(a2);
                Set<String> set = new LinkedHashSet<>(a1Values);
                set.retainAll(a2Values);
                return createAnnotationMirrorWithValue(glbKind, set);
            } else if (glbKind == qualifierKind1) {
                return a1;
            } else if (glbKind == qualifierKind2) {
                return a2;
            } else {
                throw new BugInCF("Unexpected QualifierKinds %s %s", qualifierKind1, qualifierKind2, glbKind);
            }
        }

        @Override
        // Returns true if subAnno is a subtype of superAnno.
        protected boolean isSubtypeWithElements(
                AnnotationMirror subAnno,
                QualifierKind subKind,
                AnnotationMirror superAnno,
                QualifierKind superKind) {

            // Checks if the qualifier kinds are the same
            if (subKind == superKind) {
                // if both annotations have the same qualifier kind
                // it compares their values to determine subtype relationship

                // Extracts the values associated with the super and sub annotations
                List<String> superValues = valuesStringList(superAnno);
                List<String> subValues = valuesStringList(subAnno);

                // Checks if all values of the sub-annotation are present in the super-annotation
                return subValues.containsAll(superValues);
            }

            // If qualifier kinds are different, tries to check the subtype relationship based on
            // their hierarchy
            return subKind.isSubtypeOf(superKind);
        }

        // The valuesStringList method is a helper method that extracts the values
        // associated with the "value" element of an annotation. It splits the values by
        // commas and returns them as a list of strings.
        private List<String> valuesStringList(AnnotationMirror annotation) {
            String value = AnnotationUtils.getElementValue(annotation, "value", String.class, true);
            return value.isEmpty() ? Collections.emptyList() : Arrays.asList(value.split("\\s*,\\s*"));
        }

        private AnnotationMirror createAnnotationMirrorWithValue(QualifierKind kind, Set<String> values) {
            AnnotationBuilder builder = new AnnotationBuilder(processingEnv, kind.getAnnotationClass());
            builder.setValue("value", values.toArray());
            return builder.build();
        }

        // public boolean isSubtypeWithElements(AnnotationMirror subType, QualifierKind
        // subKind,
        // AnnotationMirror superType, QualifierKind superKind) {
        // if (isPolyWithArgs(superType) && isPolyWithArgs(subType)) {
        // return AnnotationUtils.areSame(subType, superType);
        // } else if (isPolyWithArgs(superType)) {
        // if (AnnotationUtils.areSame(subType, poly)) {
        // return false;
        // } else {
        // return isSubtypeQualifiers(subType, poly);
        // }
        // } else if (isPolyWithArgs(subType)) {
        // if (AnnotationUtils.areSame(superType, poly)) {
        // return false;
        // } else {
        // return isSubtypeQualifiers(poly, superType);
        // }
        // } else {
        // return isSubtypeQualifiers(subType, superType);
        // }
        // }

        // // Rules:
        // // 1. if the superType is top, always return true
        // // 2. if both arguments are poly with args, return true iff the argument is
        // equal
        // // 3. if the superType is poly with args, return false if the subType is
        // poly, and
        // // otherwise
        // // treat the poly with args as a regular poly qual
        // // 4. and vice-versa for the subType
        // // 5. everything else use the standard rules
        // if (isPolyWithArgs(superType) && isPolyWithArgs(subType)) {
        // return AnnotationUtils.areSame(subType, superType);
        // } else if (isPolyWithArgs(superType)) {
        // if (AnnotationUtils.areSame(subType, poly)) {
        // return false;
        // } else {
        // return super.isSubtype(subType, poly);
        // }
        // } else if (isPolyWithArgs(subType)) {
        // if (AnnotationUtils.areSame(superType, poly)) {
        // return false;
        // } else {
        // return super.isSubtype(poly, superType);
        // }
        // } else {
        // return super.isSubtype(subType, superType);
        // }
        // }

        /**
         * Common check whether a1 is @PolyClassification("...") for any non-empty"...".
         *
         * @param a1 the annotation to check
         * @return true if so, false otherwise.
         */
        private boolean isPolyWithArgs(final AnnotationMirror a1) {
            if (a1 != null && AnnotationUtils.areSameByClass(a1, PolyClassification.class)) {
                String arg = AnnotationUtils.getElementValue(a1, "value", String.class, true);
                return !"".equals(arg);
            }
            return false;
        }

    }

    @Override
    protected QualifierHierarchy createQualifierHierarchy() {
        return new DataClassificationQualifierHierarchy(this.getSupportedTypeQualifiers(), elements);
    }

    // @Override
    // public QualifierHierarchy createQualifierHierarchy(final MultiGraphFactory
    // factory) {
    // return new DataClassificationQualifierHierarchy(factory);
    // }

    // Checkerframework changelog mentions removing this method
    // @Override
    // public QualifierHierarchy createQualifierHierarchy(final QualifierHierachy
    // factory) {
    // return new DataClassificationQualifierHierarchy(factory);
    // }
    // public QualifierHierarchy createQualifierHierarchy() {
    // return new DataClassificationQualifierHierarchy();
    // // }
    // protected QualifierHierarchy createQualifierKindHierarchy(
    // @UnderInitialization UnitsQualifierHierarchy this,
    // Collection<Class<? extends Annotation>> qualifierClasses) {
    // return new UnitsQualifierKindHierarchy(qualifierClasses, elements);
    // }
    // protected QualifierHierarchy createQualifierHierarchy() {
    // return new DataClassificationQualifierHierarchy(
    // this.getSupportedTypeQualifiers(), elements);
    // }
    /**
     * It's necessary to cache these intermediate results for correctness - when
     * looking up the type
     * of a member, the type factory usually checks the class' type (by calling the
     * method
     * overridden below). So we cache the type that was actually written, and return
     * it on those
     * calls; we then update the type to the inferred type and re-cache the new
     * inferred type for
     * performance reasons.
     *
     * <p>
     * This cache effectively replaces part of the element cache used by
     * AnnotatedTypeFactory.
     */
    private final Map<Element, AnnotatedTypeMirror> classCache = CollectionUtils.createLRUCache(getCacheSize());

    /**
     * This method is called when determining the "user-written" type to assign to a
     * program
     * element. The version in AnnotatedTypeFactory (which this overrides) also adds
     * implicit
     * annotations, which is why this method has been overridden.
     *
     * <p>
     * The implicit type of a class is the least upper bound of the return types of
     * all of its
     * methods, the type of all of its fields, and the programmer-written annotation
     * on the class
     * declaration.
     *
     * <p>
     * This rule ensures that a "container" class that has access to sensitive data
     * is itself
     * considered sensitive.
     */
    @Override
    public AnnotatedTypeMirror fromElement(final Element elt) {

        // Always prefer the classCache over recomputation.
        if (classCache.containsKey(elt)) {
            return classCache.get(elt);
        }

        // Use the tree so that we have access to members
        Tree decl = declarationFromElement(elt);
        if (decl != null && decl.getKind() == Tree.Kind.CLASS) {
            ClassTree tree = (ClassTree) decl;

            // Get the type that would have been resolved: the user-written class
            // annotations, if
            // there are any.
            AnnotatedTypeMirror type = super.fromElement(elt);
            classCache.put(elt, type);

            // Use an annotation mirror throughout here because that's what
            // QualifierHierachy#leastUpperBound requires
            AnnotationMirror inferredClassLowerbound = type.getAnnotationInHierarchy(getCanonicalPublicAnnotation());
            // If the class is unannotated, assume public
            if (inferredClassLowerbound == null) {
                inferredClassLowerbound = getCanonicalPublicAnnotation();
            }

            // For each member of the class that's a field or a method, update the inferred
            // type
            // with either the type
            // of the field or the return type of the method.
            for (Tree member : tree.getMembers()) {
                switch (member.getKind()) {
                    case METHOD:
                        MethodTree methodTree = (MethodTree) member;
                        ExecutableElement execElem = TreeUtils.elementFromDeclaration(methodTree);
                        if (ElementUtils.isStatic(execElem)) {
                            break;
                        }
                        if (execElem.getReturnType() == null) {
                            break;
                        }

                        AnnotatedTypeMirror.AnnotatedExecutableType methodSignature = super.fromElement(execElem);
                        AnnotationMirror returnAnno = findLeastUpperBoundOfType(
                                methodSignature.getReturnType(),
                                getCanonicalPublicAnnotation());

                        if (returnAnno == null || AnnotationUtils.areSameByName(returnAnno, poly)) {
                            break;
                        }
                        /// made chanfes to leastupperbound to leastUpperBoundQualifiersOnly

                        inferredClassLowerbound = getQualifierHierarchy()
                                .leastUpperBoundQualifiersOnly(inferredClassLowerbound, returnAnno);
                        break;
                    case VARIABLE:
                        Element fieldElt = TreeUtils.elementFromTree(member);
                        if (fieldElt == null) {
                            break;
                        }
                        if (ElementUtils.isStatic(fieldElt)) {
                            break;
                        }
                        AnnotationMirror fieldAnno = findLeastUpperBoundOfType(
                                super.fromElement(fieldElt),
                                getCanonicalPublicAnnotation());

                        if (fieldAnno == null) {
                            break;
                        }
                        /// made chanfes to leastupperbound to leastUpperBoundQualifiersOnly
                        inferredClassLowerbound = getQualifierHierarchy()
                                .leastUpperBoundQualifiersOnly(inferredClassLowerbound, fieldAnno);
                        break;
                    default:
                        break;
                }
            }
            // Replace the annotation in the type and return it after updating the cache.
            type.replaceAnnotation(inferredClassLowerbound);
            classCache.put(elt, type);
            return type;
        }

        return super.fromElement(elt);
    }

    /**
     * @param type                   the type to lub
     * @param canonicalHierarchyAnno an annotation in the hierarchy of interest
     * @return the least upper bound of the passed type and all its component types
     */
    @Nullable
    private AnnotationMirror findLeastUpperBoundOfType(final AnnotatedTypeMirror type,
            final AnnotationMirror canonicalHierarchyAnno) {
        if (type.getKind() == TypeKind.ARRAY) {
            AnnotatedTypeMirror.AnnotatedArrayType arType = (AnnotatedTypeMirror.AnnotatedArrayType) type;
            AnnotationMirror arLub = findLeastUpperBoundOfType(arType.getComponentType(), canonicalHierarchyAnno);
            AnnotationMirror anno = type.getAnnotationInHierarchy(canonicalHierarchyAnno);
            if (arLub == null) {
                return anno;
            } else if (anno == null) {
                return arLub;
            } else {
                return getQualifierHierarchy().leastUpperBoundQualifiersOnly(anno, arLub);
            }
        } else if (type.getKind() == TypeKind.DECLARED) {
            AnnotatedTypeMirror.AnnotatedDeclaredType declaredType = (AnnotatedTypeMirror.AnnotatedDeclaredType) type;
            AnnotationMirror result = type.getAnnotationInHierarchy(canonicalHierarchyAnno);
            List<AnnotatedTypeMirror> typeVars = declaredType.getTypeArguments();
            for (AnnotatedTypeMirror var : typeVars) {
                AnnotationMirror varLub = findLeastUpperBoundOfType(var, canonicalHierarchyAnno);
                if (varLub != null) {
                    if (result == null) {
                        result = varLub;
                    } else {
                        result = getQualifierHierarchy().leastUpperBoundQualifiersOnly(result, varLub);
                    }
                }
            }
            return result;
        } else {
            return type.getAnnotationInHierarchy(canonicalHierarchyAnno);
        }
    }
}

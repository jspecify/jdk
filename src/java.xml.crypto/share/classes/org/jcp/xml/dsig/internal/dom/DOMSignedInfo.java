/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * $Id: DOMSignedInfo.java 1820179 2018-01-04 19:09:52Z mullan $
 */
package org.jcp.xml.dsig.internal.dom;

import org.jspecify.annotations.Nullable;

import javax.xml.crypto.*;
import javax.xml.crypto.dsig.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.Provider;
import java.util.*;

import org.w3c.dom.Element;
import com.sun.org.apache.xml.internal.security.utils.UnsyncBufferedOutputStream;

/**
 * DOM-based implementation of SignedInfo.
 *
 */
public final class DOMSignedInfo extends DOMStructure implements SignedInfo {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(DOMSignedInfo.class);

    private List<Reference> references;
    private CanonicalizationMethod canonicalizationMethod;
    private SignatureMethod signatureMethod;
    private String id;
    private Element localSiElem;
    private InputStream canonData;

    /**
     * Creates a {@code DOMSignedInfo} from the specified parameters. Use
     * this constructor when the {@code Id} is not specified.
     *
     * @param cm the canonicalization method
     * @param sm the signature method
     * @param references the list of references. The list is copied.
     * @throws NullPointerException if
     *    {@code cm}, {@code sm}, or {@code references} is
     *    {@code null}
     * @throws IllegalArgumentException if {@code references} is empty
     * @throws ClassCastException if any of the references are not of
     *    type {@code Reference}
     */
    public DOMSignedInfo(CanonicalizationMethod cm, SignatureMethod sm,
                         List<? extends Reference> references) {
        if (cm == null || sm == null || references == null) {
            throw new NullPointerException();
        }
        this.canonicalizationMethod = cm;
        this.signatureMethod = sm;
        this.references = Collections.unmodifiableList(
            new ArrayList<>(references));
        if (this.references.isEmpty()) {
            throw new IllegalArgumentException("list of references must " +
                "contain at least one entry");
        }
        for (int i = 0, size = this.references.size(); i < size; i++) {
            Object obj = this.references.get(i);
            if (!(obj instanceof Reference)) {
                throw new ClassCastException("list of references contains " +
                    "an illegal type");
            }
        }
    }

    /**
     * Creates a {@code DOMSignedInfo} from the specified parameters.
     *
     * @param cm the canonicalization method
     * @param sm the signature method
     * @param references the list of references. The list is copied.
     * @param id an optional identifer that will allow this
     *    {@code SignedInfo} to be referenced by other signatures and
     *    objects
     * @throws NullPointerException if {@code cm}, {@code sm},
     *    or {@code references} is {@code null}
     * @throws IllegalArgumentException if {@code references} is empty
     * @throws ClassCastException if any of the references are not of
     *    type {@code Reference}
     */
    public DOMSignedInfo(CanonicalizationMethod cm, SignatureMethod sm,
                         List<? extends Reference> references, String id) {
        this(cm, sm, references);
        this.id = id;
    }

    /**
     * Creates a {@code DOMSignedInfo} from an element.
     *
     * @param siElem a SignedInfo element
     */
    public DOMSignedInfo(Element siElem, XMLCryptoContext context, Provider provider)
        throws MarshalException {
        localSiElem = siElem;

        // get Id attribute, if specified
        id = DOMUtils.getAttributeValue(siElem, "Id");

        // unmarshal CanonicalizationMethod
        Element cmElem = DOMUtils.getFirstChildElement(siElem,
                                                       "CanonicalizationMethod",
                                                       XMLSignature.XMLNS);
        canonicalizationMethod = new DOMCanonicalizationMethod(cmElem, context,
                                                               provider);

        // unmarshal SignatureMethod
        Element smElem = DOMUtils.getNextSiblingElement(cmElem,
                                                        "SignatureMethod",
                                                        XMLSignature.XMLNS);
        signatureMethod = DOMSignatureMethod.unmarshal(smElem);

        boolean secVal = Utils.secureValidation(context);

        String signatureMethodAlgorithm = signatureMethod.getAlgorithm();
        if (secVal && Policy.restrictAlg(signatureMethodAlgorithm)) {
            throw new MarshalException(
                "It is forbidden to use algorithm " + signatureMethodAlgorithm +
                " when secure validation is enabled"
            );
        }

        // unmarshal References
        ArrayList<Reference> refList = new ArrayList<>(5);
        Element refElem = DOMUtils.getNextSiblingElement(smElem, "Reference", XMLSignature.XMLNS);
        refList.add(new DOMReference(refElem, context, provider));

        refElem = DOMUtils.getNextSiblingElement(refElem);
        while (refElem != null) {
            String name = refElem.getLocalName();
            String namespace = refElem.getNamespaceURI();
            if (!"Reference".equals(name) || !XMLSignature.XMLNS.equals(namespace)) {
                throw new MarshalException("Invalid element name: " +
                                           namespace + ":" + name + ", expected Reference");
            }
            refList.add(new DOMReference(refElem, context, provider));
            if (secVal && Policy.restrictNumReferences(refList.size())) {
                String error = "A maxiumum of " + Policy.maxReferences()
                    + " references per Manifest are allowed when"
                    + " secure validation is enabled";
                throw new MarshalException(error);
            }
            refElem = DOMUtils.getNextSiblingElement(refElem);
        }
        references = Collections.unmodifiableList(refList);
    }

    @Override
    public CanonicalizationMethod getCanonicalizationMethod() {
        return canonicalizationMethod;
    }

    @Override
    public SignatureMethod getSignatureMethod() {
        return signatureMethod;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<Reference> getReferences() {
        return references;
    }

    @Override
    public InputStream getCanonicalizedData() {
        return canonData;
    }

    public void canonicalize(XMLCryptoContext context, ByteArrayOutputStream bos)
        throws XMLSignatureException {
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }

        DOMSubTreeData subTree = new DOMSubTreeData(localSiElem, true);
        try (OutputStream os = new UnsyncBufferedOutputStream(bos)) {
            ((DOMCanonicalizationMethod)
                canonicalizationMethod).canonicalize(subTree, context, os);

            os.flush();

            byte[] signedInfoBytes = bos.toByteArray();

            // this whole block should only be done if LOGging is enabled
            if (LOG.isDebugEnabled()) {
                LOG.debug("Canonicalized SignedInfo:");
                StringBuilder sb = new StringBuilder(signedInfoBytes.length);
                for (int i = 0; i < signedInfoBytes.length; i++) {
                    sb.append((char)signedInfoBytes[i]);
                }
                LOG.debug(sb.toString());
                LOG.debug("Data to be signed/verified:" + Base64.getMimeEncoder().encodeToString(signedInfoBytes));
            }

            this.canonData = new ByteArrayInputStream(signedInfoBytes);
        } catch (TransformException te) {
            throw new XMLSignatureException(te);
        } catch (IOException e) {
            LOG.debug(e.getMessage(), e);
            // Impossible
        }
    }

    @Override
    public void marshal(XmlWriter xwriter, String dsPrefix, XMLCryptoContext context)
        throws MarshalException
    {
        xwriter.writeStartElement(dsPrefix, "SignedInfo", XMLSignature.XMLNS);
        XMLStructure siStruct = xwriter.getCurrentNodeAsStructure();
        localSiElem = (Element) ((javax.xml.crypto.dom.DOMStructure) siStruct).getNode();

        // append Id attribute
        xwriter.writeIdAttribute("", "", "Id", id);

        // create and append CanonicalizationMethod element
        DOMCanonicalizationMethod dcm =
            (DOMCanonicalizationMethod)canonicalizationMethod;
        dcm.marshal(xwriter, dsPrefix, context);

        // create and append SignatureMethod element
        ((AbstractDOMSignatureMethod)signatureMethod).marshal(xwriter, dsPrefix);

        // create and append Reference elements
        for (Reference reference : references) {
            // TODO - either suppress warning here, or figure out how to get rid of the cast.
            DOMReference domRef = (DOMReference)reference;
            domRef.marshal(xwriter, dsPrefix, context);
        }

        xwriter.writeEndElement(); // "SignedInfo"
    }

    @Override
    
    
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignedInfo)) {
            return false;
        }
        SignedInfo osi = (SignedInfo)o;

        boolean idEqual = id == null ? osi.getId() == null
                                      : id.equals(osi.getId());

        return canonicalizationMethod.equals(osi.getCanonicalizationMethod())
                && signatureMethod.equals(osi.getSignatureMethod()) &&
                references.equals(osi.getReferences()) && idEqual;
    }

    @SuppressWarnings("unchecked")
    public static List<Reference> getSignedInfoReferences(SignedInfo si) {
        return si.getReferences();
    }

    @Override
    public int hashCode() {
        int result = 17;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        result = 31 * result + canonicalizationMethod.hashCode();
        result = 31 * result + signatureMethod.hashCode();
        result = 31 * result + references.hashCode();

        return result;
    }
}

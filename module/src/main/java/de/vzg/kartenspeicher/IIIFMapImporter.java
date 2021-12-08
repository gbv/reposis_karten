/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.vzg.kartenspeicher;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.digitalcollections.iiif.model.ImageContent;
import de.digitalcollections.iiif.model.Service;
import de.digitalcollections.iiif.model.jackson.IiifObjectMapper;
import de.digitalcollections.iiif.model.openannotation.Annotation;
import de.digitalcollections.iiif.model.sharedcanvas.Canvas;
import de.digitalcollections.iiif.model.sharedcanvas.Manifest;
import de.digitalcollections.iiif.model.sharedcanvas.Resource;
import de.digitalcollections.iiif.model.sharedcanvas.Sequence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.mycore.access.MCRAccessException;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRJDOMContent;
import org.mycore.common.content.transformer.MCRContentTransformer;
import org.mycore.common.content.transformer.MCRParameterizedTransformer;
import org.mycore.common.xml.MCRLayoutService;
import org.mycore.common.xsl.MCRParameterCollector;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaClassification;
import org.mycore.datamodel.metadata.MCRMetaIFS;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.mods.MCRMODSWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IIIFMapImporter {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String APPEND_MAX_QUALITY_JPG = "/full/full/0/default.jpg";

    public static void main(String[] args) throws IOException {
        //importMaps("https://digital.lb-oldenburg.de/i3f/v21/1227819/manifest");
    }

    public static void importPair(String ppn, String manifestURL, String projectID, String instituteID, String collection) throws Exception {
        MCRObjectID objectId = importPPN(ppn, projectID, instituteID, collection);
        MCRDerivate derivate = createDerivate(objectId, new ArrayList<>());
        MCRPath derivateRoot = MCRPath.getPath(derivate.getId().toString(), "/");
        String mainFile = downloadMaps(manifestURL, derivateRoot);
        if (mainFile != null) {
            derivate.getDerivate().getInternals().setMainDoc(mainFile);
            MCRMetadataManager.update(derivate);
        }
    }

    public static MCRObjectID importPPN(String ppn, String projectID, String instituteID, String collection) throws Exception {
        String url = MCRConfiguration2.getStringOrThrow("MCR.UnAPIURL") + ppn;
        Document jdomDoc = new SAXBuilder().build(new URL(url));

        MCRParameterCollector parameter = new MCRParameterCollector();
        parameter.setParameter("institute", instituteID);
        parameter.setParameter("collection", collection);
        MCRParameterizedTransformer tx = (MCRParameterizedTransformer) MCRLayoutService.getContentTransformer("pica2mods_iiif",
                parameter);
        MCRContent resultMods = tx.transform(new MCRJDOMContent(jdomDoc), parameter);

        MCRMODSWrapper mw = new MCRMODSWrapper();
        Element mods = resultMods.asXML().detachRootElement();
        converCoordinates(mods);
        mw.setMODS(mods);
        MCRObjectID odb_mods = MCRObjectID.getNextFreeId(projectID + "_mods");
        MCRObject mcrObject = mw.getMCRObject();
        mcrObject.setId(odb_mods);
        MCRMetadataManager.create(mcrObject);
        return odb_mods;
    }

    private static void converCoordinates(Element modsRoot) {
        modsRoot.getChildren("subject", MCRConstants.MODS_NAMESPACE).forEach(ch -> {
            Element cartographics = ch.getChild("cartographics", MCRConstants.MODS_NAMESPACE);
            if (cartographics != null) {
                cartographics.getChildren("coordinates", MCRConstants.MODS_NAMESPACE).forEach(el -> {
                    String coords = el.getTextTrim();
                    String converted = CoordinateConverter.convertCoordinate(coords);
                    if (converted != null) {
                        el.setText(converted);
                    }
                });
            }

        });
    }

    public static String downloadMaps(String manifestURL, Path targetFolder) throws IOException {
        ObjectMapper iiifMapper = new IiifObjectMapper();
        String mainFile = null;

        Manifest manifest = iiifMapper.readValue(
            new URL(manifestURL),
            Manifest.class);

        for (Sequence sequence : manifest.getSequences()) {
            for (Canvas canvas : sequence.getCanvases()) {
                List<Annotation> images = canvas.getImages();
                if (images.size() != 1) {
                    LOGGER.warn("More than or less then one Image found in Canvas {}", canvas);
                    return null;
                }
                Annotation image = images.stream().findFirst().get();
                Resource<ImageContent> imageResource = (Resource<ImageContent>) image.getResource();
                List<Service> services = imageResource.getServices();
                if (services.size() != 1) {
                    LOGGER.warn("More than or less then one Services found in Image {}", image);
                    return null;
                }

                Service service = services.stream().findFirst().get();
                String imageUrl = service.getIdentifier().toString();
                String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1) + ".jpg";
                imageUrl += APPEND_MAX_QUALITY_JPG;

                LOGGER.info("Download {} to {}", imageUrl, filename);
                try (InputStream is = new URL(imageUrl).openStream()) {
                    if (mainFile == null) {
                        mainFile = filename;
                    }
                    Files.copy(is, targetFolder.resolve(filename));
                }
            }
        }
        return mainFile;
    }

    private static MCRObjectID getNewCreateDerivateID(MCRObjectID objId) {
        String projectID = objId.getProjectId();
        return MCRObjectID.getNextFreeId(projectID + "_derivate");

    }

    private static MCRDerivate createDerivate(MCRObjectID objectID, List<MCRMetaClassification> classifications)
        throws MCRPersistenceException, MCRAccessException {

        MCRObjectID derivateID = getNewCreateDerivateID(objectID);
        MCRDerivate derivate = new MCRDerivate();
        derivate.setId(derivateID);
        derivate.getDerivate().getClassifications().addAll(classifications);

        String schema = MCRConfiguration2.getString("MCR.Metadata.Config.derivate").orElse("datamodel-derivate.xml")
            .replaceAll(".xml", ".xsd");
        derivate.setSchema(schema);

        MCRMetaLinkID linkId = new MCRMetaLinkID();
        linkId.setSubTag("linkmeta");
        linkId.setReference(objectID, null, null);
        derivate.getDerivate().setLinkMeta(linkId);

        MCRMetaIFS ifs = new MCRMetaIFS();
        ifs.setSubTag("internal");
        ifs.setSourcePath(null);
        derivate.getDerivate().setInternals(ifs);

        LOGGER.debug("Creating new derivate with ID {}", derivateID);
        MCRMetadataManager.create(derivate);

        return derivate;
    }

}

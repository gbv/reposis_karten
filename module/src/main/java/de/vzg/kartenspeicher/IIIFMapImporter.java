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
import de.digitalcollections.iiif.model.image.ImageService;
import de.digitalcollections.iiif.model.image.TileInfo;
import de.digitalcollections.iiif.model.jackson.IiifObjectMapper;
import de.digitalcollections.iiif.model.openannotation.Annotation;
import de.digitalcollections.iiif.model.sharedcanvas.Canvas;
import de.digitalcollections.iiif.model.sharedcanvas.Manifest;
import de.digitalcollections.iiif.model.sharedcanvas.Resource;
import de.digitalcollections.iiif.model.sharedcanvas.Sequence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.mycore.access.MCRAccessException;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRException;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.MCRJDOMContent;
import org.mycore.common.content.transformer.MCRParameterizedTransformer;
import org.mycore.common.xml.MCRLayoutService;
import org.mycore.common.xsl.MCRParameterCollector;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaClassification;
import org.mycore.datamodel.metadata.MCRMetaEnrichedLinkID;
import org.mycore.datamodel.metadata.MCRMetaIFS;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.datamodel.niofs.utils.MCRRecursiveDeleter;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.solr.MCRSolrClientFactory;
import org.mycore.solr.search.MCRSolrSearchUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IIIFMapImporter {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, MCRObjectID> existingPPNMap = new HashMap<>();

    private static final String CATALOG_URL_REG_EXP_STR = "https?:\\/\\/uri.gbv.de\\/document\\/([a-zA-Z0-9]+):ppn:([a-zA-Z0-9]+)";

    private static final Pattern CATALOG_URL_REG_EXP_PATTERN = Pattern.compile(CATALOG_URL_REG_EXP_STR);

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //importMaps("https://digital.lb-oldenburg.de/i3f/v21/1227819/manifest");
        //downloadMaps("https://digitale-sammlungen.gwlb.de/content/100650309/manifest.json", Paths.get("/home/sebastian/karten/"))
    }

    public static void importPair(String ppn,
                                  String catalog,
                                  String manifestURL,
                                  String projectID,
                                  String instituteID,
                                  String collection,
                                  boolean redownload) throws Exception {

        MCRObjectID objectId = importPPN(ppn, catalog, projectID, instituteID, collection, true);

        MCRObject mcrObject = MCRMetadataManager.retrieveMCRObject(objectId);
        Optional<MCRMetaEnrichedLinkID> mayDerivate = mcrObject.getStructure().getDerivates().stream().findFirst();
        MCRDerivate derivate;

        if(!testManifest(manifestURL)){
            LOGGER.error("The manifest " + manifestURL + " seems to be invalid!");
            return;
        }

        boolean derivateExisting = mayDerivate.isPresent();
        if (derivateExisting) {
            derivate = MCRMetadataManager.retrieveMCRDerivate(mayDerivate.get().getXLinkHrefID());
            if (redownload) {
                MCRPath root = MCRPath.getPath(derivate.getId().toString(), "/");
                Files.walkFileTree(root, MCRRecursiveDeleter.instance());
            }
        } else {
            derivate = createDerivate(objectId, new ArrayList<>());
        }

        if (!derivateExisting || redownload) {
            MCRPath derivateRoot = MCRPath.getPath(derivate.getId().toString(), "/");
            String mainFile = downloadMaps(manifestURL, derivateRoot);
            if (mainFile != null) {
                derivate.getDerivate().getInternals().setMainDoc(mainFile);
                MCRMetadataManager.update(derivate);
            }
        }
    }

    /**
     * Creates or Updates a ppn
     *
     * @param ppn         the ppn to search in the catalog
     * @param projectID   the projectID which will be used in the object id
     * @param instituteID the institute parameter which will be passed to the transformer
     * @param collection  the collection parameter which will be passed to the transformer
     * @return the id of the create or updated object
     * @throws Exception
     */
    public static MCRObjectID importPPN(String ppn, String catalog, String projectID, String instituteID, String collection, boolean overwrite) throws Exception {
        MCRObjectID existingObject = checkPPNExists(ppn, catalog);

        if(existingObject!=null && !overwrite){
            return existingObject;
        }

        String url = constructCatalogURL(ppn, catalog);
        Document picaDocument = new SAXBuilder().build(new URL(url));

        MCRParameterCollector parameter = new MCRParameterCollector();
        parameter.setParameter("institute", instituteID);
        parameter.setParameter("collection", collection);
        parameter.setParameter("MCR.PICA2MODS.DATABASE", catalog);
        MCRParameterizedTransformer tx = (MCRParameterizedTransformer) MCRLayoutService.getContentTransformer("pica2mods_iiif",
                parameter);
        MCRContent resultMods = tx.transform(new MCRJDOMContent(picaDocument), parameter);

        MCRMODSWrapper mw = new MCRMODSWrapper();
        Element mods = resultMods.asXML().detachRootElement();
        converCoordinates(mods);
        mw.setMODS(mods);
        MCRObject mcrObject = mw.getMCRObject();

        List<Element> hosts = mw.getElements("mods:relatedItem[@type='host']");
        if (hosts.size() > 1) {
            throw new MCRException("There is more then one host in " + mcrObject.getId() + "!!!");
        } else if (hosts.size() == 1) {
            Element relatedItem = hosts.get(0);
            Element part = relatedItem.getChild("part", MCRConstants.MODS_NAMESPACE);

            Tuple<String, String> catalogPpnTuple = extractPPN(relatedItem);

            MCRObjectID id = importPPN(catalogPpnTuple.getE2(), catalogPpnTuple.getE1(), projectID, instituteID, collection, false);
            relatedItem.setAttribute("href", id.toString(), MCRConstants.XLINK_NAMESPACE);
            relatedItem.setAttribute("type", "simple", MCRConstants.XLINK_NAMESPACE);
            List<Element> children = relatedItem.getChildren()
                    .stream()
                    .filter(Predicate.not(part::equals))
                    .collect(Collectors.toList());

            children.forEach(relatedItem::removeContent);
        }

        if (existingObject != null) {
            mcrObject.setId(existingObject);
            MCRMetadataManager.update(mcrObject);
        } else {
            MCRObjectID objectID = MCRObjectID.getNextFreeId(projectID + "_mods");
            mcrObject.setId(objectID);
            MCRMetadataManager.create(mcrObject);
            existingPPNMap.put(ppn, objectID);
        }

        return mcrObject.getId();
    }

    private static String constructCatalogURL(String ppn, String catalog) {
        return "https://unapi.k10plus.de/?&format=picaxml&id=" + catalog + ":ppn:" + ppn;
    }

    public static Tuple<String, String> extractPPN(Element relatedItemOrMods) {
        return relatedItemOrMods
                .getChildren("identifier", MCRConstants.MODS_NAMESPACE)
                .stream()
                .filter(el->"uri".equals(el.getAttributeValue("type")))
                .map(Element::getTextTrim)
                .map(CATALOG_URL_REG_EXP_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> new Tuple<>(matcher.group(1), matcher.group(2)))
                .findFirst().orElse(null);
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

    public static boolean testManifest(String manifestURL){
        ObjectMapper iiifMapper = new IiifObjectMapper();

        Manifest manifest = null;
        try {
            manifest = iiifMapper.readValue(
                    new URL(manifestURL),
                    Manifest.class);
        } catch (IOException e) {
            return false;
        }

        for (Sequence sequence : manifest.getSequences()) {
            for (Canvas canvas : sequence.getCanvases()) {
                List<Annotation> images = canvas.getImages();
                if (images.size() != 1) {
                    LOGGER.warn("More than or less then one Image found in Canvas {}", canvas);
                    return false;
                }
                Annotation image = images.stream().findFirst().get();
                Resource<ImageContent> imageResource = (Resource<ImageContent>) image.getResource();
                List<Service> services = imageResource.getServices();
                if (services.size() != 1) {
                    LOGGER.warn("More than or less then one Services found in Image {}", image);
                    return false;
                }
            }
        }
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


                URL imageURL = new URL(imageUrl + "/info.json");
                String s;
                // this is a hack because native quality is not supported in the iiif we use
                try (InputStream is = imageURL.openStream()) {
                    byte[] bytes = is.readAllBytes();
                    String jsonContent = new String(bytes, StandardCharsets.UTF_8);
                    s = jsonContent.replaceAll("\"native\",?", "");
                }
                ImageService imageService = iiifMapper.readValue(s, ImageService.class);

                Integer width = imageService.getWidth();
                Integer height = imageService.getHeight();

                TileInfo tileInfo = imageService.getTiles().stream().findFirst().get();

                Integer tileSizeWidth = tileInfo.getWidth();
                Integer tileSizeHeight = tileInfo.getHeight();

                double xTiles = Math.ceil((double) width / tileSizeWidth);
                double yTiles = Math.ceil((double) height / tileSizeHeight);

                BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics graphics = result.getGraphics();
                String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1) + ".jpg";

                for (int yTile = 0; yTile < yTiles; yTile++) {
                    int yStart = yTile * tileSizeWidth;
                    int yEnd = Math.min(yStart + tileSizeHeight, height); // should only be triggered at corner tile
                    int curTileHeight = yEnd - yStart;
                    for (int xTile = 0; xTile < xTiles; xTile++) {
                        int xStart = xTile * tileSizeWidth;
                        int xEnd = Math.min(xStart + tileSizeWidth, width); // should only be triggered at corner tile
                        int curTileWidth = xEnd - xStart;

                        String tileURL = imageUrl + "/" + xStart + "," + yStart + "," + curTileWidth + "," + curTileHeight + "/full/0/default.jpg";
                        LOGGER.info("Downloading and draw tile {}/{} x:{}/{} y:{}/{} of {}", (yTile * xTiles) + xTile, xTiles * yTiles, xTile, xTiles, yTile, yTiles, filename);
                        try (InputStream is = new URL(tileURL).openStream()) {
                            BufferedImage tileImage = ImageIO.read(is);
                            graphics.drawImage(tileImage, xStart, yStart, null);
                        }
                    }
                }
                graphics.dispose();


                LOGGER.info("Writing reulting Image to {}", filename);
                try (OutputStream os = Files.newOutputStream(targetFolder.resolve(filename))) {
                    if (!ImageIO.write(result, "jpg", os)) {
                        throw new IOException("Could not find a writer for the Image: " + filename + " in manifest " + manifestURL);
                    }
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

    private static MCRObjectID checkPPNExists(String ppn, String catalog) {
        // check if already exist
        if (existingPPNMap.containsKey(ppn)) {
            LOGGER.info("Object for ppn {} already exists!", ppn);
            return existingPPNMap.get(ppn);
        }
        try {
            final SolrDocument first = MCRSolrSearchUtils
                    .first(MCRSolrClientFactory.getMainSolrClient(), "+mods.identifier:\"" + "https://uri.gbv.de/document/" + catalog + ":ppn:" + ppn + "\"");
            if (first != null) {
                LOGGER.info("Object for ppn {} already exists!", ppn);
                final String id = (String) first.getFirstValue("id");
                MCRObjectID objectID = MCRObjectID.getInstance(id);
                existingPPNMap.put(ppn, objectID);
                return objectID;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static class Tuple<T1,T2> {
        T1 e1;
        T2 e2;

        @Override
        public String toString() {
            return "Tuple{" +
                    "e1=" + e1 +
                    ", e2=" + e2 +
                    '}';
        }

        public Tuple(T1 e1, T2 e2) {
            this.e1 = e1;
            this.e2 = e2;
        }

        public T1 getE1() {
            return e1;
        }

        public void setE1(T1 e1) {
            this.e1 = e1;
        }

        public T2 getE2() {
            return e2;
        }

        public void setE2(T2 e2) {
            this.e2 = e2;
        }
    }

}

package de.vzg.kartenspeicher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.datamodel.metadata.MCRBase;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaIFS;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.mycore.datamodel.niofs.utils.MCRRecursiveDeleter;
import org.mycore.frontend.cli.annotation.MCRCommand;
import org.mycore.frontend.cli.annotation.MCRCommandGroup;
import org.mycore.mods.MCRMODSWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@MCRCommandGroup(name = "Kartenspeicher")
public class VZGKartenSpeicherCommands {

    @MCRCommand(syntax = "update object {0} from catalog",
            help = "tries to reload object {0} from the catalog",
            order = 30)
    public static void reloadObject(String objid) throws Exception {
        IIIFMapImporter.updateObject(objid, false);
    }

    @MCRCommand(syntax = "update object {0} from catalog and reload",
            help = "tries to reload object {0} from the catalog",
            order = 15)
    public static void reloadObjectReload(String objid) throws Exception {
        IIIFMapImporter.updateObject(objid, true);
    }
    @MCRCommand(syntax = "update ppn {0} from {1}",
            help = "tries to reload the xml metadata of the ppn {0} from the catalog {1}",
            order = 25)
    public static void reloadPicaIIIF(String ppn, String catalog) throws Exception {
        IIIFMapImporter.importPair(ppn, catalog, null,null,null,null, false);
    }

    @MCRCommand(syntax = "update ppn {0} from {1} and reload",
            help = "tries to reload the xml metadata of the ppn {0} from the catalog {1} and redownloads the maps",
            order = 10)
    public static void reloadPicaIIIFRedownload(String ppn, String catalog) throws Exception {
        IIIFMapImporter.importPair(ppn, catalog, null,null,null,null, true);
    }
    @MCRCommand(syntax = "import ppn {0} from {1} with manifest {2} with project {3} with institute {4} with collection {5}",
            help = "Imports a object represented by ppn from k10p and downloads all images from a iiif manifest to a derivate",
            order = 20)
    public static void importPicaIIIF(String ppn, String catalog, String manifest, String projectID, String instituteID, String collection) throws Exception {
        IIIFMapImporter.importPair(ppn, catalog, manifest, projectID, instituteID, collection, false);
    }

    @MCRCommand(syntax = "import ppn {0} from {1} with manifest {2} with project {3} with institute {4} with collection {5} and reload",
            help = "Imports a object represented by ppn from k10p and downloads all images from a iiif manifest to a derivate",
            order = 5)
    public static void importPicaIIIFRedownload(String ppn, String catalog, String manifest, String projectID, String instituteID, String collection) throws Exception {
        IIIFMapImporter.importPair(ppn, catalog, manifest, projectID, instituteID, collection, true);
    }

    private static final String MAP_DOWNLOAD = "MAP_DOWNLOAD";
    private static final String URL_TEMPLATE = "http://gdz.sub.uni-goettingen.de/tiff/%s/00000001.tif";
    private static final Logger LOGGER = LogManager.getLogger();

    @MCRCommand(syntax = "download map for {0}",
            help = "Tries to download a map for the object with the id {0}. It creates a Derivate with the label " + MAP_DOWNLOAD + " or cleans it if exists.")
    public static void downloadMaps(String objectID) throws MalformedURLException {
        MCRObject object = MCRMetadataManager.retrieveMCRObject(MCRObjectID.getInstance(objectID));

        // Clean Derivate if present
        Optional<MCRObjectID> prevDerivate = object.getStructure()
                .getDerivates()
                .stream()
                .map(MCRMetaLinkID::getXLinkHrefID)
                .map(MCRMetadataManager::retrieveMCRDerivate)
                .filter(der -> der.getLabel().equals(MAP_DOWNLOAD))
                .map(MCRBase::getId)
                .findAny();
        MCRPath rootPath;

        if(prevDerivate.isPresent()) {
            MCRObjectID mcrObjectID = prevDerivate.get();
            LOGGER.info("Derivate {} with label {} is present. Delete all files in it!", mcrObjectID.toString(),
                    MAP_DOWNLOAD);

            rootPath = MCRPath.getPath(mcrObjectID.toString(), "/");
            final Path root = rootPath;
            rethrow(() -> Files.walkFileTree(root, MCRRecursiveDeleter.instance()));
        }

        // Read URL from Object
        MCRMODSWrapper mw = new MCRMODSWrapper(object);
        Element location = mw.getMODS().getChild("location", MCRConstants.MODS_NAMESPACE);
        if(location == null) {
            throw new MCRException("No mods location found in " + new XMLOutputter(Format.getPrettyFormat()).outputString(mw.getMODS()));
        }

        Element urlElement = location.getChild("url", MCRConstants.MODS_NAMESPACE);
        if(urlElement == null) {
            throw new MCRException("No url found in " + new XMLOutputter(Format.getPrettyFormat()).outputString(mw.getMODS()));
        }

        String urlString = urlElement.getTextTrim();
        LOGGER.info("Extract ppn from url {}", urlString);

        String ppn = urlString.split("[?]")[1];
        LOGGER.info("Extracted ppn is " + ppn);

        String imageURLString = String.format(Locale.ROOT, URL_TEMPLATE, ppn);

        URL imageURL = new URL(imageURLString);
        String[] parts = imageURL.getFile().split("/");
        String fileName = parts[parts.length - 1];

        LOGGER.info("Download from url {}", imageURLString);
        try (InputStream is = imageURL.openStream()) {
            // Create new if not exist
            MCRDerivate derivate;
            derivate = prevDerivate.map(MCRMetadataManager::retrieveMCRDerivate).orElseGet(() -> createDerivate(objectID, MAP_DOWNLOAD));
            rootPath = MCRPath.getPath(derivate.getId().toString(), "/");
            Files.copy(is, rootPath.resolve(fileName));
            derivate.getDerivate().getInternals().setMainDoc(fileName);
            rethrow(() -> MCRMetadataManager.update(derivate));
        } catch (IOException e) {
            throw new MCRException(e);
        }


    }

    private static MCRDerivate createDerivate(String parentObjectID, String label) {
        final String projectId = MCRObjectID.getInstance(parentObjectID).getProjectId();
        MCRObjectID oid = MCRObjectID.getNextFreeId(projectId, "derivate");
        final String derivateID = oid.toString();

        MCRDerivate derivate = new MCRDerivate();
        derivate.setId(oid);
        derivate.setLabel(label);

        String schema = MCRConfiguration2.getString("MCR.Metadata.Config.derivate").orElse("datamodel-derivate.xml").replaceAll(".xml",
                ".xsd");
        derivate.setSchema(schema);

        MCRMetaLinkID linkId = new MCRMetaLinkID();
        linkId.setSubTag("linkmeta");
        linkId.setReference(parentObjectID, null, null);
        derivate.getDerivate().setLinkMeta(linkId);

        MCRMetaIFS ifs = new MCRMetaIFS();
        ifs.setSubTag("internal");
        ifs.setSourcePath(null);

        derivate.getDerivate().setInternals(ifs);

        LOGGER.debug("Creating new derivate with ID " + derivateID);
        rethrow(() -> MCRMetadataManager.create(derivate));


        final MCRPath rootDir = MCRPath.getPath(derivateID, "/");
        if(Files.notExists(rootDir)) {
            rethrow(() -> rootDir.getFileSystem().createRoot(derivateID));
        }

        return derivate;

    }

    private static void rethrow(Throwing f) throws MCRException {
        try {
            f.fn();
        } catch (Exception exception) {
            throw new MCRException(exception);
        }
    }

    public interface Throwing {
        void fn() throws Exception;
    }
}

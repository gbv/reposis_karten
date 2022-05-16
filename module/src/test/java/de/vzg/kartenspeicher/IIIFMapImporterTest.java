package de.vzg.kartenspeicher;

import org.jdom2.Element;
import org.junit.Assert;
import org.junit.Test;
import org.mycore.common.MCRConstants;
import  org.mycore.common.MCRTestCase;

public class IIIFMapImporterTest extends MCRTestCase {

    @Test
    public void extractPPN() {
        Element relatedItem = new Element("relatedItem", MCRConstants.MODS_NAMESPACE);
        Element identifier = new Element("identifier", MCRConstants.MODS_NAMESPACE);
        relatedItem.addContent(identifier);
        identifier.setAttribute("type", "uri");
        identifier.setText("https://uri.gbv.de/document/ikar:ppn:100619533");

        IIIFMapImporter.Tuple<String, String> stringStringTuple = IIIFMapImporter.extractPPN(relatedItem);

        Assert.assertEquals("katalog should match", "ikar" ,stringStringTuple.getE1());
        Assert.assertEquals("ppn should match", "100619533" ,stringStringTuple.getE2());
    }


}
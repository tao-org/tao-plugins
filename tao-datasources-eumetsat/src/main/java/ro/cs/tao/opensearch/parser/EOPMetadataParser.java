package ro.cs.tao.opensearch.parser;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.executors.MemoryUnit;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.List;

public class EOPMetadataParser {

    public void parse(InputStream content, EOProduct product) throws Exception {
        EOPMetadataParser.Handler handler = new EOPMetadataParser.Handler(product);
        List<EOProduct> result;
        InputSource inputSource = new InputSource(content);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, handler);
    }

    private class Handler extends DefaultHandler {
        private final EOProduct product;
        private StringBuilder buffer;
        private String sizeUnit;

        Handler(EOProduct product) {
            this.product = product;
        }


        @Override
        public void startDocument() throws SAXException {
            this.buffer = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            buffer.setLength(0);
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            switch (qName) {
                case "size":
                    this.sizeUnit = attributes.getValue("uom");
                    break;
                case "MultiSurface":
                    this.product.setCrs(attributes.getValue("srsName"));
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            switch (qName) {
                case "shortName":
                    this.product.setSatelliteName(buffer.toString());
                    break;
                case "orbitNumber":
                    this.product.addAttribute("orbitNumber", buffer.toString());
                    break;
                case "size":
                    this.product.setApproximateSize(Long.parseLong(buffer.toString()) * MemoryUnit.valueOf(this.sizeUnit.toUpperCase()).value());
                    break;
                case "posList":
                    this.product.setGeometry("POLYGON((" + buffer.toString() + "))");
                    break;
                case "acquisitionType":
                    this.product.addAttribute("acquisitionType", buffer.toString());
                    break;
                case "acquisitionSubType":
                    this.product.addAttribute("acquisitionSubType", buffer.toString());
                    break;
                case "productType":
                    this.product.setProductType(buffer.toString());
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(new String(ch, start, length).replace("\n", ""));
        }
    }
}

package ro.cs.tao.datasource.remote.mundi.parsers;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.utils.DateUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

abstract class XmlResponseHandler<H extends ProductHelper>
        extends ro.cs.tao.datasource.remote.result.xml.XmlResponseHandler<EOProduct> {
    private String identifiedElement;
    H helper;
    private final DateTimeFormatter formatter = DateUtils.getFormatterAtUTC("yyyy-MM-dd'T'HH:mm:ss'Z'");

    XmlResponseHandler(String recordElementName) {
        super(EOProduct.class, recordElementName);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (this.recordElement.equals(qName)) {
            if (this.current != null && this.current.getId() == null) {
                this.current.setId(this.current.getName());
            }
        }
        super.endElement(uri, localName, qName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
        if (this.recordElement.equals(qName)) {
            initRecord();
        } else if ("content".equals(qName)) {
            this.identifiedElement = attributes.getValue("url");
        } else if ("link".equals(qName)) {
            String value = attributes.getValue("rel");
            if ("enclosure".equals(value)) {
                this.identifiedElement = attributes.getValue("href");
            }
        }
    }

    @Override
    protected void handleEndElement(String qName) {
        if (this.current != null) {
            final String elementValue = buffer.toString();
            try {
                switch (qName) {
                    case "content":
                        if (this.current.getId() == null) {
                            int idx1 = this.identifiedElement.indexOf("'");
                            if (idx1 > 0) {
                                int idx2 = this.identifiedElement.indexOf("'", idx1 + 1);
                                if (idx2 > 0) {
                                    this.current.setId(this.identifiedElement.substring(idx1 + 1, idx2));
                                }
                            }
                            if (this.current.getLocation() == null) {
                                this.current.setLocation(computeLocation());
                            }
                        }
                        break;
                    case "identifier":
                        this.current.setName(elementValue);
                        this.helper = createHelper(elementValue);
                        break;
                    case "sensingStartDate":
                        try {
                            this.current.setAcquisitionDate(LocalDateTime.parse(elementValue, formatter));
                        } catch (Exception e) {
                            logger.warning(e.getMessage());
                        }
                        break;
                    case "processingDate":
                        if (this.current.getAcquisitionDate() == null) {
                            try {
                                this.current.setAcquisitionDate(LocalDateTime.parse(elementValue, formatter));
                            } catch (Exception e) {
                                logger.warning(e.getMessage());
                            }
                        }
                        break;
                    case "category":
                        if ("QUICKLOOK".equals(elementValue)) {
                            this.current.setQuicklookLocation(this.identifiedElement);
                        }
                        break;
                    case "link":
                        if (this.identifiedElement != null) {
                            URI uri = new URI(this.identifiedElement);
                            this.current.setLocation(uri.getPath().substring(1));
                        }
                        break;
                    case "polygon":
                        if (!elementValue.isEmpty()) {
                            String[] points = elementValue.split(" ");
                            if (points.length >= 5) {
                                Polygon2D polygon2D = new Polygon2D();
                                for (int i = 0; i < points.length; i += 2) {
                                    polygon2D.append(Double.parseDouble(points[i + 1]),
                                                     Double.parseDouble(points[i]));
                                }
                                this.current.setGeometry(polygon2D.toWKT(8));
                            }
                        }
                        break;
                    case "orbitDirection":
                        this.current.addAttribute("orbitdirection", elementValue);
                        break;
                    case "productDatapackSize":
                        this.current.setApproximateSize((long) Double.parseDouble(elementValue));
                        break;
                    default:
                        handleAdditionalElements(qName, elementValue);
                        break;
                }
            } catch (URISyntaxException e) {
                logger.warning(e.getMessage());
            }
        }
    }

    protected abstract void initRecord();

    protected abstract H createHelper(String productName);

    protected abstract String computeLocation();

    protected abstract void handleAdditionalElements(String qName, String text);
}

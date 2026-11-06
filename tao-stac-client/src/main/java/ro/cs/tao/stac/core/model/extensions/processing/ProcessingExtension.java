package ro.cs.tao.stac.core.model.extensions.processing;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;
import ro.cs.tao.stac.core.parser.STACParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ProcessingExtension <E extends Extensible> extends Extension<E> {

    public ProcessingExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return ProcessingFields.PREFIX;
    }

    @Override
    public void extractField(JsonNode node, String name) throws JacksonException {
        try {
            switch (name) {
                case ProcessingFields.PROCESSING_EXPRESSION:
                    setExpression(new STACParser().parse(node.toString(), Expression.class));
                    break;
                case ProcessingFields.PROCESSING_LINEAGE:
                    setLineage(JsonValueHelper.getStringValue(node));
                    break;
                case ProcessingFields.PROCESSING_LEVEL:
                    setLevel(Level.valueOf(JsonValueHelper.getStringValue(node)));
                    break;
                case ProcessingFields.PROCESSING_FACILITY:
                    setFacility(JsonValueHelper.getStringValue(node));
                    break;
                case ProcessingFields.PROCESSING_DATETIME:
                    setDateTime(JsonValueHelper.getDateTime(node, name));
                    break;
                case ProcessingFields.PROCESSING_VERSION:
                    setVersion(JsonValueHelper.getStringValue(node));
                    break;
                case ProcessingFields.PROCESSING_SOFTWARE:
                    final int size = node.size();
                    final Map<String, String> software = new LinkedHashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        software.put(JsonValueHelper.getStringValue(node.get(i).get(0)),
                                     JsonValueHelper.getStringValue(node.get(i).get(1)));
                    }
                    setSoftware(software);
                    break;
            }
        } catch (Exception e) {
            Logger.getLogger(ProcessingExtension.class.getName()).warning("Cannot extract field " + name + ": " + e.getMessage());
        }
    }

    public Expression getExpression() { return parent.getField(ProcessingFields.PROCESSING_EXPRESSION); }
    public void setExpression(Expression expression) { parent.addField(ProcessingFields.PROCESSING_EXPRESSION, expression); }

    public String getProcessingLevel() { return parent.getField(ProcessingFields.PROCESSING_LEVEL); }
    public void setProcessingLevel(String processingLevel) { parent.addField(ProcessingFields.PROCESSING_LEVEL, processingLevel); }

    public Level getLevel() { return parent.getField(ProcessingFields.PROCESSING_LEVEL); }
    public void setLevel(Level level) { parent.addField(ProcessingFields.PROCESSING_LEVEL, level); }

    public String getLineage() { return parent.getField(ProcessingFields.PROCESSING_LINEAGE); }
    public void setLineage(String lineage) { parent.addField(ProcessingFields.PROCESSING_LINEAGE, lineage); }

    public String getFacility() { return parent.getField(ProcessingFields.PROCESSING_FACILITY); }
    public void setFacility(String facility) { parent.addField(ProcessingFields.PROCESSING_FACILITY, facility); }

    public LocalDateTime getDateTime() { return parent.getField(ProcessingFields.PROCESSING_DATETIME); }
    public void setDateTime(LocalDateTime dateTime) { parent.addField(ProcessingFields.PROCESSING_DATETIME, dateTime); }

    public String getVersion() { return parent.getField(ProcessingFields.PROCESSING_VERSION); }
    public void setVersion(String version) { parent.addField(ProcessingFields.PROCESSING_VERSION, version); }

    public Map<String, String> getSoftware() { return parent.getField(ProcessingFields.PROCESSING_SOFTWARE); }
    public void setSoftware(Map<String, String> software) { parent.addField(ProcessingFields.PROCESSING_SOFTWARE, software); }

}



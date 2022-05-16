package com.github.gbaso.timesheet.csv;

import java.io.IOException;
import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class LocalDateProcessorDeserializer extends JsonDeserializer<LocalDate> {

    private final CSVRecordProcessor<String> processor = new AddLeadingZeroToDateTimeStrings();
    private final CSVRecordParser<LocalDate> parser    = new LocalDateParser();

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        TreeNode json = codec.readTree(p);
        var node = (JsonNode) json;
        String textValue = node.textValue();
        if (StringUtils.isBlank(textValue)) {
            return null;
        }
        return parser.parse(processor.process(textValue));
    }

}

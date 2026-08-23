package com.formdock.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Rfc4180CsvWriterTest {

    private static final byte[] UTF_8_BOM = {
        (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

    @Test
    void should_writeCanonicalUtf8Csv_when_dynamicFieldsRequireEscapingAndNeutralization()
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Rfc4180CsvWriter writer = new Rfc4180CsvWriter(output);

        writer.writeBom();
        writer.writeRecord(
                new String[] {"response_id", "text", "plus", "minus", "handle", "number"},
                new boolean[] {false, true, true, true, true, false});
        writer.writeRecord(
                new String[] {
                    "1",
                    "=SUM(1,2)\r\n한글 \"원문\"",
                    "  +명령",
                    "\u2003-명령",
                    "@handle",
                    "-12.34"
                },
                new boolean[] {false, true, true, true, true, false});
        writer.flush();

        assertThat(output.toByteArray()).startsWith(UTF_8_BOM);
        String csv = new String(
                output.toByteArray(),
                UTF_8_BOM.length,
                output.size() - UTF_8_BOM.length,
                StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo(
                "response_id,text,plus,minus,handle,number\r\n"
                        + "1,\"'=SUM(1,2)\r\n한글 \"\"원문\"\"\","
                        + "'  +명령,'\u2003-명령,'@handle,-12.34\r\n");
    }
}

package com.formdock.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

final class Rfc4180CsvWriter {

    private static final byte[] UTF_8_BOM = {
        (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };

    private final OutputStream outputStream;
    private final BufferedWriter writer;

    Rfc4180CsvWriter(OutputStream outputStream) {
        this.outputStream = outputStream;
        writer = new BufferedWriter(new OutputStreamWriter(
                outputStream,
                StandardCharsets.UTF_8));
    }

    void writeBom() throws IOException {
        outputStream.write(UTF_8_BOM);
    }

    void writeRecord(String[] values, boolean[] formulaProtected) throws IOException {
        if (values.length != formulaProtected.length) {
            throw new IllegalArgumentException("CSV value and protection counts must match");
        }
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            writeField(neutralizeFormula(values[index], formulaProtected[index]));
        }
        writer.write("\r\n");
    }

    void flush() throws IOException {
        writer.flush();
    }

    private void writeField(String value) throws IOException {
        String safeValue = value == null ? "" : value;
        boolean quoted = safeValue.indexOf(',') >= 0
                || safeValue.indexOf('"') >= 0
                || safeValue.indexOf('\r') >= 0
                || safeValue.indexOf('\n') >= 0;
        if (!quoted) {
            writer.write(safeValue);
            return;
        }

        writer.write('"');
        for (int index = 0; index < safeValue.length(); index++) {
            char character = safeValue.charAt(index);
            if (character == '"') {
                writer.write('"');
            }
            writer.write(character);
        }
        writer.write('"');
    }

    private String neutralizeFormula(String value, boolean formulaProtected) {
        if (!formulaProtected || value == null || value.isEmpty()) {
            return value;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            return isFormulaPrefix(codePoint) ? "'" + value : value;
        }
        return value;
    }

    private boolean isFormulaPrefix(int codePoint) {
        return codePoint == '=' || codePoint == '+' || codePoint == '-' || codePoint == '@';
    }
}

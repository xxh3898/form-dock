package com.formdock.export;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
interface CsvOutputStreamSupplier {

    OutputStream open() throws IOException;
}

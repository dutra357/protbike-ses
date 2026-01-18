package br.com.protbike.utils;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EmailFormatterHTML {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    EmailFormatterHTML() {}


}

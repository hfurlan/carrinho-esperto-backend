package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalIssuerRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalPaymentRequest;
import br.com.carrinhoesperto.fiscal.ingestion.NfceParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the São Paulo SEFAZ NFC-e public-consultation page
 * ({@code ConsultaQRCode.aspx}) into a {@link FiscalDocumentRequest} (M003
 * §9–§15). Semantic selectors and label matching are used rather than absolute
 * DOM positions, so whitespace and minor layout changes are tolerated.
 *
 * <p>Strict boundaries:
 * <ul>
 *   <li>item descriptions are taken verbatim — never canonicalized;</li>
 *   <li>fields the public page does not show (GTIN, cEANTrib, NCM, CEST,
 *       per-item discount) are left {@code null} — never inferred;</li>
 *   <li>consumer CPF / name are never read.</li>
 * </ul>
 * The parser performs no persistence.
 */
@Component
public class SaoPauloNfceParser {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

    private static final Pattern NUMBER = Pattern.compile("Número:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERIES = Pattern.compile("Série:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMISSION = Pattern.compile(
        "Emiss[aã]o:?\\s*(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2}:\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("[Cc][oó]digo\\s*:?\\s*([\\w.\\-/]+)");
    private static final Pattern CNPJ = Pattern.compile("(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2})");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("\\d[\\d.,]*");
    /** A value after Brazilian→plain normalisation: optional sign, digits, optional single fraction. */
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * @param html      raw consultation-page bytes (charset auto-detected)
     * @param accessKey the 44-digit key already extracted and validated from the QR URL
     */
    public FiscalDocumentRequest parse(byte[] html, String accessKey) {
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(html), null,
                "https://www.nfce.fazenda.sp.gov.br/");
        } catch (IOException e) {
            throw new NfceParseException("jsoup-io");
        }

        FiscalIssuerRequest issuer = parseIssuer(doc);
        Emission emission = parseEmission(doc);
        Totals totals = parseTotals(doc);
        List<FiscalItemRequest> items = parseItems(doc);
        if (items.isEmpty()) {
            throw new NfceParseException("no-items");
        }
        List<FiscalPaymentRequest> payments = parsePayments(doc);

        return new FiscalDocumentRequest(
            accessKey,
            emission.number(),
            emission.series(),
            emission.issuedAtIso(),
            issuer,
            totals.payable(),
            totals.merchandise(),
            totals.discount(),
            items,
            payments);
    }

    // ── Issuer ─────────────────────────────────────────────────────────────

    private FiscalIssuerRequest parseIssuer(Document doc) {
        Element nameEl = doc.selectFirst("#u20");
        if (nameEl == null) {
            nameEl = doc.selectFirst(".txtTopo");
        }
        if (nameEl == null) {
            throw new NfceParseException("issuer-name");
        }
        String name = collapse(nameEl.text());
        if (name.isEmpty()) {
            throw new NfceParseException("issuer-name");
        }

        String cnpj = null;
        String address = null;
        Element header = nameEl.parent();
        if (header != null) {
            for (Element line : header.select("div.text")) {
                String text = collapse(line.text());
                if (text.isEmpty()) {
                    continue;
                }
                String upper = deaccent(text).toUpperCase(Locale.ROOT);
                if (upper.startsWith("CNPJ")) {
                    Matcher m = CNPJ.matcher(text);
                    if (m.find()) {
                        cnpj = m.group(1).replaceAll("\\D", "");
                    }
                } else if (address == null && !upper.startsWith("IE") && !upper.startsWith("IM")) {
                    address = tidyAddress(text);
                }
            }
        }
        if (cnpj == null) {
            Matcher m = CNPJ.matcher(doc.text());
            if (m.find()) {
                cnpj = m.group(1).replaceAll("\\D", "");
            }
        }
        if (cnpj == null || cnpj.length() != 14) {
            throw new NfceParseException("issuer-cnpj");
        }
        return new FiscalIssuerRequest(cnpj, name, null, address);
    }

    // ── Emission (número / série / data-hora) ──────────────────────────────

    private Emission parseEmission(Document doc) {
        Element infos = doc.selectFirst("#infos");
        String scope = infos != null ? infos.text() : doc.text();

        Long number = longOrNull(NUMBER, scope);
        Integer series = intOrNull(SERIES, scope);

        Matcher m = EMISSION.matcher(scope);
        if (!m.find()) {
            throw new NfceParseException("emission-datetime");
        }
        try {
            LocalDate date = LocalDate.parse(m.group(1), DATE);
            LocalTime time = LocalTime.parse(m.group(2));
            String iso = LocalDateTime.of(date, time)
                .atZone(SAO_PAULO)
                .toOffsetDateTime()
                .toString();
            return new Emission(number, series, iso);
        } catch (DateTimeParseException e) {
            throw new NfceParseException("emission-datetime");
        }
    }

    // ── Totals ─────────────────────────────────────────────────────────────

    private Totals parseTotals(Document doc) {
        Element block = doc.selectFirst("#totalNota");
        if (block == null) {
            throw new NfceParseException("totals-block");
        }
        BigDecimal merchandise = null;
        BigDecimal discount = null;
        BigDecimal payable = null;

        for (Element div : block.select("div")) {
            // jsoup's select() can return the context element itself; the total
            // rows are its children, never the block.
            if (div == block) {
                continue;
            }
            Element numb = div.selectFirst("span.totalNumb");
            if (numb == null) {
                continue;
            }
            Element txLabel = div.selectFirst("label.tx");
            Element label = txLabel != null ? txLabel : div.selectFirst("label");
            if (label == null) {
                continue;
            }
            String key = deaccent(collapse(label.text())).toLowerCase(Locale.ROOT);
            // Payment rows also carry span.totalNumb; skip them here.
            if (txLabel != null && !key.contains("valor pago")) {
                continue;
            }
            if (key.contains("a pagar") && payable == null) {
                payable = money(numb.text());
            } else if (key.contains("desconto") && discount == null) {
                discount = money(numb.text());
            } else if (key.contains("valor total") && merchandise == null) {
                merchandise = money(numb.text());
            }
        }
        if (payable == null) {
            payable = merchandise;
        }
        if (payable == null) {
            throw new NfceParseException("totals-payable");
        }
        return new Totals(merchandise, discount, payable);
    }

    // ── Items ──────────────────────────────────────────────────────────────

    private List<FiscalItemRequest> parseItems(Document doc) {
        Element table = doc.selectFirst("#tabResult");
        if (table == null) {
            throw new NfceParseException("items-table");
        }
        List<FiscalItemRequest> items = new ArrayList<>();
        for (Element row : table.select("tr")) {
            Element descEl = row.selectFirst("span.txtTit2, span.txtTit, span.txtTit3");
            if (descEl == null) {
                continue;
            }
            String rawDescription = collapse(descEl.text());
            if (rawDescription.isEmpty()) {
                continue;
            }

            String supplierItemCode = firstGroup(CODE, textOf(row.selectFirst("span.RCod")));
            BigDecimal quantity = numberIn(labelValue(row.selectFirst("span.Rqtd")));
            String unit = labelValue(row.selectFirst("span.RUN"));
            BigDecimal unitPrice = numberIn(labelValue(row.selectFirst("span.RvlUnit")));
            BigDecimal totalPrice = numberIn(textOf(row.selectFirst("span.valor")));

            if (quantity == null || unitPrice == null || totalPrice == null || unit == null || unit.isEmpty()) {
                throw new NfceParseException("item-fields");
            }

            items.add(new FiscalItemRequest(
                null,                 // line number assigned by FiscalDocumentService, in this order
                rawDescription,       // verbatim fiscal description
                null,                 // gtin — not shown on the public page
                null,                 // gtinTaxable — not shown
                supplierItemCode,     // cProd
                quantity,
                unit,
                unitPrice,
                totalPrice,
                null,                 // per-item discount — not shown
                null,                 // ncm — not shown
                null));               // cest — not shown
        }
        return items;
    }

    // ── Payments ───────────────────────────────────────────────────────────

    private List<FiscalPaymentRequest> parsePayments(Document doc) {
        Element block = doc.selectFirst("#totalNota");
        List<FiscalPaymentRequest> payments = new ArrayList<>();
        if (block == null) {
            return payments;
        }
        for (Element div : block.select("div")) {
            if (div == block) {
                continue;
            }
            Element txLabel = div.selectFirst("label.tx");
            Element numb = div.selectFirst("span.totalNumb");
            if (txLabel == null || numb == null) {
                continue;
            }
            String method = collapse(txLabel.text());
            String methodKey = deaccent(method).toLowerCase(Locale.ROOT);
            // "Troco" is change given back, not a payment line (and the page
            // renders it as "NaN" when there is none).
            if (method.isEmpty()
                    || methodKey.contains("valor pago")
                    || methodKey.contains("troco")) {
                continue;
            }
            BigDecimal amount = money(numb.text());
            if (amount == null) {
                continue;
            }
            payments.add(new FiscalPaymentRequest(method, null, amount));
        }
        return payments;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String textOf(Element el) {
        return el == null ? "" : el.text();
    }

    /** Text of a {@code <span><strong>Label:</strong>value</span>} — the value part only. */
    private static String labelValue(Element el) {
        if (el == null) {
            return null;
        }
        String own = collapse(el.ownText());
        if (!own.isEmpty()) {
            return stripLeadingLabel(own);
        }
        String all = collapse(el.text());
        return stripLeadingLabel(all);
    }

    private static String stripLeadingLabel(String value) {
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1).strip() : value.strip();
    }

    /** First numeric token in the text, parsed from Brazilian format. */
    private static BigDecimal numberIn(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = NUMERIC_TOKEN.matcher(normalizeSpaces(text));
        return m.find() ? money(m.group()) : null;
    }

    /**
     * Brazilian money/quantity string → {@link BigDecimal} ({@code "1.234,56"} →
     * {@code 1234.56}). Returns {@code null} for anything that is not a plain
     * number after normalisation — e.g. the literal {@code "NaN"} the SEFAZ page
     * renders for an absent "Troco", an empty cell, or a stray non-numeric token.
     * A missing <em>required</em> value is caught later by the field-level
     * {@code item-fields}/{@code totals-payable} checks, with a clearer reason.
     */
    private static BigDecimal money(String raw) {
        if (raw == null) {
            return null;
        }
        String s = normalizeSpaces(raw)
            .replaceAll("(?i)R\\$", "")
            .replaceAll("\\s", "")
            .replace(".", "")
            .replace(",", ".");
        if (!PLAIN_DECIMAL.matcher(s).matches()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstGroup(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static Long longOrNull(Pattern pattern, String text) {
        String v = firstGroup(pattern, text);
        try {
            return v == null ? null : Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Pattern pattern, String text) {
        String v = firstGroup(pattern, text);
        try {
            return v == null ? null : Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final Pattern PAD_SPACE = Pattern.compile("[\\u00A0\\u2007\\u202F]");

    private static String normalizeSpaces(String text) {
        return PAD_SPACE.matcher(text).replaceAll(" ");
    }

    private static String collapse(String text) {
        return text == null ? "" : normalizeSpaces(text).replaceAll("\\s+", " ").strip();
    }

    private static String tidyAddress(String text) {
        return collapse(text).replaceAll("\\s*,\\s*", ", ").replaceAll("^,\\s*", "").strip();
    }

    private static String deaccent(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private record Emission(Long number, Integer series, String issuedAtIso) {
    }

    private record Totals(BigDecimal merchandise, BigDecimal discount, BigDecimal payable) {
    }
}

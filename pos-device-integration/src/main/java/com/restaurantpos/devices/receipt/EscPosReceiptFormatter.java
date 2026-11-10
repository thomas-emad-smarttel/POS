package com.restaurantpos.devices.receipt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Configurable ESC/POS receipt payload builder supporting multiple paper widths,
 * character encodings (UTF-8, CP864, WPC1256), and English/Arabic/Bilingual formatting.
 */
public final class EscPosReceiptFormatter {

    private static final byte[] ESC_INIT = new byte[]{0x1B, 0x40};
    private static final byte[] ESC_ALIGN_CENTER = new byte[]{0x1B, 0x61, 0x01};
    private static final byte[] ESC_ALIGN_LEFT = new byte[]{0x1B, 0x61, 0x00};
    private static final byte[] ESC_BOLD_ON = new byte[]{0x1B, 0x45, 0x01};
    private static final byte[] ESC_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00};
    private static final byte[] GS_CUT = new byte[]{0x1D, 0x56, 0x42, 0x00};

    public record LineItem(String name, String qty, String amount, List<String> modifiers) {
    }

    public record PaymentLine(String method, String amount, String reference) {
    }

    public record ReceiptDocument(
            String restaurantName,
            String branchName,
            String branchAddress,
            String branchPhone,
            String receiptNumber,
            String receiptType,
            String language,
            int paperWidth,
            String encoding,
            boolean duplicateCopy,
            String orderNumber,
            String timestampText,
            String cashierName,
            String currencyCode,
            List<LineItem> items,
            String subtotal,
            String discountTotal,
            String taxTotal,
            String grandTotal,
            List<PaymentLine> payments,
            String paidTotal,
            String changeAmount,
            String remainingBalance,
            String footerMessage
    ) {
    }

    public record RenderedReceipt(String plainText, byte[] escPosBytes) {
    }

    private EscPosReceiptFormatter() {
    }

    public static RenderedReceipt render(ReceiptDocument doc) {
        int width = Math.max(32, Math.min(64, doc.paperWidth() > 0 ? doc.paperWidth() : 42));
        boolean arabic = "AR".equalsIgnoreCase(doc.language());
        boolean bilingual = "BILINGUAL".equalsIgnoreCase(doc.language());
        String sep = "-".repeat(width);
        String eqSep = "=".repeat(width);

        StringBuilder sb = new StringBuilder();
        if (doc.duplicateCopy() || "DUPLICATE".equalsIgnoreCase(doc.receiptType())) {
            sb.append(center(bilingual ? "*** DUPLICATE / نسخة مكررة ***"
                    : (arabic ? "*** نسخة مكررة ***" : "*** DUPLICATE RECEIPT ***"), width)).append('\n');
        } else if ("REFUND".equalsIgnoreCase(doc.receiptType())) {
            sb.append(center(bilingual ? "*** REFUND / إيصال استرداد ***"
                    : (arabic ? "*** إيصال استرداد ***" : "*** REFUND RECEIPT ***"), width)).append('\n');
        }

        sb.append(center(doc.restaurantName(), width)).append('\n');
        if (doc.branchName() != null && !doc.branchName().isBlank()) {
            sb.append(center(doc.branchName(), width)).append('\n');
        }
        if (doc.branchAddress() != null && !doc.branchAddress().isBlank()) {
            sb.append(center(doc.branchAddress(), width)).append('\n');
        }
        if (doc.branchPhone() != null && !doc.branchPhone().isBlank()) {
            sb.append(center("Tel: " + doc.branchPhone(), width)).append('\n');
        }
        sb.append(sep).append('\n');
        sb.append(pair(label("Receipt #", "رقم الإيصال", arabic, bilingual), doc.receiptNumber(), width)).append('\n');
        if (doc.orderNumber() != null) {
            sb.append(pair(label("Order #", "رقم الطلب", arabic, bilingual), doc.orderNumber(), width)).append('\n');
        }
        sb.append(pair(label("Date", "التاريخ", arabic, bilingual), doc.timestampText(), width)).append('\n');
        if (doc.cashierName() != null) {
            sb.append(pair(label("Cashier", "أمين الصندوق", arabic, bilingual), doc.cashierName(), width)).append('\n');
        }
        sb.append(sep).append('\n');

        if (doc.items() != null) {
            for (LineItem item : doc.items()) {
                sb.append(pair(item.qty() + "x " + item.name(), item.amount(), width)).append('\n');
                if (item.modifiers() != null) {
                    for (String mod : item.modifiers()) {
                        sb.append("   + ").append(mod).append('\n');
                    }
                }
            }
            sb.append(sep).append('\n');
        }

        sb.append(pair(label("Subtotal", "المجموع الفرعي", arabic, bilingual), doc.currencyCode() + " " + doc.subtotal(), width)).append('\n');
        sb.append(pair(label("Discount", "الخصم", arabic, bilingual), doc.currencyCode() + " " + doc.discountTotal(), width)).append('\n');
        sb.append(pair(label("Tax", "الضريبة", arabic, bilingual), doc.currencyCode() + " " + doc.taxTotal(), width)).append('\n');
        sb.append(eqSep).append('\n');
        sb.append(pair(label("TOTAL", "الإجمالي", arabic, bilingual), doc.currencyCode() + " " + doc.grandTotal(), width)).append('\n');
        sb.append(eqSep).append('\n');

        if (doc.payments() != null && !doc.payments().isEmpty()) {
            for (PaymentLine pay : doc.payments()) {
                String labelText = pay.method() + (pay.reference() != null ? " (" + pay.reference() + ")" : "");
                sb.append(pair(labelText, doc.currencyCode() + " " + pay.amount(), width)).append('\n');
            }
            sb.append(pair(label("Paid", "المدفوع", arabic, bilingual), doc.currencyCode() + " " + doc.paidTotal(), width)).append('\n');
            sb.append(pair(label("Change", "الباقي", arabic, bilingual), doc.currencyCode() + " " + doc.changeAmount(), width)).append('\n');
            sb.append(pair(label("Balance Due", "المبلغ المتبقي", arabic, bilingual), doc.currencyCode() + " " + doc.remainingBalance(), width)).append('\n');
        }

        if (doc.footerMessage() != null && !doc.footerMessage().isBlank()) {
            sb.append(sep).append('\n');
            sb.append(center(doc.footerMessage(), width)).append('\n');
        }

        String text = sb.toString();
        byte[] escPos = encodeEscPos(text, doc.encoding());
        return new RenderedReceipt(text, escPos);
    }

    private static byte[] encodeEscPos(String plainText, String encodingName) {
        Charset charset;
        try {
            charset = (encodingName != null && !encodingName.isBlank())
                    ? Charset.forName(encodingName)
                    : StandardCharsets.UTF_8;
        } catch (Exception ignored) {
            charset = StandardCharsets.UTF_8;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ESC_INIT);
        if ("CP864".equalsIgnoreCase(encodingName)) {
            out.writeBytes(new byte[]{0x1B, 0x74, 0x22}); // Arabic CP864 codepage select
        } else if ("WPC1256".equalsIgnoreCase(encodingName)) {
            out.writeBytes(new byte[]{0x1B, 0x74, 0x2D}); // Arabic WPC1256 codepage select
        }
        out.writeBytes(ESC_ALIGN_LEFT);
        out.writeBytes(ESC_BOLD_ON);
        out.writeBytes(ESC_BOLD_OFF);
        out.writeBytes(plainText.getBytes(charset));
        out.writeBytes(ESC_ALIGN_CENTER);
        out.writeBytes("\n\n".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(GS_CUT);
        return out.toByteArray();
    }

    private static String label(String en, String ar, boolean arabic, boolean bilingual) {
        if (bilingual) {
            return en + " / " + ar;
        }
        return arabic ? ar : en;
    }

    private static String pair(String left, String right, int width) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        int space = width - l.length() - r.length();
        if (space >= 1) {
            return l + " ".repeat(space) + r;
        }
        return l + " " + r;
    }

    private static String center(String text, int width) {
        if (text == null || text.length() >= width) {
            return text == null ? "" : text;
        }
        int pad = (width - text.length()) / 2;
        return " ".repeat(pad) + text;
    }
}

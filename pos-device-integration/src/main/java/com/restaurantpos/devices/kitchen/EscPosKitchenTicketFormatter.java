package com.restaurantpos.devices.kitchen;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ESC/POS kitchen ticket formatter supporting configurable paper width, character encoding,
 * English/Arabic/Bilingual templates, and distinct banners for INITIAL, SUPPLEMENTAL, REFIRE, and CANCELLED jobs.
 */
public final class EscPosKitchenTicketFormatter {

    private static final byte[] ESC_INIT = new byte[]{0x1B, 0x40};
    private static final byte[] ESC_BOLD_ON = new byte[]{0x1B, 0x45, 0x01};
    private static final byte[] ESC_BOLD_OFF = new byte[]{0x1B, 0x45, 0x00};
    private static final byte[] GS_CUT = new byte[]{0x1D, 0x56, 0x42, 0x00};

    public record TicketLine(
            String itemName,
            String variantName,
            String quantity,
            List<String> modifiers,
            String prepInstructions,
            String notes
    ) {
    }

    public record KitchenTicketDocument(
            String stationName,
            String ticketNumber,
            String ticketKind,
            String jobType,
            String orderNumber,
            String orderType,
            String tableLabel,
            String waiterName,
            String priorityLevel,
            String timestampText,
            String language,
            int paperWidth,
            String encoding,
            String noticeReason,
            List<TicketLine> lines
    ) {
    }

    public record RenderedTicket(String plainText, byte[] escPosBytes) {
    }

    private EscPosKitchenTicketFormatter() {
    }

    public static RenderedTicket render(KitchenTicketDocument doc) {
        int width = Math.max(32, Math.min(64, doc.paperWidth() > 0 ? doc.paperWidth() : 42));
        boolean arabic = "AR".equalsIgnoreCase(doc.language());
        boolean bilingual = "BILINGUAL".equalsIgnoreCase(doc.language());
        String eq = "=".repeat(width);
        String dash = "-".repeat(width);

        StringBuilder sb = new StringBuilder();
        String kindBanner = resolveBanner(doc.jobType(), doc.ticketKind(), arabic, bilingual);
        if (kindBanner != null) {
            sb.append(center(kindBanner, width)).append('\n');
        }
        sb.append(eq).append('\n');
        sb.append(center("[ " + doc.stationName().toUpperCase() + " ]", width)).append('\n');
        sb.append(center(label("TICKET ", "تذكرة ", arabic, bilingual) + doc.ticketNumber(), width)).append('\n');
        sb.append(eq).append('\n');

        sb.append(pair(label("Order #", "الطلب", arabic, bilingual), doc.orderNumber(), width)).append('\n');
        sb.append(pair(label("Type", "النوع", arabic, bilingual),
                doc.tableLabel() != null ? doc.orderType() + " / " + doc.tableLabel() : doc.orderType(), width)).append('\n');
        if (doc.waiterName() != null && !doc.waiterName().isBlank()) {
            sb.append(pair(label("Staff", "الموظف", arabic, bilingual), doc.waiterName(), width)).append('\n');
        }
        if (doc.priorityLevel() != null && !"NORMAL".equalsIgnoreCase(doc.priorityLevel())) {
            sb.append(pair(label("Priority", "الأولوية", arabic, bilingual), "*** " + doc.priorityLevel() + " ***", width)).append('\n');
        }
        sb.append(pair(label("Time", "الوقت", arabic, bilingual), doc.timestampText(), width)).append('\n');
        if (doc.noticeReason() != null && !doc.noticeReason().isBlank()) {
            sb.append(dash).append('\n');
            sb.append(label("Reason: ", "السبب: ", arabic, bilingual)).append(doc.noticeReason()).append('\n');
        }
        sb.append(dash).append('\n');

        if (doc.lines() != null) {
            for (TicketLine line : doc.lines()) {
                String title = line.variantName() != null
                        ? line.itemName() + " [" + line.variantName() + "]"
                        : line.itemName();
                sb.append(line.quantity()).append("x  ").append(title).append('\n');
                if (line.modifiers() != null) {
                    for (String mod : line.modifiers()) {
                        sb.append("    + ").append(mod).append('\n');
                    }
                }
                if (line.prepInstructions() != null && !line.prepInstructions().isBlank()) {
                    sb.append("    [PREP] ").append(line.prepInstructions()).append('\n');
                }
                if (line.notes() != null && !line.notes().isBlank()) {
                    sb.append("    [NOTE] ").append(line.notes()).append('\n');
                }
            }
        }
        sb.append(eq).append('\n');

        String text = sb.toString();
        byte[] bytes = encodeEscPos(text, doc.encoding());
        return new RenderedTicket(text, bytes);
    }

    private static String resolveBanner(String jobType, String ticketKind, boolean arabic, boolean bilingual) {
        String key = (jobType != null && !"INITIAL".equalsIgnoreCase(jobType)) ? jobType : ticketKind;
        if (key == null || "INITIAL".equalsIgnoreCase(key)) {
            return null;
        }
        return switch (key.toUpperCase()) {
            case "REFIRE" -> bilingual ? "*** REFIRE / إعادة تحضير ***"
                    : (arabic ? "*** إعادة تحضير ***" : "*** REFIRE ORDER ***");
            case "SUPPLEMENTAL", "ADDITION" -> bilingual ? "*** NEW ADDITION / إضافة جديدة ***"
                    : (arabic ? "*** إضافة جديدة ***" : "*** NEW ITEM ADDITION ***");
            case "CANCELLED", "VOID" -> bilingual ? "*** CANCELLED / ملغي ***"
                    : (arabic ? "*** ملغي ***" : "*** CANCELLED TICKET ***");
            case "UPDATE" -> bilingual ? "*** ORDER UPDATED / تحديث طلب ***"
                    : (arabic ? "*** تحديث طلب ***" : "*** ORDER UPDATED ***");
            default -> "*** " + key.toUpperCase() + " ***";
        };
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
            out.writeBytes(new byte[]{0x1B, 0x74, 0x22});
        } else if ("WPC1256".equalsIgnoreCase(encodingName)) {
            out.writeBytes(new byte[]{0x1B, 0x74, 0x2D});
        }
        out.writeBytes(ESC_BOLD_ON);
        out.writeBytes(ESC_BOLD_OFF);
        out.writeBytes(plainText.getBytes(charset));
        out.writeBytes("\n\n".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(GS_CUT);
        return out.toByteArray();
    }

    private static String label(String en, String ar, boolean arabic, boolean bilingual) {
        if (bilingual) return en + " / " + ar;
        return arabic ? ar : en;
    }

    private static String pair(String left, String right, int width) {
        String l = left == null ? "" : left;
        String r = right == null ? "" : right;
        int space = width - l.length() - r.length();
        return space >= 1 ? l + " ".repeat(space) + r : l + " " + r;
    }

    private static String center(String text, int width) {
        if (text == null || text.length() >= width) {
            return text == null ? "" : text;
        }
        return " ".repeat((width - text.length()) / 2) + text;
    }
}

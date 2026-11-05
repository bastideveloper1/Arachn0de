package com.r0ybt.arachn0de.document;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/** Synthetic, package-free Android runtime validation. No application context, database or APK. */
public final class NativeReportSmoke {
    public static void main(String[] args) throws Exception {
        // app_process is not forked from the UI zygote; initialize its font map explicitly.
        Class<?> typeface = Class.forName("android.graphics.Typeface");
        Class<?> systemFonts = Class.forName("android.graphics.fonts.SystemFonts");
        Object config = systemFonts.getDeclaredMethod("getSystemPreinstalledFontConfigFromLegacyXml").invoke(null);
        Class<?> fontConfig = Class.forName("android.text.FontConfig");
        Map<?, ?> fallback = (Map<?, ?>) systemFonts.getDeclaredMethod("buildSystemFallback", fontConfig).invoke(null, config);
        Map<?, ?> map = (Map<?, ?>) systemFonts.getDeclaredMethod("buildSystemTypefaces", fontConfig, Map.class).invoke(null, config, fallback);
        if (map.isEmpty()) throw new AssertionError("System font map is empty");
        try { typeface.getDeclaredMethod("setSystemFontMap", Map.class).invoke(null, map); }
        catch (NoSuchMethodException newerRuntime) {
            typeface.getDeclaredMethod("setSystemFontMap", Map.class, List.class).invoke(null, map, Collections.emptyList());
        }
        try { typeface.getDeclaredMethod("loadNativeSystemFonts").invoke(null); }
        catch (NoSuchMethodException olderRuntime) { /* Older Android initializes eagerly. */ }
        File directory = new File(args[0]);
        if (!directory.isDirectory()) throw new IllegalArgumentException("Explicit output directory required");
        Bitmap bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png); bitmap.recycle();
        Map<String, ReportImage> images = Collections.singletonMap("fixture", new ReportImage(png.toByteArray(), 400, 300));
        List<ReportSection> sections = new ArrayList<>();
        sections.add(new ReportSection("Núcleo sintético", 0, "", Collections.emptyList(), Collections.singletonList(new ReportPart.Text("Introducción antes de las secciones.", Collections.emptySet()))));
        for (int i = 1; i <= 12; i++) {
            List<ReportPart> parts = new ArrayList<>();
            parts.add(new ReportPart.Text("Texto anterior a la imagen.\n", Collections.emptySet()));
            parts.add(new ReportPart.Image("fixture", "Imagen sintética"));
            StringBuilder text = new StringBuilder();
            for (int word = 0; word < 35; word++) text.append("Texto junto a la imagen y continuación a todo el ancho. Acentos: áéíóú y ñ. ");
            parts.add(new ReportPart.Text(text.toString(), Collections.emptySet()));
            sections.add(new ReportSection("Tarea " + i, 1, Integer.toString(i), Collections.singletonList("Estado: " + (i % 2 == 0 ? "Completada" : "Pendiente")), parts));
        }
        ReportContent content = new ReportContent(sections, images);
        Function0<Unit> check = () -> Unit.INSTANCE;
        for (ReportImageLayout layout : ReportImageLayout.values()) {
            ReportOptions options = new ReportOptions("Informe nativo de validación", "Datos sintéticos", "2026-10-19", true, false, ReportFormat.PDF, layout);
            ReportPlan plan = ReportLayout.INSTANCE.measure(content, options, check);
            File pdf = new File(directory, "report-" + layout.name() + ".pdf");
            try (FileOutputStream out = new FileOutputStream(pdf)) { ReportPdfKt.writeReportPdf(content, plan, out, check); }
            try (PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))) {
                if (renderer.getPageCount() != plan.getPages().size()) throw new AssertionError("Page count");
                for (int page = 0; page < renderer.getPageCount(); page++) {
                    try (PdfRenderer.Page p = renderer.openPage(page)) {
                        Bitmap rendered = Bitmap.createBitmap(298, 421, Bitmap.Config.ARGB_8888);
                        p.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        if (rendered.getPixel(0, 0) == Color.TRANSPARENT) throw new AssertionError("Empty PDF rendering");
                        rendered.recycle();
                    }
                }
            }
            File docx = new File(directory, "report-" + layout.name() + ".docx");
            try (FileOutputStream out = new FileOutputStream(docx)) { ReportDocx.INSTANCE.write(content, options, out, check); }
            System.out.println(layout.name() + " PDF pages=" + plan.getPages().size() + " headings=" + plan.getSectionPages() + " bytes=" + pdf.length());
        }
        content.release();
        System.out.println("NATIVE_REPORT_OK");
    }
}
